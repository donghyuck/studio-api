# TODO: ObjectType Validation/Binding Improvements

아래 항목들은 objectType 레지스트리/정책/권한 라우팅 확장 시점에 정리/개선할 작업 목록이다.

## 1) objectType 유효성 검증 위치 확정
- 기본 검증(컨트롤러): `objectType > 0`, `objectId > 0` 확인 (예: @Min(1))
- 강제 검증(서비스): `ObjectTypeRegistry.findByType(objectType)` 존재 여부 확인
- 저장소 제약: 필요 시 DB 제약 또는 FK/검증 추가 여부 결정

적용 후보:
- `studio-application-modules/attachment-service` (create/upload/update)
- `studio-application-modules/template-service` (create/update)

## 2) objectType 변경 시 rebind/move 정책
- 원칙 결정: objectType/objectId 변경 금지 vs 변경 허용
- 변경 허용 시:
  - `FileStorage.rebind/move` API 추가
  - attachment 저장소에서 경로 이동 처리
  - 실패 시 롤백 전략 수립

영향 파일:
- `studio-application-modules/attachment-service/.../LocalFileStore.java`
- `studio-application-modules/attachment-service/.../AttachmentServiceImpl.java`

## 3) registry 기반 validation contract 확정
- 계약 확정:
  - `ObjectTypeRegistry`
  - `ObjectTypeMetadata`
  - `ObjectPolicyResolver`
  - `AuthorizationRouter`
- objectType 메타 필드 최소 구성 확정 (key/name/attributes 등)
- 권한 라우팅 전에 메타 존재 검사 규칙 확정

## 확인 필요 리스크
- `objectType` 변경 시 실제 파일 경로 불일치 가능
- `UNKNOWN_OBJECT_TYPE/ID` 상수 현재 미사용
