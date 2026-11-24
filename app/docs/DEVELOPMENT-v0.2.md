# 개발명세서 v0.2 반영 · 2.4 로컬

이 문서는 2.4 로컬 개발 이력이다. 후속 2.5의 서버·원격 구현은 [M2 검증 기록](M2-VERIFICATION.md)을 따른다.

2026-11-16 / 기준 저장소 suimay/MMM, 커밋 `7e0702b7e13635ccb6c5df918aa5f6373e87cc6b`.
이 문서는 이번 작업의 구현과 실행 검증만 기록한다. 원본 개발명세서와 과거 검증 기록은 보존했다. 이번 산출물은 M1 로컬 앱이며 M2 원격 협업은 포함하지 않는다.

## 이번 변경

| 명세 | 구현 | 확인 |
|---|---|---|
| FR20·22, AC23 | Articulation.NORMAL/STACCATO. 논리 duration 유지, gate=duration/2. 120tick 음의 실제 NOTE_OFF=60tick 허용 | 모델·실제 MIDI Sequence·내보낸 MIDI 재읽기·Swing 조작 |
| FR20·18, AC23 | ExpressionRange.CRESCENDO, 격자 구간·시작/끝 세기 검증, 중첩 거부. 구간의 음 시작 위치를 선형 보간 | MIDI velocity와 캔버스·썸네일·합주 두께·감상 수치가 같은 effectiveVelocity 사용 |
| FR13·43, AC22 | 명시적 RestEvent. 단계 쉼 입력·음에서 변환·선택·이동·꼬리/길이 편집·삭제. 음과 쉼 중첩 거부 | 모델 경계·Swing 리스너·Undo/Redo·저장 재시작 |
| FR19·30, AC06·24 | 쉼·표현·메모 포함 이력. 메모 교체와 연속 드래그 각각 한 단계. Undo 후 저장 기준본과 비교해 dirty 계산 | 변화 없는 선택·작은 드래그는 clean, Undo로 기준본 복원 시 clean |
| FR21, AC21 | 선택 입력 메모, 공백 포함 최대 20 Unicode 코드포인트, 한 줄 검사 | 빈 메모·20/21 이모지·공백·저장/재조회 |
| FR28·45, AC16·23 | 표준 다섯 길이의 음표·쉼표, 스타카토 점·구간 크레센도. 합성 길이는 사각 표식·시간선·수치, 빈칸은 별도 문구 | 심볼 매핑·렌더·Printable 페이지·MIDI 재읽기 |
| FR01, AC24 | 원래 곡과 주간 유지, 선택 날짜/작성자 복원, 공동 BPM으로 초안 복사 시 쉼·표현 보존 | 지난주 위치에서 오늘 기록 편집 후 저장하여 같은 주·선택일 복귀 |
| FR31~34, AC08~10·25 | schema 1 읽기 / schema 2 쓰기. 구버전 음은 NORMAL, 쉼·표현 빈 목록. 이전 원본 백업·새 형식 구앱 덮어쓰기 차단 | v1 fixture 원문 보존·백업 바이트 비교·v2 재읽기·충돌·손상 필드 쓰기 거부 |
| FR14, AC04 | nanoTime 캡처·격자 정렬·마디 끝 음 자르기. 타이머 호출 전 마디가 끝난 늦은 누름도 거부 | 카운트인 입력 제외·반복 키 억제·4박 끝 해제·Undo/Redo |
| FR44, AC17·24 | 읽기 전용 안내·선택 윤곽·스타카토 점·쉼 띠·표현 구간 표시. 선택 속성 스크롤과 상단 표현 메뉴 | 1000×720에서 조상 컨테이너 잘림까지 검사, 실제 화면 PNG 확인 |
| NFR01·05 | Pretendard Regular/Bold 및 OFL 포함. Windows build.ps1/run.cmd/demo.cmd | Windows PATH의 Oracle JDK 실행기 탐색과 깨끗한 빌드 |

기존 로그인·권한·개인/공동곡·초대/탈퇴·주간 감상·실시간 파트 제어·설정·도움말·소리 체험 흐름은 회귀 검사했다. S14 일기장은 기존 선택 기능을 유지했다.

## 실행 검증

환경: Windows, Oracle JDK 26.0.2.1. `javac --release 21 -encoding UTF-8`로 컴파일. 실제 Synthesizer와 Sequencer를 열고, 표시되는 JFrame에서 리스너·대화상자를 실행했다. 모든 시험은 별도 임시 저장소를 사용했다.

```powershell
cd MMM/app
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\build.ps1 -GuiTests
```

| 검사 | 개수 | 결과 |
|---|---:|---|
| ModelRepositoryTest | 39 | 통과 |
| FeatureTest | 26 | 통과 |
| SoundShapeTest | 8 | 통과 |
| EnsembleDataTest | 28 | 통과 |
| ExpressionTest | 53 | 통과 |
| GuiFlowTest | 73 | 통과 |
| ExpressionGuiTest | 54 | 통과 |
| 총계 | 281 | 기본 154 + Swing 127 |

별도로 Windows ZIP을 풀어 그 안의 JAR를 실행했고, 시연 기록 112개 초기화와 실행 유지에 성공했다. 클래스 형식 65(Java 21), Pretendard/OFL 자원 포함, 빌드 JAR·배포 JAR·ZIP 내부 JAR의 SHA-256 일치도 확인했다.

`build/evidence/v02-editor.png`, `v02-editor-minimum.png`, `v02-readonly.png`, `v02-score.png`, `v02-score-screen.png`에 새 렌더를 기록한다. 기존 docs의 발표용 이미지를 검사 때 덮어쓰지 않는다.

자동 검사는 실제 OS 마우스 조작이나 청음 평가를 대신하지 않는다. 실물 프린터·운영체제 PDF 저장창, 청음에 의한 음질/잔음, Java 21 설치 환경 직접 실행, macOS 패키징, 100ms 응답성 계측은 이번 완료 주장에 포함하지 않는다. 최소 크기 편집기에서 건반·표현·저장·정지 노출을 확인했으며 모든 화면의 1000×720 사용성 전체를 보장하지 않는다.

## 데이터와 호환

- 기존 `~/.mmm/local`과 `~/.mmm/demo-날짜` 경로를 유지한다. Windows 시연 실행기는 2026-11-16을 사용하며 일반 모드와 분리된다.
- 파일 schema 2 안에서도 기존 형식 기록을 읽는다. 새 기록은 필드 7의 기록 형식 `2`, 음의 articulation, rests, expressions를 포함한다.
- 읽기만으로 파일을 변경하지 않는다. 쓰기는 파일 잠금 → 기존 전체 검사 → 버전 비교 → 임시 파일 flush → 이전 정상 파일 백업 → 원자 교체 순서다.
- 구버전의 짧은 음을 스타카토로 추정하거나 음 사이 빈 구간을 명시적 쉼으로 추정하지 않는다. 새 저장소를 구버전 앱에서 열면 지원하지 않는 형식으로 거부한다.
- 비밀번호 원문·개인 저장소·테스트 계정 파일은 배포 묶음에 넣지 않는다. 폰트 라이선스는 `resources/fonts/OFL.txt`와 JAR 안에 포함한다.

## 남은 M2

FR35·37~39의 원격 동작과 AC13~15의 2기기 검증은 미구현이다. 로컬 공동곡은 같은 저장소의 모의 기능이다. 서버가 있는 것으로 표시하지 않는다.

1. 원격 AuthService 및 요청/응답 DTO 분리. 현재 Repository.account의 솔트·해시 계약을 HTTP 응답으로 옮기지 않는다.
2. Java Spring Boot + PostgreSQL: 사용자·곡·멤버십·기록·음·쉼·표현·초대·설정·세션·SaveRequest 관계와 제약, 저장 트랜잭션.
3. 서버 세션 인증, 서울 날짜·작성자·권한 검증, expectedVersion 충돌, requestId 멱등성, 초대 만료·탈퇴 검증.
4. Swing HTTP 어댑터, 타임아웃·세션 만료·실패 초안 복구, 실제 두 기기 저장/조회와 위조 요청·부분 실패 검증.

명세서 v0.2의 HTTP 경로·필드·오류 코드는 이 단계의 설계 기준으로 남는다. 현재 JAR는 서버 주소나 DB 비밀값을 요구하지 않는다.
