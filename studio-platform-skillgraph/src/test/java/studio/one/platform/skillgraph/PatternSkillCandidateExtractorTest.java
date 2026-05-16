package studio.one.platform.skillgraph;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import studio.one.platform.skillgraph.infrastructure.extraction.PatternSkillCandidateExtractor;

class PatternSkillCandidateExtractorTest {

    @Test
    void extractsTechnicalSkillTerms() {
        PatternSkillCandidateExtractor extractor = new PatternSkillCandidateExtractor();

        var terms = extractor.extract("Spring Boot, OAuth2, JWT 기반 인증 기술을 학습한다.");

        assertTrue(terms.stream().anyMatch(term -> term.term().contains("Spring Boot")));
        assertTrue(terms.stream().anyMatch(term -> term.term().contains("OAuth2")));
        assertTrue(terms.stream().anyMatch(term -> term.term().contains("JWT")));
    }

    @Test
    void returnsEmptyListForBlankText() {
        PatternSkillCandidateExtractor extractor = new PatternSkillCandidateExtractor();

        assertTrue(extractor.extract(" ").isEmpty());
    }
}
