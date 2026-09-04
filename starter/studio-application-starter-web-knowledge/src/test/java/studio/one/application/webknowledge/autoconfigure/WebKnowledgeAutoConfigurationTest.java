package studio.one.application.webknowledge.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import studio.one.application.webknowledge.infrastructure.persistence.jpa.WebKnowledgeSourceJpaRepository;
import studio.one.platform.ai.core.rag.indexed.IndexedRagSourceProvider;
import studio.one.platform.ai.core.rag.team.TeamKnowledgeSourceContributor;

class WebKnowledgeAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(WebKnowledgeAutoConfiguration.class));

    @Test
    void registersWebKnowledgeTeamKnowledgeSourceContributorWhenDependenciesExist() {
        contextRunner
                .withBean(WebKnowledgeSourceJpaRepository.class, () -> mock(WebKnowledgeSourceJpaRepository.class))
                .withBean("webKnowledgeIndexedRagSourceProvider", IndexedRagSourceProvider.class,
                        () -> mock(IndexedRagSourceProvider.class))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(TeamKnowledgeSourceContributor.class);
                });
    }

    @Test
    void backsOffWhenUserDefinedWebKnowledgeContributorExists() {
        TeamKnowledgeSourceContributor contributor = mock(TeamKnowledgeSourceContributor.class);

        contextRunner
                .withBean(WebKnowledgeSourceJpaRepository.class, () -> mock(WebKnowledgeSourceJpaRepository.class))
                .withBean("webKnowledgeIndexedRagSourceProvider", IndexedRagSourceProvider.class,
                        () -> mock(IndexedRagSourceProvider.class))
                .withBean("webKnowledgeTeamKnowledgeSourceContributor", TeamKnowledgeSourceContributor.class,
                        () -> contributor)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(TeamKnowledgeSourceContributor.class);
                    assertThat(context.getBean("webKnowledgeTeamKnowledgeSourceContributor"))
                            .isSameAs(contributor);
                });
    }
}
