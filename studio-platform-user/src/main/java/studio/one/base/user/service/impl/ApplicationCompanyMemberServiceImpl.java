package studio.one.base.user.service.impl;

import java.util.List;
import java.util.Objects;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import studio.one.base.user.company.model.CompanyMemberRef;
import studio.one.base.user.company.model.CompanyRole;
import studio.one.base.user.domain.entity.ApplicationCompany;
import studio.one.base.user.domain.entity.ApplicationCompanyMember;
import studio.one.base.user.domain.entity.ApplicationCompanyMemberId;
import studio.one.base.user.persistence.ApplicationCompanyMemberRepository;
import studio.one.base.user.persistence.ApplicationCompanyRepository;
import studio.one.base.user.service.ApplicationCompanyMemberService;
import studio.one.platform.exception.NotFoundException;

@Service(ApplicationCompanyMemberService.SERVICE_NAME)
@RequiredArgsConstructor
@Transactional
public class ApplicationCompanyMemberServiceImpl implements ApplicationCompanyMemberService {

    private final ApplicationCompanyRepository companyRepository;
    private final ApplicationCompanyMemberRepository memberRepository;

    @Override
    public CompanyMemberRef addMember(Long companyId, Long userId, CompanyRole role, Long actorUserId) {
        validateMemberKey(companyId, userId);
        CompanyRole resolvedRole = role == null ? CompanyRole.MEMBER : role;
        ApplicationCompany company = company(companyId);
        ApplicationCompanyMemberId id = new ApplicationCompanyMemberId(companyId, userId);
        ApplicationCompanyMember member = memberRepository.findById(id).orElseGet(() -> {
            ApplicationCompanyMember created = new ApplicationCompanyMember();
            created.setId(id);
            created.setCompany(company);
            created.setJoinedBy(actorUserId);
            return created;
        });
        member.setRole(resolvedRole);
        member.setUpdatedBy(actorUserId);
        return memberRepository.save(member).toRef();
    }

    @Override
    public CompanyMemberRef changeRole(Long companyId, Long userId, CompanyRole role, Long actorUserId) {
        Objects.requireNonNull(role, "role");
        ApplicationCompanyMember member = member(companyId, userId);
        member.setRole(role);
        member.setUpdatedBy(actorUserId);
        return memberRepository.save(member).toRef();
    }

    @Override
    public void removeMember(Long companyId, Long userId, Long actorUserId) {
        validateMemberKey(companyId, userId);
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
                .toList();
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
