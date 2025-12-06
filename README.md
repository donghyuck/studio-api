# Studio One Platform

모듈화된 Spring Boot 기반 백엔드 플랫폼. 인증/인가, 사용자/그룹 관리, 파일·첨부 관리, AI 임베딩/RAG 파이프라인을 공통 컴포넌트와 스타터로 제공한다.

## 레포지토리 구성
```
starter/                         # Spring Boot 스타터 모음 (자동 구성)
studio-application-modules/      # 애플리케이션 기능 모듈 (attachment, avatar, embedding pipeline)
studio-platform/                 # 코어 플랫폼 라이브러리
studio-platform-ai/              # AI 포트/서비스/컨트롤러
studio-platform-autoconfigure/   # 공통 자동 구성
studio-platform-data/            # 데이터 액세스 공통
studio-platform-security(+acl)/  # 보안 + ACL
studio-platform-user/            # 사용자/그룹/역할/회사 도메인
```

## 주요 모듈
- **studio-platform**: 공통 유틸, 도메인 베이스, 예외/텍스트/웹 지원.
- **studio-platform-security / security-acl**: JWT 인증, 계정 잠금, 로그인 감사, ACL 기반 인가.
- **studio-platform-user**: 사용자/그룹/역할/회사 도메인, `/api/mgmt` 사용자 관리 REST.
- **studio-platform-ai**: 임베딩/벡터스토어/RAG 포트와 REST 컨트롤러.
- **studio-application-modules**
  - `attachment-service`: 첨부 메타/바이너리 저장, 업로드/다운로드/검색 REST.
  - `avatar-service`: 사용자 아바타 이미지 관리.
  - `content-embedding-pipeline`: 첨부 텍스트 추출→임베딩→벡터 스토어 업서트/RAG 인덱스 API.

모듈별 상세 가이드는 `studio-application-modules/README.md` 참고.

## 스타터
각 기능은 대응되는 스타터를 추가하면 자동 구성된다. 요약은 `starter/README.md` 참고.
예시:
```kotlin
dependencies {
    implementation(project(":starter:studio-platform-starter"))          // 플랫폼 기본
    implementation(project(":starter:studio-platform-starter-security")) // 보안
    implementation(project(":starter:studio-platform-starter-user"))     // 사용자
    implementation(project(":starter:studio-application-starter-attachment")) // 첨부
}
```

## 빌드
```bash
./gradlew clean build
```
모듈은 라이브러리 형태로 배포되며, 스타터를 사용하는 애플리케이션에서 의존성을 추가해 실행한다.

## 기본 설정 예시
```yaml
studio:
  persistence:
    type: jpa            # jpa|jdbc
  features:
    attachment:
      enabled: true
      web:
        enabled: true
        base-path: /api/mgmt/attachments
      storage:
        type: filesystem # filesystem|database
        cache-enabled: false
    avatar-image:
      enabled: true
    user:
      enabled: true
      web:
        enabled: true
    security-acl:
      enabled: true
  ai:
    enabled: true
    default-provider: openai
    providers:
      - name: openai
        type: OPENAI
        api-key: ${OPENAI_API_KEY}
        embedding:
          model: text-embedding-3-small
```
필요 없는 기능은 `enabled=false` 로 비활성화하고, 경로나 저장소 타입은 `studio.features.<feature>.*` 속성으로 조정한다.

## 문서 바로가기
- 스타터 요약: `starter/README.md`
- 애플리케이션 모듈 가이드: `studio-application-modules/README.md`
- 첨부 모듈 상세: `studio-application-modules/attachment-service/README.md`
