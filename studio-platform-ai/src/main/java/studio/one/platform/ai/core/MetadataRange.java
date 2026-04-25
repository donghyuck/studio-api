package studio.one.platform.ai.core;

import java.util.Objects;

/**
 * Provider-neutral range predicate for metadata filtering.
 */
public final class MetadataRange<T extends Comparable<T>> {

    private final T from;
    private final T to;
    private final boolean includeFrom;
    private final boolean includeTo;

    public MetadataRange(T from, T to) {
        this(from, to, true, true);
    }

    public MetadataRange(T from, T to, boolean includeFrom, boolean includeTo) {
        if (from == null && to == null) {
            throw new IllegalArgumentException("from or to must be provided");
        }
        if (from != null && to != null && from.compareTo(to) > 0) {
            throw new IllegalArgumentException("from must be less than or equal to to");
        }
        this.from = from;
        this.to = to;
        this.includeFrom = includeFrom;
        this.includeTo = includeTo;
    }

    public static <T extends Comparable<T>> MetadataRange<T> closed(T from, T to) {
        return new MetadataRange<>(from, to, true, true);
    }

    public static <T extends Comparable<T>> MetadataRange<T> open(T from, T to) {
        return new MetadataRange<>(from, to, false, false);
    }

    public static <T extends Comparable<T>> MetadataRange<T> atLeast(T from) {
        return new MetadataRange<>(from, null, true, true);
    }

    public static <T extends Comparable<T>> MetadataRange<T> atMost(T to) {
        return new MetadataRange<>(null, to, true, true);
    }

    public T from() {
        return from;
    }

    public T to() {
        return to;
    }

    public boolean includeFrom() {
        return includeFrom;
    }

    public boolean includeTo() {
        return includeTo;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof MetadataRange<?> that)) {
            return false;
        }
        return includeFrom == that.includeFrom
                && includeTo == that.includeTo
                && Objects.equals(from, that.from)
                && Objects.equals(to, that.to);
    }

    @Override
    public int hashCode() {
        return Objects.hash(from, to, includeFrom, includeTo);
    }

    @Override
    public String toString() {
        return "MetadataRange{"
                + "from=" + from
                + ", to=" + to
                + ", includeFrom=" + includeFrom
                + ", includeTo=" + includeTo
                + '}';
    }
}
