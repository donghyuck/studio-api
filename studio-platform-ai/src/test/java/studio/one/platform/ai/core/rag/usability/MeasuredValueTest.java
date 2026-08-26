package studio.one.platform.ai.core.rag.usability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class MeasuredValueTest {

    @Test
    void distinguishesMeasuredZeroFromMissingAndFailureStates() {
        assertThat(MeasuredValue.measured(0.0d).state()).isEqualTo(MeasurementState.MEASURED);
        assertThat(MeasuredValue.measured(0.0d).value()).isZero();
        assertThat(MeasuredValue.notMeasured("NOT_RUN").state()).isEqualTo(MeasurementState.NOT_MEASURED);
        assertThat(MeasuredValue.notApplicable("NO_PAGES").state()).isEqualTo(MeasurementState.NOT_APPLICABLE);
        assertThat(MeasuredValue.failed("TIMEOUT").state()).isEqualTo(MeasurementState.FAILED);
    }

    @Test
    void rejectsAmbiguousMeasuredValues() {
        assertThatThrownBy(() -> new MeasuredValue<>(MeasurementState.MEASURED, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MeasuredValue<>(MeasurementState.FAILED, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
