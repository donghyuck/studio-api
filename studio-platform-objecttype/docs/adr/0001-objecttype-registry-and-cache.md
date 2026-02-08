# ADR 0001: ObjectType Registry + Policy Cache

## 상태
승인

## 맥락
업로드/권한 검증이 여러 모듈에서 반복되며, objectType/정책 정의의 변경이 잦다.
요청 경로마다 DB 조회를 수행하면 성능이 저하된다.

## 결정
- objectType과 정책을 중앙 레지스트리로 관리한다.
- 캐시 계층을 두고 TTL 기반 무효화를 적용한다.
- 변경 시 `ObjectRebindService`로 캐시를 리로드한다.

## 결과
- 정책/메타데이터 변경을 빠르게 반영할 수 있다.
- 조회 성능을 안정적으로 확보한다.
