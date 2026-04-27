# 문서 개선 및 보완 사항

이 문서는 2026-04-15 기준으로 README 계층과 소스를 대조해 확인한 문서화 상태,
표준화 필요 항목, 소스 품질 개선 후보를 정리한다.

## 현재 기준

- 공식 모듈 기준은 `settings.gradle.kts`에 포함된 모듈이다.
- 문서 인벤토리와 검색 검증에서 `.claude/`, `.omx/`, `build/`, `bin/` 산출물은 제외한다.
- 실제 설정 값은 `@ConfigurationProperties`, `@ConditionalOnProperty`, `AutoConfiguration.imports`가 우선이다.
- Flyway 버전 범위는 `docs/flyway-versioning.md`를 기준으로 한다.

## 완료됨

| 항목 | 상태 |
|---|---|
| 루트 `README.md`의 활성 모듈/스타터 설명 보강 | 완료 |
| `starter/README.md`의 objecttype/realtime starter 목록 및 문서 링크 보강 | 완료 |
| `starter/studio-platform-starter-realtime/README.md` 신규 작성 | 완료 |
| `studio-application-modules/README.md`의 Template 서비스 섹션 추가 | 완료 |
| schema 보유 README의 오래된 초기 migration 참조를 실제 버전 파일명으로 수정 | 완료 |
| `docs/flyway-versioning.md` 참조를 schema 관련 README에 연결 | 완료 |
| `starter/studio-platform-starter-ai/README.md`에서 `docs/dev/spring-ai-openai.md` 접근성 보강 | 완료 |

## 즉시 보완 후보

### API 권한 스코프 통합 문서

각 컨트롤러의 `@PreAuthorize("@endpointAuthz.can(...)")` 스코프가 모듈 README에 분산되어 있다.
인가 서버나 ACL 정책을 구성할 때 전체 목록을 한 곳에서 확인할 수 있도록 별도 문서를 추가하는 것이 좋다.

권장 위치:

```text
docs/api-permission-scopes.md
```

초기 수집 명령:

```bash
rg "@PreAuthorize\\(\"@endpointAuthz.can" starter studio-application-modules studio-platform* -g '!**/build/**'
```

### 공식 모듈 여부 정리

다음 디렉터리는 소스 또는 build 파일이 있으나 `settings.gradle.kts` 기준 공식 활성 모듈이 아니다.

| 디렉터리 | 현재 상태 | 권장 결정 |
|---|---|---|
| `studio-application-modules/custom-user-service` | 소스와 auto-configuration 존재, settings 미포함 | 공식 모듈로 승격하거나 예제/실험 모듈로 위치와 문서를 분리 |
| `studio-application-modules/image-service` | `build.gradle.kts`만 있고 `src/main` 없음, settings 미포함 | 삭제하거나 실제 구현 계획과 소유자를 명시 |

### README 표준 섹션 도입

모듈 README마다 섹션 구성이 다르다. 신규/갱신 README는 아래 순서를 기본 템플릿으로 맞춘다.

1. 역할/요약
2. 의존성
3. 설정 namespace
4. AutoConfiguration 또는 주요 빈
5. REST/API
6. permission scopes
7. schema/Flyway
8. 대응 starter 또는 관련 모듈
9. validation command

## 표준화 이슈

### 설정 namespace

현재 기준:

- feature wiring: `studio.features.<module>.*`
- runtime detail: `studio.<module>.*`
- global infrastructure: `studio.security.*`, `studio.persistence.*`, `studio.cloud.storage.*`, `studio.ai.*`

주의할 점:

- object storage는 실제 `StorageProperties`가 `studio.cloud.storage.*`에 바인딩되므로 이 경로가 authoritative이다.
- attachment binary storage는 현재 `studio.features.attachment.storage.*`가 authoritative이다.
- user password policy는 현재 `studio.features.user.password-policy.*`가 구현 경로이며, `studio.user.password-policy.*` migration은 아직 시작하지 않았다.

### Persistence package bridge

`starter/studio-platform-starter`에는 과거 오타 패키지 경로인 `perisitence`가 남아 있고,
`studio-platform-autoconfigure`에는 `perisistence` 호환 경로가 남아 있다.

- 신규 코드와 문서 예시는 정상 패키지명 `persistence`를 사용한다.
- 오타 경로는 기존 소비자 호환을 위한 bridge로만 유지한다.
- 제거하려면 별도 deprecation window와 migration guide가 필요하다.

## 소스 품질 개선 후보

| 위치 | 내용 | 권장 조치 |
|---|---|---|
| `studio-platform/src/main/java/studio/one/platform/component/RepositoryImpl.java` | dynamic configuration reload TODO | refresh flow 요구사항 확정 후 별도 이슈화 |
| `studio-platform-storage/src/main/java/studio/one/platform/storage/service/impl/OciObjectStorage.java` | auto-generated TODO 주석 | 구현 필요 여부 확인 후 제거 또는 구현 |
| `starter/**/build`, `starter/**/bin` | generated auto-configuration imports가 검색 결과에 섞임 | 문서 점검 명령에 `-g '!**/build/**' -g '!**/bin/**'` 적용 |

## 검증 명령

문서 현행화 후 아래 명령으로 주요 불일치를 확인한다.

```bash
rg "Template""Controller|V0""__" README.md starter studio-application-modules studio-platform* docs -g 'README.md' -g '*.md' -g '!**/build/**' -g '!**/bin/**'
rg "STARTER_GUIDE|spring-ai-openai|flyway-versioning|studio-platform-starter-realtime/README.md" README.md starter studio-application-modules studio-platform* docs -g 'README.md' -g '*.md' -g '!**/build/**' -g '!**/bin/**'
python3 - <<'PY'
import re
from pathlib import Path
settings = Path('settings.gradle.kts').read_text()
official = set(re.findall(r'include\\(":(.*?)"\\)', settings))
for module in sorted(official):
    path = Path(module.replace(':', '/'))
    if not (path / 'build.gradle.kts').exists():
        continue
    if not (path / 'README.md').exists():
        print(path)
PY
./gradlew :starter:studio-platform-starter-realtime:compileJava
./gradlew :starter:studio-application-starter-template:compileJava
```
