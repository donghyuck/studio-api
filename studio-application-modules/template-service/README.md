# Template Service

도메인 객체(`objectType`/`objectId`)에 연결된 제목·본문 텍스트 템플릿을 저장하고 FreeMarker로 렌더링하는 모듈이다. `studio-application-starter-template` 의존성을 추가하면 JPA/JDBC 구현과 REST 엔드포인트가 자동 등록된다.

## 구성 요소
- **TemplatesService**: 템플릿 생성·조회·수정·삭제와 제목/본문 렌더링을 담당(퍼시스턴스 구현은 `TemplatePersistenceRepository`가 책임).
- **TemplateController**: CRUD 및 렌더링 REST API 제공. 경로는 `studio.features.template.web.base-path` (기본 `/api/mgmt/templates`).
- **TemplateEntity/DefaultTemplate**: 템플릿 도메인 모델. `properties` 맵으로 임의 속성 저장.
- **FreemarkerTemplateBuilder**: 서블릿 컨텍스트/정적 모델을 포함해 FreeMarker 템플릿 처리 유틸리티.

## 자동구성 및 프로퍼티
`studio.features.template.*` 로 활성화 및 웹 노출을 제어한다.

```yaml
studio:
  features:
    template:
      enabled: true            # 모듈 활성화 (기본 true, 미설정 시 자동구성됨)
      persistence: jdbc        # template 전용 설정 (없으면 studio.persistence.type 사용)
      web:
        enabled: false         # REST 엔드포인트 노출 여부 (기본 false)
        base-path: /api/mgmt/templates
  persistence:
    type: jpa                  # jpa | jdbc (template.persistence 미설정 시 사용)
```

- `persistence.type` 에 따라 JPA(`TemplateJpaRepository`) 또는 JDBC 구현을 선택한다.
- 웹을 켜면 `TemplateController` 가 등록되며, `@endpointAuthz.can('features:template','<action>')` 스코프로 보호된다.

## REST API (기본 base-path: `/api/mgmt/templates`)
- `POST /` 생성: `objectType`(int), `objectId`(long), `name`(unique), `displayName`, `description`, `subject`, `body`, `properties` 맵을 입력. 권한 `features:template/write`.
- `GET /{templateId}` 조회, `GET /name/{name}` 이름으로 조회. 권한 `features:template/read`.
- `PUT /{templateId}` 수정. 권한 `features:template/write`.
- `DELETE /{templateId}` 삭제. 권한 `features:template/delete`.
- `POST /{templateId}/render/body` | `POST /{templateId}/render/subject`: 모델(Map)을 받아 FreeMarker로 렌더링된 문자열 반환. 권한 `features:template/read`.

## 데이터 모델
- **TB_APPLICATION_TEMPLATE**: `TEMPLATE_ID`(PK), `OBJECT_TYPE`, `OBJECT_ID`, `NAME`(UNIQUE), `DISPLAY_NAME`, `DESCRIPTION`, `SUBJECT`, `BODY`, `CREATED_BY`, `UPDATED_BY`, `CREATED_AT`, `UPDATED_AT`.
- **TB_APPLICATION_TEMPLATE_PROPERTY**: 템플릿별 속성 맵(`PROPERTY_NAME`/`PROPERTY_VALUE`), `TEMPLATE_ID` FK, PK `(TEMPLATE_ID, PROPERTY_NAME)`.

## 개발 시 참고
- 생성 시 기본 `createdBy/updatedBy` 는 0으로 세팅되며, 필요하면 서비스 사용 전에 채워 넣는다.
- 렌더링은 FreeMarker 2.3.32를 사용하며, 본문/제목이 null 이면 null 반환한다.
- JDBC 구현은 `src/main/resources/sql/template-sqlset.xml` 에 정의된 SQL을 사용하고, 프로퍼티는 별도 테이블에 저장한다.
- JPA 사용 시 `@EnableJpaRepositories` + 엔티티 스캔이 starter에 포함되어 있으며, 데이터소스와 전역 JPA 설정이 선행돼야 한다.

## 빠른 시작
1. 의존성 추가: `studio-application-starter-template` (또는 모듈 직접 추가).
2. 설정: `studio.features.template.enabled=true`, REST 사용 시 `studio.features.template.web.enabled=true` 와 `studio.persistence.type=jpa|jdbc`.
3. 권한 스코프(`features:template/read|write|delete`)를 인가 서버/ACL에 등록.
4. (선택) 기본 base-path가 필요에 맞게 `/api/mgmt/templates` 이외로 조정하고, DDL(`schema/postgres/V0.7.0__create_template_tables.sql`)을 적용한다.
