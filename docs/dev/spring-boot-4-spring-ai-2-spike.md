# Spring Boot 4.1 · Spring AI 2.0 호환성 스파이크

## 결론

2026-07-25 기준으로 정식 전환(PR 3)은 **보류**한다. 현재 기준선인 Spring Boot
3.5.16, Spring AI 1.1.8, Gradle 8.14.5는 전체 빌드와 DB 통합 테스트를 통과하지만,
Spring Boot 4.1.0과 Spring AI 2.0.0을 적용한 격리 스파이크는 전체 컴파일을 통과하지
못했다.

오류는 애플리케이션 로직보다 Boot 4의 모듈 분리, Jackson 3 기본 전환, 테스트 starter
분리에서 발생했다. 호환 bridge를 제품 artifact에 병합하지 않고, 아래 전환 목록과 실제
server compatibility matrix가 준비된 후 PR 3을 다시 시작한다. 이 결정은 Redis exact
RAG answer cache의 독립 구현을 막지 않는다.

## 스파이크 조건

- Java 17
- Gradle 8.14.5
- Spring Boot 4.1.0
- Spring AI 2.0.0
- MyBatis Spring Boot Starter 4.0.1
- 제품 worktree와 분리된 `.omx/spikes/boot4-ai2`
- 검증 명령:

```bash
./gradlew compileJava compileTestJava --continue --no-daemon
```

## 확인된 이동과 변경

| 기존 계약 | Boot 4 계약 또는 필요한 조치 | 영향 |
|---|---|---|
| `org.springframework.boot.autoconfigure.domain.EntityScanPackages` | `org.springframework.boot.persistence.autoconfigure.EntityScanPackages` | `studio-platform-autoconfigure` |
| `org.springframework.boot.autoconfigure.domain.EntityScan` | persistence 전용 모듈과 새 package 확인 후 import 전환 | object type, wiki test |
| `org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration` | persistence/JPA 전용 모듈과 새 package 확인 후 import 전환 | platform starter, wiki starter |
| `org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration` | JDBC 전용 모듈과 새 package 확인 후 import 전환 | MyBatis starter test |
| `org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration` | validation 전용 모듈과 새 package 확인 후 import 전환 | textract/thumbnail starter test |
| `org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest` | dedicated JPA test starter와 새 package 사용 | wiki service test |
| Jackson 2 `com.fasterxml.jackson.*` | Jackson 3 `tools.jackson.*` 또는 명시적 legacy bridge | document-convert, user, attachment 등 |
| `NoResourceFoundException(HttpMethod, String)` | 세 번째 인자를 받는 생성자 계약으로 테스트 수정 | `studio-platform` test |

`EntityScanPackages`는 `spring-boot-persistence:4.1.0`에 존재하며 새 package import로
해당 모듈의 main compile이 성공하는 것까지 확인했다.

## 전체 컴파일 실패 분류

### 1. Jackson 3 전환

`studio-platform-document-convert`, `studio-platform-user`,
`studio-application-modules:attachment-service`가 Jackson 2 classpath를 암묵적으로
기대한다. 단순 bridge 추가는 컴파일을 복구할 수 있지만 최종 전환 조건을 충족하지 못한다.

처리 원칙:

- HTTP, Redis, DB payload 경계는 Boot가 제공하는 application mapper를 주입한다.
- 공개 DTO annotation과 serializer는 Jackson 3 계약으로 한 번에 전환한다.
- legacy Redis/HTTP JSON을 읽어야 하는 경우에만 범위가 제한된 Jackson 2 reader를 둔다.
- `spring-boot-jackson2`는 조사용 bridge로만 사용하고 제품 runtime에는 남기지 않는다.

### 2. Boot 자동구성 모듈 분리

JPA, JDBC, validation, persistence 관련 자동구성 class가 기존
`spring-boot-autoconfigure` package/classpath에 남아 있지 않다. 각 starter가 실제로
소유하는 기술 모듈과 test starter를 명시해야 한다. class 이름만 바꾸는 방식으로 처리하지
않고 각 `AutoConfiguration.imports` context test에서 bean 조건을 재검증한다.

### 3. 테스트 API

JPA test slice와 일부 예외 생성자 계약이 변경됐다. main compile 복구와 별도로 dedicated
test starter 전환이 필요하다. 테스트를 제외해 제품 전환을 승인하지 않는다.

## Go/No-go 판정

현재 판정은 **No-go**다.

- OpenAI, Google GenAI, Ollama의 Spring AI 2 대체 API를 실제 adapter test로 확인하지 못했다.
- Jackson 3 전환 대상과 legacy payload 호환 fixture가 아직 완결되지 않았다.
- 실제 server instance의 필수 third-party library와 설정 migration 결과가 없다.
- repository 전체 main/test compile이 실패한다.

재개 조건:

1. Boot persistence/JDBC/validation/webmvc 전용 artifact와 package 매핑을 확정한다.
2. Jackson 3 전환 PR을 모듈 소유자 단위로 나누고 외부 payload fixture를 준비한다.
3. Spring AI 2 provider adapter compile 및 sync/SSE metadata parity를 확인한다.
4. PostgreSQL/MyBatis 4 통합 테스트를 통과한다.
5. 각 개발 server candidate의 ApplicationContext smoke 결과를 compatibility matrix에 기록한다.

그전까지 제품 기준선은 Boot 3.5.16/Spring AI 1.1.8을 유지하며, exact cache는 이
기준선의 Spring Data Redis로 구현한다.

## 2.1 호환 기준선 보존

Boot 4/Jackson 3 전환 전에 `2.1.0-rc.1` candidate를 아래 순서로 검증한다.

1. repository 전체 main/test build와 PostgreSQL/MySQL/MariaDB migration contract를 확인한다.
2. 개발 실행 서버를 Spring Boot 3.5.16과 `2.1.0-rc.1` artifact에 맞춘다.
3. ApplicationContext, chat, embedding, RAG sync/SSE를 확인한다.
4. exact cache의 MISS/HIT, revision 변경 miss, Redis fail-open을 확인한다.
5. 동일 소스를 `2.1.0`으로 승격해 `2.x`에 병합한 뒤 annotated tag `v2.1.0`을 생성한다.

`2.x`는 Boot 3 호환 유지보수 브랜치로 남긴다. Boot 4.1, Spring AI 2.0,
Jackson 3 전환은 `v2.1.0`에서 분기한 별도 `3.x` 또는 `upgrade/boot4-ai2`
브랜치에서 수행하며 `2.x`에 병합하지 않는다.
