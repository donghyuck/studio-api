package studio.echo.base.user.exception;

import org.springframework.http.HttpStatus;

import studio.echo.platform.error.ErrorType;
import studio.echo.platform.exception.NotFoundException;

@SuppressWarnings({ "serial", "java:S110"}) 
public class CompanyNotFoundException extends NotFoundException {

    private static final ErrorType BY_ID = ErrorType.of("error.company.not.found.id", HttpStatus.NOT_FOUND);
    private static final ErrorType BY_NAME = ErrorType.of("error.company.not.found.name", HttpStatus.NOT_FOUND);

    public CompanyNotFoundException(Long companyId) {
        super(BY_ID, "Company Not Found", companyId);
    }

    public CompanyNotFoundException(String companyName) {
        super(BY_NAME, "Company Not Found", companyName);
    }

    public static CompanyNotFoundException byId(Long companyId) {
        return new CompanyNotFoundException(companyId);
    }

    public static CompanyNotFoundException byName(String companyName) {
        return new CompanyNotFoundException(companyName);
    }

}
