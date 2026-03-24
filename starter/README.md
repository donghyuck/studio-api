# Studio Starters

애플리케이션에서 공통 기능을 빠르게 활성화하기 위한 Spring Boot starter 모음이다. 각 starter는 `studio.features.*` 또는 `studio.*` 설정으로 켜고 끌 수 있으며, 필요한 기능만 선택해서 붙이는 것을 전제로 한다.

## 빠른 선택 가이드
- 공통 웹/데이터/JPA 기반이 필요하면 `:starter:studio-platform-starter`
- 인증/인가가 필요하면 `:starter:studio-platform-starter-security`
- 사용자 기본 구현까지 필요하면 `:starter:studio-platform-starter-user`
- ACL이 필요하면 `:starter:studio-platform-starter-security-acl`
- 첨부/아바타/메일은 각 application starter를 추가

최소 예시:

```kotlin
dependencies {
    implementation(project(":starter:studio-platform-starter"))
    implementation(project(":starter:studio-platform-starter-security"))
}
```

## 사용 원칙
- feature 플래그가 `true`일 때만 빈이 등록된다.
- REST 엔드포인트를 노출하는 모듈은 `...web.enabled`로 비활성화할 수 있다.
- 파일/오브젝트 스토리지를 쓰는 모듈은 경로와 자격 증명을 먼저 확인한다.

## 포함 starter
- `studio-platform-starter`: 코어 플랫폼 자동 구성, 공통 유틸, 기본 프로퍼티 바인딩
- `studio-platform-starter-security`: Spring Security 기본 구성과 인증/인가 훅
- `studio-platform-starter-security-acl`: ACL 엔티티/리포지토리 스캔과 ACL 연동
- `studio-platform-starter-user`: 사용자 도메인 서비스와 기본 REST 구성
- `studio-platform-starter-ai`: OpenAI/Spring AI, 벡터스토어, RAG 등 AI core 구성
- `studio-platform-starter-ai-web`: AI HTTP endpoint와 JSON component 노출
- `studio-platform-starter-jasypt`: Jasypt 암호화/복호화 지원
- `studio-platform-starter-objectstorage`, `-aws`, `-oci`: 오브젝트 스토리지 공통 및 provider별 구성
- `studio-application-starter-attachment`, `-avatar`, `-template`, `-mail`: 애플리케이션 기능 모듈 자동 구성

## 문서 바로가기
- 루트 개요: `../README.md`
- 애플리케이션 모듈 가이드: `../studio-application-modules/README.md`
- 사용자 starter 상세: `studio-platform-starter-user/README.md`
- Jasypt starter 상세: `studio-platform-starter-jasypt/README.md`
- Mail starter 상세: `studio-application-starter-mail/README.md`
