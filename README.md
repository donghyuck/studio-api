# Studio One Platform

[![release](https://img.shields.io/badge/release-1.0-blue.svg)](https://github.com/metasfresh/metasfresh/releases/tag/5.175)
[![license](https://img.shields.io/badge/license-APACHE-blue.svg)](https://github.com/metasfresh/metasfresh/blob/master/LICENSE.md)

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

## 사용자 도메인 커스터마이즈 가이드
기본 `ApplicationUser` 대신 커스텀 User 구현을 쓰려면 다음 단계를 따른다.
- 엔티티/리포지토리 스캔을 별도 패키지로 지정: `studio.features.user.entity-packages`, `repository-packages`, `jdbc-repository-packages`를 커스텀 경로로 설정해 기본 엔티티와 분리한다.
- 리포지토리 교체: `ApplicationUserRepository` 인터페이스를 구현한 커스텀 JPA/JDBC 리포지토리를 빈으로 제공하면 기본 구현은 `@ConditionalOnMissingBean`으로 등록되지 않는다.
- Mutator 교체: 비밀번호/잠금/활성화 필드 접근을 커스텀 엔티티에 맞추려면 `UserMutator<YourUser>` 빈을 등록해 기본 `ApplicationUserMutator`를 대체한다.
- 서비스/매퍼 교체: 기본 `ApplicationUserServiceImpl`가 `ApplicationUser`에 묶여 있으므로, 필요 시 같은 인터페이스를 구현한 서비스를 제공하거나 매퍼(`ApplicationUserMapper`)를 커스텀 엔티티용으로 `@Primary`로 등록한다.
- 스캔 분리나 제외 필터로 충돌 방지: 기존 패키지와 겹치지 않는 루트 패키지를 사용하고, 필요 시 `@EnableJpaRepositories`/`@ComponentScan`의 `excludeFilters`로 기본 스캔에서 제외한다.
- 제외 프로퍼티: `studio.features.user.exclude-entity-packages`, `...exclude-repository-packages`, `...exclude-jdbc-repository-packages`에 정규식 패턴을 넣으면 해당 패턴과 매칭되는 엔티티/리포지토리 빈 등록을 건너뛸 수 있다.
- 샘플 모듈: `studio-application-modules/custom-user-service`에는 `CustomUser` 엔티티, JPA/JDBC 리포지토리, `CustomUserMutator`, `CustomUserAutoConfiguration`이 포함되어 있다. 이 모듈을 의존성에 추가하고 `studio.features.user.*` 설정을 커스텀 패키지로 지정하면 기본 사용자 구현 없이 커스텀 User가 등록된다.

### 커스텀 User 엔티티/테이블 설계 필수 항목
- 인터페이스 필드: `userId`, `username`(유니크), `name/firstName/lastName`, `password`(hash 저장), `email`(유니크), `enabled`, `external`, `status`(enum), `failedAttempts`, `lastFailedAt`, `accountLockedUntil`, `creationDate`, `modifiedDate`, `nameVisible`, `emailVisible`, `properties(Map<String,String>)`.
- 테이블 예시:
  - `TB_APPLICATION_USER`(또는 커스텀 명): PK `USER_ID`(IDENTITY), `USERNAME` UNIQUE, `EMAIL` UNIQUE, `PASSWORD_HASH`, `USER_ENABLED`, `USER_EXTERNAL`, `STATUS`(INT), `FAILED_ATTEMPTS`, `LAST_FAILED_AT`, `ACCOUNT_LOCKED_UNTIL`, `CREATION_DATE`, `MODIFIED_DATE`, `NAME_VISIBLE`, `EMAIL_VISIBLE`, 기타 이름 필드.
  - `TB_APPLICATION_USER_PROPERTY`(선택): `USER_ID`, `PROPERTY_NAME`, `PROPERTY_VALUE` (USER_ID+PROPERTY_NAME PK).
  - 역할/그룹 매핑: `TB_APPLICATION_USER_ROLES`(USER_ID, ROLE_ID), `TB_APPLICATION_GROUP_MEMBERS`(GROUP_ID, USER_ID) 등은 User 엔티티를 ID 기반으로 연결.
- JDBC/JPA 구현 모두 동일한 스키마 전제로 작성되어 있으니 커스텀 테이블을 사용할 때는 리포지토리 쿼리도 함께 조정한다.
