package studio.one.platform.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

class PlatformCapabilitiesTest {
    @Test
    void reportsOnlyActuallyRegisteredControllersWithoutLoadingAbsentFeatureClasses() throws Exception {
        var mapping = new RequestMappingHandlerMapping();
        mapping.registerMapping(RequestMappingInfo.paths("/sample").build(), new Sample(),
                Sample.class.getMethod("get"));
        var beans = new StaticListableBeanFactory(Map.of("mapping", mapping));
        var controller = new PlatformCapabilitiesController(
                beans.getBeanProvider(RequestMappingHandlerMapping.class),
                Map.of("present", Sample.class.getName(), "absent", "missing.module.Controller"));

        var result = controller.capabilities().getData();
        assertThat(result.contractVersion()).isEqualTo("1");
        assertThat(result.features()).containsEntry("present", true).containsEntry("absent", false);
    }

    public static class Sample { public String get() { return "ok"; } }
}
