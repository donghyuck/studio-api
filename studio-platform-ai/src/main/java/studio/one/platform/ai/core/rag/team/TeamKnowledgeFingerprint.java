package studio.one.platform.ai.core.rag.team;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/**
 * Stable fingerprint for a Team knowledge corpus without rewriting vector metadata.
 */
public final class TeamKnowledgeFingerprint {

    private static final String VERSION = "team-corpus-v1";

    private TeamKnowledgeFingerprint() {
    }

    public static String create(Long teamId, Long workspaceId, List<TeamKnowledgeSourceRef> sources) {
        if (teamId == null || teamId <= 0) {
            throw new IllegalArgumentException("teamId must be positive");
        }
        StringBuilder canonical = new StringBuilder(VERSION)
                .append('\n').append(teamId)
                .append('\n').append(workspaceId == null ? "*" : workspaceId);
        ordered(sources).forEach(source -> canonical.append('\n').append(source.canonicalValue()));
        return sha256(canonical.toString());
    }

    static List<TeamKnowledgeSourceRef> ordered(List<TeamKnowledgeSourceRef> sources) {
        if (sources == null || sources.isEmpty()) {
            return List.of();
        }
        return sources.stream()
                .distinct()
                .sorted(java.util.Comparator.comparing(TeamKnowledgeSourceRef::canonicalValue))
                .toList();
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }
}
