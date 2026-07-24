# Studio Platform AI Model Catalog

채팅과 임베딩 모델의 provider 중립 메타데이터를 제공하는 Spring 비의존 catalog 모듈이다.
Catalog 등록은 모델을 실제 runtime에 활성화하지 않는다. 애플리케이션의 provider 연결과 adapter
capability 검증을 통과한 deployment만 사용할 수 있다.

## Catalog tier

- `MIGRATION_READY`: 현재 애플리케이션 설정을 그대로 옮길 수 있는 모델
- `ADAPTER_READY`: runtime mapping 또는 managed provider adapter로 연결할 수 있는 모델
- `REFERENCE_ONLY`: 조회용 정보이며 deployment 생성과 기본 routing에서 제외할 모델

## 모델 추가

1. `models-2026.07.23.json`에 canonical `catalogId`와 실제 API의 `apiModel`을 분리해 추가한다.
2. 공식 문서나 공식 model card의 `sourceUrl`과 확인한 날짜 `verifiedAt`을 기록한다.
3. open-weight 모델은 license와 artifact ID를 기록하고 `ADAPTER_READY`이면 검증할 runtime mapping도 추가한다.
4. 가격, quota, credential, 애플리케이션 base URL은 catalog에 저장하지 않는다.
5. 다음 검증을 실행한다.

```bash
./gradlew :studio-platform-ai:test :studio-platform-ai-model-catalog:test
```

Embedding 모델의 deployment는 `EmbeddingSpaceContract` 전체로부터 생성한 `embeddingSpaceId`를
사용해야 한다. 모델명이나 차원만 같다는 이유로 기존 vector를 호환 처리하면 안 된다.
