package studio.one.platform.ai.autoconfigure.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

final class RagPipelineConditions {

    private RagPipelineConditions() {
    }

    static final class JdbcRepository implements Condition {

        private static final Logger log = LoggerFactory.getLogger(JdbcRepository.class);

        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            return "jdbc".equalsIgnoreCase(AiConfigurationMigration.propertyWithLegacyFallback(
                    context.getEnvironment(),
                    AiConfigurationMigration.RAG_PREFIX + ".jobs.repository",
                    AiConfigurationMigration.LEGACY_RAG_PREFIX + ".jobs.repository",
                    "memory",
                    log));
        }
    }

    static final class CleanerEnabled implements Condition {

        private static final Logger log = LoggerFactory.getLogger(CleanerEnabled.class);

        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            return AiConfigurationMigration.booleanWithLegacyFallback(
                    context.getEnvironment(),
                    AiConfigurationMigration.RAG_PREFIX + ".cleaner.enabled",
                    AiConfigurationMigration.LEGACY_RAG_PREFIX + ".cleaner.enabled",
                    false,
                    log);
        }
    }
}
