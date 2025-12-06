# Studio Starters

어플리케이션에서 공통 기능을 빠르게 활성화하기 위한 Spring Boot starter 모음이다. 멀티모듈 환경에서는 아래와 같이 의존성을 추가하면 된다.

```kotlin
dependencies {
    implementation(project(":starter:studio-platform-starter"))       // 예시
}
```

각 스타터는 `studio.features.*` 또는 `studio.*` 프로퍼티로 동작을 제어하며, 필요 시 REST 엔드포인트 노출 여부를 별도 플래그로 끌 수 있다.

## 플랫폼 계층
- **studio-platform-starter**: 코어 플랫폼 자동구성(웹/데이터/JPA 설정, 공통 유틸, i18n 등)과 기본 프로퍼티 바인딩을 제공.
- **studio-platform-starter-security**: Spring Security 기본 설정과 인증/인가 훅을 제공.
- **studio-platform-starter-security-acl**: ACL 엔티티/리포지토리 스캔 및 ACL 필터링 기능을 추가. `studio.features.security-acl.*` 프로퍼티로 제어.
- **studio-platform-starter-user**: 사용자 도메인 서비스와 기본 REST(옵션)를 제공, `studio.features.user.*` 관련 플래그로 활성화.
- **studio-platform-starter-ai**: 임베딩/벡터스토어/RAG 파이프라인용 포트와 기본 빈을 제공. AI 엔드포인트/서비스를 구성할 때 필요한 기반.
- **studio-platform-starter-jasypt**: Jasypt 기반 암호화/복호화 지원 및 관련 프로퍼티 바인딩을 제공.

## 오브젝트 스토리지
- **studio-platform-starter-objectstorage**: 공통 ObjectStorage 인터페이스와 로컬/기본 구현 자동구성.
- **studio-platform-starter-objectstorage-aws**: AWS S3용 ObjectStorage 구현과 프로퍼티 바인딩.
- **studio-platform-starter-objectstorage-oci**: Oracle OCI ObjectStorage 구현과 프로퍼티 바인딩.

## 애플리케이션 모듈 스타터
- **studio-application-starter-attachment**: 첨부파일 메타데이터/바이너리 저장, 업로드/다운로드 REST 컨트롤러 자동구성. `studio.features.attachment.enabled`(+`web.enabled`)로 활성화, 저장소 타입/경로는 `studio.features.attachment.storage.*`.
- **studio-application-starter-avatar**: 아바타 이미지 관리 서비스와 (선택) REST 엔드포인트 자동구성. `studio.features.avatar-image.enabled` 로 기능 활성화, 저장소/복제 경로는 `studio.features.avatar-image.*`.

## 사용 팁
- 스타터는 필요 기능만 의존성에 추가하면 되며, feature 플래그가 `true` 일 때만 빈을 등록한다.
- REST 엔드포인트를 사용하는 모듈(attachment/avatar/user 등)은 `...web.enabled` 프로퍼티를 확인해 비활성화할 수 있다.
- 파일/오브젝트 스토리지를 사용하는 모듈은 `base-dir` 또는 클라우드 자격 증명 등 환경 변수 구성을 먼저 점검한다.
