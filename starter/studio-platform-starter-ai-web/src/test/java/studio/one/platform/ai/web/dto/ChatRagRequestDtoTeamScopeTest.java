package studio.one.platform.ai.web.dto;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

class ChatRagRequestDtoTeamScopeTest {

    @Test
    void workspaceRequiresTeam() {
        assertThatThrownBy(() -> request(null, 2L, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("requires teamId");
    }

    @Test
    void teamScopeCannotBeCombinedWithObjectScope() {
        assertThatThrownBy(() -> request(7L, null, "attachment", "10"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be combined");
    }

    private ChatRagRequestDto request(Long teamId, Long workspaceId, String objectType, String objectId) {
        return new ChatRagRequestDto(
                new ChatRequestDto(null, null, List.of(new ChatMessageDto("user", "question")),
                        null, null, null, null, null, null),
                "question", 3, objectType, objectId,
                null, null, null, null, null, null, null, null, null,
                null, null, null, null, teamId, workspaceId);
    }
}
