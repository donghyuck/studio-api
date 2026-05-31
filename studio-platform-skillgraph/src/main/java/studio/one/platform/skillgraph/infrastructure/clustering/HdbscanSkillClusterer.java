package studio.one.platform.skillgraph.infrastructure.clustering;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import studio.one.platform.skillgraph.domain.model.SkillCluster;
import studio.one.platform.skillgraph.domain.model.SkillProjection;
import studio.one.platform.skillgraph.domain.port.SkillClusterer;

public class HdbscanSkillClusterer implements SkillClusterer {

    private static final String NOISE_CLUSTER_ID = "noise";

    private final int minPoints;
    private final double epsilon;

    public HdbscanSkillClusterer() {
        this(5, 0.18d);
    }

    public HdbscanSkillClusterer(int minPoints, double epsilon) {
        this.minPoints = Math.max(2, minPoints);
        this.epsilon = Math.max(0.01d, epsilon);
    }

    @Override
    public SkillClusteringResult cluster(String projectionId, List<SkillProjection> projections) {
        if (projections == null || projections.isEmpty()) {
            return new SkillClusteringResult(List.of(), List.of());
        }
        List<SkillProjection> clustered = new ArrayList<>(projections);
        boolean[] visited = new boolean[clustered.size()];
        int[] labels = new int[clustered.size()];
        int clusterNumber = 0;
        for (int index = 0; index < clustered.size(); index++) {
            if (visited[index]) {
                continue;
            }
            visited[index] = true;
            List<Integer> neighbors = regionQuery(clustered, index);
            if (neighbors.size() < minPoints) {
                labels[index] = -1;
                continue;
            }
            clusterNumber++;
            expandCluster(clustered, labels, visited, index, neighbors, clusterNumber);
        }

        int[] counts = new int[clusterNumber + 1];
        int noiseCount = 0;
        List<SkillProjection> assigned = new ArrayList<>(clustered.size());
        for (int index = 0; index < clustered.size(); index++) {
            int label = labels[index];
            if (label <= 0) {
                noiseCount++;
                assigned.add(clustered.get(index).withClusterId(NOISE_CLUSTER_ID));
            } else {
                counts[label]++;
                assigned.add(clustered.get(index).withClusterId("cluster-" + label));
            }
        }
        Instant now = Instant.now();
        List<SkillCluster> clusters = new ArrayList<>();
        for (int label = 1; label <= clusterNumber; label++) {
            if (counts[label] > 0) {
                String id = "cluster-" + label;
                clusters.add(new SkillCluster(id, "Cluster " + label, "HDBSCAN", counts[label], now));
            }
        }
        if (noiseCount > 0) {
            clusters.add(new SkillCluster(NOISE_CLUSTER_ID, "Noise", "HDBSCAN", noiseCount, now));
        }
        return new SkillClusteringResult(assigned, clusters);
    }

    private void expandCluster(
            List<SkillProjection> points,
            int[] labels,
            boolean[] visited,
            int index,
            List<Integer> neighbors,
            int clusterNumber) {
        labels[index] = clusterNumber;
        ArrayDeque<Integer> queue = new ArrayDeque<>(neighbors);
        Set<Integer> queued = new HashSet<>(neighbors);
        while (!queue.isEmpty()) {
            int next = queue.removeFirst();
            if (!visited[next]) {
                visited[next] = true;
                List<Integer> nextNeighbors = regionQuery(points, next);
                if (nextNeighbors.size() >= minPoints) {
                    for (Integer candidate : nextNeighbors) {
                        if (queued.add(candidate)) {
                            queue.addLast(candidate);
                        }
                    }
                }
            }
            if (labels[next] <= 0) {
                labels[next] = clusterNumber;
            }
        }
    }

    private List<Integer> regionQuery(List<SkillProjection> points, int index) {
        List<Integer> neighbors = new ArrayList<>();
        SkillProjection base = points.get(index);
        for (int other = 0; other < points.size(); other++) {
            if (distance(base, points.get(other)) <= epsilon) {
                neighbors.add(other);
            }
        }
        return neighbors;
    }

    private double distance(SkillProjection left, SkillProjection right) {
        double dx = left.x() - right.x();
        double dy = left.y() - right.y();
        return Math.sqrt(dx * dx + dy * dy);
    }
}
