package studio.one.platform.ai.core.vector.visualization;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

final class ProjectionCoordinateSupport {

    private static final int POWER_ITERATIONS = 60;

    private ProjectionCoordinateSupport() {
    }

    static List<VectorItem> usableItems(List<VectorItem> items) {
        return items.stream()
                .filter(item -> item.embedding() != null && !item.embedding().isEmpty())
                .toList();
    }

    static List<ProjectionVector> usableVectors(List<ProjectionVector> vectors) {
        return vectors.stream()
                .filter(vector -> vector.embedding() != null && vector.embedding().length > 0)
                .toList();
    }

    static List<double[]> pcaCoordinates(List<VectorItem> usable) {
        if (usable.isEmpty()) {
            return List.of();
        }

        int dimensions = usable.stream()
                .map(VectorItem::embedding)
                .mapToInt(List::size)
                .min()
                .orElse(0);
        if (dimensions <= 0) {
            return List.of();
        }

        double[] means = means(usable, dimensions);
        double[][] covariance = covariance(usable, dimensions, means);
        double[] first = principalComponent(covariance, null);
        double[] second = dimensions == 1 ? new double[] {0.0d} : principalComponent(covariance, first);

        List<double[]> coordinates = new ArrayList<>(usable.size());
        for (VectorItem item : usable) {
            coordinates.add(coordinate(item, dimensions, means, first, second));
        }
        normalizeCoordinates(coordinates);
        return coordinates;
    }

    static List<double[]> pcaVectorCoordinates(List<ProjectionVector> usable) {
        if (usable.isEmpty()) {
            return List.of();
        }

        int dimensions = usable.stream()
                .map(ProjectionVector::embedding)
                .mapToInt(embedding -> embedding.length)
                .min()
                .orElse(0);
        if (dimensions <= 0) {
            return List.of();
        }

        double[] means = vectorMeans(usable, dimensions);
        double[][] covariance = vectorCovariance(usable, dimensions, means);
        double[] first = principalComponent(covariance, null);
        double[] second = dimensions == 1 ? new double[] {0.0d} : principalComponent(covariance, first);

        List<double[]> coordinates = new ArrayList<>(usable.size());
        for (ProjectionVector vector : usable) {
            coordinates.add(vectorCoordinate(vector, dimensions, means, first, second));
        }
        normalizeCoordinates(coordinates);
        return coordinates;
    }

    static List<VectorProjectionPoint> points(
            String projectionId,
            List<VectorItem> usable,
            List<double[]> coordinates,
            Instant createdAt) {
        List<VectorProjectionPoint> points = new ArrayList<>(Math.min(usable.size(), coordinates.size()));
        for (int i = 0; i < usable.size() && i < coordinates.size(); i++) {
            VectorItem item = usable.get(i);
            double[] coordinate = coordinates.get(i);
            points.add(new VectorProjectionPoint(
                    projectionId,
                    item.vectorItemId(),
                    documentChunkId(item.metadata()),
                    item.targetType(),
                    item.sourceId(),
                    item.label(),
                    item.metadata(),
                    coordinate[0],
                    coordinate[1],
                    null,
                    i,
                    createdAt));
        }
        return points;
    }

    static List<VectorProjectionPoint> vectorPoints(
            String projectionId,
            List<ProjectionVector> usable,
            List<double[]> coordinates,
            Instant createdAt) {
        List<VectorProjectionPoint> points = new ArrayList<>(Math.min(usable.size(), coordinates.size()));
        for (int i = 0; i < usable.size() && i < coordinates.size(); i++) {
            ProjectionVector vector = usable.get(i);
            double[] coordinate = coordinates.get(i);
            points.add(new VectorProjectionPoint(
                    projectionId,
                    vector.vectorItemId(),
                    vector.documentChunkId(),
                    vector.targetType(),
                    vector.sourceId(),
                    vector.label(),
                    vector.metadata(),
                    coordinate[0],
                    coordinate[1],
                    null,
                    i,
                    createdAt));
        }
        return points;
    }

    private static Long documentChunkId(java.util.Map<String, Object> metadata) {
        if (metadata == null) {
            return null;
        }
        Object value = metadata.get("_documentChunkId");
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Long.valueOf(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    static void normalizeCoordinates(List<double[]> coordinates) {
        double maxAbs = coordinates.stream()
                .flatMapToDouble(coordinate -> java.util.stream.DoubleStream.of(
                        Math.abs(coordinate[0]),
                        Math.abs(coordinate[1])))
                .max()
                .orElse(0.0d);
        if (maxAbs <= 0.0d || Double.isNaN(maxAbs)) {
            return;
        }

        for (double[] coordinate : coordinates) {
            coordinate[0] /= maxAbs;
            coordinate[1] /= maxAbs;
        }
    }

    private static double[] means(List<VectorItem> items, int dimensions) {
        double[] means = new double[dimensions];
        for (VectorItem item : items) {
            List<Double> embedding = item.embedding();
            for (int i = 0; i < dimensions; i++) {
                means[i] += embedding.get(i);
            }
        }
        for (int i = 0; i < dimensions; i++) {
            means[i] /= items.size();
        }
        return means;
    }

    private static double[][] covariance(List<VectorItem> items, int dimensions, double[] means) {
        double[][] covariance = new double[dimensions][dimensions];
        int divisor = Math.max(1, items.size() - 1);
        for (VectorItem item : items) {
            List<Double> embedding = item.embedding();
            for (int row = 0; row < dimensions; row++) {
                double left = embedding.get(row) - means[row];
                for (int col = 0; col <= row; col++) {
                    covariance[row][col] += left * (embedding.get(col) - means[col]) / divisor;
                }
            }
        }
        for (int row = 0; row < dimensions; row++) {
            for (int col = 0; col <= row; col++) {
                covariance[col][row] = covariance[row][col];
            }
        }
        return covariance;
    }

    private static double[] vectorMeans(List<ProjectionVector> vectors, int dimensions) {
        double[] means = new double[dimensions];
        for (ProjectionVector vector : vectors) {
            double[] embedding = vector.embedding();
            for (int i = 0; i < dimensions; i++) {
                means[i] += embedding[i];
            }
        }
        for (int i = 0; i < dimensions; i++) {
            means[i] /= vectors.size();
        }
        return means;
    }

    private static double[][] vectorCovariance(List<ProjectionVector> vectors, int dimensions, double[] means) {
        double[][] covariance = new double[dimensions][dimensions];
        int divisor = Math.max(1, vectors.size() - 1);
        for (ProjectionVector vector : vectors) {
            double[] embedding = vector.embedding();
            for (int row = 0; row < dimensions; row++) {
                double left = embedding[row] - means[row];
                for (int col = 0; col <= row; col++) {
                    covariance[row][col] += left * (embedding[col] - means[col]) / divisor;
                }
            }
        }
        for (int row = 0; row < dimensions; row++) {
            for (int col = 0; col <= row; col++) {
                covariance[col][row] = covariance[row][col];
            }
        }
        return covariance;
    }

    private static double[] coordinate(
            VectorItem item,
            int dimensions,
            double[] means,
            double[] first,
            double[] second) {
        double x = 0.0d;
        double y = 0.0d;
        List<Double> embedding = item.embedding();
        for (int i = 0; i < dimensions; i++) {
            double centered = embedding.get(i) - means[i];
            x += centered * first[i];
            y += centered * second[i];
        }
        return new double[] {x, y};
    }

    private static double[] vectorCoordinate(
            ProjectionVector vector,
            int dimensions,
            double[] means,
            double[] first,
            double[] second) {
        double x = 0.0d;
        double y = 0.0d;
        double[] embedding = vector.embedding();
        for (int i = 0; i < dimensions; i++) {
            double centered = embedding[i] - means[i];
            x += centered * first[i];
            y += centered * second[i];
        }
        return new double[] {x, y};
    }

    private static double[] principalComponent(double[][] matrix, double[] orthogonalTo) {
        int dimensions = matrix.length;
        double[] vector = new double[dimensions];
        for (int i = 0; i < dimensions; i++) {
            vector[i] = 1.0d / Math.sqrt(dimensions);
        }

        for (int iteration = 0; iteration < POWER_ITERATIONS; iteration++) {
            double[] next = multiply(matrix, vector);
            if (orthogonalTo != null) {
                subtractProjection(next, orthogonalTo);
            }
            normalize(next);
            vector = next;
        }
        return vector;
    }

    private static double[] multiply(double[][] matrix, double[] vector) {
        double[] result = new double[vector.length];
        for (int row = 0; row < matrix.length; row++) {
            for (int col = 0; col < vector.length; col++) {
                result[row] += matrix[row][col] * vector[col];
            }
        }
        return result;
    }

    private static void subtractProjection(double[] vector, double[] basis) {
        double scale = dot(vector, basis);
        for (int i = 0; i < vector.length; i++) {
            vector[i] -= scale * basis[i];
        }
    }

    private static void normalize(double[] vector) {
        double norm = Math.sqrt(dot(vector, vector));
        if (norm == 0.0d || Double.isNaN(norm)) {
            for (int i = 0; i < vector.length; i++) {
                vector[i] = i == 0 ? 1.0d : 0.0d;
            }
            return;
        }

        for (int i = 0; i < vector.length; i++) {
            vector[i] /= norm;
        }
    }

    private static double dot(double[] left, double[] right) {
        double result = 0.0d;
        for (int i = 0; i < Math.min(left.length, right.length); i++) {
            result += left[i] * right[i];
        }
        return result;
    }

    static Comparator<double[]> byXThenY() {
        return Comparator.<double[]>comparingDouble(coordinate -> coordinate[0])
                .thenComparingDouble(coordinate -> coordinate[1]);
    }
}
