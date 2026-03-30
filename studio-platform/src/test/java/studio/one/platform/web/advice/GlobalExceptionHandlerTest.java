package studio.one.platform.web.advice;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;

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
}
