# Changelog

## 3.0.0-rc.1

- AI/RAG 전체 모듈 경계, 색인, 근거·인용, SSE, 운영 진단을 연결하는 문서 진입점을 추가했다.
  누락돼 있던 document-metadata와 chunking-runtime README를 보완하고 루트·AI starter·Markdown
  문서에서 동일한 기준 문서로 탐색할 수 있도록 정리했다.

- `3.x` 첫 release candidate를 Java 17, Gradle 8.14.5, Spring Boot 4.1.0,
  Spring AI 2.0.0, MyBatis Spring Boot Starter 4.0.1, Boot 관리형 Jackson 3으로
  전환했다. Boot 4의 분리된 WebMVC·AspectJ·Flyway starter를 적용하고 classic/Jackson 2
  application runtime 유입을 검증하는 build gate를 추가했다. 개발 실행 서버에서 빈 PostgreSQL
  migration, 인증, chat/embedding deployment, RAG sync/SSE, Redis exact-cache
  `MISS/HIT`, 근거 변경 miss와 Redis 장애 fail-open까지 검증했다. 같은 3.x schema에서
  `v2.1.0-rc.1` 플랫폼과 2.x 소비 서버를 다시 기동해 artifact rollback도 확인했다.

- RAG exact-answer cache의 기본 TTL을 5분, Jackson 3 namespace를
  `studio:ai:rag-answer:v2`로 확정했다. Redis payload의 실제 round-trip/TTL 통합 테스트와
  손상 payload fail-open, 캐시 hit 재인용 검증, sync/SSE canonical parity 회귀 테스트를
  추가했다.

- Apache POI를 5.5.1로 갱신해 문서 처리 모듈의 Commons Compress가 1.28.0으로 수렴하도록
  했으며 dependency-check에서 확인된 Commons Compress CVE 2건을 제거했다.

- `2.1.0-rc.1` release candidate로 artifact version을 갱신했다. 이 candidate는 Java 17,
  Gradle 8.14.5, Spring Boot 3.5.16, Spring AI 1.1.8 호환 기준선을 보존하며, 개발 실행
  서버의 ApplicationContext·DB migration·chat/embedding·RAG sync/SSE·Redis fail-open
  검증을 통과한 뒤에만 `v2.1.0`으로 승격한다.

- Redis가 설치되지 않은 실행 서버에서도 RAG answer-cache 자동 구성을 안전하게 건너뛰도록 Redis 전용 구성을 클래스패스 조건부 중첩 구성으로 격리했습니다.

- Spring Boot 3.5.16, Spring AI 1.1.8, Gradle 8.14.5로 패치 기준선을 갱신하고 Spring AI BOM을
  공통 property로 중앙화했다. Boot 4.1/Spring AI 2.0 격리 스파이크에서 확인한 Boot 자동구성
  모듈화와 Jackson 3 전환 blocker를 문서화했으며, 제품 전환은 compatibility gate 충족 전까지
  보류한다.

- 검증된 canonical RAG 답변을 저장하는 `NONE | CAFFEINE | REDIS` exact cache를 추가했다.
  authorization과 최신 evidence packing 이후에만 조회하며 principal/object/model/retrieval/prompt/
  evidence fingerprint가 일치할 때 provider 생성을 생략한다. citation이 `INDEX_VALID`인 답변만
  versioned JSON으로 저장하고 Redis 장애는 기본 fail-open miss로 처리한다.

- AI Web과 AI core starter의 개발 artifact 버전이 일시적으로 어긋난 환경에서도 선택적 JDBC vector
  projection 구현 class가 없으면 해당 auto-configuration을 조건 평가 전에 건너뛰도록 classpath guard를
  추가했다. 정상 배포에서는 모든 Studio AI artifact와 Spring Boot BOM 버전을 동일하게 맞춰야 한다.

- provider가 반환한 prompt cache 사용량을 uncached/read/write token으로 정규화하고, 일반 채팅과 RAG를
  분리해 모델별 hit ratio와 cache-aware 비용을 집계한다. Google GenAI와 OpenAI-compatible 응답의
  cache token을 관측하되 request body와 RAG prompt는 변경하지 않는다. 모델 카탈로그에는 검증된
  prompt cache capability를 추가하고, Micrometer가 있으면 원문이나 object 식별자를 포함하지 않는
  저카디널리티 cache metric을 기록한다.

- 문서 의미 유형과 유형별 메타데이터 스키마를 공통 `studio-platform-document-metadata` 모듈로
  분리하고 Markdown 파이프라인에 `METADATA_ENRICHMENT` 단계를 추가했다. 네이티브·구조 기반 값을
  우선 사용하며 신뢰도가 낮은 경우에만 등록된 `CHAT` structured-output deployment를 호출한다.
  revision별 `DOCUMENT_METADATA` artifact, schema/detail API, 멱등 backfill job과 DRY_RUN/APPLY
  절차를 추가했다.

- RAG prompt와 API 근거를 동일한 `PackedEvidenceSet`에서 생성하도록 변경했다. packed evidence가
  없으면 모델을 호출하지 않으며, sync/SSE가 공통 citation validation과 canonical finalization을
  사용한다. SSE `complete` 이벤트는 canonical content와 검증된 reference를 제공하고, 확장된
  previous/seed/next 컨텍스트도 각 원본 chunk ID와 exact source span을 보존한다. 프롬프트가 요구하는
  복수 근거 형식 `[1, 2]`를 citation validator도 동일하게 인식하며, metadata enrichment에서 생성된
  compact 문서 메타데이터를 같은 실행의 청크와 RAG 벡터에 투영한다.

- Pandoc 제출 실패와 비동기 실패 callback이 동시에 native fallback을 요청해 동일 revision의 추출 작업이
  중복 예약되던 경쟁 조건을 제거했다. 먼저 예약된 fallback은 계속 실행하고 후속 요청은 현재 revision을
  반환하므로 `Markdown task is already running` 오류로 생성 API가 실패하지 않는다. 실패 callback이
  native fallback으로 전환한 뒤 늦게 반환된 Pandoc submit 응답이 revision을 다시 PANDOC 상태로 덮어쓰지
  않도록 현재 상태를 재확인한다.

- `POST /api/ai/chat/rag/stream` SSE API를 추가했다. 기존 RAG 검색·컨텍스트 구성과 권한 계약을 공유하고,
  검색 시작·완료 상태, 답변 delta, token usage, RAG 근거와 구간별 timing을 순서대로 전달한다. RAG Chat
  클라이언트도 fetch 기반 SSE 처리로 전환해 검색 진행 상태와 생성 중인 답변을 즉시 표시한다.

- EPUB extraction now loads only package and HTML content entries, and exposes configurable per-entry and combined extracted-size limits under `studio.textract.epub`.

- Markdown 추출·재추출·resume·RAG 재색인과 RAG 작업 생성의 임베딩 선택 계약을
  `embeddingDeploymentId`로 통일했다. 기존 `embeddingModelId`와 `embeddingProfileId`는 호환 입력으로
  유지하되, 찾을 수 없는 명시 선택을 기본 모델로 조용히 대체하지 않는다. 선택한 deployment ID를
  RAG 작업 이력에 저장하고 API 응답에 catalog/embedding-space 식별자를 함께 노출한다. 기존 벡터는
  canonical profile이 실제 생성 모델을 증명하는 경우에만 provider/model/deployment metadata를 보정한다.

- 채팅·임베딩 모델의 provider 중립 계약과 built-in model catalog 모듈을 추가했다. 현재 사용 중인
  Gemma/KURE/Gemini와 검증된 managed·open-weight 모델 정보를 catalog snapshot으로 제공하며,
  deployment 활성화와 catalog 등록을 분리한다. embedding vector 공간은 모델·차원·정규화·task type·
  input transform을 canonical SHA-256 fingerprint로 식별할 수 있다. 신규 `ModelDeploymentRegistry`는
  `studio.ai.model-deployments` 설정을 우선 사용하고, 설정이 없으면 기존 provider channel에서 deployment를
  합성한다. 채팅, RAG, SkillGraph, LLM blockify와 벡터 시각화의 런타임 선택은
  `ModelDeploymentRegistry`로 전환하고, `AiProviderRegistry`는 기존 생성자 및 옵션 API 호환용으로 유지한다.
  RAG index/search 요청은 `embeddingDeploymentId`를 받아 canonical embedding-space metadata를 기록하며,
  model/deployment 조회 API와 PostgreSQL dry-run/보수적 batch backfill 서비스를 제공한다. 신규 조회 API는
  declared/effective modality 및 catalog/provider/adapter/effective 상태를 구분하고, 기존
  `embedding-options`와 `info/providers`에는 deployment summary를 additive하게 투영한다. 명시적
  deployment 설정에서는 provider를 연결 계정 단위로 재사용하고 catalog의 `apiModel`·dimension으로
  deployment별 port를 생성한다. 기존 embedding profile ID는 중복 모델 설정 없이 deployment로 위임할
  수 있으며, 신규 설정이 없을 때의 provider channel 기반 legacy 합성 경로는 rollback용으로 유지한다.
  secret presence guard도 deployment 모드에서는 provider channel 모델 대신 기본 deployment와 연결
  credential/base URL을 검증해 catalog 기반 설정이 시작 단계에서 잘못 거부되지 않도록 했다.

- Markdown 청킹 옵션을 생략하거나 `documentProfile=AUTO`를 사용하면 normalized block의 구조를 분석해
  `structure-based` 또는 `recursive`를 자동 선택한다. 구조 기반 처리의 기존 `recursive → fixed-size`
  fallback은 유지하며 `fixed-size`, `blockify`, `knowledge-block`은 자동 선택 대상에서 제외한다.
  실제 선택 전략과 결정 근거를 chunk metadata 및 ChunkSet fingerprint에 additive하게 기록한다.

- 임베딩 선택 옵션에 Google 공식 모델명을 포함한 canonical `embeddingModelId`, 사용자 표시명,
  `embeddingSpaceId`, legacy alias를 추가한다. 기존 `embeddingProfileId` 요청은 호환 alias로 유지하고,
  동일 모델·차원의 기존 PostgreSQL 벡터 metadata는 재임베딩 없이 canonical 식별자로 이관한다.
  `gemini-embedding-2`에는 지원하지 않는 legacy `task_type`을 전달하지 않는다.

- Markdown 파이프라인 추산 응답에 원본 크기 기반 예비 추산과 실제 revision 내용 기반 추산을 구분하는
  `estimateBasis`, `confidence` 및 선택된 임베딩 모델 정보를 additive하게 노출한다. 클라이언트가 추산과
  추천 설정 적용을 분리하고 RAG 색인 모델을 명시적으로 표시할 수 있도록 기존 응답 필드는 유지한다.

- 대용량 문서 청크의 JDBC 저장 크기를 `studio.ai.rag.indexing.chunk-write-batch-size`로 설정할 수
  있게 했다. staging과 영속 ChunkSet을 기본 25개, 최대 200개 단위로 metadata를 분할 직렬화·저장하고
  전체 staging 교체를 transaction으로 묶어 batch SQL 로깅에 의한 heap 사용량과 부분 저장 위험을 줄였다.

- RAG 임베딩이 staging 청크 전체를 한 번에 역직렬화하지 않고 `chunk_index` keyset pagination으로
  제한 조회하도록 개선했다. `pdfExtractionParts`, `pageQuality` 같은 문서 전체 진단 배열은 normalized
  snapshot에만 유지하고 청크별 metadata에서는 제외해 대용량 PDF의 heap 소진과 중복 저장을 방지한다.
  parent-child 전략도 `parentChunkId`와 child provenance는 유지하되 `parentChunkContent`,
  `parentChunkBlockIds`, `parentChunkSourceRefs` 같은 parent 전체 payload는 staging 청크마다 반복하지 않는다.

- 전체 문서 요약 컨텍스트와 Map-Reduce 결과에 문서 제목과 원본 파일명을 보존하고, 요약 답변이 해당
  식별 정보로 시작하도록 개선했다. metadata에 없는 제목이나 파일명은 생성하지 않는다.

- 대용량 RAG 문서의 구간별 요약을 최대 3개 제한 병렬 처리로 변경해 첫 전체 요약의 순차 대기 시간을
  줄였다. RAG 응답 metadata와 10초 이상 소요된 서버 로그에는 검색, 중간 요약, 최종 생성, 전체 시간을
  분리해 기록한다.

- 대용량 문서 전체 요약은 구간별 근거 요약을 먼저 생성하고 내용 지문 기반 제한 캐시를 재사용한다.
  구간 요약 실패 시 기존 전체 문서 컨텍스트로 복귀해 기존 API와 답변 경로를 유지한다.

- 문서 요약·줄거리 요청에 사전 생성 summary metadata가 없을 때 object의 첫 chunk들만 요약하던 문제를
  수정했다. 전체 object chunk를 순서대로 조회하고 `startOffset`/`endOffset` overlap을 제거해 하나의
  전체 문서 context로 재구성한다. 안전 한도를 넘는 문서는 균등 coverage sample로 제한하고 응답 metadata에
  `overviewCoverageStatus=PARTIAL`을 표시해 부분 근거를 전체 줄거리로 오인하지 않도록 했다.
  마지막 user message만으로도 요약 의도를 감지하므로 클라이언트가 동일한 `ragQuery`를 중복 전송할 필요가
  없으며, 서버 prompt가 원문에 없는 장소·진단을 단정하거나 극중 작품을 원본 전체와 혼동하지 않도록 한다.

- RAG chat에 `INTERPRETIVE_ANALYSIS` query intent를 추가했다. MBTI·성격·인물 동기·상징 해석처럼 문서의
  여러 근거를 종합해야 하는 질문은 semantic search를 유지하면서 최소 8개 근거 후보와 최대 `0.55`
  score threshold를 사용한다. 모델에는 사실과 추론을 구분하고 대안 해석과 확신도를 제시하도록 안내하며,
  응답 metadata에 `answerType=EVIDENCE_BASED_INFERENCE`와 실제 검색 조건을 additive로 노출한다.

- RAG chat 자동 검색 전략이 일반 `recursive` chunk 문서까지 structure/idea-block hybrid filter로 보내
  검색 결과를 0건으로 만드는 문제를 수정했다. object sample에 구조화/idea-block 신호가 없으면 기존
  default semantic search를 사용하며, 저장된 embedding profile 정렬 동작은 유지한다.

- 종료된 Gemini 1.5 Pro와 `text-embedding-004` 대신 실제 지원 모델인 `gemini-2.5-pro`와
  `gemini-embedding-2`를 provider/profile로 선택할 수 있도록 Google provider별 명시적
  `model-override`를 추가했다. 기존 `gemini-2.5-flash`, `gemini-embedding-001`, 기본 embedding profile은
  유지한다. 임베딩 요청의 명시적 provider도 실제 provider별 port로 라우팅한다. chat 응답의 실제
  provider/model/token usage를 기준으로 요청 수, token, 지연시간, 설정 기반
  USD 추정 비용을 집계하고 `GET /api/ai/usage/models`에서 조회할 수 있게 했다. provider 정보 API는
  비활성 channel의 상속 모델을 노출하지 않아 provider 이름과 실제 활성 모델이 일관되게 표시된다.

- RAG prompt의 각 근거에 원본 파일명, 문서 제목, 페이지, 섹션, sourceRef provenance를 함께 전달하고,
  `ragReferences`에도 `originalFileName`, `sourceFileName`, `title`, `sourceRef`, `citationLabel`을 additive로
  노출해 답변의 `(근거 N)`을 실제 원문 위치와 연결할 수 있도록 개선했다. object-scoped 검색은 저장된
  chunk의 `embeddingProfileId`와 질의 임베딩 profile을 자동 정렬해, 다른 profile을 전달했을 때 존재하지
  않는 TEI endpoint를 호출하거나 색인과 다른 차원의 embedding을 사용하는 문제를 방지한다.

- RAG chat이 저장된 chunk metadata에서 실제 청킹 전략을 감지해 불필요한 hybrid 검색과 질의 확장을
  생략하도록 개선했다. 규칙 기반 query intent classifier를 추가해 구체 질문은 semantic retrieval로,
  문서 요약·핵심 내용 요청은 `documentSummary`, `keyPoints`, `highlights` 등 사전 추출 metadata를
  우선 사용하고 metadata가 없으면 기존 object chunk 요약으로 fallback한다. 분류 결과와 실제 retrieval
  mode는 응답 metadata에 additive로 노출한다.

- Fixed content embedding auto-configuration to pass the configured `ChunkSetStore` into the attachment structured RAG indexer, allowing prepared Markdown chunks to be indexed instead of failing with `ChunkSet store is not configured`.

- Markdown 품질 게이트는 빈 Markdown 또는 정규화 block 부재만 치명 오류로 차단하고, 한글 자모·수식
  손실 가능성·페이지 커버리지 같은 비치명 품질 이슈는 `ragIndexEligible=true`,
  `qualityGateStatus=REVIEW_REQUIRED`로 보존해 검토 가능한 유효 chunk의 RAG 색인을 계속하도록 보완했다.

- 재추출 요청에서 문서 프로필과 OCR/수식 보정 옵션이 모두 누락되면 최근 명시적 품질 설정을 계승해 대용량 문서의 비용 추산 경로에서 한글·수식 품질이 퇴행하지 않도록 보완했다.

- 수학 PDF 보정 페이지 선정이 `x’`, `xry`, `52x'`처럼 지수·변수 경계가 손상된 OCR 패턴을 높은
  우선순위로 판정하도록 보완했다. Gemini vision 응답의 LaTeX가 JSON에서 `\{`, `\frac` 형태로
  잘못 escape되어도 복구한 뒤 수식 block으로 보존하며, 정규화·Markdown 품질 경고가 청킹 완료 시
  `chunkQualityStatus=VALID`로 덮이지 않도록 원본 issue를 병합한다. 수학 질문 청크의 parent context가
  질문 한 줄뿐이면 같은 페이지의 가까운 조건식까지 제한적으로 확장해 식과 질문의 분리를 복구한다.

- Markdown/NormalizedDocument 청킹 결과를 영속 `ChunkSet`으로 저장하고 Markdown에서 시작된 RAG 색인은
  지정된 `chunkSetId`의 text/order/metadata만 임베딩하도록 분리했다. 임베딩 profile 변경 재색인도 같은
  ChunkSet을 재사용하며, ChunkSet이 없거나 scope·품질 상태가 유효하지 않으면 원문 재추출·재청킹 없이
  실패한다. 일반 attachment RAG의 기존 추출/청킹 fallback과 OCR·정규화·renderer·청킹 알고리즘은
  유지한다. PostgreSQL/MySQL/MariaDB에 `tb_ai_chunk_set`, `tb_ai_chunk_item` 저장소를 추가했다.

- Markdown 생성 요청에 서버 관리형 `documentProfile`을 추가했다. `AUTO`, 일반 문서, 전문 서적,
  교과서, 수학 교과서, 스캔 문서, 프레젠테이션, 기술 매뉴얼 프로필을 제공하며, 프로필 목록 조회
  `GET /api/markdown-documents/profiles`와 유효 옵션 미리보기
  `POST /api/markdown-documents/processing-plan` API를 추가했다. 명시적으로 전달한 nullable OCR/청킹
  옵션은 프로필 기본값보다 우선하고, 프로필이 없는 기존 요청과 DB schema는 그대로 유지한다.
  requested/resolved profile과 정책 버전은 revision options 및 normalized snapshot metadata에 기록한다.

- Markdown provenance API가 normalized block의 `page`, `slide`, `bbox`를 `metadataJson`뿐 아니라
  응답 최상위 필드로도 노출한다. 기존 `locatorNo`와 persisted locator 계약은 유지한다.

- oversized standalone OCR/table block을 validation 전에 내부 recursive split하여 구조 기반 청킹 전체가
  `invalid-structure-chunks`로 fallback되는 문제를 방지한다. overlap tail과 다음 블록의 결합이 최대
  크기를 다시 초과할 때는 overlap만 제거한다.

- 한국어 OCR 교정으로 손상된 baseline BODY/TABLE을 숨길 때 그 안의 `0xx` 문제 식별자만 page/sourceRef를
  유지한 `문제 0xx` marker로 복원한다. OCR 난이도 badge가 `6`, `e`, `중`으로 붙은 번호도 정규화하므로
  본문 노이즈를 다시 노출하지 않고 문제 번호 기반 검색과 튜터 인용을 보존한다.

- Preserve successful Korean OCR page batches when a later PaddleOCR batch fails, and send only missing pages to the configured fallback provider instead of discarding the full primary result.

- 한국어 OCR 페이지 교체가 품질 비교를 통과하면 손상된 기존 본문과 표를 Markdown에서 제외하고,
  균형 잡힌 LaTeX 또는 짧고 명확한 대수식으로 검증된 수식만 보존하도록 병합 정책을 강화했다.
  `discardedOcrNoise`와 `searchContextOnly` 블록은 renderer에서도 독립적으로 제외하며, 구조화된 수학
  전체 페이지 교체 블록은 우선순위를 유지해 정상 문서와 고품질 math provider 결과의 회귀를 방지한다.
  치명 손상 페이지의 한국어 보정 본문에 수식이 섞여 있거나 비교 가능한 baseline 본문이 없는 경우에도
  충분한 한글 복원도가 확인되면 교체를 적용하며, 한국어 OCR 적용 상태를 유효 OCR로 판정한다.

- 스캔 PDF의 PyMuPDF4LLM Markdown fallback을 페이지별 block으로 복원해 전체 문서가 `page[1]`로
  축약되던 provenance 결함을 수정했다. 한국어 OCR 강제 요청에서 OCR 권장 문서가 충분한 텍스트를
  반환했지만 한글은 거의 없고 라틴 문자가 대부분이면 치명적 한글 손상으로 판정해 요청 범위의
  페이지를 PaddleOCR 본문 보정 대상으로 선택한다. 보정 결과는 길이뿐 아니라 한글 복원도 상승과
  라틴 문자 비율 감소를 확인한 뒤 교체하며, 잔존 손상은 `KOREAN_TEXT_GARBLING` 품질 이슈로 RAG
  색인을 차단한다. PaddleOCR 보정은 연속 페이지를 기본 2페이지씩 묶어 전송해 대형 PDF 원문을
  페이지마다 반복 업로드하지 않는다. 치명적인 한글 손상 문서에서 provider 실패 또는 페이지 누락이
  발생하면 손상된 baseline을 `COMPLETED`로 저장하지 않고 추출을 실패 처리하며, 보정 요청·완료·누락
  페이지와 `koreanTextOcrComplete`를 진단 metadata에 기록한다. 원격 PaddleOCR 장애나 불완전 응답 시
  선택적으로 로컬 PyMuPDF4LLM 강제 OCR endpoint로 전환하는 Korean OCR fallback도 추가했다.

- 수학 PDF hybrid 추출에서 본문 baseline의 강제 OCR을 제거하고 PyMuPDF 성공/실패/채택 상태와 fallback
  원인을 metadata로 보존하도록 개선했다. Pix2Text와 vision 결과는 본문 전체를 대체하지 않고 수식 block만
  보강하며, 오류 심각도 기반 페이지 예산·wave·시간 예산을 적용한다. 선택적 `KoreanTextOcrClient`와
  PaddleOCR 호환 self-host HTTP adapter를 추가해 한글 자모 손상 페이지의 본문만 품질 비교 후 교체할 수
  있게 했다. 페이지별 `page/sourceRef/bbox/confidence`를 반환하는 Mac ARM64 native PaddleOCR worker와
  LaunchAgent 설정도 추가했다. 품질 점수에는 한글 자모 비율, 수식 보존율, 페이지 coverage,
  PyMuPDF fallback을 반영하며,
  심각한 결함은 `ragIndexEligible=false`로 RAG 색인을 차단한다.

- Pix2Text worker의 PyTorch CPU wheel을 `torch==2.6.0+cpu`로 올려 `optimum/onnxruntime` 초기화 중
  `torch.int4` 누락으로 math OCR provider가 503을 반환하던 문제를 수정했다. Docker compose 컨테이너
  이름도 `studio-pix2text-worker`로 고정했다.

- Pix2Text math OCR client가 긴 PDF를 전체 단일 요청으로 보내지 않고 `batch-size` 기준 page range 요청으로
  나누어 호출한 뒤 Markdown/block/provenance를 병합하도록 보강했다. 설정 키
  `studio.textract.pdf.engines.math.pix2text.batch-size`를 추가했다. CPU self-host 환경에서 긴 단일
  요청이 timeout/fallback으로 이어지지 않도록 기본 batch size는 1 page로 둔다.

- 수학 PDF의 명시 OCR 재추출에서 전체 문서를 Pix2Text로 대체하지 않고, baseline OCR/PyMuPDF 결과를 전체
  문서 본문으로 유지한 뒤 설정된 샘플 페이지만 math OCR supplement로 붙이는 hybrid route를 추가했다.
  `studio.textract.pdf.engines.math.hybrid.enabled`와
  `studio.textract.pdf.engines.math.hybrid.sample-pages` 설정을 추가해 self-host Pix2Text 부하를 제한하면서
  `mathHybridApplied`, `mathHybridSupplementPageCount`, `mathHybridSupplementParts` metadata로 보강 범위를
  추적할 수 있게 했다. supplement 결과는 품질 검토용 metadata로만 남기고 본문 Markdown/block에는 직접
  병합하지 않아 Pix2Text OCR 노이즈가 최종 Markdown을 오염시키지 않도록 했다. 이후 supplement에서
  LaTeX/math 후보만 선별해 page provenance가 있는 math supplement block으로 추가하고, `OO`, `SS`,
  중국어 오인식 같은 noise line은 제외해 수식 표현만 보강하도록 했다.

- 수학 PDF의 고품질 보정 경로를 위해 `MathVisionCorrectionClient` 포트를 추가하고, 첫 구현체로 Gemini
  vision 보정 client를 제공했다. `studio.textract.pdf.engines.math.vision-correction.*` 설정으로 켜고
  provider/model/timeout/max-file-size/API key를 조정할 수 있으며, 기본값은 부하와 비용을 피하기 위해
  비활성화다. 보정 결과는 최종 Markdown을 직접 대체하지 않고 page provenance가 있는 수식 block과
  `mathVisionCorrectionApplied`, `mathVisionCorrectionProvider`, `mathVisionFormulaBlockCount` metadata로
  additive 반영한다. OCR 후처리는 `00`, `O00`, `xm`, `me` 같은 단독 아이콘성 노이즈 제거와 같은 page/line
  후보의 짧은 한글 파편 병합을 보강했다.

- Markdown 생성/재추출 요청의 `mathVisionCorrection` opt-in 값을 서버 옵션에 저장하고 native PDF 추출
  컨텍스트와 `PdfExtractionOptions`까지 전달하도록 연결했다. 이제 클라이언트가 `mathVisionCorrection=true`를
  보낸 경우에만 Gemini vision 보정을 시도하며, metadata에는 `mathVisionCorrectionRequested`,
  `mathVisionCorrectionApplied`, `mathVisionCorrectionSkipReason`을 남긴다.

- NormalizedDocument snapshot metadata에 `contentBlockCount`, `pageProvenanceCoverage`,
  `searchablePageCoverage`, `mathBlockCount`, `mathPageProvenanceCoverage`를 추가해 수학 PDF가
  `REVIEW_REQUIRED` 상태라도 페이지별 검색 가능한지 판정할 수 있게 했다. page/math provenance가
  부족하면 `PAGE_SEARCHABILITY_REVIEW_REQUIRED`, `MATH_PAGE_PROVENANCE_REVIEW_REQUIRED` 이슈를 남긴다.
  또한 page provenance가 있는 추출 block은 Markdown 본문에는 렌더링하지 않는 `PAGE_SEARCH_CONTEXT`
  normalized block으로 한 번 더 묶어 청킹/RAG 입력에서 같은 페이지의 개념, 문제, 수식이 함께 검색되도록 했다.
  구조 기반 chunking metadata에는 `searchContextOnly`, `aggregationType`, `sourceBlockCount`,
  `sourceBlockIds`를 보존해 RAG 결과에서 page context chunk를 식별할 수 있게 했다. normalized snapshot의
  coverage 지표도 downstream chunking metadata로 전달해 색인 품질을 추적할 수 있게 했다.

- PDF Markdown 추출에서 `ocrMode=FORCE`만 전달된 경우에도 내부 PyMuPDF/OCR 요청의 기존 호환
  `ocrRequired` flag를 함께 활성화하도록 수정했다. 수학 문서 route가 추천됐지만 math OCR provider가
  처리하지 못해 fallback되는 경우에도 `MATH_DOCUMENT` 추천 route, fallback warning, 품질 metadata를
  유지한다.

- PyMuPDF4LLM worker 호출이 worker 내부 OCR 처리 지연으로 무기한 대기하지 않도록 Java HTTP request timeout
  외에 `sendAsync().get(timeout)` 기반 hard timeout을 추가했다. timeout 발생 시 기존 fallback/failure 경로로
  빠져 Markdown revision이 장시간 `RUNNING`에 머무르지 않도록 했다.

- PDFBox OCR fallback은 기본적으로 `studio.textract.pdf.ocr-fallback.max-pages`를 따르되,
  클라이언트가 `ocrMode=FORCE`로 명시 OCR을 요청한 경우에는 fallback page limit으로 잘라내지 않고 전체
  페이지를 렌더링하도록 수정했다. 6번 수학 PDF처럼 명시 OCR 재추출한 문서가 20페이지까지만 저장되는
  문제를 방지한다.

- Markdown locator/provenance API가 `NORMALIZED_DOCUMENT` snapshot의 block provenance(`page`,
  `sourceRef`, `bbox`)를 `NORMALIZED_BLOCK` locator로 함께 반환하도록 보강했다. 별칭 endpoint
  `GET /api/markdown-documents/{id}/provenance`도 추가했다. native 추출 경로에서 normalized snapshot이
  이미 생성된 경우에도 block provenance locator를 저장소에 함께 persist하도록 보강했다.

- 수학 문서 PDF 추출을 위해 optional `MathDocumentOcrClient` 포트를 추가하고,
  `studio.textract.pdf.engines.math.provider=pix2text|mathpix` 설정으로 Pix2Text self-host PoC 또는
  Mathpix backend를 선택할 수 있게 했다. 전용 수식 OCR 엔진 실패 시 기존 heuristic math markdown 경로로
  fallback한다.

- Markdown Pandoc 변환 대상 format을 `studio.markdown.pandoc-formats` allowlist로 설정할 수 있게 하고,
  Pandoc submit/conversion 실패 시 `studio.markdown.fallback-to-native-on-pandoc-failure` 설정에 따라
  native 추출로 fallback하도록 보강했다.

- Markdown source 크기 제한인 `studio.markdown.max-source-bytes`가 기존 숫자 byte 값과 함께 `64M`,
  `64MB` 같은 data size 표현을 지원하도록 변경했다.

- Markdown 생성 pipeline에서 `NormalizedDocument` snapshot을 내부 표준 산출물로 저장하고,
  `NormalizedDocument -> Markdown` 렌더링 결과를 `MarkdownRevision.markdownText()`에 유지하도록
  보강했다. 청킹은 저장된 normalized blocks를 우선 사용하고 없으면 기존 Markdown/locator 기반 입력으로
  fallback한다.

- Native Markdown 추출 중 서버 재시작 등으로 `RUNNING` revision에 완료된 extract part만 남은 경우,
  `resume` 또는 native task 재시작 시 저장된 part를 page 순서로 재조립해 revision을 완료하도록 복구 경로를
  추가했다.

- Markdown 결과 확인을 위해 현재 revision과 특정 revision의 Markdown 본문을 로컬 파일 캐시에 저장한 뒤
  `text/markdown`으로 스트리밍하는 보기/다운로드 API를 추가하고, 클라이언트 반영 지시문
  `docs/plans/client-markdown-result-view-guide.md`를 추가했다. 캐시 위치는
  `studio.markdown.result-cache-dir`로 조정하며 기본값은 `var/lib/app/markdown`이다.

- 클라이언트가 Markdown 생성 흐름의 정규화 상태와 normalized chunk 입력 여부를 표시할 수 있도록
  `docs/plans/client-normalized-markdown-pipeline-guide.md` 지시문을 추가했다.

- Markdown 생성/재추출/재개 API에 `ocrRequired` 옵션을 추가하고, PDF native 추출 경로에서 요청값을
  `PdfExtractionOptions`로 전달해 OCR 적용 여부를 클라이언트가 선택할 수 있도록 했다.

- Markdown 생성/재추출/재개 API에 `ocrMode=AUTO|FORCE|DISABLED` 옵션을 추가했다. 수학 PDF의 Pix2Text/Mathpix
  math OCR provider는 `FORCE` 또는 기존 호환 `ocrRequired=true` 요청이 있을 때만 시도하며, 서버는
  `ocrRequestedBy`, `ocrDecisionReason`, `ocrMode` metadata를 기록한다.
  클라이언트 반영 지시문은 `docs/plans/client-ocr-mode-markdown-guide.md`에 추가했다.

- PyMuPDF4LLM worker가 `ocrRequired=true` 요청에서 실제 PyMuPDF OCR textpage를 사용해 Markdown과 block을
  생성하고, block bbox와 OCR 적용 metadata를 응답하도록 보강했다.

- PDF Markdown 추출 전에 문서 분석 결과(`GENERAL`, `SCANNED`, `MATH_LIKE`, `MIXED`)를 산출하고,
  추천 route와 실제 route를 metadata에 기록하도록 보강했다. v1에서는 `MathDocumentExtractionEngine`
  확장 계약을 먼저 추가하고, Math route가 추천되더라도 엔진이 비활성인 경우 기존 PyMuPDF4LLM/OCR/PDFBox
  경로로 처리하며 normalized snapshot에는 품질 검토 issue를 남긴다.

- Math route가 추천된 PDF는 기본 `HeuristicMathDocumentExtractionEngine`을 통해 PyMuPDF4LLM/OCR 결과의
  수식 후보를 Markdown math(`$...$`)로 후처리하고, `mathMarkdownApplied`,
  `mathMarkdownExpressionCount`, `mathMarkdownEngine` metadata를 normalized snapshot에 기록하도록 했다.

- PDF Markdown 추출에서 `engine=PYMUPDF4LLM`와 `ocrRequired=true`가 명시된 경우에도 수학 문서 분석 결과가
  우선 반영되어 `MathDocumentExtractionEngine` 후처리 경로를 타도록 수정했다. 또한 생성/재추출/재개 요청의
  `ocrLanguage`를 `PdfExtractionOptions`, PyMuPDF4LLM worker, PDFBox OCR fallback, normalized snapshot
  metadata까지 전달하도록 보강했다.

- OCR 기반 수학 PDF 결과는 짧은 라틴 잡음 제거, OCR block의 heading/list/paragraph 재분류, 휴리스틱 수식
  후처리 품질 metadata(`mathMarkdownQuality`, `mathDocumentEngineRequired`) 기록을 수행하도록 보강했다.
  또한 OCR 잡음, 수식 OCR 의심, 한글 공백 손실, 휴리스틱-only 수식 변환을 품질 issue로 감지해
  `REVIEW_REQUIRED`로 남기도록 했다.

- chunking metadata에 요청/실제 전략, fallback 상태, 품질 상태 key를 추가하고, structure-based full-strategy
  fallback을 `recursive -> fixed-size` 순서로 기록하도록 보강했다. 기본 계약은 `recursive + character`로 유지한다.

- 공통 chunking metadata 정책을 클라이언트가 표시할 수 있도록 요청/실제 전략, fallback, 품질 상태 표시 지침을
  `docs/plans/client-chunking-metadata-policy-update-guide.md`에 추가했다.

- 클라이언트가 청킹 품질 요약, 필터, provenance badge, strategy 분포, 재처리 추천을 구현할 수 있도록
  `docs/plans/client-chunking-quality-ux-improvement-guide.md`를 추가했다.

- chunking starter에 opt-in `knowledge-block` 전략을 추가했다. 기존 Blockify/IdeaBlock 생성 파이프라인을
  재사용하되 `knowledge-block-metadata-v1`, `knowledgeBlockFingerprint`, coverage/distillation alias를
  저장해 운영 RAG 정책과 PoC `blockify` 비교 결과를 분리할 수 있게 했다.

- 이슈 #514 대응으로 chunking starter에 opt-in `blockify` 전략 PoC를 추가했다. 기본값은 비활성이며,
  `studio.chunking.blockify.enabled=true`일 때 Markdown pipeline의 `chunkingStrategy=blockify`가
  질문·답변 중심 chunk와 `blockify-metadata-v1` metadata를 생성하고, 표/검증 실패 섹션은
  `structure-based` fallback metadata로 보존한다.

- AI vector projection 작업이 제한 시간 동안 점을 쓰지 못한 채 `REQUESTED`/`PROCESSING`에 머무르면
`FAILED`로 회수하고, OOM 실패를 `PROJECTION_JOB_OUT_OF_MEMORY`로 기록하도록 변경했다.

- AI vector projection PCA 작업은 primitive embedding 경로와 누적 PCA 계산을 사용하고, projection 생성 조회에서 본문/전체 metadata를 제외하며, 단계별 heap 로그와 chunk 구간 균등 sampling을 적용하도록 개선했다.

- AI vector projection PCA 좌표의 `vectorItemId`가 chunk metadata의 `chunkId`를 우선 사용하고, 값이 없으면 `row-{id}`로 폴백하도록 수정해 `/points` 조회 조인이 누락되지 않게 했다.

- AI vector projection `/points`의 필터 없는 total count가 projection point 테이블만 조회하도록 최적화하고, PostgreSQL chunk 조인 계산식에 expression index를 추가해 대용량 조회 지연을 줄였다.

- 잘못 생성되거나 더 이상 필요하지 않은 완료/실패 projection과 좌표를 제거하는 관리용 `DELETE /api/mgmt/ai/vectors/projections/{projectionId}` API를 추가했다. 실행 중인 projection 삭제는 거부한다.

- AI vector projection 기본 sampling 전략을 `studio.ai.vector.projection.default-sampling-strategy`로 설정할 수 있도록 추가했다.

- AI vector projection 작업 상태 변경을 STOMP topic(`/topic/ai/vectors/projections/{projectionId}`)으로 발행하도록 추가했다.

- Markdown 지식 파이프라인 요청에서 Skill 후보 추출 모드(`regex`, `llm`)를 선택할 수 있으며,
  생략 시 `studio.skillgraph.extraction.mode` 서버 기본값을 사용하도록 개선

- Markdown pipeline 요청에서 RAG LLM keyword extraction과 Skill 후보 embedding 설정을
  저장·재개하고 하위 RAG/SkillGraph 작업으로 전달하도록 확장했다.

### 변경됨
- Vector Projection API에 `OVERVIEW`/`DETAIL` mode, 사전 건수 estimate, DB 단계 `HEAD`/`RANDOM`/`STRATIFIED` sampling을 추가했다. 1,000건을 초과하는 전체 범위는 Overview에서 자동 샘플링하고, Detail은 명시적 scope와 sample size를 검증하며 실행 이력에 대상·샘플·구조화 오류 정보를 저장한다.
- 기존 Markdown 본문을 재추출하지 않고 chunking과 RAG를 새 embedding profile/provider/model로 다시 실행하는 `POST /api/markdown-documents/{id}/rag/reindex` API를 추가했다. 재색인은 새 Markdown Revision으로 기록하며 기존 Revision과 locator/resource 이력을 보존한다.
- RAG 색인의 embedding 요청 배치와 vector upsert 배치를 `studio.ai.rag.indexing.embedding-batch-size`, `upsert-batch-size`로 분리해 각각 독립적으로 조정할 수 있도록 변경했다.
- Attachment Markdown native 추출과 후속 Chunking/RAG/Skill 처리를 commit 이후 background executor에서 실행하도록 변경했다. 생성 요청은 `RUNNING` Revision을 먼저 반환하며, 첨부파일 상세 polling을 위해 `GET /api/markdown-documents/by-attachment/{attachmentId}`를 추가했다.
- Markdown pipeline의 현재 단계, 마지막 성공 단계, 오류와 시도 횟수를 `tb_ai_markdown_pipeline_execution`에 저장한다. `GET /api/markdown-documents/{id}/pipeline`으로 상태를 조회하고 `POST /api/markdown-documents/{id}/resume`으로 중단되거나 실패한 추출 또는 Chunking/RAG/Skill 단계를 자동/수동 재개할 수 있다.
- RAG object metadata endpoint가 기존 vector metadata와 함께 구조화된 `embedding` 정보를 반환하도록 확장하고, metadata contributor SPI를 통해 `attachment` 객체의 Markdown 생성 및 pipeline 상태를 `markdown` 하위 객체로 제공한다.
- Markdown pipeline 실행 이력이 없는 경우에도 `/pipeline`이 non-null 상태를 반환하도록 보강했다. 신규 Revision은 downstream 단계가 없어도 최종 상태를 영속화하며, 과거 이력이 없는 완료 Revision은 `UNKNOWN/PIPELINE_HISTORY_UNAVAILABLE`로 표시한다. 변환 이력이 없는 `by-attachment` 조회는 `404 Not Found`로 처리하고 성공 `ApiResponse`와 실패 RFC 7807 `ProblemDetails` 응답 규약을 문서화했다.
- Markdown 기반 RAG 색인이 `RagPipelineService`를 직접 호출하지 않고 `RagIndexJobService`를 통해 Job을 생성·실행하도록 변경했다. 기존 RAG Job 목록과 로그에 `sourceType=markdown-revision`, Attachment object scope, Markdown document/revision metadata 및 embedding 선택이 기록된다.
- EPUB 첨부파일의 Markdown/RAG embedding 요청이 `Unsupported file type`으로 실패하지 않도록 Textract에 EPUB2/EPUB3 native parser를 추가했다. OPF spine 순서의 XHTML/HTML 제목, 문단, 목록을 Markdown으로 정규화하며 ZIP 추출 한도, package path 검증과 XXE 차단을 적용한다.
- `studio-platform-thumbnail`에 EPUB2/EPUB3 cover thumbnail renderer를 추가했다. `application/epub+zip`과 `.epub` 입력에서 OPF cover metadata 또는 큰 raster resource를 선택하고, cover가 없으면 기본 EPUB 아이콘을 생성한다. ZIP 추출 한도, package path 검증, XXE 차단을 적용하며 SVG cover는 Batik runtime이 있을 때만 선택적으로 지원한다.
- Attachment Markdown 파이프라인 요청에 Chunking 전략/크기/overlap/unit과 RAG embedding profile/provider/model/dimension 선택을 추가하고, RAG 및 Skill 추출 선택 시 필요한 선행 단계를 자동 활성화하도록 변경했다.
- Markdown starter가 JDBC, Attachment, Textract, Document Convert 자동설정 이후에 구성되도록 순서를 보장하고, `MarkdownDocumentService`가 생성된 경우에만 API controller를 등록하도록 수정했다.
- Attachment 기반 Markdown Document/Revision 저장소와 추출 API를 추가했다.
- DOCX/HTML Pandoc 변환 완료를 event port로 Markdown orchestration에 연결했다.
- Markdown Revision 식별자와 source metadata를 RAG chunk 및 Skill Candidate에 전달한다.
- Textract structured extraction 결과에 호환 가능한 Markdown 정규화와 page/slide/section locator를 추가했다.
- Chunking adapter가 정규화 Markdown을 우선 사용하고 `contentFormat`과 locator metadata를 전달하도록 변경했다.
- DOCX/HTML의 Pandoc 변환은 Attachment/ObjectStorage 기반 document-convert 비동기 Job으로 제한하고, 동기 `File`/`InputStream` 추출은 native parser를 유지하도록 문서화했다.
- Attachment signed URL 기반 비동기 문서 변환 기능을 추가했다. `studio-platform-document-convert`와 starter는 Job 생성/조회/재시도/취소, JDBC 상태 저장, worker callback과 결과 upload endpoint를 제공하고, 독립 Python FastAPI `studio-pandoc-worker`가 컨테이너 임시 디렉터리에서 Pandoc을 실행한다. 원본과 결과는 기존 Attachment 저장 방식(database, local, objectstorage)을 그대로 사용하며 local path는 shared Docker volume 개발 환경에서만 opt-in으로 허용한다.
- 이슈 #507 대응으로 SkillGraph projection/cluster 실행 이력 기반을 보강했다. `SkillType` single label 정규화, type별 projection 입력 분리, cluster metadata/member 저장, representative skill 선정, cluster member 조회 API, PostgreSQL/MySQL/MariaDB migration을 추가했다.
- 이슈 #505 대응으로 SkillGraph 스킬 후보 자동 분석 및 추천 결과 일괄 승인 기능을 추가했다. 동일 embedding provider/model/dimension의 `tb_skill_embedding` 벡터만 비교해 신규 스킬 후보와 기존 스킬 매칭 추천 결과를 저장하고, 일괄 승인 시 기존 후보 approve/review 로직을 재사용해 신규 사전 등록 또는 기존 스킬 연결만 적용한다.
- 이슈 #505 대응으로 SkillGraph 스킬 후보 추출 결과를 구조화했다. LLM prompt와 parser가 `searchText`, `skillType`, `action`, `technology`, `target`, `evidenceText`, `context`, `difficulty`를 처리하고, `tb_skill_candidate` 확장 컬럼 및 공통 `tb_skill_embedding` 테이블 migration을 추가했으며 dictionary embedding 유사도 매칭은 `searchText`를 우선 사용한다.
- 로컬 KURE embedding 운영을 위해 `starter-ai`에 Hugging Face Text Embeddings Inference(TEI) embedding provider를 추가했다. `tools/kure-embedding-server/compose.yaml`로 `nlpai-lab/KURE-v1` 서버를 실행하고, `studio.ai.providers.<id>.type=TEI`와 `base-url` 설정으로 Gemini 대신 KURE embedding을 사용할 수 있다.
- NCS/Skill reference dataset의 concept embedding 저장 테이블과 관리용 vectorize/vector-search API를 추가했다. 임포트된 `tb_skill_dataset_concept` 데이터를 `SkillEmbeddingPort`로 벡터화해 `tb_skill_dataset_concept_embedding`에 분리 저장하고, 동일 embedding provider/model/text type 기준의 pgvector cosine similarity 검색을 제공한다.
- NCS reference embedding 생성 시 요청의 `embeddingProvider`/`embeddingModel`을 실제 AI provider 선택에 사용하도록 수정하고, 대량 concept 조회가 `dataset_id`, `provider`, `concept_type`, `concept_id` 순서로 빠르게 스캔되도록 PostgreSQL 복합 인덱스를 추가했다.
- NCS reference embedding 생성에서 `batchSize` 단위로 TEI/KURE embedding 요청을 묶어 보내도록 수정했다. 빈 텍스트와 이미 생성된 embedding은 배치 요청 전에 제외되며, 기존 단건 embedding 포트 구현은 기본 fallback으로 유지된다.
- 기본 Gradle `test` 실행에서 Testcontainers 기반 PostgreSQL/pgvector 통합 테스트를 제외해 로컬 빌드가 외부 DB 컨테이너 준비로 지연되지 않도록 했다. DB 통합 테스트가 필요한 경우 `-PrunDbTests=true`를 명시해 실행한다.
- 이슈 #414 확인 중 발견된 `studio-platform-objecttype` PostgreSQL seed migration 오류를 수정했다. `V201__seed_well_known_attachment_objecttypes.sql`의 `policy_json` seed 값을 `NULL::jsonb`로 명시해 dev 서버 Flyway 적용 시 `jsonb` 컬럼에 `text`가 insert되어 8080 인스턴스 기동이 실패하지 않도록 했다. reserved objectType/code 충돌은 조용히 건너뛰지 않고 실패하도록 보강했으며, 기존 Flyway history 복구가 필요한 환경은 운영 커스텀 정책을 덮어쓰지 않는 `docs/dev/objecttype-v201-postgres-repair.md` 절차를 따른다.
- 이슈 #453 대응으로 `studio-platform-user`, `studio-platform-user-default`, `studio-platform-security`, `studio-platform-security-acl`, `studio-platform-storage`, `studio-platform-textract` 패키지를 `domain/application/infrastructure/web` 구조로 재배치했다. 이 변경은 의도적인 breaking rename이며 기존 user/security/storage/textract의 `service`, `persistence`, `exception`, `model`, `extractor`, `web.dto` 계열 package wrapper를 제공하지 않는다. 관련 starter와 downstream import는 새 `application.usecase`, `application.command`, `application.result`, `domain.model`, `domain.port`, `domain.support`, `infrastructure.*`, `web.dto.request`, `web.dto.response` 기준으로 갱신했으며 REST endpoint, JSON DTO shape, DB schema, migration version은 변경하지 않았다.
- 이슈 #451 대응으로 `studio-application-modules:attachment-service`, `avatar-service`, `mail-service`, `template-service` 패키지를 `domain/application/infrastructure/web` 구조로 재배치했다. 이 변경은 의도적인 breaking rename이며 기존 attachment/avatar/mail/template의 `service`, `persistence`, `storage`, `thumbnail`, `config`, `replica`, `web.dto` 계열 package wrapper를 제공하지 않는다. `content-embedding-pipeline`과 관련 starter import는 새 `application.usecase`/`application.service`/`infrastructure.*`/`web.dto.*` 기준으로 갱신했으며 REST endpoint, JSON DTO shape, DB schema, migration version은 변경하지 않았다.
- 이슈 #449 대응으로 `studio-platform-workspace`, `studio-platform-workspace-default`, `studio-application-modules:wiki-service` 패키지를 `domain/application/infrastructure/web` 구조로 재배치했다. 이 변경은 의도적인 breaking rename이며 기존 workspace/wiki `model`, `permission`, `service`, `exception`, `persistence.jpa`, `web.dto` 패키지 wrapper를 제공하지 않는다. `WorkspacePermissionContributor`는 application usecase extension contract로 분리하고, Spring `HttpStatus` 기반 error type은 `application.error`에 둔다. REST endpoint, JSON DTO shape, DB schema, migration version은 변경하지 않았다.
- 이슈 #447 대응으로 `studio-platform-objecttype`에 `attachment`, `post-attachment`, `mail-attachment`, `workspace-attachment`, `wiki-attachment` well-known attachment objectType catalog와 50MB 기본 정책을 추가했다. `ObjectTypeRuntimeService`는 key 기반 definition/검증/타입 해석 API를 제공하며, attachment service는 `AttachmentObjectTypeResolver`와 key 기반 create/list helper를 통해 도메인 모듈이 숫자 `objectType`을 직접 알지 않아도 되도록 했다. 도메인 전용 첨부 타입은 `AttachmentOwnerAccessAuthorizer`가 원본 도메인 권한을 승인하지 않으면 공통 attachment REST API에서 fail-closed로 거부한다. ObjectType runtime/management endpoint에는 `features:objecttype/read`/`manage` 권한과 `endpointAuthz` 등록 조건을 적용하고, 관리 API audit actor는 요청 body 대신 현재 principal에서 산출하며 기존 생성자/생성시각을 보존한다.
- 이슈 #442 대응으로 `POST /api/ai/chat/rag`의 LLM 전달 context를 예산 기반으로 packing하도록 보강했다. 큰 chunk는 deterministic excerpt preview로 압축하고, 외부 provider prompt에는 요청 단위 인덱스와 packed preview만 전달하며, `ragReferences`는 citation용 allowlist field만 기본 반환한다. packed content와 diagnostics는 opt-in debug 조건에서만 노출한다.
- 이슈 #444 대응으로 `studio-platform-objecttype` 구현 패키지를 `domain/application/infrastructure/web` 구조로 재정리했다. 이 변경은 의도적인 breaking rename이며 기존 `studio.one.platform.objecttype.service`, `db`, `cache`, `yaml`, `web.dto` 패키지 wrapper를 제공하지 않는다. 직접 import하는 소비자는 `application.usecase`, `application.command`, `application.result`, `domain.port`, `infrastructure.persistence`, `infrastructure.cache`, `infrastructure.yaml`, `web.dto.request`, `web.dto.response` 기준으로 import를 갱신해야 한다. REST endpoint, JSON DTO shape, DB schema, MyBatis SQL 동작은 변경하지 않았다.
- 이슈 #441 대응으로 custom SqlQuery/sqlset mapper 런타임과 starter 자동구성을 제거하고 SQL mapper 표준을 MyBatis convention으로 전환했다. `JdbcAutoConfiguration`은 `JdbcTemplate`/`NamedParameterJdbcTemplate`만 등록하며, mapper XML은 `starter:studio-platform-starter-mybatis`의 `classpath*:mybatis/**/*.xml` 경로를 사용한다.
- 이슈 #438 대응으로 관리용 회원 목록 API `GET /api/mgmt/users`에 `companyId` 필터를 추가했다. `companyId`와 `q`를 함께 사용하면 Company 멤버 범위 안에서 `username`/`name`/`email` 검색을 수행하며, platform admin이 아닌 호출자는 대상 Company의 `company.member.read` 권한이 필요하고 pagination/sort는 기존 사용자 목록과 동일하게 적용된다. JDBC pagination 정렬은 allowlist에 없는 sort 필드를 SQL에 반영하지 않고 기본 정렬로 대체하도록 보강했다.
- 이슈 #436 대응으로 Company별 권한 정책 관리 API `GET/PUT /api/mgmt/companies/{companyId}/permissions/policy`를 추가했다. 정책은 role별 action override를 저장하고, 정책이 없거나 `override=false`이면 기존 `CompanyPermissionActions.actionsFor(role)` 기본 mapping으로 fallback한다. 저장된 정책은 `permissions/me`와 service-level 권한 판정에 반영되며, 정책 수정은 Company `OWNER` 또는 platform admin으로 제한하고 `V304__create_company_permission_policy.sql` migration을 제공한다.
- 이슈 #435 대응으로 Company 멤버 키 발급과 인증 사용자 가입 요청/승인/거절 API를 추가했다. 멤버 키는 생성 응답에서만 평문으로 반환하고 저장소에는 SHA-256 hash만 보존하며, email-only 비로그인 요청은 계정 소유권을 검증할 수 없어 노출하지 않는다. `POST /api/self/company-join-requests`, `POST /api/mgmt/companies/{companyId}/member-keys`, `GET/approve/reject /api/mgmt/companies/{companyId}/member-join-requests` 흐름을 제공한다. Company 목록 조회는 `features:company/admin` 권한으로 제한하고, 마지막 `OWNER` 강등/삭제는 거부한다.
- 이슈 #433 대응으로 Company 목록/단건 조회가 DTO 변환 시 lazy `properties` 접근으로 500이 발생하지 않도록 `ApplicationCompanyService`에서 properties를 transaction 안에서 초기화한다.
- 이슈 #425 대응으로 Workspace 활성화 API `POST /api/workspaces/{workspaceId}/activate`, `POST /api/mgmt/workspaces/{workspaceId}/activate`를 추가하고, archive/activate 요청 body에 `cascade` 정책을 문서화했다. 활성 descendant가 있는 workspace archive는 `cascade=true`가 필요하며, archived ancestor 아래 child 단독 활성화는 거부한다.
- Company → Workspace → Wiki 단계별 작업 리뷰 보완으로 Company 관리 API에 tenant 객체 권한 검사를 추가하고, workspace company scope 적용 시 `ApplicationCompanyService` 기반 company 존재 검증과 `V1303__add_workspace_company_fk.sql`을 추가했다. V1302 schema가 적용된 환경은 `studio.features.workspace.company-scope-enforced=true` 설정 없이는 기동하지 않도록 보강했다. Company 객체 권한 검사가 필요한 endpoint는 `IdentityService`로 actor를 해석하지 못하면 fail-closed로 거부하며, workspace member keyword 검색은 `ApplicationUserService`를 우선 사용하고 없으면 기존 user table DB fallback을 유지한다.
- 이슈 #430 대응으로 Workspace Company scope 운영 강제 전환용 `V1302__enforce_workspace_company_scope.sql`을 postgres/mysql/mariadb에 추가했다. `COMPANY_ID NOT NULL`, company-scoped path/root slug/parent slug unique 제약과 MySQL/MariaDB `PARENT_KEY` generated column 전략, `studio.features.workspace.company-scope-enforced` runtime 전환 설정, backfill/duplicate 검증 운영 문서를 제공한다.
- 이슈 #428 대응으로 Workspace permission 판정에 opt-in Company `OWNER` override를 연결했다. `studio.workspace.permission.company-owner-override-enabled=true`일 때 Workspace direct/ancestor role을 우선 적용하고, 부족할 때만 Company `OWNER`가 Workspace `OWNER`급 권한을 받으며, Company `ADMIN`은 private workspace/wiki content read 권한을 자동으로 얻지 않는다.
- 이슈 #426 대응으로 Workspace root 생성과 조회에 nullable Company scope를 추가했다. `WorkspaceRef.companyId`, `CreateRootWorkspaceCommand`, company-aware `getByPath`, `studio.features.workspace.company-required` 설정과 `V1301__add_workspace_company_scope.sql` migration을 제공하며 child workspace는 parent companyId를 상속한다.
- 이슈 #423 대응으로 Company member/permission 기반을 추가했다. 기존 `ApplicationCompany*` 구조를 유지하면서 `CompanyRole`, `ApplicationCompanyMemberService`, `ApplicationCompanyPermissionService`, Company archive 상태와 `TB_APPLICATION_COMPANY_MEMBERS` migration을 제공한다.
- 이슈 #421 대응으로 Workspace direct/effective member 목록 조회 API를 서버 페이징 응답으로 전환했다. `q`/`keyword`, `role`, `inherited`, pagination, sort를 지원하고 `keyword`는 사용자 `username`/`name`/`email` 및 숫자 `userId` 검색에 사용한다.
- 이슈 #417 대응으로 Workspace parent 변경 API `PATCH /api/workspaces/{workspaceId}/parent`, `PATCH /api/mgmt/workspaces/{workspaceId}/parent`를 추가했다. `newParentId=null`은 root 이동으로 처리하고, subtree의 `rootId`/`path`/`depth`와 closure table을 재계산하며 자기 자신 또는 descendant 아래 이동은 거부한다.
- 이슈 #415 대응으로 `studio-application-modules:wiki-service`와 `starter:studio-application-starter-wiki`를 추가해 workspace 단위 Wiki page/revision MVP, 사용자용/관리용 API, markdown render/sanitize, 기존 page write의 `baseRevisionId` 충돌 검증, JPA schema `V1400`을 제공한다.
- 이슈 #412 대응으로 Workspace 관리 화면 진입용 `GET /api/mgmt/workspaces` 목록 조회 API를 추가했다. `q`, `parentId`, `rootOnly`, `archived`, pagination, sort를 지원하고 기존 관리용 권한 정책을 적용한다.
- 이슈 #410 대응으로 `studio-platform-workspace`, `studio-platform-workspace-default`, `starter:studio-platform-starter-workspace`를 추가해 workspace tree 생성/조회, member role, effective permission, 사용자용/관리용 API와 JPA 자동구성을 제공한다.
- `workspace` Flyway range `1300-1399`와 `V1300__create_workspace_tables.sql`을 postgres/mysql/mariadb에 추가하고, 사용자용 `public-base-path`와 관리용 `mgmt-base-path`를 분리했다.
- 이슈 #408 대응으로 `GET /api/mgmt/audit/attachment-download-url-issues` 응답에 실제 signed-download 접근 이력 수인 `downloadCount`를 추가했다. 집계는 현재 페이지의 발급 로그를 `issueLogId`/`tokenHash` 기준으로 bulk 조회하며, 접근 이력이 없으면 `0`을 반환한다.
- 이슈 #406 대응으로 `GET /api/attachments/signed-download` 실제 접근 이력을 `TB_APPLICATION_ATTACHMENT_DOWNLOAD_LOG`에 저장하고, `GET /api/mgmt/audit/attachment-downloads` 조회 API를 추가했다. 응답은 `tokenHash`만 노출하고 raw token/signed URL은 포함하지 않으며, `features:attachment_download_audit/read` 권한과 pagination/filter/default `requestedAt desc` 정렬을 적용한다.
- 이슈 #404 대응으로 attachment download-url 기능을 object storage presigned URL에서 application-level HMAC-SHA256 signed link로 전환하고, `GET /api/attachments/signed-download` token-only 스트리밍 endpoint와 `linkType`/`tokenHash` 감사 로그 필드를 추가했다.
- 이슈 #402 대응으로 object storage signed download URL 발급 감사 로그를 조회하는 `GET /api/mgmt/audit/attachment-download-url-issues` API를 추가했다. 조회 응답은 `objectKeyHash`만 노출하고 signed URL/raw object key는 포함하지 않으며, `features:attachment_download_url_issue_audit/read` 권한과 pagination/filter/default `issuedAt desc` 정렬을 적용한다.
- 이슈 #398 대응으로 `studio-platform-textract`에 Apache POI 기반 Excel 구조화 추출기를 추가해 `.xlsx`/`.xls`의 visible sheet used range를 `ParsedFile`, `ParsedBlock`, `ExtractedTable`로 반환하도록 했다.
- 이슈 #395 대응으로 `DELETE /api/mgmt/ai/rag/objects/{objectType}/{objectId}`를 추가해 object scope의 RAG chunk/vector/metadata와 종료된 색인 이력을 삭제할 수 있게 했다. 진행 중인 `PENDING`/`RUNNING` job이 있으면 `409 Conflict`로 거부하고, 삭제 후 metadata 응답은 `indexed=false`를 반환한다.
- 이슈 #392 대응으로 `studio-platform-textract` PDF 추출을 `PdfExtractionEngine` 전략 구조로 분리하고, 기존 PDFBox 구현은 기본/fallback 엔진으로 유지했다.
- PyMuPDF4LLM 기반 Python worker 선택 연동을 위해 Java client/mapper/engine 구조와 `studio.textract.pdf.*` 설정 metadata를 추가했다.
- `tools/pymupdf4llm-worker`에 FastAPI worker PoC, Dockerfile, PyMuPDF4LLM 설치 및 환경 구성 문서를 추가했다.
- `studio-platform-textract` README에 PDF extraction engine 선택, fallback 정책, RAG chunking 연결, worker 운영 주의 사항을 문서화했다.

## 2026-04-28

### 변경됨
- 이슈 #380 대응으로 기존 `tb_ai_document_chunk`를 원본으로 사용하는 vector projection visualization API를 추가했다.
- `tb_ai_vector_projection`, `tb_ai_vector_projection_point` migration을 추가하고, 비동기 PCA job으로 2D 좌표를 미리 계산해 저장하도록 했다.
- `/api/mgmt/ai/vectors/projections`, `/api/mgmt/ai/vectors/items/{vectorItemId}`, `/api/mgmt/ai/vectors/search-visualization` 관리자 API와 클라이언트 산점도용 응답 DTO를 추가했다.
- 이슈 #371 대응으로 `studio-platform-thumbnail`에 PPTX/DOCX/HWP/HWPX 문서 썸네일 renderer를 추가했다.
- PPTX는 Apache POI slide renderer로 실제 slide thumbnail을 생성하고, DOCX/HWP/HWPX는 `FileContentExtractionService`의 구조화 추출 결과로 preview thumbnail을 생성한다.
- `studio.thumbnail.renderers.<format>.*` configuration metadata 및 README 예시를 추가했다.
- 문서 썸네일 renderer는 PPTX package pre-scan, HWP/HWPX aggregate extraction budget, deterministic failure memoization으로 반복 파싱과 압축 확장 위험을 줄였다.
- attachment 썸네일은 저장된 썸네일이 없을 때 pending placeholder 이미지를 즉시 반환하고, 실제 생성은 백그라운드 executor에서 수행하도록 변경했다. 변환 실패는 bounded TTL 캐시에 memoize하고 동일 source의 동시 요청은 하나의 background job으로 합친다.
- 이슈 #368 대응으로 독립 `studio-platform-thumbnail` SPI와 `studio-platform-thumbnail-starter`를 추가해 image/PDF 썸네일 생성을 attachment 도메인 밖으로 분리했다.
- attachment 썸네일 endpoint와 저장소 구조는 유지하되, 기존 `ThumbnailServiceImpl`은 `ThumbnailGenerationService`에 위임하도록 변경했다.
- 썸네일 생성 기본값은 `studio.thumbnail.*`로 이동하고, `studio.attachment.thumbnail.default-size/default-format` 및 기존 `studio.features.attachment.thumbnail.default-size/default-format`는 fallback과 deprecation warning을 유지한다.
- 이슈 #366 대응으로 vector/RAG/RAG Chat 검색 파라미터 계약을 정리해 `topK`와 `minScore`를 동일 의미로 적용하고, 요청값이 없으면 `studio.ai.rag.retrieval.*` 설정값을 사용하도록 했다.
- 이슈 #364 대응으로 `/api/ai/chat/rag`의 `objectType`/`objectId`를 attachment 전용이 아닌 일반 RAG object scope로 허용해 management RAG search/chunk 조회와 같은 scope 계약을 사용하도록 했다.
- 이슈 #362 대응으로 RAG job source 표시명 resolver SPI를 추가하고, generic attachment source job 생성 시 `sourceName`이 attachment id 대신 파일명으로 보강되도록 했다.
- 이슈 #360 대응으로 RAG job 응답 DTO에 `sourceName`을 추가하고, in-memory/JDBC job 저장소와 attachment RAG job 생성 경로에서 표시명을 유지하도록 했다.
- 이슈 #357 대응으로 Vector/RAG query 검색 중 embedding provider quota/rate limit이 발생하면 chat 오류와 구분되는 HTTP 429 메시지를 반환하도록 했다.
- `starter-ai-web` README에 query 기반 Vector/RAG 검색이 embedding provider를 호출한다는 점과 provider-free chunk inspection 대체 경로를 명시했다.
- 이슈 #355 대응으로 `studio-platform-textract-starter`의 기능/런타임 설정을 `studio.features.textract.*`와 `studio.textract.*`로 정리하고, 텍스트 추출 크기 제한을 `studio.textract.max-extract-size`에서 `10M`, `50MB` 같은 단위로 설정할 수 있도록 했다.
- 기존 `studio.features.text.*`와 `studio.text.*`는 fallback과 deprecation warning, configuration metadata를 유지한다.
- 텍스트 추출 크기 초과 예외 메시지에 감지된 크기, 제한값, 조치 설정 키를 포함하도록 보강했다.
- 이슈 #354 대응으로 namespace migration 문서를 `spring.*`, `studio.features.<module>.*`, `studio.<module>.*`의 3층 모델 기준으로 정리했다.
- `README.md`, `starter/README.md`, `starter/STARTER_GUIDE.md`, 모듈/스타터 README의 configuration 예시를 새 키 기준으로 정리하고, legacy fallback은 migration note로만 남겼다.
- `studio-platform-starter-user`, `studio-application-starter-attachment`, `studio-application-starter-mail`, `studio-platform-starter-ai`에 additional Spring configuration metadata를 추가해 legacy key deprecation과 대표 target property를 노출했다.
- `.env.example`와 AI 테스트용 sample application.yml을 현재 Google GenAI 환경 변수와 AI namespace 기준에 맞게 정리했다.

## 2026-04-26

### 변경됨
- 이슈 #333 대응으로 `content-embedding-pipeline`이 `AttachmentRagIndexService`와 attachment source executor를 auto-configuration으로 등록해 attachment RAG controller 생성 시 service bean 누락이 발생하지 않도록 했다.
- 이슈 #329/#330/#331 대응으로 RAG job JDBC repository opt-in, chunk page 조회 API, job HTTP 상태 smoke 테스트를 보강하고 job 생성/retry 권한을 write 기준으로 정리했다.
- 이슈 #327 대응으로 AI client update guide의 RAG chat attachment-only 제약, job 조회 권한, in-memory job 404 처리, chunk 조회 limit 안내를 보완했다.
- 이슈 #325 대응으로 AI client update guide의 RAG job 운영 화면 API 목록과 polling/retry/cancel 흐름을 최신화했다.
- 이슈 #323 대응으로 RAG index job cancel API(`POST /api/mgmt/ai/rag/jobs/{jobId}/cancel`)를 추가했다.
- `RagIndexJobService.cancelJob(jobId)` 계약과 `JOB_CANCELLED` 로그 코드를 추가하고, 기본 in-memory job service/repository가 취소 상태를 late progress callback으로 덮어쓰지 않도록 보강했다.
- 이슈 #321 대응으로 RAG job 목록 조회에 `sort`/`direction` 요청 계약을 추가하고 기본 정렬을 `createdAt desc`로 명시했다.
- 이슈 #319 대응으로 `RagIndexJobSourceExecutor` 계약을 추가하고, `sourceType=attachment` RAG job이 기존 attachment RAG 색인 흐름을 비동기로 실행하도록 연결했다.
- `POST /api/mgmt/ai/rag/jobs`는 raw `text` 없이 source 기반 요청을 받을 수 있으며, `content-embedding-pipeline`은 attachment source executor를 제공한다.
- 이슈 #317 대응으로 RAG 색인 작업 management API와 in-memory job repository/service 계약을 추가했다.
- `POST /api/mgmt/ai/rag/index`와 attachment RAG index API는 기존 empty `202 Accepted` 응답을 유지하면서 `X-RAG-Job-Id` 헤더를 additive하게 반환한다.
- RAG 색인 진행 단계(`EXTRACTING`, `CHUNKING`, `EMBEDDING`, `INDEXING`, `COMPLETED`)와 chunk/embedding/index count, warning/error log를 조회할 수 있도록 했다.
- 이슈 #313 대응으로 RAG metadata key reference를 `studio-platform-ai` README에 통합하고 관련 모듈 README에서 참조하도록 문서화했다.
- 이슈 #312 대응으로 AI web vector 검색 경로가 내부적으로 `VectorSearchResults`/`VectorSearchHit` aggregate 계약을 사용하도록 연결했다.
- `POST /api/mgmt/ai/vectors/search` 요청에서 `includeText`/`includeMetadata`를 optional field로 받아 core `VectorSearchRequest`에 전달한다.
- 이슈 #311 대응으로 `DefaultRagPipelineService`의 기본 RAG indexing 저장 경로를 `VectorRecord.builder()`와 `VectorStorePort.upsertAll(...)`/`replaceRecordsByObject(...)` 사용으로 전환했다.
- `VectorRecord` metadata pass-through가 blank string 값을 보존하도록 해 기존 `cleanerPrompt=""` metadata 호환성을 유지했다.
- 이슈 #309 대응으로 `VectorSearchHit.from(...)`이 문자열 숫자 `page`/`slide`와 iterable `headingPath`/`sourceRef` metadata를 안정적으로 변환하도록 보강했다.
- 이슈 #305 대응으로 첨부 RAG 색인과 web RAG context expansion에 opt-in diagnostics를 추가했다.
- `POST /api/mgmt/attachments/{id}/rag/index`는 서버 `allow-client-debug=true`와 요청 `debug=true`가 모두 만족될 때 안전한 `X-RAG-Index-*` 헤더로 구조화/fallback 경로와 count 정보를 노출한다.
- `POST /api/ai/chat/rag`는 서버 `allow-client-debug=true`와 요청 `debug=true`가 모두 만족될 때 `metadata.ragContextDiagnostics`에 context expansion 적용 상태를 노출한다.
- 이슈 #304 대응으로 `content-embedding-pipeline`의 구조화 attachment RAG 색인 경로가 `VectorRecord.builder()`와 `VectorStorePort.replaceRecordsByObject(...)`를 사용하도록 전환됐다.
- `VectorStorePort`에 record 기반 object-scope replace default adapter를 추가해 기존 `VectorDocument` 기반 store 구현과 호환되도록 했다.
- 이슈 #303 대응으로 `starter-ai-web`의 RAG context expansion을 `studio.ai.endpoints.rag.context.expansion.*` 설정으로 제어할 수 있도록 했다.
- context expansion candidate 조회 배수, 후보 조회 상한, previous/next window, parent content 포함 여부를 설정으로 분리했다.

### 검증
- `./gradlew :starter:studio-platform-starter-ai:test :starter:studio-platform-starter-ai-web:test :studio-application-modules:content-embedding-pipeline:test`
- `./gradlew :studio-platform-ai:compileJava :starter:studio-platform-starter-ai:compileJava :starter:studio-platform-starter-ai-web:compileJava :studio-application-modules:content-embedding-pipeline:compileJava`
- `./gradlew :studio-platform-ai:testClasses :starter:studio-platform-starter-ai:testClasses :starter:studio-platform-starter-ai-web:testClasses :studio-application-modules:content-embedding-pipeline:testClasses`
- `./gradlew :studio-platform-ai:test :starter:studio-platform-starter-ai:test :starter:studio-platform-starter-ai-web:test :studio-application-modules:content-embedding-pipeline:test`
- `./gradlew :studio-platform-ai:test :starter:studio-platform-starter-ai:test :starter:studio-platform-starter-ai-web:test`
- `git diff --check`
- `./gradlew :starter:studio-platform-starter-ai:test :studio-platform-ai:test`
- `./gradlew :starter:studio-platform-starter-ai-web:test :studio-application-modules:content-embedding-pipeline:test`
- `./gradlew :studio-platform-ai:test :studio-application-modules:content-embedding-pipeline:test :starter:studio-platform-starter-ai:test`
- `./gradlew :starter:studio-platform-starter-ai-web:test`
- `git diff --check`

## 2026-04-25

### 변경됨
- 이슈 #290 대응으로 `starter-ai-web`의 `RagContextBuilder`가 optional `ChunkContextExpander`를 사용해 object-scoped RAG 검색 결과의 parent/neighbor/table 문맥을 확장할 수 있도록 했다.
- RAG chat context 확장 후에도 기존 `max-chunks`, `max-chars` 제한을 유지하고, expander 또는 metadata가 없으면 기존 retrieval hit content 조립 경로를 유지한다.
- 이슈 #299/#300 대응으로 `starter-ai` README에 legacy RAG chunk 설정 migration guide를 추가했다.
- `studio.ai.pipeline.chunk-size`와 `studio.ai.pipeline.chunk-overlap`는 deprecated `TextChunker` fallback 전용 설정으로 표시하고, 기존 binding 호환성 테스트를 보강했다.
- 이슈 #297 대응으로 `starter-ai`의 기본 `TextChunker` bean 생성을 `ChunkingOrchestrator`가 없을 때의 legacy fallback으로 제한했다.
- `RagPipelineService` auto-configuration은 `TextChunker`를 optional로 받아 orchestrator-only 환경에서도 동작하도록 했다.
- `AiAutoConfiguration`은 `ChunkingAutoConfiguration` 이후 평가되도록 정렬해 chunking starter가 제공하는 `ChunkingOrchestrator`를 우선 감지한다.
- 이슈 #295 대응으로 `starter-ai`의 RAG indexing chunking 분기를 `RagChunker` adapter로 분리했다.
- `DefaultRagPipelineService`는 `ChunkingOrchestrator` 우선 경로와 deprecated `TextChunker` fallback 변환을 직접 다루지 않고 adapter 결과만 사용하도록 정리했다.
- 이슈 #293 대응으로 `studio-platform-ai`의 `TextChunk`/`TextChunker`와 `starter-ai`의 `OverlapTextChunker`를 deprecated legacy fallback으로 표시했다.
- 신규 RAG chunking은 `studio-platform-chunking`의 `ChunkingOrchestrator`를 기준으로 사용하도록 README를 정리했다.
- 이슈 #188 대응으로 PostgreSQL 그룹 멤버 summary 검색에 `pg_trgm` 기반 `lower(username|name|email)` GIN trigram index migration을 추가했다.
- MySQL/MariaDB에는 PostgreSQL 전용 검색 최적화와 schema version 이력을 맞추기 위한 V301 schema-neutral migration을 추가했다.
- 그룹 멤버 summary 검색에서 blank keyword를 null keyword와 동일하게 전체 조회로 처리하도록 service/JPA repository 경로를 정리했다.
- 그룹 멤버 summary repository 검색 테스트에 null, blank, username/name/email 매칭 케이스를 추가했다.
- 이슈 #281 대응으로 `studio-platform-chunking`과 `starter:studio-platform-starter-chunking` README를 한국어 기준으로 현행화했다.
- chunk metadata key reference, parent-child 모델, context expansion 계약, textract 연계, 하위 호환성 기준을 문서화했다.
- README 예시와 실제 public API가 어긋나지 않도록 parent-child chunking, context expansion, text fallback 문서 시나리오 테스트를 추가했다.

### 검증
- `./gradlew :studio-platform-chunking:test`
- `./gradlew :starter:studio-platform-starter-ai:test`
- `./gradlew :studio-platform-ai:test :starter:studio-platform-starter-chunking:test`
- `./gradlew :studio-platform-user:test --tests 'studio.one.base.user.infrastructure.persistence.jpa.ApplicationGroupMembershipJpaRepositorySearchTest' --tests 'studio.one.base.user.infrastructure.persistence.jdbc.ApplicationGroupMembershipJdbcRepositoryTest'`
- `./gradlew :studio-platform-chunking:test :starter:studio-platform-starter-chunking:test`

## 2026-04-24

### 변경됨
- 이슈 #235 대응으로 `HtmlFileParser.parse()`가 기존 `Jsoup.text()` 기반 plain text 전용 추출에서 `parseStructured(...).plainText()` 경로를 사용하도록 변경됐다.
- HTML 추출은 `script`, `style`, `nav`, `aside`, `footer`, `form` 등 boilerplate 후보를 제거하고 `h1`-`h6`, `p`, `li`, `table`, `img` 기반 semantic block을 구성한다.
- PDF/PPTX/HTML 파서에 구조화 block/provenance 추출을 추가하고, DOCX footnote/list 및 OCR line block metadata를 보강했다.

### 검증
- `./gradlew :studio-platform-textract:test`
- `./gradlew :studio-platform-textract:build`

## 2026-04-20

### 변경됨
- `studio-platform-data`의 문서/텍스트 추출 계약과 구현을 `studio-platform-textract` 모듈로 분리했다.
- 텍스트 추출 자동설정을 `starter:studio-platform-textract-starter`로 분리하고, 기존 `studio.features.text` 설정 prefix는 유지했다.
- 기존 `studio.one.platform.text.*` API는 `studio-platform-data`의 deprecated wrapper로 유지하고, 내부 attachment/RAG 사용처는 `studio.one.platform.textract.*`를 직접 사용하도록 전환했다.
- `StructuredFileParser`, `ParsedFile`, `ParsedBlock`, `BlockType`, `ParseWarning`을 추가해 HWP/HWPX와 OCR 확장을 위한 RAG 친화 구조화 파싱 계약을 마련했다.
- `rhwp` 분석 결과를 참고해 HWPX 문단·표·이미지와 HWP BodyText/BinData 추출 parser를 추가했다.
- 후속 정리로 `starter:studio-platform-starter`의 legacy text auto-configuration과 `studio-platform-data`의 포맷별 parser wrapper를 제거하고, data에는 최소 facade만 남겼다.

### 검증
- `./gradlew :studio-platform-textract:test :studio-platform-data:test :starter:studio-platform-textract-starter:test :starter:studio-platform-starter:test :studio-application-modules:attachment-service:test :studio-application-modules:content-embedding-pipeline:test`
- `gradle :studio-platform-data:test :starter:studio-platform-starter:test :starter:studio-platform-textract-starter:test`
- `git diff --check`

## 2026-04-17

### 변경됨
- `POST /api/ai/chat`와 `POST /api/ai/chat/rag`에 요청 단위 opt-in chat memory를 추가했다.
- 기본 동작은 stateless로 유지하고, `studio.ai.endpoints.chat.memory.enabled=true`와 요청 `memory.enabled=true`가 모두 설정된 경우에만 `conversationId`별 최근 메시지를 in-memory로 보관한다.
- chat memory는 `max-messages`, `max-conversations`, `ttl` 상한을 적용하며, 재시작 시 소실되고 다중 인스턴스 간 공유되지 않는 제약을 README에 문서화했다.
- 응답 metadata에 memory 사용 여부와 conversation 상태를 노출하도록 했다.

### 검증
- `./gradlew :studio-platform-ai:test :starter:studio-platform-starter-ai-web:test`

## 2026-04-16

### 변경됨
- 최근 AI/RAG 모듈 변경에 따른 클라이언트 수정 항목을 `docs/dev/ai-client-update-guide.md`에 정리하고 README 문서 목록에 추가했다.
- 이슈 #221 대응으로 `studio-platform-chunking` 계약 모듈과 `starter:studio-platform-starter-chunking`을 추가해 RAG indexing chunking을 starter 형태로 분리했다.
- `DefaultRagPipelineService`가 `ChunkingOrchestrator`를 optional로 사용하고, 없으면 기존 `TextChunker` fallback을 유지하도록 했다.
- `VectorStorePort`에 object scope replace/delete 흐름을 추가해 같은 `objectType`/`objectId` 재색인 시 stale chunk가 남지 않도록 했다.
- 이슈 #219 대응으로 Chunking starter와 Spring AI Retrieval pipeline을 병렬 구현하기 위한 `docs/dev/chunking-rag-pipeline-plan.md` 계획 문서를 추가했다.
- 이슈 #213 대응으로 AI web endpoint에서 provider quota/rate limit 예외를 500 대신 429 `ProblemDetails`로 반환하도록 했다.
- 이슈 #217 대응으로 `studio-platform-ai`를 AI/RAG 공통 계약 중심 모듈로 축소하고, RAG pipeline 구현체와 pgvector adapter, LLM 기반 keyword/cleaner 구현을 `starter:studio-platform-starter-ai`로 이동했다.
- 기존 `RagPipelineService`는 같은 FQN의 facade interface로 유지하고, 기본 구현은 starter의 `DefaultRagPipelineService`로 분리해 web/content 소비 모듈의 계약 의존을 유지했다.
- `TextCleaner`, `KeywordExtractor`, `PromptRenderer`, RAG option 타입은 확장 계약으로 `studio-platform-ai`에 유지하고, 관련 테스트 fixture와 구현 테스트를 starter 모듈로 이동했다.
- `RagPipelineService.SERVICE_NAME`의 `rag-pipelien-service` 오탈자를 `rag-pipeline-service`로 수정하고, 기존 bean name은 `LEGACY_SERVICE_NAME` alias로 유지했다.
- `studio-platform-ai`에서 Spring/JDBC/pgvector/Caffeine/Resilience4j 구현 의존을 제거했다.
- 이슈 #215 대응으로 AI web endpoint 설정을 `studio.ai.endpoints.enabled`, `base-path`, `mgmt-base-path` 기준으로 정리했다.
- Breaking: embedding/vector/RAG endpoint 기본 경로를 `/api/ai`에서 `/api/mgmt/ai`로 변경했다. 기존 경로를 유지하려면 `studio.ai.endpoints.mgmt-base-path=/api/ai`를 설정해야 한다.
- 사용자용 AI endpoint는 기본 `/api/ai`, 관리용 embedding/vector/RAG endpoint는 기본 `/api/mgmt/ai`를 사용하도록 분리했다.
- `studio.ai.endpoints.enabled=false`가 AI web controller 전체 비활성화로 동작하도록 자동 구성 조건을 정리했다.
- 기존 `studio.ai.endpoints.enabled=false`는 `AiInfoController`만 숨겼지만, 이제 AI web endpoint 전체를 비활성화한다.
- 이슈 #206 대응으로 `studio.ai.pipeline.keywords.scope`, `max-input-chars`와 `studio.ai.pipeline.retrieval.query-expansion.*` 설정을 추가해 keyword metadata 범위와 query expansion 동작을 조정할 수 있도록 했다.
- 기본 `keywords.scope=document`는 기존 문서 단위 `keywords`/`keywordsText` 동작을 유지하고, `chunk` 또는 `both` 설정 시 chunk metadata에 `chunkKeywords`/`chunkKeywordsText`를 추가한다.
- `LlmKeywordExtractor`의 입력 최대 길이 4000자 제한을 설정으로 이동하고, keyword trim/blank 제거/case-insensitive de-duplication을 적용했다.
- `studio.ai.vector.postgres.text-search-config=simple`은 향후 PostgreSQL FTS config 지원을 위한 문서화된 설정 후보로 남기고, 이번 작업에서는 기존 PostgreSQL SQL ranking 동작과 DB migration은 변경하지 않았다.
- 이슈 #205 대응으로 `studio.ai.pipeline.diagnostics.*`와 `studio.ai.endpoints.rag.diagnostics.allow-client-debug` 설정을 추가해 RAG 검색 fallback 전략과 결과 상태를 선택적으로 관찰할 수 있도록 했다.
- `RagRetrievalDiagnostics`를 추가해 strategy, result count, score threshold, hybrid weight, object scope, topK를 기록하고, client debug 허용 시에만 `ChatResponseDto.metadata.ragDiagnostics`에 노출한다.
- 기존 `POST /api/ai/chat/rag`의 per-hit info 로그를 제거하고, diagnostics result logging이 명시적으로 활성화된 경우에만 bounded debug snippet을 출력하도록 했다.
- 이슈 #204 대응으로 `studio.ai.pipeline.cleaner.*` 설정을 추가해 RAG 색인 전 LLM 기반 텍스트 정제를 선택적으로 적용할 수 있도록 했다.
- `TextCleaner`/`LlmTextCleaner`를 추가하고 `rag-cleaner` prompt의 `clean_text` JSON 응답을 색인 텍스트로 사용하도록 했다.
- `RagPipelineService.index()`가 cleaner 적용 여부, 원문/색인 텍스트 길이, chunk 수, chunk 길이를 vector metadata에 additive로 기록하도록 했다.
- 첨부 RAG 인덱싱 metadata에 `filename`, `sourceType=attachment`, `indexedAt`을 `putIfAbsent`로 추가해 클라이언트/운영 추적 정보를 보강했다.
- 이슈 #202 대응으로 `RagPipelineService`의 hybrid 검색 weight, 최소 relevance score, keyword/semantic fallback 사용 여부를 `studio.ai.pipeline.retrieval.*` 설정으로 조정할 수 있도록 했다.
- query 없는 object-scope RAG 조회가 과도한 chunk를 반환하지 않도록 `studio.ai.pipeline.object-scope.default-list-limit`, `max-list-limit` 설정과 service layer clamp를 추가했다.
- `POST /api/ai/chat/rag`가 system context에 포함하는 RAG chunk 수/문자 수를 `studio.ai.endpoints.rag.context.*` 설정으로 제한하도록 했다.
- hybrid search weight는 합계가 0보다 커야 하며, context 문자 수 한도 초과 시 chunk를 중간 절단하지 않고 제외하도록 명확히 했다.
- Issue #203의 RAG 품질 개선 Phase 2 범위로 live LLM 호출 없이 동작하는 deterministic RAG smoke fixture를 추가했다.
- 한국어 정책형 fixture와 첨부 요약형 fixture를 추가해 한국어 질의가 기대 chunk로 매핑되는지, object scope 검색이 다른 첨부 chunk를 반환하지 않는지, `listByObject`가 chunk 순서를 보존하는지 검증한다.
- Phase 1의 context truncation 구현에 의존하는 검증은 이번 범위에서 제외하고 후속 Phase 1/통합 검증 대상으로 남겼다.
- 이 작업은 PR #207의 RAG 설정화 변경과 통합 검증되도록 `2.x` 최신으로 rebase했다.

### 검증
- `gradle :studio-platform-chunking:test :starter:studio-platform-starter-chunking:test :starter:studio-platform-starter-ai:test :starter:studio-platform-starter-ai-web:test :studio-application-modules:content-embedding-pipeline:test :studio-platform-ai:test`
- `git diff --check`
- `./gradlew :starter:studio-platform-starter-ai-web:test`
- `gradle :studio-platform-ai:test :starter:studio-platform-starter-ai:test :starter:studio-platform-starter-ai-web:test :studio-application-modules:content-embedding-pipeline:test`
- `gradle :starter:studio-platform-starter-ai-web:test`
- `gradle :studio-platform-ai:test`
- `gradle :starter:studio-platform-starter-ai:test`
- `gradle :studio-platform-ai:test :starter:studio-platform-starter-ai:test :starter:studio-platform-starter-ai-web:test --rerun-tasks`
- `gradle :studio-platform-ai:test`
- `gradle :starter:studio-platform-starter-ai-web:test`
- `gradle :studio-platform-ai:test --tests 'studio.one.platform.ai.service.pipeline.RagQualitySmokeTest'`
- `gradle :studio-platform-ai:test`
- `gradle :starter:studio-platform-starter-ai:test`
- `gradle :studio-application-modules:content-embedding-pipeline:test`
- `./gradlew :studio-platform-ai:test`
- `./gradlew :starter:studio-platform-starter-ai-web:test`
- `git diff --check`

## 2026-04-15

### 변경됨
- Spring AI 기반 AI web API가 기존 React 클라이언트 계약을 수용하도록 `ChatRequestDto.provider`, `ChatRequestDto.systemPrompt`를 추가하고, `ChatController`가 `AiProviderRegistry`로 provider별 `ChatPort`를 선택하도록 변경했다.
- `POST /api/ai/chat/rag`가 파일/객체 범위 RAG 답변에 사용할 `objectType`/`objectId` 흐름과 client system prompt를 함께 지원하도록 보강했다.
- `POST /api/ai/chat/rag`의 attachment 범위 RAG가 `features:attachment read` 권한을 추가로 요구하고, 안전하게 권한 확인할 수 없는 object scope 요청을 거부하도록 보강했다.
- `POST /api/ai/chat`의 `provider` 입력을 trim/blank normalize하고 알 수 없는 provider를 400 오류로 반환하도록 정리했다.
- `POST /api/ai/vectors/search` 응답에 기존 `documentId`와 동일한 `id` alias를 추가해 클라이언트 grid와 기존 소비자를 모두 지원하도록 했다.
- `ObjectTypeMgmtController`에 `GET /api/mgmt/object-types/{objectType}/policy/effective`를 추가해 저장 정책이 없을 때도 클라이언트가 실제 적용 정책(`source=default`, ObjectType별 추가 제한 없음)을 안내할 수 있도록 했다.

### 검증
- `./gradlew :starter:studio-platform-starter-ai-web:test`
- `./gradlew :starter:studio-platform-starter-ai:compileJava`
- `./gradlew :studio-platform-ai:test`
- `./gradlew :studio-platform-objecttype:test`
- `./gradlew :starter:studio-platform-starter-objecttype:compileJava`

## 2026-04-14

### 변경됨
- README 계층을 소스 기준으로 현행화해 활성 모듈/스타터 목록, 대표 스타터 조합, 환경변수 매핑을 보강했다.
- `starter/studio-platform-starter-realtime/README.md`를 추가해 STOMP/WebSocket, Redis Pub/Sub, JWT handshake 자동 구성을 문서화했다.
- schema 보유 모듈 README의 Flyway 버전 참조를 실제 `Vxxx__*.sql` 파일명과 `docs/flyway-versioning.md` 기준으로 정리했다.
- `docs/documentation-improvements.md`를 현재 상태/완료 항목/즉시 보완 후보/표준화/소스 품질 개선 후보 기준으로 재정리했다.
- `template-service`의 관리 컨트롤러 클래스를 `TemplateController`에서 `TemplateMgmtController`로 변경해 관리용 컨트롤러 명명 규칙을 맞췄다.
- `GET /api/mgmt/templates` 계열 응답의 `createdBy`, `updatedBy`를 숫자 userId 대신 `{ userId, username }` 형태의 `UserDto`로 변경했다.
- `template-service`에 사용자 응답 매핑 회귀 테스트를 추가하고, 기존 권한 테스트를 새 컨트롤러명 기준으로 갱신했다.
- `starter:studio-application-starter-template`의 auto-configuration과 관련 README가 새 컨트롤러명 `TemplateMgmtController`를 참조하도록 맞춰 starter 컴파일 오류를 수정했다.

### 검증
- `rg "TemplateController|V0__" README.md starter studio-application-modules studio-platform* docs -g 'README.md' -g '*.md' -g '!**/build/**' -g '!**/bin/**'`
- `rg "STARTER_GUIDE|spring-ai-openai|flyway-versioning|studio-platform-starter-realtime/README.md" README.md starter studio-application-modules studio-platform* docs -g 'README.md' -g '*.md' -g '!**/build/**' -g '!**/bin/**'`
- README 누락 확인 스크립트 (`settings.gradle.kts`의 공식 include 기준)
- `./gradlew :starter:studio-platform-starter-realtime:compileJava`
- `./gradlew :studio-application-modules:template-service:test`
- `./gradlew :starter:studio-application-starter-template:compileJava`

## 2026-04-10

### 변경됨
- `DELETE /groups/{id}/members`의 요청 body를 raw `List<Long>`에서 `{"userIds":[...]}` 형태의 `AddMembersRequest`로 교체해 `POST /groups/{id}/members`와 API 계약을 통일했다.
- `GroupMgmtController`의 메서드명 오타 `removeaddMemberships`를 `removeMemberships`로 수정했다.
- `GET /groups/{id}/member-summaries`의 `ApplicationGroupMemberSummary` → `GroupMemberSummaryDto` 매핑을 컨트롤러 인라인에서 `ApplicationGroupService.getMemberSummaryDtos()` default 메서드로 위임했다.
- `GroupMgmtControllerTest`에 `removeMembershipsCallsServiceWithUserIds` 테스트를 추가했다.

### 검증
- `./gradlew :studio-platform-user:test`
- `./gradlew :studio-platform-user:compileJava :studio-platform-user-default:compileJava`

## 2026-04-09

### 변경됨
- 그룹 멤버 조회용 전용 읽기 API `GET /api/mgmt/groups/{id}/member-summaries`를 추가했다.
- 그룹 멤버 summary 조회는 `userId`, `username`, `name`, `enabled`만 반환하고 `username`/`name`/`email` 기준 `q` 검색을 지원한다.
- 기존 `GET /api/mgmt/groups/{id}/members`는 유지하고, `studio-platform-identity` 계약은 변경하지 않았다.
- `studio-platform-user`에 그룹 멤버 summary 조회용 repository/service/controller 테스트와 최소 테스트 의존성을 추가했다.

### 검증
- `./gradlew :studio-platform-user:test`
- `./gradlew :studio-platform-user:compileJava :studio-platform-user-default:compileJava`

## 2026-04-08 (local nexus publish)

### 변경됨
- `gradle.properties`를 수정하지 않고 로컬 Nexus로 배포할 수 있도록 `scripts/publish-local-nexus.sh`를 추가했다.
- 로컬 Nexus에 같은 버전의 모듈이 이미 있을 때 `--delete-existing`로 전체 모듈을 확인해 삭제 후 재배포할 수 있도록 했다.
- 특정 모듈만 처리할 수 있도록 `--delete-existing --module <gradle-path>`도 지원한다.
- 로컬 Nexus 배포 스크립트가 기본적으로 `.env.local`을 읽어 `NEXUS_USERNAME`, `NEXUS_PASSWORD`, `NEXUS_URL` 값을 사용할 수 있도록 했다.
- README에 로컬 Nexus 배포 절차와 특정 모듈 publish 예시를 추가했다.

### 검증
- `bash -n scripts/publish-local-nexus.sh`
- `scripts/publish-local-nexus.sh` 환경변수 미설정 실패 경로 확인
- `scripts/publish-local-nexus.sh --delete-existing` 환경변수 미설정 실패 경로 확인
- `git diff --check`

## 2026-04-08

### 변경됨
- `UserMgmtControllerApi`를 `UserMgmtApi`로 변경해 사용자 관리 엔드포인트 확장 인터페이스 이름에서 컨트롤러 구현 세부 표현을 제거했다.
- `UserPublicControllerApi`, `UserMeControllerApi`, `UserAuthPublicControllerApi`도 같은 기준의 `UserPublicApi`, `UserMeApi`, `UserAuthPublicApi`로 정리했다.
- 일반 사용자 정보 수정 시 기존 비밀번호 해시가 다시 인코딩되지 않도록 비밀번호 인코딩을 비밀번호 전용 변경/초기화 경로로 제한했다.
- 일반 사용자 정보 수정 경로에서 mutator가 비밀번호 값을 변경하면 저장 전에 실패하도록 방어를 추가했다.
- 일반 사용자 정보 수정과 비밀번호 초기화 경로를 구분하는 회귀 테스트를 추가했다.

### 검증
- `./gradlew :studio-platform-user:compileJava :studio-platform-user-default:compileJava :starter:studio-platform-starter-user:compileJava`
- `./gradlew :studio-platform-user-default:test --tests 'studio.one.base.user.application.service.ApplicationUserServiceImplTest'`
- `rg "User(Mgmt|Public|AuthPublic|Me)ControllerApi|User(Mgmt|Public|AuthPublic|Me)Api" studio-platform-user studio-platform-user-default starter/studio-platform-starter-user`

## 2026-03-31 (follow-up)

### 변경됨
- `AttachmentServiceImpl`의 `InputStream` 기반 size 계산을 `available()`에서 임시 파일 버퍼링으로 바꿔 정확한 크기를 보장하도록 수정했다. 서비스 레이어에서도 최대 50MB 상한을 다시 적용한다.
- `AttachmentServiceImpl`의 `File` 기반 업로드는 입력 스트림을 try-with-resources로 닫고, 너무 큰 파일은 명시적으로 실패하도록 정리했다.
- storage save 실패 시 partial binary만 best-effort로 정리하고, 메타데이터는 트랜잭션 rollback에 맡기도록 저장 경계를 명확히 했다. 입력 스트림 close 실패는 경고로만 남기고 저장 성공을 뒤집지 않도록 정리했다.
- `attachment-service`에 `AttachmentServiceImpl`, `LocalFileStore`, `JpaFileStore` 회귀 테스트를 추가해 unknown-size stream 처리, explicit size 유지, filesystem/database 저장 경로를 검증했다.

### 검증
- `./gradlew :studio-application-modules:attachment-service:test --tests 'studio.one.application.attachment.service.AttachmentServiceImplTest' --tests 'studio.one.application.attachment.storage.LocalFileStoreTest' --tests 'studio.one.application.attachment.storage.JpaFileStoreTest'`
- `./gradlew :studio-application-modules:attachment-service:compileJava`

## 2026-03-31

### 변경됨
- `attachment-service`의 `AttachmentController`, `AttachmentMgmtController`, `MeAttachmentController`가 파일명 정제, MIME 정규화, 다운로드 헤더 구성을 공통 `AttachmentWebSupport`로 공유하도록 정리했다.
- `AttachmentMgmtController`의 관리자 판별이 `ADMIN`과 `ROLE_ADMIN`을 모두 허용하도록 보강해 Spring Security authority 표현 차이로 인한 owner 우회 오판정을 줄였다.
- `attachment-service`에 attachment 웹 helper 회귀 테스트를 추가하고, mgmt 권한 테스트가 `ROLE_ADMIN` 경로를 검증하도록 보강했다.
- `attachment-service`의 접근 제어 helper를 `AttachmentAccessSupport`로 분리해 principal 조회, 관리자 판별, owner 접근 검사를 컨트롤러에서 공통으로 사용하도록 정리했다.

### 검증
- `./gradlew :studio-application-modules:attachment-service:test --tests 'studio.one.application.web.controller.AttachmentAccessSupportTest' --tests 'studio.one.application.web.controller.AttachmentControllerTest' --tests 'studio.one.application.web.controller.AttachmentMgmtControllerAuthorizationTest' --tests 'studio.one.application.web.controller.AttachmentWebSupportTest' --tests 'studio.one.application.web.controller.MeAttachmentControllerTest'`
- `./gradlew :studio-application-modules:attachment-service:compileJava`

## 2026-03-30

### 변경됨
- `PropertyValidator`가 점(`.`)과 하이픈(`-`)이 포함된 민감 프로퍼티 키도 감지하도록 수정했다.
- `RepositoryImpl`에 경로 탐색 방어를 추가하고, startup refresh 이벤트가 즉시 `UnsupportedOperationException`으로 실패하지 않도록 정리했다.
- `GlobalExceptionHandler`가 unsupported method/media type 예외를 각각 405/415로 응답하도록 수정했다.
- `JasyptHttpController`의 토큰 검증을 상수 시간 비교로 바꾸고, `JasyptProperties`에 암호화 비밀번호 최소 길이 검증을 추가했다.
- `studio-platform`과 `starter-jasypt`에 회귀 테스트를 추가하고, 테스트용 웹 의존성을 보강했다.

### 검증
- `./gradlew :studio-platform:test --tests 'studio.one.platform.component.PropertyValidatorTest' --tests 'studio.one.platform.component.RepositoryImplTest' --tests 'studio.one.platform.web.advice.GlobalExceptionHandlerTest'`
- `./gradlew :starter:studio-platform-starter-jasypt:test --tests 'studio.one.platform.autoconfigure.jasypt.JasyptHttpControllerTest' --tests 'studio.one.platform.autoconfigure.jasypt.JasyptPropertiesTest'`

## 2026-03-30 (follow-up)

### 변경됨
- `studio-platform-autoconfigure`의 `CompositeAuditorAware`가 외부에서 주입한 `AuditorAware`를 우선 처리할 수 있도록 확장했다.
- `CompositeAuditorAware`의 기존 security/header/fixed 기본 합성 동작은 유지했다.
- `studio-platform-autoconfigure`에 `CompositeAuditorAware` 회귀 테스트와 테스트용 `spring-data-commons` 의존성을 추가했다.
- 루트 OWASP dependency-check의 `failBuildOnCVSS`를 `7.0F`로 낮춰 High 이상 취약점에서 빌드가 실패하도록 조정했다.
- `studio-platform-data`에 `PaginationDialect` 회귀 테스트를 추가했다.
- `studio-platform`에 `DomainPolicyRegistryImpl` 병합/정규화 회귀 테스트를 추가하고, contributor 병합 시 불변 맵을 다시 수정하던 경로를 안전하게 고쳤다.
- `starter`의 `perisitence`와 `studio-platform-autoconfigure`의 `perisistence` 오타 패키지에 대응해 정상 패키지명 `persistence` 경로를 추가하고, 기존 경로는 deprecated 호환 브리지로 유지했다.
- Spring Boot auto-configuration 등록 경로를 `persistence` 패키지로 전환했다.
- `studio-platform-identity` 계약에 principal/resolver 규약과 `UserDto` 용도를 문서화하고, identity service bean 이름 상수를 별도 상수 클래스로 분리했다.
- `studio-platform-identity`를 순수 계약 모듈로 유지하도록 Spring Boot 플러그인을 제거했다.

### 검증
- `./gradlew :studio-platform-autoconfigure:test --tests 'studio.one.platform.autoconfigure.perisistence.jpa.auditor.CompositeAuditorAwareTest'`
- `./gradlew :studio-platform:test --tests 'studio.one.platform.security.authz.DomainPolicyRegistryImplTest'`
- `./gradlew :studio-platform-data:test --tests 'studio.one.platform.data.jdbc.pagination.PaginationDialectTest'`
- `./gradlew :studio-platform-autoconfigure:test --tests 'studio.one.platform.autoconfigure.persistence.jpa.auditor.CompositeAuditorAwareTest'`
- `./gradlew :starter:studio-platform-starter:compileJava`
- `./gradlew :studio-platform-identity:test`
- `./gradlew :studio-platform-identity:build`

## 2026-03-31

### 변경됨
- `studio-platform-objecttype`의 `ObjectTypeRuntimeService`와 `ObjectTypeAdminService`가 `web.dto` 대신 서비스 전용 command/result 타입을 사용하도록 정리했다.
- `ObjectTypeController`와 `ObjectTypeMgmtController`가 서비스 모델을 기존 웹 DTO로 매핑하도록 책임을 이동해 HTTP 응답 형식은 유지했다.
- `attachment-service`의 objecttype 업로드 정책 검증 호출이 서비스 전용 `ValidateUploadCommand`를 사용하도록 변경했다.
- `studio-platform-objecttype`에 runtime 성공 경로와 controller 매핑 회귀 테스트를 추가했다.

### 검증
- `./gradlew :studio-platform-objecttype:test --tests 'studio.one.platform.objecttype.ObjectTypeRuntimeServiceTest' --tests 'studio.one.platform.objecttype.ObjectTypeControllerTest' --tests 'studio.one.platform.objecttype.ObjectTypeMgmtControllerTest'`
- `./gradlew :studio-application-modules:attachment-service:test --tests 'studio.one.application.attachment.service.AttachmentServiceImplTest' --tests 'studio.one.application.web.controller.AttachmentMgmtControllerAuthorizationTest'`
- `./gradlew :studio-application-modules:attachment-service:compileJava :starter:studio-platform-starter-objecttype:compileJava`

## 2026-03-26

### 변경됨
- `studio-platform-starter-ai`의 provider 의존성(OpenAI, Google GenAI, Ollama)을 `implementation`에서 `compileOnly`로 전환했다. 소비 애플리케이션이 필요한 provider 라이브러리를 직접 선언해야 한다.
- Spring AI BOM을 `api(platform(...))`으로 노출하여 소비 앱이 별도 BOM 선언 없이 Spring AI 버전을 일관되게 관리할 수 있도록 했다.
- `ProviderChatPortFactory` / `ProviderEmbeddingPortFactory` 인터페이스와 provider별 `@Configuration` 구현체를 도입했다. 각 구현체는 `@ConditionalOnClass`로 보호되어, provider 라이브러리가 classpath에 있을 때만 해당 factory가 등록된다.
- `ProviderChatConfiguration` / `ProviderEmbeddingConfiguration`의 switch 기반 직접 참조를 제거하고, 등록된 factory를 수집하는 방식으로 교체했다. factory가 없는 provider는 조용히 제외된다.
- `AiSecretPresenceGuard`에서 `ChatModel` / `EmbeddingModel` bean 주입을 제거했다. property 기반 검증만 유지한다.
- `AiProviderRegistryConfiguration`에 fail-fast guard를 추가했다. `studio.ai.default-provider`에 지정된 provider가 chat port와 embedding port 모두에 없으면 시작 시점에 명확한 오류로 실패한다.

### 사용 방법 (OpenAI 예시)
```kotlin
// build.gradle.kts
implementation("studio-platform-starter-ai")
implementation("org.springframework.ai:spring-ai-starter-model-openai")
```
```yaml
# application.yml
studio:
  ai:
    enabled: true
    default-provider: openai
    providers:
      openai:
        type: OPENAI
        chat:
          enabled: true
        embedding:
          enabled: true
spring:
  ai:
    openai:
      api-key: ${OPENAI_API_KEY}
      chat.options.model: gpt-4o-mini
      embedding.options.model: text-embedding-3-small
```

### 검증
- `./gradlew :starter:studio-platform-starter-ai:build`

## 2026-03-23

### 변경됨
- JWT refresh cookie가 설정된 cookie name/path/SameSite/Secure 값을 따르도록 수정했다.
- realtime STOMP 기본값을 same-origin, JWT 요구, 익명 연결 거부 방향으로 강화했다.
- 파일 텍스트 추출에 기본 10MB 상한을 추가해 과도한 메모리 적재를 막았다.
- realtime JWT 의존성 누락을 startup 단계에서 fail-fast 처리하고, text extraction 상한 설정을 바인딩 단계에서 검증하도록 보강했다.

### 검증
- `./gradlew -PnimbusJoseJwtVersion=9.37.3 -PjsonSmartVersion=2.5.2 :studio-platform-security:test --tests 'studio.one.base.security.web.controller.JwtCookieSettingsTest' :studio-platform-realtime:test --tests 'studio.one.platform.realtime.stomp.config.RealtimeStompPropertiesTest' :studio-platform-data:test --tests 'studio.one.platform.text.service.FileContentExtractionServiceTest' ':studio-application-modules:attachment-service:test' --tests 'studio.one.application.web.controller.AttachmentMgmtControllerAuthorizationTest' :starter:studio-platform-starter-realtime:compileJava`

## 2026-03-24

### 변경됨
- `starter:studio-platform-starter-ai`에 Spring AI OpenAI starter 기반 스파이크를 추가하고, OpenAI 직접 모델 생성 대신 Spring AI auto-configuration bean을 alias port에 연결하도록 정리했다.
- `studio.ai.spring-ai.source-provider`와 fail-fast guard를 추가해 Spring AI alias가 명시된 OpenAI provider와 `spring.ai.openai.*` 설정을 사용하도록 고정했다.
- source provider로 지정된 OpenAI의 LangChain base 경로도 `spring.ai.openai.*`를 사용하도록 바꿔, OpenAI runtime 설정의 단일 소스를 유지한 채 LangChain/Spring AI 비교가 가능하게 했다.
- `openai-springai` default cutover 검증을 위해 `AiInfoController`, `ChatController`, `EmbeddingController` smoke 테스트를 추가했다.
- `studio.ai.default-provider`를 비웠을 때 Spring AI alias를 기본 provider로 승격하고, `default-provider=openai`를 명시하면 LangChain base provider로 rollback할 수 있게 정리했다.
- OpenAI provider를 Spring AI 단일 경로로 정리하고, `openai-springai` alias 및 LangChain OpenAI base path 제거 방향을 [spring-ai-openai.md](/Users/donghyuck.son/git/studio-api/docs/dev/spring-ai-openai.md)에 문서화했다.

### 검증
- `./gradlew -PnimbusJoseJwtVersion=9.37.3 -PjsonSmartVersion=2.5.2 :starter:studio-platform-starter-ai:test --tests 'studio.one.platform.ai.autoconfigure.AiSecretPresenceGuardTest' --tests 'studio.one.platform.ai.autoconfigure.adapter.SpringAiChatAdapterTest' --tests 'studio.one.platform.ai.autoconfigure.adapter.SpringAiEmbeddingAdapterTest' --tests 'studio.one.platform.ai.autoconfigure.config.SpringAiAliasProviderRegistrationTest' --tests 'studio.one.platform.ai.autoconfigure.config.SpringAiAliasProviderAutoConfigurationTest'`
# 2026-03-24

- refactor(ai): start splitting AI HTTP endpoints into a dedicated web starter module.
- refactor(ai): remove remaining core bean stereotypes so AI service ownership stays in starter auto-configuration.
- refactor(ai): narrow AI dependency ownership so web/security concerns stay with the web starter boundary.
- refactor(ai): replace AI starter component scanning with explicit auto-configuration bean registration.
- refactor(ai): prune unused compileOnly Spring starter dependencies from `studio-platform-starter-ai`.
- refactor(ai): remove LangChain4j `TokenUsage` coupling from ai-web starter and normalize chat `tokenUsage` metadata shape.
- refactor(ai): migrate Ollama embedding wiring from LangChain4j to Spring AI and validate `spring.ai.ollama.embedding.options.model` at startup.
- refactor(ai): migrate Google embedding wiring from LangChain4j to Spring AI and validate `spring.ai.google.genai.embedding.*` at startup.
- refactor(ai): preserve Google embedding `taskType` during the Spring AI migration; `titleMetadataKey` remains inactive because the current embedding request model carries text only.
- refactor(ai): remove the remaining LangChain4j embedding adapter and dead embedding wiring, keeping LangChain4j only for the Google chat path.
- refactor(ai): migrate Google chat wiring from LangChain4j to Spring AI and remove the remaining LangChain4j chat adapter path.
- fix(ai): preserve custom Google chat base URL when building the Spring AI Google GenAI client.
- refactor(ai): rename provider wiring configurations to neutral names after removing LangChain4j runtime paths.
