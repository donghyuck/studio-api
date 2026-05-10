package studio.one.base.user.domain.error;

import org.springframework.http.HttpStatus;

import studio.one.platform.error.ErrorType;
import studio.one.platform.exception.PlatformException;

public class CompanyJoinRequestException extends PlatformException {

    public static final ErrorType INVALID_MEMBER_KEY = ErrorType.of("error.company.member-key.invalid", HttpStatus.BAD_REQUEST);
    public static final ErrorType EXPIRED_MEMBER_KEY = ErrorType.of("error.company.member-key.expired", HttpStatus.BAD_REQUEST);
    public static final ErrorType EXHAUSTED_MEMBER_KEY = ErrorType.of("error.company.member-key.exhausted", HttpStatus.BAD_REQUEST);
    public static final ErrorType REQUEST_ALREADY_DECIDED = ErrorType.of("error.company.join-request.already-decided", HttpStatus.CONFLICT);
    public static final ErrorType USER_REQUIRED = ErrorType.of("error.company.join-request.user-required", HttpStatus.BAD_REQUEST);
    public static final ErrorType COMPANY_INACTIVE = ErrorType.of("error.company.join-request.company-inactive", HttpStatus.CONFLICT);
    public static final ErrorType ALREADY_MEMBER = ErrorType.of("error.company.join-request.already-member", HttpStatus.CONFLICT);
    public static final ErrorType DUPLICATE_PENDING_REQUEST = ErrorType.of("error.company.join-request.duplicate-pending", HttpStatus.CONFLICT);

    public CompanyJoinRequestException(ErrorType type, String message, Object... args) {
        super(type, message, args);
    }

    public static CompanyJoinRequestException invalidMemberKey() {
        return new CompanyJoinRequestException(INVALID_MEMBER_KEY, INVALID_MEMBER_KEY.getId());
    }

    public static CompanyJoinRequestException expiredMemberKey() {
        return new CompanyJoinRequestException(EXPIRED_MEMBER_KEY, EXPIRED_MEMBER_KEY.getId());
    }

    public static CompanyJoinRequestException exhaustedMemberKey() {
        return new CompanyJoinRequestException(EXHAUSTED_MEMBER_KEY, EXHAUSTED_MEMBER_KEY.getId());
    }

    public static CompanyJoinRequestException alreadyDecided(Long requestId) {
        return new CompanyJoinRequestException(REQUEST_ALREADY_DECIDED, REQUEST_ALREADY_DECIDED.getId(), requestId);
    }

    public static CompanyJoinRequestException userRequired(Long requestId) {
        return new CompanyJoinRequestException(USER_REQUIRED, USER_REQUIRED.getId(), requestId);
    }

    public static CompanyJoinRequestException companyInactive(Long companyId) {
        return new CompanyJoinRequestException(COMPANY_INACTIVE, COMPANY_INACTIVE.getId(), companyId);
    }

    public static CompanyJoinRequestException alreadyMember(Long companyId, Long userId) {
        return new CompanyJoinRequestException(ALREADY_MEMBER, ALREADY_MEMBER.getId(), companyId, userId);
    }

    public static CompanyJoinRequestException duplicatePending(Long companyId, Long userId) {
        return new CompanyJoinRequestException(DUPLICATE_PENDING_REQUEST, DUPLICATE_PENDING_REQUEST.getId(), companyId, userId);
    }
}
