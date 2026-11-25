# MMM · 음음음

**하루를 한 마디 소리로 기록하는 Java Swing 데스크톱 앱.**

건반 연주 → 시각 편집 → 들어보기 → 저장 → 주간 감상까지 인터넷 없이 동작합니다. 지능형 UI/UX 프로그래밍 텀 프로젝트로, 그래픽·재생·저장이 같은 NoteEvent 모델을 사용합니다.

![MMM 편집 화면](app/docs/S06-editor.png)

## 시작하기

Java 21 이상이 필요합니다. JDK를 설치하고 `JAVA_HOME`을 지정하세요.

```sh
git clone https://github.com/suimay/MMM.git
cd MMM/app
./build.sh
./demo.command
```

또는 [실행 JAR 다운로드](downloads/MMM-2.1.jar?raw=true) 후 실행합니다.

```sh
java -jar MMM-2.1.jar --demo-date=2026-11-01
```

시작 화면에서 **샘플 바로 듣기**를 누르거나 `demo / demo1234`로 로그인합니다. 이 계정은 공개 시연용입니다. 고정 날짜의 샘플 기록은 일반 모드 데이터와 분리됩니다.

## 주요 기능

- 단계 입력·4박 녹음, 펜타토닉, 카운트인·메트로놈
- 길이·세기·높낮이를 담은 소리 덩어리와 꼬리 드래그, 실행 취소/다시 실행, 편집 전후 비교
- 일시정지·반복·재생 위치 이동, 개인 주간·3파트 공동 합주
- 개인 2곡·공동 2곡, 총 112개 샘플 기록
- 로컬 곡 관리·초대·참여, 시간 비례 악보·인쇄, MIDI 내보내기
- 날짜·권한·버전 검사, 원자적 파일 저장, 이전 정상 파일 백업

## 구현 범위

**Java Swing / Java Sound MIDI / 로컬 파일 저장**입니다. 서버 인증·Spring Boot·PostgreSQL·원격 협업은 미구현이며 공동 기능은 같은 기기의 로컬 모의 기능입니다. 악보는 시간 비례 간단 기보입니다.

모델·저장·기능 검사 **73개**, 실제 Swing 통합 검사 **39개**를 통과했습니다. 상세 범위와 검증 한계는 아래 문서에 기록했습니다.

- [디자인 기준·소리 덩어리의 의미](app/docs/DESIGN.md)
- [사용법·단축키·빌드·구현 구조](app/README.md)
- [검증 기록](app/docs/VERIFICATION.md)
- [Java 소스](app/src/main/java/mmm)
- [테스트](app/src/test/java/mmm)

개인 저장 데이터·계정 파일·JDK 설치본·macOS 앱 묶음은 저장소에 포함하지 않습니다. macOS에서는 `app/package-mac.sh`로 런타임을 포함한 앱을 만들 수 있습니다.
