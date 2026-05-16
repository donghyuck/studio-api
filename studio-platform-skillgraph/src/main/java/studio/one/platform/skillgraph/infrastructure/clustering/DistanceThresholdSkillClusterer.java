package studio.one.platform.skillgraph.infrastructure.clustering;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import studio.one.platform.skillgraph.domain.model.SkillCluster;
import studio.one.platform.skillgraph.domain.model.SkillProjection;
import studio.one.platform.skillgraph.domain.port.SkillClusterer;

public class DistanceThresholdSkillClusterer implements SkillClusterer {

    private final double radius;

    public DistanceThresholdSkillClusterer() {
        this(0.24d);
    }

    public DistanceThresholdSkillClusterer(double radius) {
        this.radius = Math.max(0.01d, radius);
    }

    @Override
    public SkillClusteringResult cluster(String projectionId, List<SkillProjection> projections) {
        if (projections == null || projections.isEmpty()) {
            return new SkillClusteringResult(List.of(), List.of());
        }
        List<Centroid> centroids = new ArrayList<>();
        List<SkillProjection> clustered = new ArrayList<>(projections.size());
        Map<String, Integer> counts = new HashMap<>();
        for (SkillProjection point : projections) {
            Centroid nearest = nearest(point, centroids);
            if (nearest == null || distance(point.x(), point.y(), nearest.x, nearest.y) > radius) {
                nearest = new Centroid("cluster-" + (centroids.size() + 1), point.x(), point.y(), 0);
                centroids.add(nearest);
            }
            nearest.add(point.x(), point.y());
            counts.merge(nearest.id, 1, Integer::sum);
            clustered.add(point.withClusterId(nearest.id));
        }
        Instant now = Instant.now();
        List<SkillCluster> clusters = counts.entrySet().stream()
                .map(entry -> new SkillCluster(entry.getKey(), entry.getKey(), "DISTANCE_THRESHOLD", entry.getValue(), now))
                .toList();
        return new SkillClusteringResult(clustered, clusters);
    }

    private Centroid nearest(SkillProjection point, List<Centroid> centroids) {
        Centroid nearest = null;
        double best = Double.MAX_VALUE;
        for (Centroid centroid : centroids) {
            double distance = distance(point.x(), point.y(), centroid.x, centroid.y);
            if (distance < best) {
                best = distance;
                nearest = centroid;
            }
        }
        return nearest;
    }

    private double distance(double leftX, double leftY, double rightX, double rightY) {
        double dx = leftX - rightX;
        double dy = leftY - rightY;
        return Math.sqrt(dx * dx + dy * dy);
    }

    private static final class Centroid {
        private final String id;
        private double x;
        private double y;
        private int count;

        private Centroid(String id, double x, double y, int count) {
            this.id = id;
            this.x = x;
            this.y = y;
            this.count = count;
        }

        private void add(double nextX, double nextY) {
            count++;
            x += (nextX - x) / count;
            y += (nextY - y) / count;
        }
    }
}
