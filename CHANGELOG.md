# Changelog

## 2026-03-20

### Docs
- `README.md` 에 Gradle wrapper JAR 누락 시 `gradle wrapper`로 복구하는 안내 추가
- `README.md` 를 저장소 인덱스 중심으로 축약하고 `LLM_DEVELOPMENT_GUIDE.md` 를 제거해 문서 중복을 줄임
- `README.md` 에 빠른 시작, 대표 starter 선택 기준, 자주 쓰는 secret, 모듈별 진입 포인트를 보강
- `README.md` 에 Java 17, Spring Boot 2.7.18 등 기술 기준 버전을 명시
- `starter/README.md` 와 `studio-application-modules/README.md` 를 인덱스형 문서 구조로 정리해 루트 README와 톤을 맞춤
- `CONFIGURATION_NAMESPACE_GUIDE.md` 를 실제 코드 기준으로 보정해 현재 구현 키와 향후 마이그레이션 대상을 구분
- `avatar-service`, `content-embedding-pipeline` 모듈에 최소 README를 추가해 문서 진입점을 보강

## 2026-03-09

### Security
- 권한 fail-open 기본 동작을 제거하고 `endpointAuthz` 미구성 시 deny-all 로 동작하도록 변경
- 첨부/템플릿 관리 API에 creator 기반 객체 단위 접근 통제 추가
- 메일 및 오브젝트 스토리지 운영 API를 관리자 전용으로 강화
- 고정 초기 비밀번호 reset 엔드포인트 제거
- Jasypt iteration 상향, Jasypt HTTP 기본 비활성화, 개발 설정의 하드코딩 secret 제거
- JWT, 메일, AI, 오브젝트 스토리지 필수 secret 누락 시 fail-fast guard 추가
- secret scan CI, `.env.example`, `.gitignore` 보강, 루트/Gradle 설정의 평문 secret 제거

### Tests
- attachment/template/mail/object storage/user 관리 권한 회귀 테스트 추가
- JWT/mail/AI/object storage secret guard 단위 테스트 추가

### Docs
- `SECURITY.md` 신설 수준으로 보강하여 secret 관리와 로컬 개발 규칙 문서화
- `README.md` 에 보안 문서 및 `.env.example` 포인터 추가
