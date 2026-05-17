package studio.one.platform.skillgraph.application.service;

import java.util.List;

import studio.one.platform.skillgraph.domain.port.SkillCandidateExtractor;
import studio.one.platform.skillgraph.domain.port.SkillCandidateStore;
import studio.one.platform.skillgraph.domain.port.SkillDictionaryStore;
import studio.one.platform.skillgraph.domain.port.SkillEmbeddingPort;
import studio.one.platform.skillgraph.domain.port.NoOpSkillEmbeddingPort;

/**
 * 텍스트 기반 스킬 추출 유스케이스 구현체.
 *
 *
 *
 * 주요 역할:
 *
 * - 입력 텍스트에서 스킬 후보 추출
 *
 * - Skill Dictionary 매칭
 *
 * - embedding 기반 유사 스킬 탐색
 *
 * - SkillCandidate 저장
 *
 *
 *
 * 핵심 처리 흐름:
 *
 * 1. 입력 텍스트 검증
 * 2. source chunk 저장
 * 3. SkillCandidateExtractor 실행
 * 4. 용어 normalize
 * 5. exact/alias 매칭 시도
 * 6. 실패 시 embedding 유사도 검색
 * 7. SkillCandidate 생성 또는 기존 후보 갱신

 *
 * 현재 구조는 패턴 기반 후보 추출에 의존하므로 단순 기술 키워드 수준 결과가 발생할 수 있다.
 *
 * 향후 개선 방향:
 * - Token-aware chunking
 * - semantic chunking
 * - LLM structured extraction
 * - canonical skill generation
 * - evidence 기반 confidence scoring
 *
 * @author donghyuck, son
 * @since 2026-05-17
 *
 *        <pre>
 *
 * &lt;&lt; 개정이력(Modification Information) &gt;&gt;
 *   수정일        수정자           수정내용
 *  ---------    --------    ---------------------------
 * 2026-05-17  donghyuck, son: 최초 생성.
 *        </pre>
 */

public class RegexSkillCandidateExtractor extends AbstractSkillCandidateExtractor {

    private final SkillCandidateExtractor extractor;

    public RegexSkillCandidateExtractor(SkillCandidateStore store, SkillCandidateExtractor extractor) {
        this(store, null, extractor, new NoOpSkillEmbeddingPort(), SkillMatchPolicy.defaults());
    }

    public RegexSkillCandidateExtractor(
            SkillCandidateStore store,
            SkillDictionaryStore dictionaryStore,
            SkillCandidateExtractor extractor,
            SkillEmbeddingPort embeddingPort,
            SkillMatchPolicy matchPolicy) {
        super(store, dictionaryStore, embeddingPort, matchPolicy);
        this.extractor = extractor;
    }

    @Override
    protected List<SkillCandidateExtractor.ExtractedSkillTerm> recommendTerms(String text) {
        return extractor.extract(text);
    }
}
