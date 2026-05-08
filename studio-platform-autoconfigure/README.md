# studio-platform-autoconfigure

플랫폼 전반에 걸쳐 반복 사용되는 공통 자동 구성을 모은 모듈이다. JPA 감사(Auditing), 영속성 타입 설정, i18n, 피처 플래그 조건, 커스텀 `@Conditional` 어노테이션을 제공한다.
이 모듈은 단독으로 사용하지 않으며, `studio-platform-starter`가 포함해 자동 구성 경로에 등록한다.

## 요약
애플리케이션 공통 인프라(감사자 주입, 영속성 전략 선택, 기능 토글)를 YAML 프로퍼티 한 곳에서 제어할 수 있도록 표준화한다.

## 설계
- `CompositeAuditorAware`: security / header / fixed 전략을 순서대로 시도하는 합성 감사자
- `JpaAuditingProperties`: 감사 활성화, 클럭 타임존, 감사자 전략(security|header|fixed|composite) 설정
- `PersistenceProperties`: 플랫폼 전역 영속성 타입(`jpa` | `mybatis` | `jdbc`) 선택. `jdbc`는 기존 직접 JDBC 구현을 위한 2.x 호환 값이다.
- `PersistenceTypeResolver`: 신규/전환된 MyBatis-aware 경로에서 사용할 normalize helper다. `studio.features.<feature>.persistence`를 우선하고, 없으면 `studio.persistence.type`을 사용한다. 이 resolver를 쓰는 경로에서 `jdbc`는 deprecated alias로 `mybatis`에 normalize된다.
- `FeaturesProperties`: 피처 플래그(enabled, failIfMissing, persistence) 및 동적 `others` 맵 관리
- `ConditionalOnProperties`: 여러 프로퍼티 조건을 AND로 결합하는 커스텀 `@Conditional` 어노테이션
- `ConditionalOnClassPresence` / `ConditionalOnMissingClass`: 클래스 존재 여부 기반 커스텀 조건

## 주요 구성 요소

| 클래스 | 패키지 | 설명 |
|---|---|---|
| `JpaAuditingProperties` | `autoconfigure` | `studio.persistence.jpa.auditing.*` 바인딩 |
| `PersistenceProperties` | `autoconfigure` | `studio.persistence.type` 바인딩 |
| `StudioPersistenceType` / `PersistenceTypeResolver` | `autoconfigure.persistence` | jpa/mybatis/jdbc alias 해석과 normalize |
| `FeaturesProperties` | `autoconfigure` | `studio.features.*` 바인딩 |
| `CompositeAuditorAware` | `persistence.jpa.auditor` | security→header→fixed 순 감사자 합성 |
| `ConditionalOnProperties` | `condition` | 복수 프로퍼티 AND 조건 어노테이션 |
| `ConditionalOnClassPresence` | `condition` | 클래스 존재 시 조건 등록 어노테이션 |
| `ConditionalOnMissingClass` | `condition` | 클래스 부재 시 조건 등록 어노테이션 |

## YAML 예시
```yaml
studio:
  persistence:
    type: jpa                        # jpa | mybatis | jdbc (기본: jpa)
    jpa:
      auditing:
        enabled: true
        clock:
          zone-id: Asia/Seoul
        auditor:
          strategy: composite        # security | header | fixed | composite
          header: X-Actor
          fixed: system
          composite:
            - security
            - header
            - fixed
        fallback:
          pre-persist: true
          pre-update: true

  features:
    others:
      my-feature:
        enabled: true
        fail-if-missing: false
        persistence: jpa
        attrs:
          custom-key: custom-value
```

기존 직접 JDBC 구현이 남아 있는 starter 조건부 등록은 raw `jdbc` 값을 사용한다. `PersistenceTypeResolver`는
이 resolver를 명시적으로 주입해 사용하는 MyBatis-aware 경로에서만 deprecated `jdbc` alias를 `mybatis`로
normalize한다. `@ConditionalOnFeaturePersistence` 기반 starter 조건은 해당 feature의 실제 지원 구현에 맞춰
`jpa`, `mybatis`, `jdbc` 값을 직접 비교한다.

전환이 완료되지 않은 feature가 함께 켜진 애플리케이션에서는 전역 `studio.persistence.type=mybatis`를
무조건 적용하지 않는다. MyBatis 구현이 추가된 feature는 `studio.features.<feature>.persistence=mybatis`를
명시하고, 직접 JDBC 호환 경로를 써야 하는 feature는 `studio.features.<feature>.persistence=jdbc`를 유지한다.

## `@ConditionalOnProperties` 사용 예시
```java
@Configuration
@ConditionalOnProperties(
    prefix = "studio.features.my-feature",
    value = {
        @ConditionalOnProperties.Property(name = "enabled", havingValue = "true"),
        @ConditionalOnProperties.Property(name = "fail-if-missing", havingValue = "false")
    }
)
public class MyFeatureConfiguration {
    // ...
}
```

## 주의
이 모듈은 단독으로 사용하지 않는다. `studio-platform-starter` 또는 기능별 스타터를 통해 자동 구성 경로에 포함된다.

`starter/studio-platform-starter`에는 과거 오타 패키지 경로인 `perisitence`와
`studio-platform-autoconfigure`의 `perisistence` 호환 경로가 일부 남아 있다.
신규 코드와 문서 예시는 정상 패키지명 `persistence`를 기준으로 작성한다. 기존 오타
경로는 호환을 위한 bridge로만 유지한다.

## 관련 모듈
- `starter/studio-platform-starter` — 이 모듈을 포함해 플랫폼 공통 자동 구성을 활성화
- `studio-platform-security` — `ConditionalOnProperties`/감사 설정 활용
- `studio-platform-data` — `PersistenceProperties`로 영속성 전략 분기
