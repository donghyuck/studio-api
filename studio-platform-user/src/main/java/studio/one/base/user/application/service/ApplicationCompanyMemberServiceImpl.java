package studio.one.base.user.application.service;

import java.util.stream.Collectors;
import java.util.List;
import java.util.Objects;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import studio.one.base.user.domain.model.company.CompanyMemberRef;
import studio.one.base.user.domain.model.company.CompanyRole;
import studio.one.base.user.domain.model.ApplicationCompany;
import studio.one.base.user.domain.model.ApplicationCompanyMember;
import studio.one.base.user.domain.model.ApplicationCompanyMemberId;
import studio.one.base.user.domain.error.CompanyJoinRequestException;
import studio.one.base.user.domain.port.ApplicationCompanyMemberRepository;
import studio.one.base.user.domain.port.ApplicationCompanyRepository;
import studio.one.base.user.application.usecase.ApplicationCompanyMemberService;
import studio.one.platform.exception.NotFoundException;

@Service(ApplicationCompanyMemberService.SERVICE_NAME)
@RequiredArgsConstructor
@Transactional
public class ApplicationCompanyMemberServiceImpl implements ApplicationCompanyMemberService {

    private final ApplicationCompanyRepository companyRepository;
    private final ApplicationCompanyMemberRepository memberRepository;

    @Override
    public CompanyMemberRef addMember(Long companyId, Long userId, CompanyRole role, Long actorUserId, boolean bypassRoleLimit) {
        validateMemberKey(companyId, userId);
        CompanyRole resolvedRole = role == null ? CompanyRole.MEMBER : role;
        assertAssignableRole(companyId, actorUserId, resolvedRole, bypassRoleLimit);
        ApplicationCompany company = company(companyId);
        ApplicationCompanyMemberId id = new ApplicationCompanyMemberId(companyId, userId);
        if (memberRepository.existsById(id)) {
            throw CompanyJoinRequestException.alreadyMember(companyId, userId);
        }
        ApplicationCompanyMember member = new ApplicationCompanyMember();
        member.setId(id);
        member.setCompany(company);
        member.setJoinedBy(actorUserId);
        member.setRole(resolvedRole);
        member.setUpdatedBy(actorUserId);
        try {
            return memberRepository.save(member).toRef();
        } catch (DataIntegrityViolationException e) {
            throw CompanyJoinRequestException.alreadyMember(companyId, userId);
        }
    }

    @Override
    public CompanyMemberRef changeRole(Long companyId, Long userId, CompanyRole role, Long actorUserId, boolean bypassRoleLimit) {
        Objects.requireNonNull(role, "role");
        assertAssignableRole(companyId, actorUserId, role, bypassRoleLimit);
        ApplicationCompanyMember member = member(companyId, userId);
        assertCanManageTarget(companyId, actorUserId, member.getRole(), bypassRoleLimit);
        assertOwnerRemains(companyId, member.getRole(), role);
        member.setRole(role);
        member.setUpdatedBy(actorUserId);
        return memberRepository.save(member).toRef();
    }

    @Override
    public void removeMember(Long companyId, Long userId, Long actorUserId, boolean bypassRoleLimit) {
        validateMemberKey(companyId, userId);
        ApplicationCompanyMember member = member(companyId, userId);
        assertCanManageTarget(companyId, actorUserId, member.getRole(), bypassRoleLimit);
        assertOwnerRemains(companyId, member.getRole(), null);
        memberRepository.deleteById(new ApplicationCompanyMemberId(companyId, userId));
    }

    @Override
    @Transactional(readOnly = true)
    public CompanyMemberRef getMember(Long companyId, Long userId) {
        return member(companyId, userId).toRef();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<CompanyMemberRef> getMembers(Long companyId, Pageable pageable) {
        requireCompanyId(companyId);
        return memberRepository.findAllByCompanyId(companyId, pageable).map(ApplicationCompanyMember::toRef);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CompanyMemberRef> getMembers(Long companyId) {
        requireCompanyId(companyId);
        return memberRepository.findAllByCompanyId(companyId).stream()
                .map(ApplicationCompanyMember::toRef)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isCompanyMember(Long companyId, Long userId) {
        if (companyId == null || userId == null || companyId <= 0 || userId <= 0) {
            return false;
        }
        return memberRepository.existsById(new ApplicationCompanyMemberId(companyId, userId));
    }

    @Override
    @Transactional(readOnly = true)
    public CompanyRole getCompanyRole(Long companyId, Long userId) {
        if (companyId == null || userId == null || companyId <= 0 || userId <= 0) {
            return null;
        }
        return memberRepository.findById(new ApplicationCompanyMemberId(companyId, userId))
                .map(ApplicationCompanyMember::getRole)
                .orElse(null);
    }

    private ApplicationCompany company(Long companyId) {
        requireCompanyId(companyId);
        return companyRepository.findById(companyId)
                .orElseThrow(() -> new NotFoundException("Company", companyId));
    }

    private ApplicationCompanyMember member(Long companyId, Long userId) {
        validateMemberKey(companyId, userId);
        return memberRepository.findById(new ApplicationCompanyMemberId(companyId, userId))
                .orElseThrow(() -> new NotFoundException("CompanyMember", companyId + ":" + userId));
    }

    private void assertAssignableRole(Long companyId, Long actorUserId, CompanyRole requestedRole, boolean bypassRoleLimit) {
        if (bypassRoleLimit) {
            return;
        }
        CompanyRole actorRole = getCompanyRole(companyId, actorUserId);
        if (actorRole == null || requestedRole.rank() > actorRole.rank()) {
            throw new AccessDeniedException("Cannot assign company role " + requestedRole);
        }
    }

    private void assertCanManageTarget(Long companyId, Long actorUserId, CompanyRole targetRole, boolean bypassRoleLimit) {
        if (bypassRoleLimit) {
            return;
        }
        CompanyRole actorRole = getCompanyRole(companyId, actorUserId);
        if (actorRole == null || targetRole.rank() > actorRole.rank()) {
            throw new AccessDeniedException("Cannot manage company member role " + targetRole);
        }
    }

    private void assertOwnerRemains(Long companyId, CompanyRole currentRole, CompanyRole nextRole) {
        if (currentRole != CompanyRole.OWNER || nextRole == CompanyRole.OWNER) {
            return;
        }
        if (memberRepository.countByCompanyIdAndRole(companyId, CompanyRole.OWNER) <= 1) {
            throw new AccessDeniedException("Cannot remove the last company owner");
        }
    }

    private void validateMemberKey(Long companyId, Long userId) {
        requireCompanyId(companyId);
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("userId must be positive");
        }
    }

    private void requireCompanyId(Long companyId) {
        if (companyId == null || companyId <= 0) {
            throw new IllegalArgumentException("companyId must be positive");
        }
    }
}
