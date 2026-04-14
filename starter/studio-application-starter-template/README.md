# studio-application-starter-template

템플릿 서비스(FreeMarker 렌더링)를 자동으로 구성하는 스타터이다.
`studio-application-modules:template-service` 모듈의 서비스/리포지토리 빈을 등록하고,
선택적으로 CRUD + 렌더링 REST 엔드포인트를 노출한다.

## 1) 의존성 추가

```kotlin
dependencies {
    implementation(project(":starter:studio-application-starter-template"))
    // REST 엔드포인트를 사용할 때
    implementation("org.springframework.boot:spring-boot-starter-web")
    // JPA 영속성 사용 시
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    // FreeMarker 렌더링 사용 시 (버전은 gradle.properties의 freemarkerVersion 참고)
    implementation("org.freemarker:freemarker")
}
```

## 2) 기능 활성화

```yaml
studio:
  features:
    template:
      enabled: true
```

## 3) 설정

`studio.features.template.*` 속성으로 동작을 제어한다.

```yaml
studio:
  features:
    template:
      enabled: true            # 필수: 모듈 활성화
      persistence: jpa         # jpa | jdbc (기본: 전역 studio.persistence.type 또는 jpa)
      web:
        enabled: true          # REST 엔드포인트 노출 여부 (기본 true)
        base-path: /api/mgmt/templates
```

### FreeMarker 렌더링 연동

Spring의 `FreeMarkerConfig` 빈 또는 `freemarker.template.Configuration` 빈이 컨텍스트에 있으면
자동으로 `FreemarkerTemplateBuilder`에 주입된다. 두 빈이 모두 없어도 기본 구성으로 동작하나,
클래스패스 기반 템플릿 로딩 등 고급 기능은 FreeMarker 설정이 필요하다.

```yaml
spring:
  freemarker:
    template-loader-path: classpath:/templates
    suffix: .ftl
    enabled: true
```

## 4) 자동 구성되는 주요 빈

| 빈 | 클래스 | 조건 |
|----|--------|------|
| `TemplatesService` | `TemplatesServiceImpl` | `enabled=true` |
| `FreemarkerTemplateBuilder` | `FreemarkerTemplateBuilder` | `enabled=true` |
| `TemplateJpaPersistenceRepository` | `TemplateJpaPersistenceRepository` | persistence=jpa |
| `TemplateJdbcRepository` | `TemplateJdbcRepository` | persistence=jdbc |
| `TemplateMgmtController` | `TemplateMgmtController` | `web.enabled=true` (기본) |

- JPA 사용 시 `EntityManagerFactory`가 필요하다.
- JDBC 사용 시 `NamedParameterJdbcTemplate` 빈이 필요하다.

## 5) REST 엔드포인트

`studio.features.template.web.enabled=true`(기본값) 설정 시 아래 엔드포인트가 등록된다.

기본 base-path: `/api/mgmt/templates`

| 메서드 | 경로 | 설명 | 권한 |
|--------|------|------|------|
| `POST` | `/` | 템플릿 생성 (`objectType`, `objectId`, `name` 필수) | `features:template/write` |
| `GET` | `/{templateId}` | 단건 조회 | `features:template/read` |
| `GET` | `/` | 페이지 목록 (`q`, `fields` 선택) | `features:template/read` |
| `GET` | `/name/{name}` | 이름으로 조회 | `features:template/read` |
| `PUT` | `/{templateId}` | 전체 수정 | `features:template/write` |
| `DELETE` | `/{templateId}` | 삭제 | `features:template/delete` |
| `POST` | `/{templateId}/render/body` | 본문 렌더링 (요청 바디: `Map<String,Object>` 모델) | `features:template/read` |
| `POST` | `/{templateId}/render/subject` | 제목 렌더링 (요청 바디: `Map<String,Object>` 모델) | `features:template/read` |

목록 조회(`GET /`)는 `ADMIN` 역할이면 전체 템플릿을, 일반 사용자는 본인이 생성한 템플릿만 반환한다.
이름 기반 조회(`GET /name/{name}`)도 동일한 권한 분기 로직이 적용된다.

검색 가능 필드(`fields` 파라미터): `name`, `displayName`, `description`, `subject`, `body`

### 렌더링 요청 예시

```bash
curl -X POST "/api/mgmt/templates/1/render/body" \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{"username": "홍길동", "title": "공지사항"}'
```

## 6) template-service 모듈과의 관계

이 스타터는 `studio-application-modules:template-service` 모듈에 `api` 의존성으로 연결된다.
template-service 모듈은 도메인 모델(`Template`, `TemplateEntity`)과 서비스/리포지토리 인터페이스 및
FreeMarker 기반 렌더링 구현체를 제공하며, 스타터가 실제 빈을 자동 구성한다.

## 7) 참고 사항

- `studio.features.template.enabled=false`로 전체 비활성화할 수 있다.
- 템플릿 이름(`name`)은 전체 유니크 제약이 있다.
- 스키마 마이그레이션 파일은 `template-service/src/main/resources/schema/template/{db}/V900__create_template_tables.sql`에 위치한다.
- 권한 스코프(`features:template/read`, `features:template/write`, `features:template/delete`)를 인가 서버 또는 ACL에 등록해야 한다.
- `PrincipalResolver` 빈이 없으면 목록/이름 조회 엔드포인트에서 인증 오류가 발생한다. `studio-platform-starter`의 identity 자동구성이 켜져 있어야 한다.
