# studio-platform-storage

클라우드 오브젝트 스토리지 추상화 모듈이다. S3 호환 스토리지와 OCI Object Storage를 단일 인터페이스로 다룰 수 있도록 포트/어댑터 구조를 제공하며, REST API를 통해 프로바이더/버킷/객체를 관리한다.

## 요약
여러 클라우드 공급자를 `CloudObjectStorage` 인터페이스로 통일해 애플리케이션 코드가 공급자에 의존하지 않도록 분리한다. 런타임 구현체(S3, OCI)는 스타터 모듈이 조건부로 등록한다.

## 설계
- `CloudObjectStorage`: 객체 업로드/다운로드/삭제/목록/Presigned URL의 공급자 중립 계약
- `ObjectStorageRegistry`: 공급자 이름(name)을 키로 `CloudObjectStorage` 인스턴스를 관리하는 레지스트리
- `ProviderCatalog`: 등록된 프로바이더 목록과 헬스 정보를 노출하는 카탈로그
- `ObjectStorageController`: 관리용 REST 엔드포인트 제공 (ADMIN 권한 필요)

## 주요 인터페이스

| 타입 | 설명 |
|---|---|
| `CloudObjectStorage` | put / get / download / delete / head / list / presignedGetUrl / presignedPut |
| `ObjectStorageRegistry` | `get(name)` / `ids()` — 이름으로 스토리지 인스턴스 조회 |
| `ProviderCatalog` | `list(includeHealth)` — 전체 프로바이더 목록 반환 |
| `ObjectStorageType` | 지원 타입 열거형 (S3, OCI) |
| `BucketInfo` | 버킷 메타데이터 |
| `ObjectInfo` | 객체 메타데이터 (key, size, contentType, eTag 등) |
| `PageResult<T>` | 커서 기반 페이징 결과 (items, nextToken, truncated, commonPrefixes) |

## REST 엔드포인트
기본 경로: `/api/mgmt/objectstorage` (`studio.cloud.storage.web.endpoint`로 변경 가능)  
모든 엔드포인트는 `ROLE_ADMIN` 권한이 필요하다.

| 메서드 | 경로 | 설명 |
|---|---|---|
| GET | `/providers` | 등록된 프로바이더 목록 조회 (`?health=true`로 헬스 포함) |
| GET | `/providers/{providerId}/buckets` | 특정 프로바이더의 버킷 목록 조회 |
| GET | `/providers/{providerId}/buckets/{bucket}/objects` | 버킷 내 객체 목록 (커서 페이징, prefix/delimiter 지원) |
| GET | `/providers/{providerId}/buckets/{bucket}/object` | 객체 메타데이터 조회 (`?key=path/to/obj`) |
| GET | `/providers/{providerId}/buckets/{bucket}/object:presigned-get` | GET용 Presigned URL 생성 |
| POST | `/providers/{providerId}/buckets/{bucket}/object:presigned-put` | PUT용 Presigned URL 생성 |

## 설정
```yaml
studio:
  cloud:
    storage:
      web:
        endpoint: /api/mgmt/objectstorage   # 기본값
```

## 구현체 주입 예시
```java
// 레지스트리에서 프로바이더 이름으로 인스턴스 조회
CloudObjectStorage storage = objectStorageRegistry.get("my-s3");

// 파일 업로드
try (InputStream in = Files.newInputStream(path)) {
    storage.put("my-bucket", "uploads/file.pdf", in,
        Files.size(path), "application/pdf", Map.of());
}

// Presigned URL 생성
URL url = storage.presignedGetUrl(
    "my-bucket", "uploads/file.pdf",
    Duration.ofMinutes(10), null, "inline");
```

## 관련 모듈
- `starter/studio-platform-starter-objectstorage` — 이 모듈의 포트를 자동 구성하고 REST 엔드포인트를 노출하는 핵심 스타터
- `starter/studio-platform-starter-objectstorage-aws` — AWS S3 SDK를 classpath에 추가하는 thin 스타터
- `starter/studio-platform-starter-objectstorage-oci` — Oracle OCI SDK를 classpath에 추가하는 thin 스타터
- `studio-platform-autoconfigure` — `ConditionalOnProperties` 등 공통 조건 활용
