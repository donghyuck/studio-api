package studio.echo.base.user.exception;

import org.springframework.http.HttpStatus;

import studio.echo.platform.error.ErrorType;
import studio.echo.platform.exception.AlreadyExistsException;


@SuppressWarnings({ "serial", "java:S110"}) 
public class CompanyAlreadyExistsException extends AlreadyExistsException{

    private static final ErrorType BY_ID = ErrorType.of("error.company.already.exists.id", HttpStatus.INTERNAL_SERVER_ERROR);
    private static final ErrorType BY_NAME = ErrorType.of("error.company.already.exists.name", HttpStatus.INTERNAL_SERVER_ERROR);

    public CompanyAlreadyExistsException(Long companyId) {
        super(BY_ID, "Company Already Exists", companyId);
    }

    public CompanyAlreadyExistsException(String companyName) {
        super(BY_NAME, "Company Already Exists", companyName);
    }

    public static CompanyAlreadyExistsException byId(Long companyId) {
        return new CompanyAlreadyExistsException(companyId);
    }

    public static CompanyAlreadyExistsException byName(String companyName) {
        return new CompanyAlreadyExistsException(companyName);
    }

}
