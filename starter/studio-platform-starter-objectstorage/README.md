# studio-platform-starter-objectstorage

클라우드 오브젝트 스토리지를 공통 인터페이스로 자동 구성하는 스타터이다.
AWS S3 호환 스토리지(S3, NCP, MinIO 등)를 멀티 프로바이더로 등록하고,
`ObjectStorageRegistry`와 `ProviderCatalog` 빈을 통해 런타임에 프로바이더를 조회하거나
오브젝트를 관리한다. 선택적으로 REST 엔드포인트를 활성화하면 관리용 HTTP API도 제공된다.

> AWS S3 SDK를 classpath에 추가하려면 `studio-platform-starter-objectstorage-aws`를,
> Oracle OCI SDK를 추가하려면 `studio-platform-starter-objectstorage-oci`를 함께 선언해야 한다.

## 1) 의존성 추가

```kotlin
dependencies {
    implementation(project(":starter:studio-platform-starter-objectstorage"))

    // AWS S3 / S3 호환(NCP, MinIO, OCI S3 호환) 사용 시
    implementation(project(":starter:studio-platform-starter-objectstorage-aws"))

    // Oracle OCI 네이티브 SDK 사용 시
    implementation(project(":starter:studio-platform-starter-objectstorage-oci"))

    // REST 엔드포인트 활성화 시 필요
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-validation")
}
```

## 2) 기능 활성화

REST 엔드포인트는 기본적으로 비활성화되어 있다. 프로바이더는 `enabled: true`로 개별 활성화한다.

```yaml
studio:
  cloud:
    storage:
      web:
        enabled: true              # REST 엔드포인트 활성화 (기본: false)
        endpoint: /api/mgmt/objectstorage  # 기본 경로
```

## 3) 설정

### 기본 구조

```yaml
studio:
  cloud:
    storage:
      web:
        enabled: true
        endpoint: /api/mgmt/objectstorage
      providers:
        <provider-id>:
          enabled: true
          type: s3          # s3 | oci | fs
          region: ap-northeast-2
          endpoint: https://...   # S3 호환 엔드포인트 (AWS 표준이면 생략)
          credentials:
            access-key: ${ACCESS_KEY}
            secret-key: ${SECRET_KEY}
          s3:
            path-style: false      # NCP / MinIO / OCI S3 호환 시 true 권장
            presigner-enabled: true
            api-timeout-ms: 5000
```

### AWS S3 예시

```yaml
studio:
  cloud:
    storage:
      web:
        enabled: true
      providers:
        aws-main:
          enabled: true
          type: s3
          region: ap-northeast-2
          credentials:
            access-key: ${AWS_ACCESS_KEY_ID}
            secret-key: ${AWS_SECRET_ACCESS_KEY}
          s3:
            path-style: false
            presigner-enabled: true
```

### NCP Object Storage (S3 호환) 예시

```yaml
studio:
  cloud:
    storage:
      providers:
        ncp:
          enabled: true
          type: s3
          region: kr-standard
          endpoint: https://kr.object.ncloudstorage.com
          credentials:
            access-key: ${NCP_ACCESS_KEY}
            secret-key: ${NCP_SECRET_KEY}
          s3:
            path-style: true
            presigner-enabled: true
```

### MinIO 예시

```yaml
studio:
  cloud:
    storage:
      providers:
        minio:
          enabled: true
          type: s3
          region: us-east-1
          endpoint: http://localhost:9000
          credentials:
            access-key: minioadmin
            secret-key: minioadmin
          s3:
            path-style: true
            presigner-enabled: true
```

### OCI Object Storage (S3 호환) 예시

```yaml
studio:
  cloud:
    storage:
      providers:
        oci:
          enabled: true
          type: s3
          region: ap-seoul-1
          endpoint: https://<namespace>.compat.objectstorage.ap-seoul-1.oraclecloud.com
          credentials:
            access-key: ${OCI_ACCESS_KEY}
            secret-key: ${OCI_SECRET_KEY}
          s3:
            path-style: true
            presigner-enabled: true
          oci:
            namespace: mytenantns
            compartment-id: ocid1.compartment.oc1..xxx
```

## 4) REST 엔드포인트

기본 경로는 `/api/mgmt/objectstorage`이며 `studio.cloud.storage.web.endpoint`로 변경할 수 있다.
모든 엔드포인트는 `ADMIN` 권한이 필요하다.

| 메서드 | 경로 | 설명 | 권한 |
|---|---|---|---|
| `GET` | `{basePath}/providers` | 등록된 프로바이더 목록 조회 (`?health=true` 로 상태 포함) | `services:storage_cloud read` |
| `GET` | `{basePath}/providers/{providerId}/buckets` | 특정 프로바이더의 버킷 목록 | `services:storage_cloud read` |
| `GET` | `{basePath}/providers/{providerId}/buckets/{bucket}/objects` | 오브젝트 목록 (`prefix`, `delimiter`, `token`, `size` 파라미터 지원) | `services:storage_cloud read` |
| `GET` | `{basePath}/providers/{providerId}/buckets/{bucket}/object` | 오브젝트 메타데이터 조회 (`?key=path/to/file`) | `services:storage_cloud read` |
| `GET` | `{basePath}/providers/{providerId}/buckets/{bucket}/object:presigned-get` | Presigned GET URL 생성 | `services:storage_cloud write` |
| `POST` | `{basePath}/providers/{providerId}/buckets/{bucket}/object:presigned-put` | Presigned PUT URL 생성 | `services:storage_cloud write` |

### Presigned GET URL 예시

```http
GET /api/mgmt/objectstorage/providers/aws-main/buckets/my-bucket/object:presigned-get
    ?key=uploads/document.pdf&ttl=300&disposition=attachment&filename=document.pdf
```

### Presigned PUT URL 요청 예시

```http
POST /api/mgmt/objectstorage/providers/aws-main/buckets/my-bucket/object:presigned-put
Content-Type: application/json

{
  "key": "uploads/image.png",
  "contentType": "image/png",
  "ttlSeconds": 300
}
```

## 5) 참고 사항

- 프로바이더 라이브러리(`software.amazon.awssdk:s3`)가 classpath에 없는데 `type: s3` 프로바이더를
  활성화하면 경고 로그가 출력된다. 이 경우 `studio-platform-starter-objectstorage-aws`를 추가한다.
- `ObjectStorageRegistry` 빈은 이름(`ObjectStorageRegistry.SERVICE_NAME`)으로 조건 관리되므로
  커스텀 구현체로 교체할 수 있다.
- Presigned URL TTL은 최소 1초, 최대 86400초(24시간)로 제한된다.
- REST 엔드포인트는 `studio.cloud.storage.web.enabled=false`(기본값)이면 등록되지 않는다.
