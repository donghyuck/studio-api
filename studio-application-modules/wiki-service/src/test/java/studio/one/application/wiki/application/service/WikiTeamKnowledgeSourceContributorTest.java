package studio.one.application.wiki.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;

import studio.one.application.wiki.infrastructure.persistence.jpa.WikiPageEntity;
import studio.one.application.wiki.infrastructure.persistence.jpa.WikiPageJpaRepository;
import studio.one.platform.ai.core.rag.team.TeamKnowledgeContributionRequest;

class WikiTeamKnowledgeSourceContributorTest {

    @Test
    void contributesCurrentWikiRevisionByExistingPageObjectId() {
        WikiPageJpaRepository pages = mock(WikiPageJpaRepository.class);
        WikiPageEntity page = new WikiPageEntity();
        page.setPageId(11L);
        page.setWorkspaceId(2L);
        page.setCurrentRevisionId(31L);
        when(pages.findByWorkspaceIdAndArchivedFalse(org.mockito.ArgumentMatchers.eq(2L), any()))
                .thenReturn(new PageImpl<>(List.of(page)));

        var result = new WikiTeamKnowledgeSourceContributor(pages).contribute(
                new TeamKnowledgeContributionRequest(7L, Set.of(2L), 10));

        assertThat(result).singleElement().satisfies(ref -> {
            assertThat(ref.objectType()).isEqualTo("wiki_page");
            assertThat(ref.objectId()).isEqualTo("11");
            assertThat(ref.revisionId()).isEqualTo("31");
        });
    }
}
