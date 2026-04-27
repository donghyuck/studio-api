package studio.one.platform.autoconfigure.persistence.jpa.auditor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import studio.one.platform.autoconfigure.JpaAuditingProperties;

public class CompositeAuditorAware implements org.springframework.data.domain.AuditorAware<String> {
    private final List<org.springframework.data.domain.AuditorAware<String>> delegates;

    public CompositeAuditorAware(JpaAuditingProperties props) {
        this(buildDefaultDelegates(props));
    }

    public CompositeAuditorAware(List<org.springframework.data.domain.AuditorAware<String>> delegates,
            JpaAuditingProperties props) {
        this(mergeDelegates(delegates, buildDefaultDelegates(props)));
    }

    private CompositeAuditorAware(List<org.springframework.data.domain.AuditorAware<String>> delegates) {
        this.delegates = Collections.unmodifiableList(new ArrayList<>(delegates));
    }

    private static List<org.springframework.data.domain.AuditorAware<String>> mergeDelegates(
            List<org.springframework.data.domain.AuditorAware<String>> delegates,
            List<org.springframework.data.domain.AuditorAware<String>> defaults) {
        var merged = new ArrayList<org.springframework.data.domain.AuditorAware<String>>();
        if (delegates != null) {
            merged.addAll(delegates);
        }
        merged.addAll(defaults);
        return merged;
    }

    private static List<org.springframework.data.domain.AuditorAware<String>> buildDefaultDelegates(
            JpaAuditingProperties props) {
        var list = new ArrayList<org.springframework.data.domain.AuditorAware<String>>();
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
            default:
                break;
            }
        }
        return list;
    }

    @Override
    public Optional<String> getCurrentAuditor() {
        for (var d : delegates) {
            var v = d.getCurrentAuditor();
            if (v.isPresent())
                return v;
        }
        return Optional.of("system");
    }
}
