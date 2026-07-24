package studio.one.platform.ai.model;

public record ModelRuntimeMapping(String runtime, String model, String protocol) {

    public ModelRuntimeMapping {
        runtime = required(runtime, "runtime");
        model = required(model, "model");
        protocol = required(protocol, "protocol");
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
