import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Configuration;

class CompositionTest {
    private final String modules = System.getProperty("moduleSet");
    private final boolean team = List.of("team", "workspace", "team-ai", "full", "rag-minimal").contains(modules);
    private final boolean workspace = List.of("workspace", "full", "rag-minimal").contains(modules);
    private final boolean ai = List.of("ai", "team-ai", "full", "rag-minimal").contains(modules);

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    static class Consumer { }

    @Test
    void bootAutoDiscoveryStartsWithFeaturesDisabled() {
        new WebApplicationContextRunner().withUserConfiguration(Consumer.class)
                .withPropertyValues("studio.features.ai.enabled=false", "studio.features.team.enabled=false",
                        "studio.features.workspace.enabled=false",
                        "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.containsBean("platformCapabilitiesController")).isTrue();
                    try {
                        Object controller = context.getBean("platformCapabilitiesController");
                        Object response = controller.getClass().getMethod("capabilities").invoke(controller);
                        Object data = response.getClass().getMethod("getData").invoke(response);
                        Map<?, ?> features = (Map<?, ?>) data.getClass().getMethod("features").invoke(data);
                        assertThat(features.containsKey("ai-chat")).isEqualTo(ai);
                        if (ai) assertThat(features.get("ai-chat")).isEqualTo(false);
                    } catch (ReflectiveOperationException ex) { throw new AssertionError(ex); }
                });
    }

    @Test
    void publishedArtifactsHaveOnlyRequestedFeatureDependencies() throws Exception {
        assertThat(present("studio.one.platform.team.application.usecase.TeamService")).isEqualTo(team);
        assertThat(present("studio.one.platform.workspace.application.usecase.WorkspaceTreeService")).isEqualTo(workspace);
        assertThat(present("studio.one.platform.ai.core.chat.ChatPort")).isEqualTo(ai);
        assertThat(present("studio.one.platform.textract.autoconfigure.TextractAutoConfiguration")).isFalse();
        assertThat(present("studio.one.platform.thumbnail.autoconfigure.ThumbnailAutoConfiguration")).isFalse();
        if (ai) Class.forName("studio.one.platform.ai.autoconfigure.AiWebAutoConfiguration").getDeclaredMethods();
    }

    @Test
    void selectedConfigurationsStartAndUnusedBridgesBackOff() throws Exception {
        List<Class<?>> configs = new ArrayList<>();
        configs.add(ConfigurationPropertiesAutoConfiguration.class);
        if (team) {
            configs.add(Class.forName("org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration"));
            configs.add(Class.forName("org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration"));
            configs.add(Class.forName("studio.one.platform.team.autoconfigure.TeamAutoConfiguration"));
        }
        if (workspace) configs.add(Class.forName("studio.one.platform.workspace.autoconfigure.WorkspaceAutoConfiguration"));
        if (ai) {
            configs.add(Class.forName("org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration"));
            configs.add(Class.forName("studio.one.platform.ai.autoconfigure.AiWebAutoConfiguration"));
            if (team && workspace) configs.add(Class.forName("studio.one.platform.ai.autoconfigure.AiTeamRagAutoConfiguration"));
        }
        ApplicationContextRunner runner = new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(configs.toArray(Class<?>[]::new)))
                .withPropertyValues("spring.datasource.generate-unique-name=true",
                        "spring.datasource.driver-class-name=org.h2.Driver", "spring.jpa.hibernate.ddl-auto=create-drop",
                        "studio.features.team.enabled=" + team, "studio.features.workspace.enabled=" + workspace,
                        "studio.ai.vector.projection.enabled=" + !modules.equals("rag-minimal"),
                        "studio.features.ai.enabled=" + ai, "studio.ai.endpoints.enabled=" + ai);
        if (ai) {
            // Infrastructure/provider ports are test doubles: no model calls or external DB access.
            for (String type : List.of("studio.one.platform.ai.service.pipeline.RagPipelineService",
                    "studio.one.platform.ai.model.ModelDeploymentRegistry",
                    "studio.one.platform.ai.core.embedding.EmbeddingPort",
                    "studio.one.platform.ai.core.chat.ChatPort",
                    "studio.one.platform.ai.service.prompt.PromptRenderer")) runner = withMock(runner, type);
            runner = withMock(runner, "studio.one.platform.ai.autoconfigure.config.AiAdapterProperties");
            if (team && workspace) runner = withMock(runner, "studio.one.platform.identity.PrincipalResolver");
        }
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.containsBean("teamService")).isEqualTo(team);
            assertThat(context.containsBean("workspaceTreeService")).isEqualTo(workspace);
            assertThat(context.containsBean("chatController")).isEqualTo(ai);
            assertThat(context.containsBean("teamRagScopeResolver")).isEqualTo(team && workspace && ai);
            assertThat(context.containsBean("vectorProjectionExecutor")).isEqualTo(ai && !modules.equals("rag-minimal"));
        });
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private ApplicationContextRunner withMock(ApplicationContextRunner runner, String name) throws Exception {
        Class type = Class.forName(name);
        return runner.withBean(type, () -> mock(type));
    }
    private boolean present(String name) {
        try { Class.forName(name, false, getClass().getClassLoader()); return true; }
        catch (ClassNotFoundException ex) { return false; }
    }
}
