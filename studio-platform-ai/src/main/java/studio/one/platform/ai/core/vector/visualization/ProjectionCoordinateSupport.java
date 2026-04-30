package studio.one.platform.ai.core.vector.visualization;

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
        double[][] centered = centeredMatrix(usable, dimensions);
        double[][] covariance = covariance(centered, dimensions);
        double[] first = principalComponent(covariance, null);
        double[] second = dimensions == 1 ? new double[] {0.0d} : principalComponent(covariance, first);
        List<double[]> coordinates = new ArrayList<>(usable.size());
        for (double[] vector : centered) {
            coordinates.add(new double[] {dot(vector, first), dot(vector, second)});
        }
        normalizeCoordinates(coordinates);
        return coordinates;
    }

    static List<VectorProjectionPoint> points(
            String projectionId,
            List<VectorItem> usable,
            List<double[]> coordinates,
            java.time.Instant createdAt) {
        List<VectorProjectionPoint> points = new ArrayList<>(Math.min(usable.size(), coordinates.size()));
        for (int i = 0; i < usable.size() && i < coordinates.size(); i++) {
            VectorItem item = usable.get(i);
            double[] coordinate = coordinates.get(i);
            points.add(new VectorProjectionPoint(
                    projectionId,
                    item.vectorItemId(),
                    coordinate[0],
                    coordinate[1],
                    null,
                    i,
                    createdAt));
        }
        return points;
    }

    static void normalizeCoordinates(List<double[]> coordinates) {
        double maxAbs = coordinates.stream()
                .flatMap(values -> List.of(Math.abs(values[0]), Math.abs(values[1])).stream())
                .max(Comparator.naturalOrder())
                .orElse(0.0d);
        if (maxAbs <= 0.0d || Double.isNaN(maxAbs)) {
            return;
        }
        for (double[] coordinate : coordinates) {
            coordinate[0] /= maxAbs;
            coordinate[1] /= maxAbs;
        }
    }

    private static double[][] centeredMatrix(List<VectorItem> items, int dimensions) {
        double[] means = new double[dimensions];
        for (VectorItem item : items) {
            for (int i = 0; i < dimensions; i++) {
                means[i] += item.embedding().get(i);
            }
        }
        for (int i = 0; i < dimensions; i++) {
            means[i] /= items.size();
        }
        double[][] centered = new double[items.size()][dimensions];
        for (int row = 0; row < items.size(); row++) {
            List<Double> embedding = items.get(row).embedding();
            for (int col = 0; col < dimensions; col++) {
                centered[row][col] = embedding.get(col) - means[col];
            }
        }
        return centered;
    }

    private static double[][] covariance(double[][] centered, int dimensions) {
        double[][] covariance = new double[dimensions][dimensions];
        int divisor = Math.max(1, centered.length - 1);
        for (double[] row : centered) {
            for (int i = 0; i < dimensions; i++) {
                for (int j = i; j < dimensions; j++) {
                    covariance[i][j] += row[i] * row[j] / divisor;
                }
            }
        }
        for (int i = 0; i < dimensions; i++) {
            for (int j = 0; j < i; j++) {
                covariance[i][j] = covariance[j][i];
            }
        }
        return covariance;
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
}
