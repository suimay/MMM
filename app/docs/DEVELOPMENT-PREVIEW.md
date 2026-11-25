# 화면을 보면서 개발하기

프로젝트 루트의 `preview.cmd`를 실행한다. 현재 Java 소스를 빌드해 샘플 계정 `demo`의 편집 화면을 바로 연다. 별도의 서버나 ZIP 재설치가 필요하지 않다. 앱의 상단에 **개발 미리보기**가 표시된다.

1. 건반·주간·보관함·곡 메뉴를 직접 사용해 본다.
2. 화면을 보며 다음에 바꿀 위치와 원하는 동작을 정한다.
3. 코드 수정 후 **변경 반영 · F5**를 누른다.
4. 빌드하는 동안 창이 닫혔다가 최신 화면으로 다시 열린다. 곡·주간·주요 화면과 창 크기를 이어 읽는다.

미저장 입력이 있으면 기존 D04의 **저장하고 이동 / 버리고 이동 / 계속 편집**을 거친다. 저장을 고르면 D03 확인과 실제 파일 저장이 끝난 뒤 갱신한다. 계속 편집이나 확인 취소는 현재 창과 입력을 유지한다. 음악 저장과 음역 이동 기능은 일반 앱과 같다.

## 실행과 데이터

- JDK 21 이상이 필요하다. 작업용 `app/tools/jdk21`이 있으면 우선 사용하고, 다음은 `JAVA_HOME`과 설치된 JDK를 확인한다.
- 컴파일과 앱 실행은 이 PC 안에서 진행한다. 이 화면 확인 모드는 샘플 데이터를 사용하는 로컬 시연이다.
- 샘플 계정과 음악: `%USERPROFILE%\.mmm\development-preview`.
- 현재 화면 정보: 위 폴더의 `view.properties`. 계정 비밀번호·토큰·편집 초안은 기록하지 않는다.
- 빌드/실행 로그: `app/build/development-preview/build.log`, `runtime.log`.
- 빌드 실패 시 마지막으로 성공한 미리보기 JAR를 유지하고 해당 화면을 다시 연다. 처음부터 빌드가 실패하면 로그 경로와 오류를 알린다.
- 미리보기 창을 닫으면 실행기도 종료한다. 새로 열 때도 같은 `preview.cmd`를 사용한다.

원격 공동 사용은 기존 `remote.cmd`와 서버로 확인한다. 개발 미리보기는 소스 변경을 같은 폴더에서 확인하는 실행 방식이다.

## 검사

`PreviewGuiTest`의 15개 검사가 통과했다. 편집기 바로 열기·샘플 로그인·F5 버튼·녹음 중인 음의 마무리·미저장 취소·버리고 갱신·현재 화면 복원·저장 완료 후 갱신·메모 복원을 확인했다. 기존 기본/건반/전체 음역 226개와 일반 Swing 135개도 통과했다. 별도 복사한 테스트 작업 폴더에서 컴파일 오류를 주입해 마지막 정상 JAR의 해시가 유지되는지도 확인했다. 실행 중인 개인 서버와 개인 계정 DB는 변경하지 않았다.

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\app\preview.ps1 -BuildOnly
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\app\build.ps1 -GuiTests
```

미리보기 검사 기록은 `app/build/preview-gui-tests.log`, `preview-failure-test.log`에 있다.

실제 `preview.ps1` 실행기로 별도 서버 없이 개발 미리보기 JVM이 시작하고 Windows에 해당 제목의 창이 생성된 것을 확인했다. 창 캡처 도구는 `window capture timed out`으로 실패해 수동 화면 캡처는 확인하지 못했다. 앱 자체 렌더링은 `app/build/evidence/development-preview.png`와 실제 Swing 검사로 확인했다.
