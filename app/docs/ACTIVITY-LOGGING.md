# 2.5.2 활동 로그 · 2026-11-16

로그인·로그아웃, 회원가입, 곡 생성·수정, 날짜별 음악 저장·수정, 초대·참여·탈퇴·설정과 조회 요청의 결과를 서버 창과 파일에 남긴다. 클라이언트는 기존 2.5와 같은 API를 사용한다.

## 사용 방법

기존 서버 ZIP의 콘솔에서 `Esc`로 선택/일시정지를 해제한 뒤 `stop`을 입력해 종료하고, 새 2.5.2 ZIP을 별도 폴더에 푼 뒤 `start-server.cmd`를 실행한다. 기본 DB 폴더 `.mmm/dev-server/postgres`를 그대로 사용하므로 계정과 기록을 이어 읽는다. 이번 변경에는 DB 스키마 변경이 없다. 기존에 --data 옵션을 사용했다면 새 서버에도 같은 경로를 지정한다.

`view-logs.cmd`를 실행하면 최근 40줄과 새 활동이 실시간으로 표시된다. 소스 작업 폴더에서는 `devserver/view-logs.cmd`다. 서버 창에서는 `ACTIVITY`로 표시된 줄을 찾는다.

`remote.cmd`에서 서버로 제출한 작업을 기록한다. `local.cmd`·`demo.cmd`는 로컬 파일 모드이므로 이 서버 로그를 만들지 않는다. 편집기에서 음을 움직이거나 메모를 고친 뒤 **하루 저장**까지 눌러야 서버 저장/수정 로그가 남는다.

## 2.5.2 응답 시간 초과 수정

2.5.1의 실행 중 스레드 덤프에서 첫 요청이 DispatcherServlet 초기화의 동기 콘솔 출력에 걸리고, 다른 요청들이 같은 초기화 잠금을 기다리는 상태를 확인했다. 이때 활동 필터에 도달하지 못해 가입·로그인 로그도 없었다. Windows 콘솔의 선택/일시정지처럼 stdout이 멈추는 상태에서 발생할 수 있다.

2.5.2는 상태·활동 로그 모두 파일에 먼저 기록하고, 콘솔 복사본은 각각 별도 비동기 스레드로 보낸다. 큐 256개가 가득 차도 HTTP 스레드를 대기시키지 않는다. 이때 콘솔 복사본 일부는 생략될 수 있으므로 활동 확인 기준은 `activity.log`다. 파일 기록은 콘솔 큐의 생략 정책을 적용하지 않는다. 동작 근거는 [Logback AsyncAppender의 neverBlock 옵션](https://logback.qos.ch/manual/appenders-async-sift.html)이다.

가입·로그인의 시간 초과 안내를 작업에 맞게 바꿨다. 가입 응답이 늦으면 서버에서는 이미 가입됐을 수 있으므로 같은 아이디로 로그인을 먼저 시도한다. 비밀번호나 가입을 자동 재전송하지 않는다.

| 파일 | 용도 |
|---|---|
| `%USERPROFILE%\.mmm\logs\activity.log` | 로그인·저장·수정 등 요청별 결과 |
| `%USERPROFILE%\.mmm\logs\server.log` | 시작·DB 연결·프레임워크 메시지 |
| `%USERPROFILE%\.mmm\logs\archive\` | 날짜·크기에 따라 보관한 압축 로그 |

`MMM_LOG_DIR` 환경 변수로 폴더를 바꿀 수 있다. 서버와 로그 보기에 같은 값을 지정한다. 같은 PC에서 서버를 여러 개 실행할 때는 각각 다른 로그 폴더를 사용한다. 로그는 UTF-8과 Asia/Seoul 시각으로 기록하고, 각 파일 10MB 또는 날짜 변경 시 순환하며 최근 14일·각 로그 종류 200MB 범위의 보관 정책을 적용한다.

```powershell
Get-Content "$env:USERPROFILE\.mmm\logs\activity.log" -Encoding UTF8 -Tail 40 -Wait
```

## 읽는 방법

아래는 필드를 줄여 표시한 형식 예다.

```text
event=LOGIN result=SUCCESS user=demo ... committed=true changed=true http=200 code=OK
event=RECORD_CREATE result=SUCCESS user=demo ... expectedVersion=0 version=1 ...
event=RECORD_UPDATE result=SUCCESS user=demo ... expectedVersion=1 version=2 ...
event=RECORD_REPLAY result=REPLAY user=demo ... committed=true changed=false ...
event=RECORD_UPDATE result=FAILURE user=demo ... committed=false changed=false http=409 code=VERSION_CONFLICT
```

- `user`: 서버가 확인한 사용자 ID. 인증 전 실패는 `-`, 입력한 ID는 별도 `attemptedUser`에 기록한다. 허용 형식 밖 입력은 INVALID로 가린다.
- `songId`, `date`: 대상 곡과 음악 기록 날짜.
- `expectedVersion`, `version`: 요청한 기준 버전과 결과/확인한 현재 버전.
- `noteCount`, `restCount`, `expressionCount`: 본문 대신 개수.
- `requestId`: 같은 음악 저장 재시도를 묶는 UUID. 다른 HTTP 시도에도 유지될 수 있다.
- `traceId`: HTTP 요청마다 새 UUID. 응답의 `X-MMM-Trace-Id`와 같다.
- `http`, `code`: 응답 상태와 원인 코드. 실패 입력·권한·세션 만료·버전 충돌·저장소 실패를 구분한다.
- `committed`, `changed`: 작업의 트랜잭션 확정 및 실제 변경 여부. 조회는 둘 다 false다. 재시도 응답 재사용은 committed=true, changed=false다.
- `result=UNCONFIRMED`: HTTP가 성공해도 쓰기 트랜잭션 확정을 확인하지 못했을 때 사용하는 보호 표시다. 정상 구현의 쓰기 API는 커밋 확인 후 SUCCESS가 된다.

이벤트 이름은 SIGNUP, LOGIN, LOGOUT, SONG_CREATE, SONG_UPDATE, RECORD_CREATE, RECORD_UPDATE, RECORD_REPLAY, INVITE_CREATE, INVITE_REDEEM, MEMBER_LEAVE, SETTINGS_UPDATE 등을 사용한다. 정상 health 확인은 제외하고 조회·오류 요청도 기록한다.

DB 커밋 확인은 Spring TransactionSynchronization.afterCommit에서 받는다. SQL 실행을 마쳤더라도 최종 커밋이 실패하면 성공 표시를 하지 않는다. 커밋 후 응답 전송이 실패한 경우 HTTP 실패와 committed=true가 함께 보일 수 있으므로 두 필드를 함께 확인한다.

비밀번호·솔트/해시·세션 토큰·초대 코드·닉네임·곡명·메모·음 목록, 원문 URL/쿼리·HTTP 헤더·요청/응답 본문·예외 메시지는 활동 로그에 넣지 않는다. URL은 고정된 경로 템플릿으로만 기록한다. 로그는 이 버전부터 생성되며 이전 활동을 소급해서 복원하지 않는다.

## 검증

기존 19개 서버 시나리오에 콘솔 정지 검증을 추가해 **20개 통과, 실패/오류/생략 0**을 확인했다. 실제 PostgreSQL과 HTTP(S), 원격 Swing 흐름을 실행했다.

1. 로그인·곡 생성/수정·음악 저장/수정·재시도·충돌·설정·로그아웃 결과, 파일 출력과 추적 번호.
2. 두 로그 파일에 비밀번호·토큰·해시·초대 코드·메모 등이 없고 줄바꿈/URL을 통한 로그 주입이 차단되는지 확인.
3. 실제 DB 지연 제약 트리거로 최종 커밋 실패를 일으켜 데이터 롤백과 성공 로그 부재를 확인.
4. 상태·활동 콘솔 출력을 모두 중단하고 각 큐를 포화시킨 뒤 health·회원가입·로그인·곡 생성·음악 저장/수정·실패 로그인이 응답하는지, 포화 전후 이벤트가 파일에 빠짐없이 남는지 확인.

빌드: `mvnw.cmd -B -ntp -pl server -am test -Dmmm.gui.tests=true`. 기록: `app/build/console-fix-tests.log`, `server/target/surefire-reports/`, `server/target/test-logs/`.

패키지 2.5.1 개발 서버를 별도 임시 DB와 로그 폴더에서 실행해 가입·로그인·곡 생성/수정·기록 저장/수정·재시도·충돌·실패 로그인·로그아웃의 콘솔/UTF-8 파일 출력을 확인했다. `view-logs.cmd -Once`로 지정한 폴더의 기록을 읽는 동작도 통과했다. 사용 중인 개인 DB와 서버 프로세스는 변경하지 않았다.

2.5.2 패키지는 Oracle Java **26.0.2.1** 서버·클라이언트로 추가 검사했다. 서버 stdout 파이프를 읽지 않아 실제 출력 스레드가 막힌 것을 스레드 덤프로 확인하고, 650개 HTTP 요청으로 콘솔 큐를 포화시킨 뒤 실제 RemoteRepository/MusicService로 가입·로그인·곡 생성·저장·수정·조회·로그아웃을 완료했다. 그동안 활동 파일 기록도 남았고, 정상 종료 후 같은 임시 DB를 재시작해 음악 버전 2·메모·음을 다시 읽었다. UTF-8 로그 보기 검사도 통과했다. 기록은 `app/build/console-fix-packaged-smoke.log`다. 개인 DB와 실행 중인 기존 서버는 수정하지 않았다.

참고: [Spring 트랜잭션 콜백](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/transaction/support/TransactionSynchronization.html), [Logback Appenders](https://logback.qos.ch/manual/appenders.html).
