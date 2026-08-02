package studio.one.platform.ai.web.controller;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import studio.one.platform.ai.core.rag.indexed.IndexedRagSourceProvider;
import studio.one.platform.ai.core.rag.indexed.ResolvedIndexedRagSource;
import studio.one.platform.ai.web.dto.IndexedWebSourceRefDto;

public record RagEvidenceSourceSelection(
        List<ResolvedIndexedRagSource> indexedSources,
        String embeddingDeploymentId,
        String embeddingSpaceId,
        String fingerprint) {

    public RagEvidenceSourceSelection {
        indexedSources = indexedSources == null ? List.of() : List.copyOf(indexedSources);
        fingerprint = fingerprint == null ? sha256("") : fingerprint;
    }

    public static RagEvidenceSourceSelection resolve(
            List<IndexedWebSourceRefDto> requested,
            List<IndexedRagSourceProvider> providers,
            RagObjectAuthorizationRouter authorizationRouter,
            int maxSources) {
        if (requested == null || requested.isEmpty()) {
            return new RagEvidenceSourceSelection(List.of(), null, null, sha256(""));
        }
        if (requested.size() > maxSources) {
            throw new IllegalArgumentException("indexedWebSources must contain at most " + maxSources + " sources");
        }
        Set<String> seen = new LinkedHashSet<>();
        List<ResolvedIndexedRagSource> resolved = new ArrayList<>();
        for (IndexedWebSourceRefDto reference : requested) {
            if (reference.corpusRevisionId() != null
                    && !reference.corpusRevisionId().isBlank()
                    && reference.revisionId() != null
                    && !reference.revisionId().isBlank()
                    && !reference.corpusRevisionId().equals(reference.revisionId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "WEB_SOURCE_REVISION_CONFLICT");
            }
            String selectedRevision = reference.corpusRevisionId() == null
                    ? reference.revisionId()
                    : reference.corpusRevisionId();
            if (selectedRevision == null || selectedRevision.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "WEB_CORPUS_REVISION_REQUIRED");
            }
            String key = reference.sourceId() + ":" + selectedRevision;
            if (!seen.add(key)) {
                continue;
            }
            if (authorizationRouter == null
                    || !authorizationRouter.canRead("web_source", reference.sourceId())) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "WEB_SOURCE_NOT_FOUND");
            }
            ResolvedIndexedRagSource source = providers == null
                    ? null
                    : providers.stream()
                            .filter(provider -> provider.supports("web_source"))
                            .map(provider -> provider.resolve(reference.sourceId(), selectedRevision))
                            .flatMap(java.util.Optional::stream)
                            .findFirst()
                            .orElse(null);
            if (source == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "WEB_SOURCE_REVISION_NOT_READY");
            }
            resolved.add(source);
        }
        String deployment = unique(resolved.stream()
                .map(ResolvedIndexedRagSource::embeddingDeploymentId)
                .toList(), "WEB_SOURCE_EMBEDDING_SPACE_MISMATCH");
        String space = unique(resolved.stream()
                .map(ResolvedIndexedRagSource::embeddingSpaceId)
                .filter(value -> value != null && !value.isBlank())
                .toList(), "WEB_SOURCE_EMBEDDING_SPACE_MISMATCH");
        List<ResolvedIndexedRagSource> ordered = resolved.stream()
                .sorted(Comparator.comparing(ResolvedIndexedRagSource::sourceId)
                        .thenComparing(ResolvedIndexedRagSource::revisionId))
                .toList();
        String material = ordered.stream()
                .map(source -> String.join("|",
                        source.sourceId(),
                        source.revisionId(),
                        source.contentHash(),
                        String.valueOf(source.metadata().getOrDefault("crawlPolicyHash", "")),
                        source.embeddingDeploymentId(),
                        source.embeddingSpaceId() == null ? "" : source.embeddingSpaceId(),
                        String.join(",", source.partitionIds().stream().sorted().toList())))
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
        return new RagEvidenceSourceSelection(ordered, deployment, space, sha256(material));
    }

    public Map<String, Object> toMetadata() {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("fingerprint", fingerprint);
        value.put("count", indexedSources.size());
        if (embeddingDeploymentId != null) {
            value.put("embeddingDeploymentId", embeddingDeploymentId);
        }
        if (embeddingSpaceId != null) {
            value.put("embeddingSpaceId", embeddingSpaceId);
        }
        value.put("sources", indexedSources.stream()
                .map(source -> Map.of(
                        "sourceId", source.sourceId(),
                        "revisionId", source.revisionId(),
                        "partitionCount", source.partitionIds().size()))
                .toList());
        return Map.copyOf(value);
    }

    private static String unique(List<String> values, String errorCode) {
        Set<String> unique = values.stream()
                .filter(value -> value != null && !value.isBlank())
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (unique.size() > 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, errorCode);
        }
        return unique.stream().findFirst().orElse(null);
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }
}
