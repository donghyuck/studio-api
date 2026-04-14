# studio-platform-starter-objectstorage-aws

AWS S3 SDK(`software.amazon.awssdk:s3`)를 classpath에 추가하는 thin 스타터이다.
자체 자동 구성 로직은 없으며, `studio-platform-starter-objectstorage`와 함께 사용하면
S3 및 S3 호환 스토리지(NCP, MinIO, OCI S3 호환 등) 프로바이더가 자동 구성된다.

## 1) 의존성 추가

```kotlin
dependencies {
    // AWS S3 SDK (이 스타터)
    implementation(project(":starter:studio-platform-starter-objectstorage-aws"))

    // 오브젝트 스토리지 자동 구성 (필수 동반)
    implementation(project(":starter:studio-platform-starter-objectstorage"))
}
```

## 2) 기능 활성화

별도 활성화 설정은 없다. 이 스타터를 추가하면 `studio-platform-starter-objectstorage`의
S3 자동 구성(`ObjectStorageAutoConfiguration.S3Available`)이 활성화된다.

프로바이더 설정은 `studio-platform-starter-objectstorage`의 README를 참고한다.

```yaml
studio:
  cloud:
    storage:
      providers:
        aws-main:
          enabled: true
          type: s3
          region: ap-northeast-2
          credentials:
            access-key: ${AWS_ACCESS_KEY_ID}
            secret-key: ${AWS_SECRET_ACCESS_KEY}
```

## 3) 설정

추가 설정 항목 없음. 전체 설정 속성은 `studio-platform-starter-objectstorage`를 참고한다.

## 4) 자동 구성되는 주요 빈

이 스타터는 S3 SDK 라이브러리만 제공한다. 실제 빈 등록은 `studio-platform-starter-objectstorage`가 담당한다.

- `S3Client` — 각 프로바이더별로 내부 생성 (레지스트리에 보관)
- `S3Presigner` — `s3.presigner-enabled=true` 인 프로바이더에 대해 생성

## 5) 참고 사항

- 이 스타터가 없으면 `studio.cloud.storage.providers.<id>.type=s3` 프로바이더를 활성화했을 때
  경고 로그만 출력되고 `CloudObjectStorage` 빈이 생성되지 않는다.
- AWS SDK 버전은 프로젝트의 `awssdkS3Version` 프로퍼티로 관리된다(기본: `2.37.3`).
