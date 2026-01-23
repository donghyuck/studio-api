# Studio Platform Security ACL 개발자 가이드

이 모듈은 Studio Platform의 데이터베이스 기반 ACL 구현과 관련 서비스를 제공합니다.
Spring Security ACL과 통합되어 권한 관리용 고수준 API를 노출합니다.

## 모듈 개요
- 목적: ACL 메타데이터 저장, 권한 관리, 정책 갱신
- 의존: Spring Security ACL, JPA 리포지토리(일부 경로에서 선택)
- 제공: 서비스, 정책 동기화, 관리자 엔드포인트(스타터를 통해 노출)

## 아키텍처 요약
- ACL 메타데이터 테이블: `acl_class`, `acl_object_identity`, `acl_sid`, `acl_entry`
- 정책 집계: `DatabaseAclDomainPolicyContributor`
- 정책 갱신: `DomainPolicyRefreshEvent` -> `DomainPolicyRegistryImpl.refresh`
- 권한 관리:
  - 인터페이스: `studio.one.platform.security.acl.AclPermissionService`
  - 구현: `DefaultAclPermissionService`

## 주요 클래스
- `DefaultAclPermissionService`
  - grant/revoke/delete API
  - 리포지토리 존재 시 벌크 grant/revoke 지원
  - 감사 로그 + 메트릭 훅 포함
- `AclAdministrationService`
  - 클래스/SID/객체 식별자/엔트리 CRUD
  - 변경 후 정책 갱신 이벤트 발행
- `AclPolicySynchronizationServiceImpl`
  - 정책 디스크립터를 ACL 테이블에 동기화하고 갱신 이벤트 발행
- `AclPolicyRefreshPublisher`
  - 정책 갱신 이벤트 발행 통합
- `AclCacheInvalidationListener`
  - 정책 갱신 시 ACL 캐시 무효화

## 이벤트 및 갱신 흐름
1. ACL 변경 또는 정책 동기화 수행
2. `AclPolicyRefreshPublisher.publishAfterCommit()` 호출
3. `DomainPolicyRefreshEvent` 발행
4. `DomainPolicyRegistryImpl` 정책 재로드
5. `AclCacheInvalidationListener` ACL 캐시 클리어(활성화 시)

## 설정
설정 경로: `studio.security.acl.*`
- `cache-name` (기본값: `aclCache`)
- `admin-role` (기본값: `ROLE_ADMIN`)
- `metrics-enabled` (기본값: `true`)
- `audit-enabled` (기본값: `true`)

예시:
```yaml
studio:
  security:
    acl:
      enabled: true
      cache-name: aclCache
      admin-role: ROLE_ADMIN
      use-spring-acl: false
      metrics-enabled: true
      audit-enabled: true
```

## 메트릭
메트릭은 `AclMetricsRecorder`를 통해 기록됩니다.
- `MeterRegistry`가 있으면 Micrometer 구현이 자동 등록됩니다.
- 메트릭 이름:
  - `acl.operation.duration` (Timer)
  - `acl.operation.calls` (Counter)
  - `acl.operation.affected` (Counter)

## 감사 로그
ACL 감사 로그는 INFO 레벨에서 `ACL_AUDIT` prefix로 출력됩니다.
감사 로그 비활성화: `studio.security.acl.audit-enabled=false`

## 벌크 권한 작업
리포지토리 사용 가능 시 벌크 grant/revoke를 지원합니다.
- `grantPermissions(...)`: 누락된 ACE를 배치 삽입
- `revokePermissions(...)`: 매칭 ACE를 배치 삭제
리포지토리가 없으면 개별 처리로 폴백합니다.

## 캐시
캐시는 선택 사항입니다.
- `CacheManager`가 있으면 `SpringCacheBasedAclCache`를 생성
- 캐시 이름은 `studio.security.acl.cache-name`으로 설정
- `DomainPolicyRefreshEvent` 발생 시 캐시 전체 삭제

## 외부 모듈 사용
외부 모듈은 인터페이스만 의존합니다.
```java
import studio.one.platform.security.acl.AclPermissionService;
```
`DefaultAclPermissionService`에 직접 의존하지 않습니다.
권한 목록 조회도 `AclPermissionService.listPermissions(...)`로 통일합니다.

## 구현 선택
- `studio.security.acl.use-spring-acl=true` 이고 `MutableAclService` 빈이 있으면 Spring ACL 기반 구현(`DefaultAclPermissionService`)이 사용됩니다.
- 기본값은 `false`이며, 이 경우 리포지토리 기반 구현(`RepositoryAclPermissionService`)이 사용됩니다.
- `use-spring-acl=true`인데 `MutableAclService`가 없으면 `AclPermissionService` 빈이 생성되지 않습니다.

## 확장 포인트
- `AclMetricsRecorder` 빈을 제공해 메트릭 연동 커스텀
- `AclPolicyRefreshPublisher`를 교체해 이벤트 전달 방식 변경
- 필요 시 `AclPermissionService` 구현 교체

## 테스트 팁
- 단위 테스트: `AclPermissionService`를 스텁으로 대체
- 통합 테스트: 다음을 확인
  - ACL 테이블 존재
  - `MutableAclService` 구성
  - `DomainPolicyRefreshEvent` 갱신 동작

## 알려진 트레이드오프
- 벌크 grant/revoke는 리포지토리 접근 전제
- 캐시 무효화는 전부 삭제 방식(거친 무효화)
