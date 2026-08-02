package studio.one.platform.ai.service.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.SocketTimeoutException;

import org.junit.jupiter.api.Test;

class AiProviderExceptionSupportTest {

    @Test
    void identifiesNestedProviderTimeout() {
        RuntimeException failure =
                new RuntimeException("provider call failed", new SocketTimeoutException("timed out"));

        assertThat(AiProviderExceptionSupport.isTimeout(failure)).isTrue();
    }

    @Test
    void doesNotTreatUnrelatedFailureAsTimeout() {
        assertThat(AiProviderExceptionSupport.isTimeout(
                        new IllegalStateException("invalid embedding response")))
                .isFalse();
    }
}
