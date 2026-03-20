# Avatar Service

사용자 아바타 이미지 관리 모듈이다. 직접 의존하거나 `:starter:studio-application-starter-avatar`를 통해 자동 구성할 수 있다.

## 사용 요약
- 기능 활성화: `studio.features.avatar-image.enabled=true`
- 영속성: `studio.features.avatar-image.persistence=jpa|jdbc`
- 파일 복제 경로: `studio.features.avatar-image.replica.base-dir`
- 대응 starter: `:starter:studio-application-starter-avatar`

## 제공 기능
- 사용자별 아바타 업로드와 교체
- 대표 이미지(primary) 지정
- 파일 기반 저장과 복제 경로 지원

## 문서 바로가기
- 모듈 인덱스: `../README.md`
- 루트 개요: `../../README.md`
