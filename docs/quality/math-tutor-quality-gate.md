# Math Tutor Document Quality Gate

## 목적

한글 수학 교재 PDF의 Markdown 변환 결과가 단순 열람용을 넘어 구조화 청킹, 검색, 근거 기반 튜터 질의응답에 사용할 수 있는지를 판정한다. 기준 문서는 attachment `6`이며, 최종 판정은 동일 원본에서 새로 생성한 revision을 대상으로 수행한다.

## 합격 조건

모든 필수 게이트를 동시에 통과해야 `TUTOR_READY`로 판정한다.

| 영역 | 지표 | 합격 기준 |
|---|---|---:|
| Markdown | `markdownQualityScore` | `>= 0.80` |
| Markdown | 심각한 OCR 깨짐 줄 비율 | `<= 0.5%` |
| Markdown | 문제 번호와 선택지 보존율 | `>= 95%` |
| Markdown | 표본 수식 의미 일치율 | `>= 90%` |
| Markdown | 표본 개념 본문 복원율 | `>= 95%` |
| Provenance | 검색 가능 block의 page/sourceRef coverage | `100%` |
| Index | `ragIndexEligible` | `true` |
| Retrieval | 표본 질문 `Recall@5` | `>= 90%` |
| Tutor QA | 표본 질문 핵심 답변 정확도 | `>= 85%` |
| Tutor QA | 답변 page/sourceRef 인용률 | `>= 95%` |

## 평가 규칙

- OCR 깨짐은 한글 자모 단독 사용, 무의미한 Latin/icon 문자열, 손상된 문제 번호, 비정상 반복 기호를 포함한다.
- 수식 평가는 최소 20개를 페이지 전반에서 층화 표본으로 선정한다. 연산자, 지수, 괄호, 변수와 등호 관계가 원문과 의미상 일치해야 한다.
- 개념 본문은 최소 20개 문단을 표본으로 선정하며 핵심 용어와 문장 의미가 보존되어야 한다.
- Retrieval 평가는 최소 30개 질문을 사용한다. 개념, 공식, 문제 조건, 풀이 단서 질문을 고르게 포함한다.
- Tutor QA는 Retrieval 평가와 같은 질문을 사용하되, 정답만 맞고 근거 위치가 없는 답변은 인용률에서 실패로 처리한다.
- 자동 지표는 원문 페이지 표본 검토로 보완한다. 자동 점수만으로 `TUTOR_READY`를 부여하지 않는다.

## 실패 처리

- 문서 품질 또는 provenance가 실패하면 색인 전에 extraction/normalization을 수정하고 새 revision으로 재평가한다.
- Retrieval만 실패하면 chunking 또는 embedding 설정을 조정하고 동일 revision을 재색인한다.
- Tutor QA만 실패하면 retrieval context 구성과 답변 지시를 조정한다.
- 치명적이지 않은 경고라도 합격 기준에 영향을 주면 `REVIEW_REQUIRED`를 유지한다.

## 증적

최종 보고서에는 revision ID, extraction route, OCR/math provider, 처리 시간, 각 지표의 분자/분모, 실패 표본, 검색 평가 job ID와 사용한 질문 집합을 기록한다.
