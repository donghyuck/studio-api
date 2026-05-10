package studio.one.base.user.application.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import studio.one.base.user.domain.model.company.CompanyJoinRequestRef;
import studio.one.base.user.domain.model.company.CompanyJoinRequestStatus;
import studio.one.base.user.domain.model.company.CompanyMemberKeyRef;
import studio.one.base.user.domain.model.company.CompanyMemberKeyStatus;
import studio.one.base.user.domain.model.company.CompanyRole;
import studio.one.base.user.domain.model.company.CompanyStatus;
import studio.one.base.user.domain.model.ApplicationCompany;
import studio.one.base.user.domain.model.ApplicationCompanyJoinRequest;
import studio.one.base.user.domain.model.ApplicationCompanyMemberKey;
import studio.one.base.user.domain.error.CompanyJoinRequestException;
import studio.one.base.user.domain.port.ApplicationCompanyJoinRequestRepository;
import studio.one.base.user.domain.port.ApplicationCompanyMemberKeyRepository;
import studio.one.base.user.domain.port.ApplicationCompanyRepository;
import studio.one.base.user.application.usecase.ApplicationCompanyJoinRequestService;
import studio.one.base.user.application.usecase.ApplicationCompanyMemberService;
import studio.one.platform.exception.NotFoundException;

@Service(ApplicationCompanyJoinRequestService.SERVICE_NAME)
@RequiredArgsConstructor
@Transactional
public class ApplicationCompanyJoinRequestServiceImpl implements ApplicationCompanyJoinRequestService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final Base64.Encoder BASE64_URL = Base64.getUrlEncoder().withoutPadding();

    private final ApplicationCompanyRepository companyRepository;
    private final ApplicationCompanyMemberKeyRepository memberKeyRepository;
    private final ApplicationCompanyJoinRequestRepository joinRequestRepository;
    private final ApplicationCompanyMemberService memberService;

    @Override
    public CompanyMemberKeyRef createMemberKey(Long companyId, CompanyRole role, Instant expiresAt, Integer maxUses, Long actorUserId, boolean bypassRoleLimit) {
        requirePositive(companyId, "companyId");
        requirePositive(actorUserId, "actorUserId");
        if (maxUses != null && maxUses <= 0) {
            throw new IllegalArgumentException("maxUses must be positive");
        }
        if (expiresAt != null && !expiresAt.isAfter(Instant.now())) {
            throw new IllegalArgumentException("expiresAt must be in the future");
        }

        ApplicationCompany company = company(companyId);
        if (company.getStatus() != CompanyStatus.ACTIVE) {
            throw CompanyJoinRequestException.companyInactive(companyId);
        }

        CompanyRole resolvedRole = role == null ? CompanyRole.MEMBER : role;
        assertAssignableRole(companyId, actorUserId, resolvedRole, bypassRoleLimit);

        String plainKey = generateMemberKey();
        ApplicationCompanyMemberKey key = new ApplicationCompanyMemberKey();
        key.setCompany(company);
        key.setCompanyId(companyId);
        key.setRole(resolvedRole);
        key.setKeyHash(hash(plainKey));
        key.setStatus(CompanyMemberKeyStatus.ACTIVE);
        key.setExpiresAt(expiresAt);
        key.setMaxUses(maxUses);
        key.setCreatedBy(actorUserId);
        key.setUpdatedBy(actorUserId);
        return memberKeyRepository.save(key).toRef(plainKey);
    }

    @Override
    public CompanyJoinRequestRef createSelfRequest(String memberKey, Long userId, String name, String email, String message) {
        requirePositive(userId, "userId");
        return createRequest(memberKey, userId, trimToNull(name), normalize(email), trimToNull(message), userId);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<CompanyJoinRequestRef> getRequests(Long companyId, CompanyJoinRequestStatus status, Pageable pageable) {
        requirePositive(companyId, "companyId");
        company(companyId);
        return joinRequestRepository.findAllByCompanyId(companyId, status, pageable)
                .map(ApplicationCompanyJoinRequest::toRef);
    }

    @Override
    public CompanyJoinRequestRef approve(Long companyId, Long requestId, Long actorUserId, boolean bypassRoleLimit) {
        ApplicationCompanyJoinRequest request = pendingRequestForUpdate(companyId, requestId);
        assertCompanyActive(requestCompanyId(request));
        ApplicationCompanyMemberKey key = memberKey(request);
        validateUsableKey(key, 0);
        Long userId = resolveUserId(request);
        if (memberService.isCompanyMember(companyId, userId)) {
            throw CompanyJoinRequestException.alreadyMember(companyId, userId);
        }
        assertAssignableRole(companyId, actorUserId, request.getRequestedRole(), bypassRoleLimit);
        memberService.addMember(companyId, userId, request.getRequestedRole(), actorUserId, true);
        key.setUsedCount(key.getUsedCount() + 1);
        memberKeyRepository.save(key);
        request.setUserId(userId);
        request.setStatus(CompanyJoinRequestStatus.APPROVED);
        request.setDecidedAt(Instant.now());
        request.setDecidedBy(actorUserId);
        return joinRequestRepository.save(request).toRef();
    }

    @Override
    public CompanyJoinRequestRef reject(Long companyId, Long requestId, Long actorUserId) {
        ApplicationCompanyJoinRequest request = pendingRequestForUpdate(companyId, requestId);
        request.setStatus(CompanyJoinRequestStatus.REJECTED);
        request.setDecidedAt(Instant.now());
        request.setDecidedBy(actorUserId);
        return joinRequestRepository.save(request).toRef();
    }

    private ApplicationCompanyJoinRequest saveNewRequest(ApplicationCompanyJoinRequest request) {
        try {
            return joinRequestRepository.save(request);
        } catch (DataIntegrityViolationException e) {
            throw CompanyJoinRequestException.duplicatePending(requestCompanyId(request), request.getUserId());
        }
    }

    private CompanyJoinRequestRef createRequest(
            String rawMemberKey,
            Long userId,
            String name,
            String email,
            String message,
            Long requestedBy) {
        ApplicationCompanyMemberKey key = memberKeyRepository.findForUpdateByKeyHash(hash(rawMemberKey))
                .orElseThrow(CompanyJoinRequestException::invalidMemberKey);
        long pendingReservations = joinRequestRepository.countPendingByKeyId(key.getKeyId());
        validateUsableKey(key, pendingReservations);
        Long companyId = keyCompanyId(key);
        assertCompanyActive(companyId);
        if (memberService.isCompanyMember(companyId, userId)) {
            throw CompanyJoinRequestException.alreadyMember(companyId, userId);
        }
        if (joinRequestRepository.existsPendingByCompanyIdAndUserId(companyId, userId)) {
            throw CompanyJoinRequestException.duplicatePending(companyId, userId);
        }

        ApplicationCompanyJoinRequest request = new ApplicationCompanyJoinRequest();
        request.setCompany(key.getCompany());
        request.setCompanyId(companyId);
        request.setMemberKey(key);
        request.setKeyId(key.getKeyId());
        request.setUserId(userId);
        request.setName(name);
        request.setEmail(email);
        request.setMessage(message);
        request.setRequestedRole(key.getRole());
        request.setStatus(CompanyJoinRequestStatus.PENDING);
        request.setRequestedBy(requestedBy);
        return saveNewRequest(request).toRef();
    }

    private ApplicationCompanyMemberKey memberKey(ApplicationCompanyJoinRequest request) {
        if (request.getMemberKey() != null) {
            Long entityKeyId = request.getMemberKey().getKeyId();
            if (entityKeyId == null) {
                return request.getMemberKey();
            }
            return memberKeyRepository.findForUpdateById(entityKeyId)
                    .orElseThrow(CompanyJoinRequestException::invalidMemberKey);
        }
        Long keyId = request.getKeyId();
        if (keyId == null) {
            throw CompanyJoinRequestException.invalidMemberKey();
        }
        return memberKeyRepository.findForUpdateById(keyId)
                .orElseThrow(CompanyJoinRequestException::invalidMemberKey);
    }

    private void assertCompanyActive(Long companyId) {
        ApplicationCompany company = company(companyId);
        if (company.getStatus() != CompanyStatus.ACTIVE) {
            throw CompanyJoinRequestException.companyInactive(companyId);
        }
    }

    private void assertAssignableRole(Long companyId, Long actorUserId, CompanyRole requestedRole, boolean bypassRoleLimit) {
        if (bypassRoleLimit) {
            return;
        }
        CompanyRole actorRole = memberService.getCompanyRole(companyId, actorUserId);
        if (actorRole == null || requestedRole.rank() > actorRole.rank()) {
            throw new AccessDeniedException("Cannot issue member key for role " + requestedRole);
        }
    }

    private void validateUsableKey(ApplicationCompanyMemberKey key, long pendingReservations) {
        if (key.getStatus() != CompanyMemberKeyStatus.ACTIVE) {
            throw CompanyJoinRequestException.invalidMemberKey();
        }
        Instant now = Instant.now();
        if (key.getExpiresAt() != null && !key.getExpiresAt().isAfter(now)) {
            throw CompanyJoinRequestException.expiredMemberKey();
        }
        if (key.getMaxUses() != null && key.getUsedCount() + pendingReservations >= key.getMaxUses()) {
            throw CompanyJoinRequestException.exhaustedMemberKey();
        }
    }

    private ApplicationCompanyJoinRequest pendingRequest(Long companyId, Long requestId) {
        return pendingRequest(companyId, requestId, false);
    }

    private ApplicationCompanyJoinRequest pendingRequestForUpdate(Long companyId, Long requestId) {
        return pendingRequest(companyId, requestId, true);
    }

    private ApplicationCompanyJoinRequest pendingRequest(Long companyId, Long requestId, boolean forUpdate) {
        requirePositive(companyId, "companyId");
        requirePositive(requestId, "requestId");
        ApplicationCompanyJoinRequest request = (forUpdate
                ? joinRequestRepository.findForUpdateById(requestId)
                : joinRequestRepository.findById(requestId))
                .orElseThrow(() -> new NotFoundException("CompanyJoinRequest", requestId));
        Long requestCompanyId = requestCompanyId(request);
        if (!Objects.equals(companyId, requestCompanyId)) {
            throw new NotFoundException("CompanyJoinRequest", requestId);
        }
        if (request.getStatus() != CompanyJoinRequestStatus.PENDING) {
            throw CompanyJoinRequestException.alreadyDecided(requestId);
        }
        return request;
    }

    private Long requestCompanyId(ApplicationCompanyJoinRequest request) {
        return request.getCompanyId() != null
                ? request.getCompanyId()
                : (request.getCompany() == null ? null : request.getCompany().getCompanyId());
    }

    private Long keyCompanyId(ApplicationCompanyMemberKey key) {
        return key.getCompanyId() != null
                ? key.getCompanyId()
                : (key.getCompany() == null ? null : key.getCompany().getCompanyId());
    }

    private Long resolveUserId(ApplicationCompanyJoinRequest request) {
        if (request.getUserId() != null) {
            return request.getUserId();
        }
        throw CompanyJoinRequestException.userRequired(request.getRequestId());
    }

    private ApplicationCompany company(Long companyId) {
        return companyRepository.findById(companyId)
                .orElseThrow(() -> new NotFoundException("Company", companyId));
    }

    private String generateMemberKey() {
        byte[] random = new byte[32];
        SECURE_RANDOM.nextBytes(random);
        return "cmk_" + BASE64_URL.encodeToString(random);
    }

    private String hash(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            throw CompanyJoinRequestException.invalidMemberKey();
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(normalized.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private String normalize(String value) {
        String trimmed = trimToNull(value);
        return trimmed == null ? null : trimmed.toLowerCase(Locale.ROOT);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private void requirePositive(Long value, String name) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }
}
