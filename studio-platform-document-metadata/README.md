# Studio Platform Document Metadata

문서 의미 유형과 유형별 metadata schema, provenance, evidence, projection 정책을 제공하는 순수 Java
계약 모듈이다. Spring, AI SDK, 문서 파서, 저장소 구현에 의존하지 않는다.

전체 AI/RAG 구조는 [AI/RAG 아키텍처 가이드](../docs/ai-rag/README.md)를 먼저 참고한다.

## 책임 범위

이 모듈이 담당하는 것:

- 문서 의미 유형과 사용자 선택값 분리
- 유형별 metadata field schema와 UI 설명
- revision 단위 metadata artifact
- field confidence, provenance와 source evidence
- vector/prompt/API projection allowlist

이 모듈이 담당하지 않는 것:

- EPUB/PDF/OOXML/HTML native metadata 추출
- LLM 호출과 JSON Schema 응답 처리
- artifact DB 저장과 backfill job
- HTTP controller
- 청킹 전략 선택과 embedding

위 runtime 기능은 `studio-platform-starter-markdown`과 `studio-platform-markdown`이 연결한다.

## 핵심 타입

| 타입 | 역할 |
|---|---|
| `DocumentSemanticType` | 감지·확정된 문서 의미 유형 |
| `DocumentSemanticTypeSelection` | 요청 선택값. `AUTO`는 저장 유형이 아님 |
| `DocumentMetadataSchemaRegistry` | 유형별 field 정의 조회 |
| `BuiltInDocumentMetadataSchemaRegistry` | 기본 schema registry |
| `DocumentMetadataArtifact` | revision별 metadata 결과와 version/fingerprint |
| `DocumentMetadataField` | raw/normalized value, confidence, provenance, evidence |
| `DocumentMetadataEvidence` | 정규화 원문 기준 locator와 offset |
| `DocumentMetadataProjectionPolicy` | vector와 prompt에 노출할 field allowlist |
| `MetadataEnrichmentMode` | `OFF`, `AUTO`, `REQUIRED` 실행 정책 |

## 의미 유형

지원 유형:

- `GENERAL`
- `BOOK`
- `ACADEMIC_PAPER`
- `THESIS`
- `REPORT`
- `POLICY`
- `MANUAL`
- `PRESENTATION`
- `UNKNOWN`

요청에서는 `DocumentSemanticTypeSelection.AUTO`를 사용할 수 있지만 감지 결과로 `AUTO`를 저장하지 않는다.
처리 프로필, 의미 유형, Blockify 유형은 별도 계약이며 이 모듈은 처리 프로필이나 청킹 전략을 결정하지 않는다.

## Artifact

`DocumentMetadataArtifact`는 다음 식별·품질 값을 보존한다.

- artifact ID와 revision ID
- schema/extractor version
- content fingerprint
- requested/detected/effective classification
- `COMPLETE | PARTIAL | WARNING` 품질
- 등록 field와 warning

field map의 key는 `DocumentMetadataField.fieldId`와 같아야 한다. 등록되지 않은 임의 field를 외부
metadata에서 그대로 저장하지 않는다.

## Provenance와 evidence

| Provenance | 의미 |
|---|---|
| `USER_PROVIDED` | 사용자가 명시한 값 |
| `NATIVE_STRUCTURED` | OPF/XMP/OOXML/meta 같은 native 속성 |
| `STRUCTURAL_HEURISTIC` | 제목·표지·문서 구조 규칙으로 추출 |
| `SOURCE_VERIFIED` | 제안값이 정규화 원문에서 확인됨 |
| `INFERRED` | 원문 exact evidence로 확정되지 않은 추론 |

RAG prompt의 확정 사실에는 source-verified field만 사용한다. inferred 값은 UI 검토 대상으로 표시할 수
있지만 근거가 확인된 사실로 승격하지 않는다.

## Projection 정책

전체 artifact를 chunk/vector마다 반복 저장하지 않는다.

vector compact metadata:

- `docMetadataId`
- `docSemanticType`
- `docTitle`
- `docAuthors` 최대 5개
- `docPublicationYear`
- `docOrganization`
- `docKeywords` 최대 8개
- `docSummary` 최대 480자

`DocumentMetadataProjectionPolicy.promptFacts(...)`는 source-verified field만 반환한다.
projection 정책을 변경할 때는 vector payload 크기, 개인정보, 기존 index 호환성을 함께 검토한다.

## 사용

```kotlin
dependencies {
    implementation(project(":studio-platform-document-metadata"))
}
```

```java
DocumentMetadataSchema schema = registry.require(DocumentSemanticType.BOOK);
Map<String, Object> compact = projectionPolicy.compact(artifact);
Map<String, DocumentMetadataField> promptFacts = projectionPolicy.promptFacts(artifact);
```

## 관련 문서

- [RAG 색인](../docs/ai-rag/indexing.md)
- [Studio Platform Markdown](../studio-platform-markdown/README.md)
- [Studio Platform Starter Markdown](../starter/studio-platform-starter-markdown/README.md)
