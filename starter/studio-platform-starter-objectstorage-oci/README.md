# studio-platform-starter-objectstorage-oci

Oracle OCI Java SDK(`oci-java-sdk`, `oci-java-sdk-objectstorage`, `oci-java-sdk-common-httpclient-jersey`)를
classpath에 추가하는 thin 스타터이다.
자체 자동 구성 로직은 없으며, `studio-platform-starter-objectstorage`와 함께 사용한다.

> OCI Object Storage는 S3 호환 인터페이스를 지원하므로, 대부분의 경우
> `studio-platform-starter-objectstorage-aws`와 함께 S3 호환 모드(`type: s3`)로
> 사용할 수 있다. 이 스타터는 OCI 네이티브 SDK가 필요한 경우에 추가한다.

## 1) 의존성 추가

```kotlin
dependencies {
    // Oracle OCI SDK (이 스타터)
    implementation(project(":starter:studio-platform-starter-objectstorage-oci"))

    // 오브젝트 스토리지 자동 구성 (필수 동반)
    implementation(project(":starter:studio-platform-starter-objectstorage"))

    // S3 호환 모드를 함께 사용할 경우
    implementation(project(":starter:studio-platform-starter-objectstorage-aws"))
}
```

## 2) 기능 활성화

별도 활성화 설정은 없다. 프로바이더 설정은 `studio-platform-starter-objectstorage`의 README를 참고한다.

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

## 3) 설정

추가 설정 항목 없음. 전체 설정 속성은 `studio-platform-starter-objectstorage`를 참고한다.

## 4) 자동 구성되는 주요 빈

이 스타터는 OCI SDK 라이브러리만 제공한다. 실제 빈 등록은 `studio-platform-starter-objectstorage`가 담당한다.

## 5) 참고 사항

- OCI SDK 버전은 프로젝트의 `oracleOciSdkVersion` 프로퍼티로 관리된다.
- OCI Object Storage를 S3 호환 모드로 사용할 때는 `studio-platform-starter-objectstorage-aws`도
  함께 추가해야 `S3Client` 기반 자동 구성이 활성화된다.
- OCI 네임스페이스는 `studio.cloud.storage.providers.<id>.oci.namespace`에 설정한다.
