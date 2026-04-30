package studio.one.platform.ai.core.vector.visualization;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

abstract class NeighborVectorProjectionGenerator implements VectorProjectionGenerator {

    private static final double EPSILON = 1.0e-9d;
    private static final int MAX_DISTANCE_DIMENSIONS = 256;

    private final int neighborCount;
    private final int iterations;
    private final double attraction;
    private final double repulsion;
    private final double targetDistance;

    NeighborVectorProjectionGenerator(
            int neighborCount,
            int iterations,
            double attraction,
            double repulsion,
            double targetDistance) {
        this.neighborCount = neighborCount;
        this.iterations = iterations;
        this.attraction = attraction;
        this.repulsion = repulsion;
        this.targetDistance = targetDistance;
    }

    @Override
    public List<VectorProjectionPoint> generate(String projectionId, List<VectorItem> items, Instant createdAt) {
        List<VectorItem> usable = ProjectionCoordinateSupport.usableItems(items);
        if (usable.isEmpty()) {
            return List.of();
        }
        List<double[]> coordinates = ProjectionCoordinateSupport.pcaCoordinates(usable);
        if (coordinates.isEmpty()) {
            return List.of();
        }
        List<List<Neighbor>> neighbors = neighbors(
                usable,
                Math.min(neighborCount, Math.max(1, usable.size() - 1)));
        refine(coordinates, neighbors);
        ProjectionCoordinateSupport.normalizeCoordinates(coordinates);
        return ProjectionCoordinateSupport.points(projectionId, usable, coordinates, createdAt);
    }

    private List<List<Neighbor>> neighbors(List<VectorItem> items, int limit) {
        List<List<Neighbor>> neighbors = new ArrayList<>(items.size());
        for (int i = 0; i < items.size(); i++) {
            List<Neighbor> candidates = new ArrayList<>(Math.max(0, items.size() - 1));
            for (int j = 0; j < items.size(); j++) {
                if (i == j) {
                    continue;
                }
                double distance = embeddingDistance(items.get(i).embedding(), items.get(j).embedding());
                candidates.add(new Neighbor(j, distance, similarity(distance)));
            }
            candidates.sort(Comparator.comparingDouble(Neighbor::distance));
            neighbors.add(candidates.stream().limit(limit).toList());
        }
        return neighbors;
    }

    private void refine(List<double[]> coordinates, List<List<Neighbor>> neighbors) {
        if (coordinates.size() <= 1) {
            return;
        }
        for (int iteration = 0; iteration < iterations; iteration++) {
            double[][] deltas = new double[coordinates.size()][2];
            for (int i = 0; i < coordinates.size(); i++) {
                double[] current = coordinates.get(i);
                for (Neighbor neighbor : neighbors.get(i)) {
                    double[] other = coordinates.get(neighbor.index());
                    double dx = other[0] - current[0];
                    double dy = other[1] - current[1];
                    double distance = Math.sqrt(dx * dx + dy * dy) + EPSILON;
                    double force = attraction * neighbor.similarity() * (distance - targetDistance);
                    deltas[i][0] += force * dx / distance;
                    deltas[i][1] += force * dy / distance;
                }
                applySampledRepulsion(i, coordinates, deltas[i]);
            }
            for (int i = 0; i < coordinates.size(); i++) {
                coordinates.get(i)[0] += deltas[i][0];
                coordinates.get(i)[1] += deltas[i][1];
            }
        }
    }

    private void applySampledRepulsion(int index, List<double[]> coordinates, double[] delta) {
        int samples = Math.min(16, coordinates.size() - 1);
        if (samples <= 0) {
            return;
        }
        double[] current = coordinates.get(index);
        for (int sample = 1; sample <= samples; sample++) {
            int otherIndex = Math.floorMod(index + sample * 37, coordinates.size());
            if (otherIndex == index) {
                otherIndex = (otherIndex + 1) % coordinates.size();
            }
            double[] other = coordinates.get(otherIndex);
            double dx = current[0] - other[0];
            double dy = current[1] - other[1];
            double squared = dx * dx + dy * dy + EPSILON;
            double force = repulsion / squared;
            delta[0] += force * dx;
            delta[1] += force * dy;
        }
    }

    private double similarity(double distance) {
        return 1.0d / (1.0d + Math.max(0.0d, distance));
    }

    private double embeddingDistance(List<Double> left, List<Double> right) {
        int dimensions = Math.min(left.size(), right.size());
        if (dimensions <= 0) {
            return 1.0d;
        }
        int step = Math.max(1, dimensions / MAX_DISTANCE_DIMENSIONS);
        double dot = 0.0d;
        double leftNorm = 0.0d;
        double rightNorm = 0.0d;
        for (int i = 0; i < dimensions; i += step) {
            double leftValue = left.get(i);
            double rightValue = right.get(i);
            dot += leftValue * rightValue;
            leftNorm += leftValue * leftValue;
            rightNorm += rightValue * rightValue;
        }
        if (leftNorm <= 0.0d || rightNorm <= 0.0d) {
            return 1.0d;
        }
        double similarity = dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
        return 1.0d - Math.max(-1.0d, Math.min(1.0d, similarity));
    }

    private record Neighbor(int index, double distance, double similarity) {
    }
}
