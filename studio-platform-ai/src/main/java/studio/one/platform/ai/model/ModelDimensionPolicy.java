package studio.one.platform.ai.model;

import java.util.Set;

public record ModelDimensionPolicy(Set<Integer> supported, Integer defaultDimension) {

    public ModelDimensionPolicy {
        supported = supported == null ? Set.of() : Set.copyOf(supported);
        if (supported.stream().anyMatch(value -> value == null || value <= 0)) {
            throw new IllegalArgumentException("supported dimensions must be positive");
        }
        if (defaultDimension != null && !supported.isEmpty() && !supported.contains(defaultDimension)) {
            throw new IllegalArgumentException("defaultDimension must be included in supported dimensions");
        }
    }

    public static ModelDimensionPolicy none() {
        return new ModelDimensionPolicy(Set.of(), null);
    }
}
