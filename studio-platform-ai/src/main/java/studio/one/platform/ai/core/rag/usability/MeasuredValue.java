package studio.one.platform.ai.core.rag.usability;

/**
 * A value whose absence has an explicit, machine-readable meaning.
 */
public record MeasuredValue<T>(
        MeasurementState state,
        T value,
        String reasonCode) {

    public MeasuredValue {
        state = state == null ? MeasurementState.NOT_MEASURED : state;
        reasonCode = normalize(reasonCode);
        if (state == MeasurementState.MEASURED && value == null) {
            throw new IllegalArgumentException("MEASURED value must not be null");
        }
        if (state != MeasurementState.MEASURED && value != null) {
            throw new IllegalArgumentException("Only MEASURED values may contain a value");
        }
        if (state == MeasurementState.FAILED && reasonCode == null) {
            throw new IllegalArgumentException("FAILED value requires a reasonCode");
        }
    }

    public static <T> MeasuredValue<T> measured(T value) {
        return new MeasuredValue<>(MeasurementState.MEASURED, value, null);
    }

    public static <T> MeasuredValue<T> notMeasured(String reasonCode) {
        return new MeasuredValue<>(MeasurementState.NOT_MEASURED, null, reasonCode);
    }

    public static <T> MeasuredValue<T> notApplicable(String reasonCode) {
        return new MeasuredValue<>(MeasurementState.NOT_APPLICABLE, null, reasonCode);
    }

    public static <T> MeasuredValue<T> failed(String reasonCode) {
        return new MeasuredValue<>(MeasurementState.FAILED, null, reasonCode);
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
