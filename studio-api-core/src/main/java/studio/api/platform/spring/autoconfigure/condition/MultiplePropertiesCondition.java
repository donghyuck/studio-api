package studio.api.platform.spring.autoconfigure.condition;

import java.util.Map;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;

public class MultiplePropertiesCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        if (!metadata.isAnnotated(ConditionalOnProperties.class.getName())) {
            return true;
        }

        Map<String, Object> attributes = metadata.getAnnotationAttributes(ConditionalOnProperties.class.getName());
        String prefix = (String) attributes.get("prefix");
        Object[] props = (Object[]) attributes.get("value");

        Environment env = context.getEnvironment();

        for (Object p : props) {
            @SuppressWarnings("unchecked")
            Map<String, Object> prop = (Map<String, Object>) p;

            String name = (String) prop.get("name");
            String expected = (String) prop.get("havingValue");
            boolean matchIfMissing = (Boolean) prop.get("matchIfMissing");

            String fullKey = prefix.isEmpty() ? name : prefix + "." + name;
            String actual = env.getProperty(fullKey);

            if (actual == null && !matchIfMissing) return false;
            if (actual != null && !actual.equalsIgnoreCase(expected)) return false;
        }

        return true;
    }
}
