# Microsoft Presidio PII Masking 서비스

이 디렉토리는 외부 LLM(OpenAI, Gemini 등) API 호출 전후에 개인정보(PII - 주민등록번호, 연락처, 이메일, 이름 등)를 자동으로 감지하여 가명화(Anonymize)하고 역치환(Deanonymize)할 수 있는 **Microsoft Presidio**의 로컬 컨테이너 배포 구성 패키지입니다.

기본적으로 한국어 NLP 엔진(`SpaCy ko_core_news_sm`)을 포함해 도커 이미지로 빌드되어 작동합니다.

---

## 1. 디렉토리 구성

* `docker-compose.yml`: Analyzer와 Anonymizer 서비스를 묶어 오케스트레이션합니다.
* `Dockerfile`: Presidio Analyzer 이미지에 한국어 패키지를 내려받아 설치하고 커스텀 설정을 복사합니다.
* `config.yaml`: Analyzer에 한국어 엔진을 활성화하도록 매핑하는 프로필입니다.

---

## 2. 손쉬운 설치 및 시작 방법

도커가 구동 중인 터미널에서 다음 명령어를 실행하여 컨테이너를 빌드하고 백그라운드에서 실행합니다.

```bash
# 1. tools/presidio-pii-masking 디렉토리로 이동
cd tools/presidio-pii-masking

# 2. 컨테이너 빌드 및 실행
docker compose up --build -d
```

실행 완료 후, 아래의 포트가 리슨(LISTEN) 상태로 열립니다:
- **Analyzer API**: `http://localhost:5002`
- **Anonymizer API**: `http://localhost:5001`

---

## 3. 작동 확인 (cURL API 테스트)

### 1단계: PII 분석 (Analyze)
텍스트 내 개인정보 유출 요소의 유형과 문자열 인덱스 범위를 식별합니다.

```bash
curl -X POST http://localhost:5002/analyze \
  -H "Content-Type: application/json" \
  -d '{
    "text": "인사 담당자 홍길동(850101-1234567)의 이메일은 gd@example.com 입니다.",
    "language": "ko"
  }'
```

### 2단계: 가명화 (Anonymize)
분석 좌표 및 원문을 입력받아 지정된 마스킹/암호화 방식으로 텍스트를 대체합니다.

```bash
curl -X POST http://localhost:5001/anonymize \
  -H "Content-Type: application/json" \
  -d '{
    "text": "인사 담당자 홍길동(850101-1234567)의 이메일은 gd@example.com 입니다.",
    "anonymizers": {
      "DEFAULT": {
        "type": "replace",
        "value": "[PERSONAL_INFO_MASKED]"
      }
    },
    "analyzer_results": [
      {
        "start": 8,
        "end": 11,
        "score": 0.85,
        "entity_type": "PERSON"
      },
      {
        "start": 12,
        "end": 26,
        "score": 1.0,
        "entity_type": "SSN"
      },
      {
        "start": 33,
        "end": 47,
        "score": 1.0,
        "entity_type": "EMAIL_ADDRESS"
      }
    ]
  }'
```

---

## 4. RAG / Blockify 연동 가이드

1. **Spring Boot (WebClient)**를 활용해 LLM 호출 전단에서 `http://localhost:5002/analyze` -> `http://localhost:5001/anonymize` 순차 호출을 처리합니다.
2. 가명화 결과(Masked Text)와 함께 반환된 가명화 규칙(암호 키 등)을 캐시 혹은 임시 세션 메모리에 맵 구조로 적재합니다.
3. LLM API 응답물(Q&A)을 가져온 후, 해당 암호 키를 가지고 `http://localhost:5001/deanonymize` API를 호출해 `[PERSONAL_INFO_MASKED]` 영역을 원래 이름/번호 정보로 정상 복원하여 로컬 DB(tb_ai_document_chunk)에 저장합니다.
