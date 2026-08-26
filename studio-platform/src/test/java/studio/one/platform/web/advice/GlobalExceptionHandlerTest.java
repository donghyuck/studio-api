package studio.one.platform.web.advice;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.server.ResponseStatusException;

import studio.one.platform.error.ErrorType;
import studio.one.platform.exception.PlatformRuntimeException;
import studio.one.platform.service.I18n;

class GlobalExceptionHandlerTest {

    private final I18n i18n = (code, args, locale) -> code;
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler(i18n);

    @Test
    void returnsMethodNotAllowedForUnsupportedHttpMethod() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/test");

        var response = handler.handleHttp4xx(new HttpRequestMethodNotSupportedException("POST"), request);

        assertThat(response.getStatusCode().value()).isEqualTo(405);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(405);
    }

    @Test
    void returnsUnsupportedMediaTypeForUnsupportedContentType() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/test");

        var response = handler.handleHttp4xx(
                new HttpMediaTypeNotSupportedException(MediaType.APPLICATION_XML, List.of(MediaType.APPLICATION_JSON)),
                request);

        assertThat(response.getStatusCode().value()).isEqualTo(415);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(415);
    }

    @Test
    void preservesResponseStatusExceptionStatus() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/test");

        var response = handler.handleResponseStatus(
                new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "Company-scoped user listing is not supported"),
                request);

        assertThat(response.getStatusCode().value()).isEqualTo(501);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(501);
        assertThat(response.getBody().getDetail()).isEqualTo("Company-scoped user listing is not supported");
    }

    @Test
    void preservesClassifiedRuntimeErrorStatusAndCode() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/test");
        PlatformRuntimeException exception = new PlatformRuntimeException(
                ErrorType.of("error.test.upstream", HttpStatus.BAD_GATEWAY),
                "safe log message");

        var response = handler.handlePlatform(exception, request);

        assertThat(response.getStatusCode().value()).isEqualTo(502);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(502);
        assertThat(response.getBody().getCode()).isEqualTo("error.test.upstream");
        assertThat(response.getBody().getDetail()).isEqualTo("error.test.upstream");
    }

    @Test
    void returnsNotFoundForNoResource() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/mgmt/attachments/6/rag/index");

        var response = handler.handleNoResource(
                new NoResourceFoundException(
                        HttpMethod.POST, "", "/api/mgmt/attachments/6/rag/index"),
                request);

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(404);
    }
}
