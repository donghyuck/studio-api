package studio.one.platform.autoconfigure.jpa.auditor;

import java.util.Optional;

public class HeaderAuditorAware implements org.springframework.data.domain.AuditorAware<String> {
    private final String headerName;

    public HeaderAuditorAware(String headerName) {
        this.headerName = headerName;
    }

    @Override
    public java.util.Optional<String> getCurrentAuditor() {
        var attrs = org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
        if (!(attrs instanceof org.springframework.web.context.request.ServletRequestAttributes))
            return Optional.empty();
        var req = ((org.springframework.web.context.request.ServletRequestAttributes) attrs).getRequest();
        String v = req.getHeader(headerName);
        return (v == null || v.isEmpty()) ? Optional.empty() : Optional.of(v);
    }
}
