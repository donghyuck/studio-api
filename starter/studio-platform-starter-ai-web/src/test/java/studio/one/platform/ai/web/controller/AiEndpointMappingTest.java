package studio.one.platform.ai.web.controller;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.bind.annotation.RequestMapping;

import studio.one.platform.constant.PropertyKeys;

class AiEndpointMappingTest {

    // Verifies annotation values. Placeholder resolution is covered by Spring MVC integration tests.
    @Test
    void mapsUserEndpointsToPublicBasePath() {
        assertRequestMapping(ChatController.class, "${" + PropertyKeys.AI.Endpoints.BASE_PATH + ":/api/ai}/chat");
        assertRequestMapping(QueryRewriteController.class,
                "${" + PropertyKeys.AI.Endpoints.BASE_PATH + ":/api/ai}/query-rewrite");
        assertRequestMapping(AiInfoController.class, "${" + PropertyKeys.AI.Endpoints.BASE_PATH + ":/api/ai}/info");
    }

    @Test
    void mapsManagementEndpointsToMgmtBasePath() {
        assertRequestMapping(EmbeddingController.class,
                "${" + PropertyKeys.AI.Endpoints.MGMT_BASE_PATH + ":/api/mgmt/ai}/embedding");
        assertRequestMapping(VectorController.class,
                "${" + PropertyKeys.AI.Endpoints.MGMT_BASE_PATH + ":/api/mgmt/ai}/vectors");
        assertRequestMapping(RagController.class,
                "${" + PropertyKeys.AI.Endpoints.MGMT_BASE_PATH + ":/api/mgmt/ai}/rag");
    }

    private void assertRequestMapping(Class<?> controllerType, String expectedPath) {
        RequestMapping mapping = AnnotatedElementUtils.findMergedAnnotation(controllerType, RequestMapping.class);
        assertThat(mapping).isNotNull();
        assertThat(mapping.value()).containsExactly(expectedPath);
    }
}
