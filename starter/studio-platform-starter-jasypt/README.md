# Jasypt 설정 정보 암호화 가이드

이 문서는 `studio-platform-starter-jasypt`를 이용해 설정 정보를 암호화/복호화하는 방법을 설명한다.

## 1) 의존성 추가
Gradle 예시:

```kotlin
dependencies {
    implementation(project(":starter:studio-platform-starter-jasypt"))
    // ENC(...) 프로퍼티 자동 복호화가 필요하면 추가
    implementation("com.github.ulisesbocchio:jasypt-spring-boot-starter:3.0.4")
}
```

## 2) 기본 설정
`studio.features.jasypt.enabled`를 켜고 암호화 비밀번호를 설정한다.
운영에서는 환경변수로 주입하는 것을 권장한다.

```yaml
studio:
  features:
    jasypt:
      enabled: true
      fail-if-missing: true
      encryptor:
        password: ${JASYPT_ENCRYPTOR_PASSWORD}
        algorithm: PBEWithSHA256And128BitAES-CBC-BC
        provider-name: BC
        key-obtention-iterations: 1000
        pool-size: 1
        salt-generator-classname: org.jasypt.salt.RandomSaltGenerator
        iv-generator-classname: org.jasypt.iv.RandomIvGenerator
        string-output-type: base64
```

필요하면 빈 별칭을 지정할 수 있다.

```yaml
studio:
  features:
    jasypt:
      encryptor:
        bean: myEncryptor
```

## 3) 암호화 값 생성
두 가지 방법을 제공한다.

### 3-1) CLI 방식 (비웹 환경)
`studio.features.jasypt.cli.enabled`는 기본 `true`이며, 웹 앱이 아닌 경우에만 동작한다.

```bash
java -jar app.jar \
  --spring.main.web-application-type=none \
  --jasypt.encrypt=plainText
```

출력값이 `ENC(...)` 형태로 반환된다.

파일/표준입력 처리도 가능하다.

```bash
java -jar app.jar \
  --spring.main.web-application-type=none \
  --jasypt.encrypt \
  --jasypt.in=- \
  --jasypt.wrap=true
```

### 3-2) HTTP 엔드포인트 방식 (웹 환경)
로컬 loopback에서만 접근 가능하며, 토큰 검증을 지원한다.

```yaml
studio:
  features:
    jasypt:
      http:
        enabled: true
        base-path: /internal/jasypt
        require-token: true
        token-env: JASYPT_HTTP_TOKEN
```

암호화 요청 예시:

```bash
curl -X POST "http://127.0.0.1:8080/internal/jasypt/encrypt" \
  -H "Content-Type: application/json" \
  -H "X-JASYPT-TOKEN: $JASYPT_HTTP_TOKEN" \
  -d '{"value":"plainText"}'
```

## 4) 설정 파일에 적용
암호화된 값을 `ENC(...)`로 감싸서 설정에 넣는다.

```yaml
spring:
  datasource:
    username: app
    password: ENC(EncryptedValueHere)
```

`jasypt-spring-boot-starter`를 함께 사용하면 `ENC(...)`가 자동 복호화된다.
자동 복호화를 사용하지 않는 경우, 애플리케이션 코드에서 `StringEncryptor`를 직접 사용해야 한다.

## 5) 문제 해결
- 비밀번호 누락: `studio.features.jasypt.encryptor.password`가 없으면 기본적으로 실패한다.
  경고만 원하면 `studio.features.jasypt.fail-if-missing=false`로 낮춘다.
- BC Provider 누락: `provider-name=BC`일 때 BouncyCastle이 없으면 경고 또는 오류가 발생한다.

## 6) 운영 주의사항
- 암호화 비밀번호는 환경변수/시크릿 매니저로 관리한다.
- HTTP 엔드포인트는 로컬에서만 호출되도록 유지하고, 토큰을 반드시 사용한다.
- 암호화/복호화 대상 값은 로그에 남기지 않는다.
