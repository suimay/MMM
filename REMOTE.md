# MMM 2.6.0 원격 개발·실행

Java 21 대상 Spring Boot 3.5.16, JDBC, PostgreSQL, Java HttpClient로 구현했다. Swing은 DB에 직접 접속하지 않는다. 로컬·원격 저장은 실행 모드로 구분한다.

## 같은 컴퓨터에서 공동 기록 확인

소스에서는 `devserver/start.cmd` → `app/remote.cmd` 순서로 실행한다. Windows 실행 ZIP에서는 `start-server.cmd` → `remote.cmd` 순서다. Java 런타임이 필요하고 PostgreSQL은 따로 설치하지 않아도 된다.

개발 서버는 실제 PostgreSQL 14.22를 시작한다. HTTP는 `127.0.0.1:8080`, DB는 `127.0.0.1:54329`에만 바인딩한다. 데이터는 사용자 홈의 `.mmm/dev-server/postgres`에 보관한다. 로컬 개발 DB 연결은 trust 인증이다. 외부 기기용으로는 아래 일반 서버를 사용한다.

클라이언트 두 개에서 서로 다른 계정을 가입한다. A가 공동 곡을 만들고 초대 코드를 발급하면 B가 **초대 참여**에서 입력한다. A의 **하루 저장** 후 B의 **새로고침**으로 기록을 불러온다. 합주는 내려받은 기록을 같은 기기의 MIDI에서 동시에 재생한다.

개발 서버 옵션은 `--port=18080 --db-port=54330 --data=원하는경로`다. `--stdin-control`을 붙이면 콘솔의 `stop` 입력으로 앱과 PostgreSQL을 정상 종료한다. 기록은 재시작 후 유지된다. 같은 DB 폴더를 두 서버가 동시에 사용하지 않는다.

2.5.1부터 활동 로그는 `%USERPROFILE%\.mmm\logs\activity.log`, 서버 상태 로그는 같은 폴더의 `server.log`다. `view-logs.cmd`로 실시간 확인하며 `MMM_LOG_DIR`로 경로를 바꾼다. [로그 읽는 방법과 업데이트](app/docs/ACTIVITY-LOGGING.md)를 참고한다.

2.5.2는 파일 기록과 콘솔 출력을 분리했다. 콘솔이 정지되거나 화면 출력 대기열이 포화돼도 HTTP 처리는 계속하며 파일 로그를 남긴다. 포화 상태에서는 화면용 복사본만 생략할 수 있다. 이전 버전 서버 창이 선택/일시정지 상태면 `Esc` 후 `stop`으로 정상 종료하고 새 버전을 시작한다.

2.5.4의 확장 음역은 MIDI 0–127(C-1–G9)이다. 원격 참여자 모두 서버와 클라이언트를 2.5.4로 업데이트한다. 서버 시작 시 Flyway V2가 기존 notes의 음역 CHECK를 확장한다. V1 파일과 기존 계정·곡·노트 값은 유지한다. 이전 클라이언트와 서버는 48–84 밖의 노트를 처리할 수 없다.

2.6.0은 앱 악기 ID 128을 드럼 키트로 사용한다. 0–127은 기존 MIDI 프로그램으로 유지한다. 새 선택지는 0 피아노, 33 베이스, 24 기타, 128 드럼이다. 원격 드럼 저장에는 서버와 클라이언트 모두 2.6.0이 필요하다. V3는 songs/bar_records/user_settings의 악기 제약을 0–128로 확장하고 기존 마림바 12·오르골 10 기록을 변환하지 않는다. V1/V2 파일은 유지한다.

## 일반 서버와 다른 기기 연결

일반 서버는 `server/target/MMM-server-exec.jar` 또는 Windows 묶음의 `server/MMM-server.jar`다. 설정을 환경 변수로 전달한다.

| 변수 | 의미 |
|---|---|
| `MMM_DB_URL` | 서버의 JDBC URL, 예: `jdbc:postgresql://127.0.0.1:5432/mmm` |
| `MMM_DB_USER`, `MMM_DB_PASSWORD` | PostgreSQL 계정·비밀값 |
| `MMM_BIND_ADDRESS` | 기본 127.0.0.1, 외부 TLS 서비스의 바인딩 주소 |
| `MMM_PORT` | 기본 8080 |
| `MMM_SESSION_DURATION` | 기본 PT12H, 0 초과 30일 이하 |
| `MMM_LOG_DIR` | 기본 사용자 홈/.mmm/logs, 서버 활동/상태 로그 폴더 |
| `SERVER_SSL_ENABLED` | Boot 자체 TLS를 사용할 때 true |
| `SERVER_SSL_KEY_STORE`, `SERVER_SSL_KEY_STORE_PASSWORD`, `SERVER_SSL_KEY_STORE_TYPE` | 인증서 저장소·비밀값·형식(PKCS12 등) |

```powershell
# DB와 TLS 비밀값은 실행 환경에서 공급한다.
java -jar server/target/MMM-server-exec.jar
```

Boot TLS 또는 신뢰할 수 있는 인증서의 HTTPS 프록시를 사용한다. 프록시 뒤 HTTP 백엔드는 루프백을 유지한다. 클라이언트에는 `MMM_SERVER_URL=https://서버주소:포트`, `--server=https://서버주소:포트` 또는 `remote.cmd HTTPS주소`를 지정한다.

클라이언트는 외부 HTTP·URL 내 자격 정보·리다이렉트를 거부한다. localhost HTTP는 개발용으로만 허용한다. 자체 서명 인증서는 신뢰 저장소에 등록하며 TLS 검증을 끄는 기능은 제공하지 않는다. DB 비밀값을 Swing 설정에 넣지 않는다.

`compose.yaml`은 PostgreSQL 17 DB를 제공한다. `MMM_DB_PASSWORD`를 외부에서 설정한 뒤 `docker compose up -d`로 시작한다. 기본 루프백 포트 5432, DB명 mmm다. 이번 환경은 Docker 데몬이 중지돼 Compose 실행은 검사하지 않았고 native PostgreSQL 14.22로 통합 검사를 수행했다.

## API 계약

UTF-8 JSON과 `/api`를 사용한다. 인증 후 `Authorization: Bearer 토큰`을 보낸다. 토큰은 클라이언트 메모리에만 보관하고 서버에는 SHA-256 해시·만료·폐기 시각을 저장한다. 비밀번호는 솔트 16바이트, PBKDF2-HMAC-SHA256 120000회, 256비트로 검증하며 응답에 솔트·해시를 넣지 않는다.

| 메서드·경로 | 요청 또는 결과 |
|---|---|
| POST /auth/signup | loginId, nickname, password → 공개 사용자(201) |
| POST /auth/login | loginId, password → token, user, serverToday, expiresAt |
| POST /auth/logout | 토큰 폐기(204) |
| GET /me | 공개 사용자와 서버의 서울 날짜 |
| GET /songs | 활성 참여 곡 목록 |
| POST /songs | title, type=PERSONAL/SHARED, bpm, instrument → 곡(201) |
| PATCH /songs/{id} | title, bpm, instrument, expectedVersion, 소유자만 변경 |
| GET /songs/{id}/members | 활성 참여자 공개 ID·닉네임·역할 |
| POST /songs/{id}/invites | 소유자에게 code, expiresAt(201) |
| POST /invites/redeem | code → 참여 곡, 만료·중복·15명 제한 검사 |
| DELETE /songs/{id}/members/me | 일반 참여자 탈퇴(204), 기여 기록 보관 |
| GET /songs/{id}/records?from=&to= | 날짜 범위 1~366일 조회 |
| PUT /songs/{id}/records/{date}/me | SaveBody → 저장된 기록 |
| GET 또는 PUT /me/settings | volume, instrument, countIn, metronome, countInBeats |
| GET /health | 비인증 상태 확인, status=ok |

SaveBody는 requestId(UUID), expectedVersion, memo, bpm, instrument, notes, rests, expressions, schemaVersion=2를 받는다.

- notes: startTick, durationTick, pitch=0–127, velocity=1–127, articulation=NORMAL/STACCATO.
- rests: startTick, durationTick.
- expressions: startTick, endTick, kind=CRESCENDO, startVelocity, endVelocity.
- 기록 응답은 songId, authorId, date, version과 같은 음악 필드를 가진다.

최초 expectedVersion은 0, 결과는 1이며 수정마다 1 증가한다. 버전 값은 필수다. 작성자는 세션에서 정하고 서버의 오늘만 저장한다. 임의 authorId 등 미정의 필드, 중복 JSON 키, 소수 tick·문자열 숫자·범위 밖 데이터는 거부한다.

같은 사용자·requestId·내용은 최초 응답을 반환한다. 후속 버전이 생겨도 재시도는 최초 버전 응답이다. 같은 ID의 다른 내용은 409다. 기록·자식 음·쉼·표현·SaveRequest를 한 트랜잭션으로 저장/롤백한다. 곡 행 잠금으로 저장·설정·참여 변경을 직렬화하고 조회는 REPEATABLE READ로 일관된 음악 데이터를 읽는다.

초대는 7일이며 코드 해시만 DB에 남긴다. 소유자 탈퇴는 차단한다. 참여자 탈퇴 후 조회·저장은 금지하고 기여 기록은 보관한다.

오류는 code와 message를 가진다. 400 입력, 401 인증, 403 권한/오늘 제한, 404 없음, 409 충돌, 413 크기, 503 저장소 실패를 구분한다. SQL·비밀값·스택을 본문에 넣지 않는다. 요청 본문 제한 128KiB, 클라이언트 연결 5초·요청 15초이며 무기한 자동 재시도는 없다.

공개 사용자 ID와 DB 사용자 PK는 변경 불가 loginId를 사용한다. 기존 로컬 author/member 식별자를 유지하기 위한 초안 대비 결정이다. 곡·기록·요청 ID는 UUID다. 가입 규칙은 ID 영문·숫자·밑줄 3~24자, 닉네임 1~20 코드포인트, 비밀번호 8~256 UTF-16 코드 유닛이며 로컬/원격이 같은 검사를 쓴다.

## 초안과 호환

실패·시간 초과·충돌은 편집기를 유지한다. 동일 초안 재시도는 같은 requestId를 사용한다. **최신 저장본 보기**는 별도 읽기 전용 창이며 초안을 덮어쓰지 않는다. 세션 만료 시 같은 계정 재로그인을 화면 안에서 진행하고 저장은 사용자가 다시 시도한다. 날짜가 바뀐 초안은 보존하고 서버가 저장을 거부한다.

로컬 파일은 schema 1/2를 읽고 schema 3으로 쓴다. 곡 버전 필드를 추가하며 이전 정상 파일은 백업한다. 구버전 2.4는 schema 3을 거부해 버전 필드를 지우지 못한다. 원격 음악 기록의 schemaVersion은 별도로 2다.

앱 종료 후 초안 자동 복원·충돌 자동 병합·실시간 연주 스트리밍은 추가하지 않았다. 로컬 계정을 서버로 자동 이관하지 않으며 원격 계정은 서버에 새로 가입한다.

## 빌드·검증

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\build-m2.ps1 -GuiTests
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\package-m2.ps1
```

GUI 없는 환경은 -GuiTests를 생략한다. Maven package는 서버 API 검사를, -Dmmm.gui.tests=true는 원격 Swing도 수행한다. 기존 main 기반 클라이언트 검사는 app/build.ps1 또는 app/build.sh로 실행한다. build-m2.ps1은 두 검사를 함께 실행한다.

[M2 검증 기록](app/docs/M2-VERIFICATION.md), [Spring Boot](https://docs.spring.io/spring-boot/3.5/system-requirements.html), [Embedded PostgreSQL](https://github.com/zonkyio/embedded-postgres), [Microsoft OpenJDK](https://learn.microsoft.com/en-us/java/openjdk/download)를 참고한다.
