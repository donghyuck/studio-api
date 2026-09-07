package studio.one.platform.ai.web.controller;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import studio.one.platform.ai.core.rag.RagObjectScope;
import studio.one.platform.ai.core.rag.RagSearchRequest;
import studio.one.platform.ai.core.rag.RagSearchResult;
import studio.one.platform.ai.core.rag.team.TeamKnowledgeManifest;
import studio.one.platform.ai.core.rag.team.TeamKnowledgeSourceRef;
import studio.one.platform.ai.core.rag.team.TeamRagScopeResolver;
import studio.one.platform.ai.service.pipeline.RagPipelineService;
import studio.one.platform.ai.web.cache.TeamRagCacheScope;

/**
 * Bounded multi-object retrieval for Team Chat. Missing and denied scopes both return empty.
 */
public final class TeamRagRetrievalService {

    private static final int MAX_ROUTED_OBJECT_SCOPES = 8;
    private static final double RECIPROCAL_RANK_CONSTANT = 60.0d;

    private final RagPipelineService ragPipelineService;
    private final TeamRagScopeResolver scopeResolver;
    private final int maxObjectScopes;

    public TeamRagRetrievalService(
            RagPipelineService ragPipelineService,
            TeamRagScopeResolver scopeResolver,
            int maxObjectScopes) {
        if (ragPipelineService == null || scopeResolver == null) {
            throw new IllegalArgumentException("pipeline and scope resolver are required");
        }
        if (maxObjectScopes <= 0 || maxObjectScopes > RagPipelineService.MAX_AGGREGATE_OBJECT_SCOPES) {
            throw new IllegalArgumentException("maxObjectScopes is outside the supported range");
        }
        this.ragPipelineService = ragPipelineService;
        this.scopeResolver = scopeResolver;
        this.maxObjectScopes = maxObjectScopes;
    }

    public Optional<RetrievalResult> retrieve(Long teamId, Long workspaceId, RagSearchRequest request) {
        return retrieveQueries(teamId, workspaceId, List.of(request));
    }

    public Optional<RetrievalResult> retrieveQueries(
            Long teamId,
            Long workspaceId,
            List<RagSearchRequest> requestedQueries) {
        TeamKnowledgeManifest manifest = scopeResolver.resolveAuthorized(teamId, workspaceId).orElse(null);
        if (manifest == null) {
            return Optional.empty();
        }
        List<RagSearchRequest> queries = sanitizeQueries(requestedQueries);
        if (queries.isEmpty()) {
            throw new IllegalArgumentException("at least one Team RAG query is required");
        }
        List<RagObjectScope> allScopes = manifest.objectScopes();
        if (allScopes.size() > maxObjectScopes) {
            throw new IllegalArgumentException("Team knowledge scope exceeds configured source limit");
        }
        List<List<RagSearchResult>> resultLists = new ArrayList<>();
        List<RagSearchResult> primaryResults = authorizedSearch(manifest, queries.get(0), allScopes);
        resultLists.add(primaryResults);

        List<RagObjectScope> routedScopes = routedScopes(
                manifest, primaryResults, allScopes);
        boolean routingApplied = queries.size() > 1
                && !routedScopes.isEmpty()
                && routedScopes.size() < allScopes.size();
        List<RagObjectScope> followUpScopes = routingApplied
                ? routedScopes
                : allScopes;
        for (int index = 1; index < queries.size(); index++) {
            resultLists.add(authorizedSearch(manifest, queries.get(index), followUpScopes));
        }

        int resultLimit = queries.stream().mapToInt(RagSearchRequest::topK).max().orElse(queries.get(0).topK());
        List<RagSearchResult> results = reciprocalRank(resultLists, resultLimit);
        List<TeamRagCitationRef> citations = results.stream()
                .map(result -> citation(manifest, result))
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        return Optional.of(new RetrievalResult(
                results,
                manifest,
                TeamRagCacheScope.from(manifest),
                citations,
                queries.size(),
                allScopes.size(),
                followUpScopes.size(),
                routingApplied,
                primaryResults.isEmpty() && !results.isEmpty()));
    }

    private List<RagSearchRequest> sanitizeQueries(List<RagSearchRequest> requestedQueries) {
        if (requestedQueries == null || requestedQueries.isEmpty()) {
            return List.of();
        }
        Map<String, RagSearchRequest> unique = new LinkedHashMap<>();
        requestedQueries.stream()
                .filter(java.util.Objects::nonNull)
                .filter(request -> request.query() != null && !request.query().isBlank())
                .forEach(request -> unique.putIfAbsent(request.query().trim(), request));
        return List.copyOf(unique.values());
    }

    private List<RagSearchResult> authorizedSearch(
            TeamKnowledgeManifest manifest,
            RagSearchRequest request,
            List<RagObjectScope> scopes) {
        return ragPipelineService.searchByObjects(request, scopes, maxObjectScopes).stream()
                .filter(result -> canReadResult(manifest, result))
                .toList();
    }

    private List<RagObjectScope> routedScopes(
            TeamKnowledgeManifest manifest,
            List<RagSearchResult> primaryResults,
            List<RagObjectScope> allScopes) {
        if (primaryResults == null || primaryResults.isEmpty()) {
            return allScopes;
        }
        Set<String> candidateKeys = new LinkedHashSet<>();
        for (RagSearchResult result : primaryResults) {
            Map<String, Object> metadata = result.metadata() == null ? Map.of() : result.metadata();
            String objectType = text(metadata.get("objectType"));
            String objectId = text(metadata.get("objectId"));
            if (objectType != null && objectId != null && manifest.contains(objectType, objectId, null)) {
                candidateKeys.add(scopeKey(objectType, objectId));
            }
            if (candidateKeys.size() >= MAX_ROUTED_OBJECT_SCOPES) {
                break;
            }
        }
        if (candidateKeys.isEmpty()) {
            return allScopes;
        }
        return allScopes.stream()
                .filter(scope -> candidateKeys.contains(scopeKey(scope.objectType(), scope.objectId())))
                .limit(MAX_ROUTED_OBJECT_SCOPES)
                .toList();
    }

    private List<RagSearchResult> reciprocalRank(List<List<RagSearchResult>> resultLists, int limit) {
        Map<String, RankedResult> ranked = new LinkedHashMap<>();
        for (List<RagSearchResult> results : resultLists) {
            if (results == null) {
                continue;
            }
            for (int index = 0; index < results.size(); index++) {
                RagSearchResult result = results.get(index);
                String key = resultKey(result);
                RankedResult current = ranked.get(key);
                double contribution = 1.0d / (RECIPROCAL_RANK_CONSTANT + index + 1);
                if (current == null) {
                    ranked.put(key, new RankedResult(result, contribution));
                } else {
                    RagSearchResult best = result.score() > current.result().score() ? result : current.result();
                    ranked.put(key, new RankedResult(best, current.rankScore() + contribution));
                }
            }
        }
        return ranked.values().stream()
                .sorted(Comparator.comparingDouble(RankedResult::rankScore).reversed()
                        .thenComparing(Comparator.comparingDouble(
                                (RankedResult value) -> value.result().score()).reversed()))
                .limit(Math.max(1, limit))
                .map(RankedResult::result)
                .toList();
    }

    private String resultKey(RagSearchResult result) {
        Map<String, Object> metadata = result.metadata() == null ? Map.of() : result.metadata();
        String chunkId = firstText(metadata, "chunkId", "documentChunkId", "_documentChunkId");
        return String.join(":",
                String.valueOf(metadata.getOrDefault("objectType", "")),
                String.valueOf(metadata.getOrDefault("objectId", "")),
                chunkId == null ? result.documentId() : chunkId);
    }

    private String scopeKey(String objectType, String objectId) {
        return objectType + ":" + objectId;
    }

    private boolean canReadResult(TeamKnowledgeManifest manifest, RagSearchResult result) {
        Map<String, Object> metadata = result.metadata() == null ? Map.of() : result.metadata();
        String objectType = text(metadata.get("objectType"));
        String objectId = text(metadata.get("objectId"));
        if (objectType == null || objectId == null) {
            return false;
        }
        TeamKnowledgeSourceRef source = manifest.sources().stream()
                .filter(candidate -> candidate.objectType().equals(objectType)
                        && candidate.objectId().equals(objectId))
                .findFirst()
                .orElse(null);
        if (source == null) {
            return false;
        }
        return true;
    }

    private String firstText(Map<String, Object> metadata, String... keys) {
        for (String key : keys) {
            String value = text(metadata.get(key));
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private TeamRagCitationRef citation(TeamKnowledgeManifest manifest, RagSearchResult result) {
        Map<String, Object> metadata = result.metadata() == null ? Map.of() : result.metadata();
        String objectType = text(metadata.get("objectType"));
        String objectId = text(metadata.get("objectId"));
        TeamKnowledgeSourceRef source = manifest.sources().stream()
                .filter(candidate -> candidate.objectType().equals(objectType)
                        && candidate.objectId().equals(objectId))
                .findFirst()
                .orElse(null);
        if (source == null) {
            return null;
        }
        String revisionId = firstText(metadata, "markdownRevisionId", "corpusRevisionId", "revisionId");
        return new TeamRagCitationRef(
                source.workspaceId(),
                objectType,
                objectId,
                revisionId == null ? source.revisionId() : revisionId);
    }

    private String text(Object value) {
        if (value == null || value.toString().isBlank()) {
            return null;
        }
        return value.toString().trim();
    }

    private record RankedResult(RagSearchResult result, double rankScore) {
    }

    public record RetrievalResult(
            List<RagSearchResult> results,
            TeamKnowledgeManifest manifest,
            TeamRagCacheScope cacheScope,
            List<TeamRagCitationRef> citations,
            int executedQueryCount,
            int sourceScopeCount,
            int routedScopeCount,
            boolean routingApplied,
            boolean coverageFallback) {

        public RetrievalResult {
            results = results == null ? List.of() : List.copyOf(results);
            citations = citations == null ? List.of() : List.copyOf(citations);
        }

        public RetrievalResult(
                List<RagSearchResult> results,
                TeamKnowledgeManifest manifest,
                TeamRagCacheScope cacheScope,
                List<TeamRagCitationRef> citations) {
            this(
                    results,
                    manifest,
                    cacheScope,
                    citations,
                    1,
                    manifest == null ? 0 : manifest.objectScopes().size(),
                    manifest == null ? 0 : manifest.objectScopes().size(),
                    false,
                    false);
        }
    }
}
