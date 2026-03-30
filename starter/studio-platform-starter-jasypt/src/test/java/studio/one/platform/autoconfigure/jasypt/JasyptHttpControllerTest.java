package studio.one.platform.autoconfigure.jasypt;

import static org.assertj.core.api.Assertions.assertThat;

import org.jasypt.encryption.StringEncryptor;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import studio.one.platform.service.I18n;

class JasyptHttpControllerTest {

    private final StringEncryptor encryptor = new StubEncryptor();
    private final JasyptProperties properties = new JasyptProperties();
    private final I18n i18n = (code, args, locale) -> code;

    @Test
    void encryptReturnsUnauthorizedForInvalidToken() {
        JasyptHttpController controller = new JasyptHttpController(encryptor, httpProps("expected-token"), i18n);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("X-JASYPT-TOKEN", "wrong-token");

        JasyptHttpController.CryptoRequest body = new JasyptHttpController.CryptoRequest();
        body.setValue("plain");

        var response = controller.encrypt(body, request);

        assertThat(response.getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void encryptSucceedsForValidToken() {
        JasyptHttpController controller = new JasyptHttpController(encryptor, httpProps("expected-token"), i18n);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("X-JASYPT-TOKEN", "expected-token");

        JasyptHttpController.CryptoRequest body = new JasyptHttpController.CryptoRequest();
        body.setValue("plain");

        var response = controller.encrypt(body, request);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isEqualTo("ENC(cipher)");
    }

    @Test
    void encryptReturnsForbiddenForNonLocalRequest() {
        JasyptHttpController controller = new JasyptHttpController(encryptor, httpProps("expected-token"), i18n);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.1");
        request.addHeader("X-JASYPT-TOKEN", "expected-token");

        JasyptHttpController.CryptoRequest body = new JasyptHttpController.CryptoRequest();
        body.setValue("plain");

        var response = controller.encrypt(body, request);

        assertThat(response.getStatusCode().value()).isEqualTo(403);
    }

    @Test
    void encryptReturnsUnauthorizedWhenTokenHeaderMissing() {
        JasyptHttpController controller = new JasyptHttpController(encryptor, httpProps("expected-token"), i18n);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");

        JasyptHttpController.CryptoRequest body = new JasyptHttpController.CryptoRequest();
        body.setValue("plain");

        var response = controller.encrypt(body, request);

        assertThat(response.getStatusCode().value()).isEqualTo(401);
    }

    private JasyptProperties.JasyptHttpEndpointProperties httpProps(String tokenValue) {
        JasyptProperties.JasyptHttpEndpointProperties http = properties.new JasyptHttpEndpointProperties();
        http.setEnabled(true);
        http.setRequireToken(true);
        http.setTokenValue(tokenValue);
        return http;
    }

    private static class StubEncryptor implements StringEncryptor {

        @Override
        public String encrypt(String message) {
            return "cipher";
        }

        @Override
        public String decrypt(String encryptedMessage) {
            return "plain";
        }
    }
}
