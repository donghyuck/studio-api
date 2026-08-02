package studio.one.application.webknowledge.infrastructure.persistence.jpa;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface WebKnowledgeCorpusPageJpaRepository
        extends JpaRepository<WebKnowledgeCorpusPageEntity, String> {

    List<WebKnowledgeCorpusPageEntity> findByCorpusRevisionIdOrderByPageOrder(
            String corpusRevisionId);

    void deleteByCorpusRevisionId(String corpusRevisionId);
}
