package studio.one.base.security.audit;

import java.util.Collection;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.security.authentication.AuthenticationDetailsSource;

public class ClientRequestDetailsAuthenticationDetailsSource
        implements AuthenticationDetailsSource<HttpServletRequest, ClientRequestDetails> {

    private final String captureIpHeader;

    private final Collection<String> trustedProxyCidrs;

    public ClientRequestDetailsAuthenticationDetailsSource() {
        this(null, List.of());
    }

    public ClientRequestDetailsAuthenticationDetailsSource(String captureIpHeader, Collection<String> trustedProxyCidrs) {
        this.captureIpHeader = captureIpHeader;
        this.trustedProxyCidrs = trustedProxyCidrs == null ? List.of() : List.copyOf(trustedProxyCidrs);
    }

    @Override
    public ClientRequestDetails buildDetails(HttpServletRequest context) {
        return ClientRequestDetails.from(context, captureIpHeader, trustedProxyCidrs);
    }
}
