package studio.one.platform.ai.web.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import studio.one.platform.ai.core.rag.RagObjectAuthorizer;
import studio.one.platform.ai.web.dto.ChatRagRequestDto;

class RagObjectAuthorizationRouterTest {

    @Test
    void rejectsIncompleteScopeAndAttachmentWithoutDomainAuthorizer() {
        RagObjectAuthorizationRouter router = new RagObjectAuthorizationRouter(null);

        assertThat(router.canRead(request("attachment", null))).isFalse();
        assertThat(router.canRead(request(null, "11"))).isFalse();
        assertThat(router.canRead(request("attachment", "11"))).isFalse();
    }

    @Test
    void delegatesAttachmentAccessAndFailsClosedOnAuthorizerFailure() {
        RagObjectAuthorizer allowed = authorizer(true, false);
        RagObjectAuthorizationRouter router = new RagObjectAuthorizationRouter(null, List.of(allowed));
        assertThat(router.canRead(request("attachment", "11"))).isTrue();
        assertThat(router.canRead("attachment", "11")).isTrue();

        RagObjectAuthorizationRouter failing =
                new RagObjectAuthorizationRouter(null, List.of(authorizer(false, true)));
        assertThat(failing.canRead(request("attachment", "11"))).isFalse();
    }

    private RagObjectAuthorizer authorizer(boolean allowed, boolean fail) {
        return new RagObjectAuthorizer() {
            @Override
            public boolean supports(String objectType) {
                return "attachment".equals(objectType);
            }

            @Override
            public boolean canRead(String objectType, String objectId) {
                if (fail) {
                    throw new IllegalStateException("test");
                }
                return allowed;
            }
        };
    }

    private ChatRagRequestDto request(String objectType, String objectId) {
        return new ChatRagRequestDto(null, null, null, objectType, objectId);
    }
}
