package studio.echo.platform.autoconfigure.jpa.auditor;

import studio.echo.platform.autoconfigure.JpaAuditingProperties;

public class CompositeAuditorAware implements org.springframework.data.domain.AuditorAware<String> {
    private final java.util.List<org.springframework.data.domain.AuditorAware<String>> delegates;

    public CompositeAuditorAware(JpaAuditingProperties props) {
        var list = new java.util.ArrayList<org.springframework.data.domain.AuditorAware<String>>();
        for (String s : props.getAuditor().getComposite()) {
            switch (s.toLowerCase()) {
                case "security":
                    list.add(new SecurityAuditorAware());
                    break;
                case "header":
                    list.add(new HeaderAuditorAware(props.getAuditor().getHeader()));
                    break;
                case "fixed":
                    list.add(new FixedAuditorAware(props.getAuditor().getFixed()));
                    break;
            }
        }
        this.delegates = java.util.Collections.unmodifiableList(list);
    }

    @Override
    public java.util.Optional<String> getCurrentAuditor() {
        for (var d : delegates) {
            var v = d.getCurrentAuditor();
            if (v.isPresent())
                return v;
        }
        return java.util.Optional.of("system");
    }
}
