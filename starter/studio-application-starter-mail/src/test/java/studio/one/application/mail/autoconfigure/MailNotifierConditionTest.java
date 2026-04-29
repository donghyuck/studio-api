package studio.one.application.mail.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.mock.env.MockEnvironment;

class MailNotifierConditionTest {

    private final AnnotationMetadata metadata = AnnotationMetadata.introspect(MailNotifierConditionTest.class);

    @Test
    void sseEndpointIsEnabledByDefaultEvenWhenNotifyTransportIsStomp() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("studio.features.mail.web.notify", "stomp");

        boolean matches = new MailAutoConfiguration.MailSseCondition()
                .matches(new TestConditionContext(environment), metadata);

        assertThat(matches).isTrue();
    }

    @Test
    void sseEndpointCanBeDisabledExplicitly() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("studio.features.mail.web.notify", "stomp")
                .withProperty("studio.features.mail.web.sse", "false");

        boolean matches = new MailAutoConfiguration.MailSseCondition()
                .matches(new TestConditionContext(environment), metadata);

        assertThat(matches).isFalse();
    }

    @Test
    void stompNotifierIsEnabledByNotifyTransportEvenWhenSseEndpointIsEnabled() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("studio.features.mail.web.notify", "stomp")
                .withProperty("studio.features.mail.web.sse", "true");

        boolean matches = new MailStompNotifierAutoConfiguration.MailStompCondition()
                .matches(new TestConditionContext(environment), metadata);

        assertThat(matches).isTrue();
    }

    private record TestConditionContext(Environment environment) implements ConditionContext {

        @Override
        public org.springframework.beans.factory.support.BeanDefinitionRegistry getRegistry() {
            return null;
        }

        @Override
        public ConfigurableListableBeanFactory getBeanFactory() {
            return null;
        }

        @Override
        public Environment getEnvironment() {
            return environment;
        }

        @Override
        public ResourceLoader getResourceLoader() {
            return null;
        }

        @Override
        public ClassLoader getClassLoader() {
            return getClass().getClassLoader();
        }
    }
}
