# VSCode Java Snippet 적용 가이드

## 목적
- 개인 VSCode 환경에서도 프로젝트 공통 Java snippet(`addCopyright`, `addDeveloper`)을 동일하게 사용한다.

## 기준 파일
- `<repo-root>/.vscode/java.code-snippets`

## 적용 방법
1. 워크스페이스 스니펫을 개인 snippet 파일로 복사한다.
- macOS:
```bash
cp <repo-root>/.vscode/java.code-snippets \
  ~/Library/Application\ Support/Code/User/snippets/java.json
```

2. VSCode에서 Java 파일을 열고 아래 prefix를 입력해 snippet을 적용한다.
- `addCopyright`
- `addDeveloper`

## 운영 규칙
- 스니펫 변경은 워크스페이스 스니펫(`.vscode/java.code-snippets`)을 단일 기준으로 먼저 반영한다.
- `@author` 값 변경은 개인 `java.json`에서 직접 수정하지 않고,
  반드시 워크스페이스 스니펫(`.vscode/java.code-snippets`)의 `addDeveloper` body를 수정한 뒤 배포/복사해 사용한다.
