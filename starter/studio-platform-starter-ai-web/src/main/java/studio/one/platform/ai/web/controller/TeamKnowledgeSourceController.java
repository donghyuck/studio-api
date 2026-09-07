package studio.one.platform.ai.web.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import studio.one.platform.ai.core.rag.team.TeamRagScopeResolver;
import studio.one.platform.ai.web.dto.TeamKnowledgeSourceDto;
import studio.one.platform.web.dto.ApiResponse;

@RestController
@RequestMapping("/api/teams")
public class TeamKnowledgeSourceController {

    private final TeamRagScopeResolver scopeResolver;

    public TeamKnowledgeSourceController(TeamRagScopeResolver scopeResolver) {
        if (scopeResolver == null) {
            throw new IllegalArgumentException("scopeResolver must not be null");
        }
        this.scopeResolver = scopeResolver;
    }

    @GetMapping("/{teamId:[\\p{Digit}]+}/knowledge-sources")
    @PreAuthorize("@endpointAuthz.can('services:ai_rag','read')")
    public ResponseEntity<ApiResponse<List<TeamKnowledgeSourceDto>>> list(
            @PathVariable Long teamId,
            @RequestParam(required = false) Long workspaceId) {
        var manifest = scopeResolver.resolveAuthorized(teamId, workspaceId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "TEAM_RAG_SCOPE_NOT_FOUND"));
        List<TeamKnowledgeSourceDto> result = manifest.sources().stream()
                .map(source -> new TeamKnowledgeSourceDto(
                        source.teamId(),
                        source.workspaceId(),
                        source.sourceType().name(),
                        source.objectId(),
                        source.objectId(),
                        source.revisionId(),
                        source.partitionIds()))
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(result));
    }
}
