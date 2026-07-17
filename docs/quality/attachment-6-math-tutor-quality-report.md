# Attachment 6 Math Tutor Quality Report

## 판정

**TUTOR_READY**

동일 원본에서 새로 생성한 최종 revision을 대상으로 Markdown, provenance, 구조 기반 청킹,
검색, 튜터 응답을 다시 평가했다. 모든 필수 게이트가 합격 기준을 충족했다.

## 실행 정보

| 항목 | 값 |
|---|---|
| Attachment | `6` |
| Document | `mdoc-9740b967-f475-4201-9584-e4135286b37d` |
| Final revision | `mrev-311e0fb4-b5b1-4fd4-94dd-2f68c1291949` |
| Content hash | `343caa329785a754a6071168b61712f59acdd9671b828839360e197b39fce272` |
| 처리 시간 | 22분 17초 (`2026-07-16T14:47:19Z` ~ `15:09:36Z`) |
| OCR | `ocrRequired=true`, `FORCE`, `kor+eng`, PaddleOCR 44/44 pages |
| Math | local Pix2Text + Gemini page supplement, 460 normalized math blocks |
| Markdown | 52,111 chars, 1,317 lines, 107 rendered formula lines |
| Chunking | `structure-based`, 247 chunks, max 1,198 chars, fallback 없음 |
| Retrieval run | `reval-ca9b5f5f-3030-406a-8513-16163ede4e57` |
| Tutor model | `google-ai-gemini` / `gemini-2.5-flash`, `maxOutputTokens=1500` |

## 품질 게이트

| 영역 | 측정값 | 기준 | 결과 |
|---|---:|---:|---|
| Markdown quality score | `0.8622` | `>= 0.80` | PASS |
| 심각한 OCR 깨짐 줄 | `2/821` (`0.244%`) | `<= 0.5%` | PASS |
| 문제 번호/선택지 보존 | `40/40` (`100%`) | `>= 95%` | PASS |
| 수식 의미 표본 | `20/20` (`100%`) | `>= 90%` | PASS |
| 개념 본문 표본 | `20/20` (`100%`) | `>= 95%` | PASS |
| 검색 가능 block provenance | `100%` (44/44 pages) | `100%` | PASS |
| RAG index eligibility | `true` | `true` | PASS |
| Retrieval Recall@5 | `30/30` (`100%`) | `>= 90%` | PASS |
| Tutor 핵심 답변 정확도 | `30/30` (`100%`) | `>= 85%` | PASS |
| Tutor page 인용률 | `30/30` (`100%`) | `>= 95%` | PASS |

문제 번호 표본은 `025`~`030`, `064`~`077`을 사용했다. baseline BODY/TABLE 노이즈는
렌더링하지 않으면서 누락되던 번호를 page/sourceRef marker로 복원했다. 선택지, 수식, 개념 본문은
페이지 3, 10, 18, 34를 포함한 층화 표본을 원문과 대조했다.

## 검색 및 튜터 평가

질문 집합은 `docs/quality/attachment-6-retrieval-benchmark.json`의 30문항이다. default와 hybrid
검색 모두 Recall@5 `100%`, MRR `0.6956`이었다. 결과는 모두 최종 revision과
`actualChunkingStrategy=structure-based`를 참조했다.

동일 질문으로 실제 RAG chat을 호출했다. 30개 응답이 모두 HTTP 성공, 비어 있지 않은 답변,
최소 1개 이상의 RAG reference를 반환했으며 150개 reference에서 page 누락은 없었다. 공식의 등식
방향이나 설명 문구가 정답 문자열과 달라도 수학적으로 같은 경우는 의미 기준으로 판정했다.

## 잔여 경고

normalized snapshot은 `REVIEW_REQUIRED`이며 이슈는 `MATH_DOCUMENT_REVIEW_REQUIRED`,
`KOREAN_SPACING_REVIEW_REQUIRED`, `PAGE_QUALITY_REVIEW_REQUIRED`이다. 이는 일부 붙여쓰기와 저품질
페이지에 대한 비치명 진단이다. 렌더링 noise 0줄, short line 0줄, page coverage 100%, 품질 gate
`PASSED`, `ragIndexEligible=true`이므로 현재 튜터 사용 판정을 차단하지 않는다.

추출 중 `/pipeline/progress`가 page별 진행률을 제공하지 않는 관측성 한계는 남아 있다. 추출 결과나
검색 품질에는 영향을 주지 않지만 장시간 작업의 운영 상태 표시 개선 항목으로 분리한다.

## 검증 명령

```text
./gradlew :studio-platform-markdown:test :studio-platform-textract:test \
  :studio-platform-chunking-runtime:test \
  :starter:studio-platform-textract-starter:test \
  :starter:studio-platform-starter-markdown:test \
  :starter:studio-platform-starter-chunking:test --no-daemon
git diff --check
```
