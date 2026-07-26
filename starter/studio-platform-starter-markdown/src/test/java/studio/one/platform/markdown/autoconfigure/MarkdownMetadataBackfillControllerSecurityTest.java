package studio.one.platform.markdown.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.security.access.prepost.PreAuthorize;

class MarkdownMetadataBackfillControllerSecurityTest {

    @Test
    void supportsClassBasedMethodSecurityProxy() {
        MarkdownMetadataBackfillService service = org.mockito.Mockito.mock(MarkdownMetadataBackfillService.class);
        ProxyFactory factory = new ProxyFactory(new MarkdownMetadataBackfillController(service));
        factory.setProxyTargetClass(true);

        assertThat(factory.getProxy()).isInstanceOf(MarkdownMetadataBackfillController.class);
    }

    @Test
    void readOperationsRequireMarkdownManage() {
        for (String methodName : Set.of("list", "get", "items")) {
            PreAuthorize authorization = method(methodName).getAnnotation(PreAuthorize.class);
            assertThat(authorization).isNotNull();
            assertThat(authorization.value())
                    .contains("features:markdown")
                    .contains("'manage'")
                    .doesNotContain("services:ai_rag','write");
        }
    }

    @Test
    void mutationOperationsAlsoRequireRagWrite() {
        for (String methodName : Set.of("create", "retry", "cancel")) {
            PreAuthorize authorization = method(methodName).getAnnotation(PreAuthorize.class);
            assertThat(authorization).isNotNull();
            assertThat(authorization.value())
                    .contains("features:markdown")
                    .contains("'manage'")
                    .contains("services:ai_rag")
                    .contains("'write'");
        }
    }

    private Method method(String name) {
        return java.util.Arrays.stream(MarkdownMetadataBackfillController.class.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(name))
                .findFirst()
                .orElseThrow();
    }
}
