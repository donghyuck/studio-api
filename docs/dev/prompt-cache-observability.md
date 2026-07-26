# Prompt cache 관측과 활성화 기준

## 현재 범위

현재 서버는 provider가 반환한 prompt cache token을 `PromptCacheUsage`로 정규화한다.
요청 body, RAG prompt 순서, 검색, 청킹, 임베딩은 변경하지 않는다.

- Google GenAI: `cachedContentTokenCount`를 cache read token으로 사용한다.
- OpenAI-compatible: 응답의 cached/write token 필드가 모두 있으면 완전한 billing bucket으로 사용한다.
- 필드가 일부만 있으면 `PARTIAL`로 노출하고 cache-aware 비용을 추정하지 않는다.
- sync 응답과 SSE `complete`는 같은 metadata 계약을 사용한다.
- 일반 채팅과 RAG는 `requestKind`로 분리 집계한다.

## 설정

일반 input/output 단가 외에 실제 provider 가격표의 cache read/write 단가를 설정한다.
가격은 모델 카탈로그에 고정하지 않고 서버 설정에서 관리한다.

```yaml
studio:
  ai:
    usage:
      pricing:
        "[provider/model]":
          input-per-million-tokens: 1.0
          output-per-million-tokens: 4.0
          cache-read-input-per-million-tokens: 0.1
          cache-write-input-per-million-tokens: 1.25
```

단가 또는 완전한 token bucket이 없으면 `pricingConfigured=false`이며 비용을 추정하지 않는다.
기본 usage store는 인스턴스 로컬 메모리이므로 재시작 후 초기화된다.

## OSS cache 선택

- Hosted provider는 provider-native prompt caching을 사용한다.
- Self-hosted vLLM은 애플리케이션 라이브러리가 아니라 inference server에서 APC를 먼저 평가한다.
- LMCache는 다중 worker 또는 worker 간 KV 재사용 필요가 측정된 뒤 평가한다.
- LiteLLM, GPTCache, Redis response cache는 RAG 답변·인용·ACL·revision 정합성 때문에 사용하지 않는다.

## 후속 활성화 게이트

provider-specific cache control은 다음 조건을 만족한 뒤 별도 변경으로 추가한다.

- cache metadata 매핑 성공률 95% 이상
- cache-read token 비율 20% 이상
- 입력 비용 10% 이상 절감 또는 SSE p95 TTFT 15% 이상 개선
- 오류율 증가 0.5%p 이하
- RAG citation/reference 및 validation 회귀 0건

metric과 로그에는 prompt, 질의, 문서 제목, object ID, principal, raw cache key를 기록하지 않는다.
