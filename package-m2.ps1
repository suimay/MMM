param([string]$OutputDirectory)
$ErrorActionPreference = 'Stop'
if (-not $OutputDirectory) { $OutputDirectory = Join-Path (Split-Path -Parent $PSScriptRoot) 'output\development' }
$requiredFiles = @('app\target\MMM-client.jar','server\target\MMM-server-exec.jar','devserver\target\MMM-dev-server.jar')
foreach ($file in $requiredFiles) {
    if (-not (Test-Path -LiteralPath (Join-Path $PSScriptRoot $file))) { throw 'Run build-m2.ps1 before packaging.' }
}
$buildRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot 'app\build'))
$stageRoot = [IO.Path]::GetFullPath((Join-Path $buildRoot 'release-m2'))
if (-not $stageRoot.StartsWith($buildRoot + [IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)) { throw 'Invalid release staging path.' }
if (Test-Path -LiteralPath $stageRoot) { Remove-Item -LiteralPath $stageRoot -Recurse -Force }
$release = Join-Path $stageRoot 'MMM-2.6.0-Windows'
foreach ($folder in @($release,(Join-Path $release 'build'),(Join-Path $release 'server'),(Join-Path $release 'docs'))) {
    New-Item -ItemType Directory -Path $folder -Force | Out-Null
}
Copy-Item -LiteralPath (Join-Path $PSScriptRoot $requiredFiles[0]) -Destination (Join-Path $release 'build\MMM.jar')
Copy-Item -LiteralPath (Join-Path $PSScriptRoot $requiredFiles[1]) -Destination (Join-Path $release 'server\MMM-server.jar')
Copy-Item -LiteralPath (Join-Path $PSScriptRoot $requiredFiles[2]) -Destination (Join-Path $release 'server\MMM-dev-server.jar')
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'app\remote.cmd'),(Join-Path $PSScriptRoot 'app\demo.cmd'),(Join-Path $PSScriptRoot 'REMOTE.md'),(Join-Path $PSScriptRoot 'compose.yaml') -Destination $release
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'devserver\view-logs.cmd'),(Join-Path $PSScriptRoot 'devserver\view-activity-log.ps1') -Destination $release
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'app\resources\fonts\OFL.txt') -Destination (Join-Path $release 'OFL.txt')
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'app\docs\M2-VERIFICATION.md'),(Join-Path $PSScriptRoot 'app\docs\m2-editor.png'),(Join-Path $PSScriptRoot 'app\docs\m2-reauthenticated-draft.png') -Destination (Join-Path $release 'docs')
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'app\docs\ACTIVITY-LOGGING.md') -Destination (Join-Path $release 'docs')
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'app\docs\PIANO-DESIGN.md'),(Join-Path $PSScriptRoot 'app\docs\piano-keyboard.png'),(Join-Path $PSScriptRoot 'app\docs\piano-editor.png') -Destination (Join-Path $release 'docs')
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'app\docs\INSTRUMENT-SURFACES.md'),(Join-Path $PSScriptRoot 'app\docs\instrument-bass.png'),(Join-Path $PSScriptRoot 'app\docs\instrument-guitar.png'),(Join-Path $PSScriptRoot 'app\docs\instrument-drums.png') -Destination (Join-Path $release 'docs')
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'app\docs\LIVE-SOUND.md'),(Join-Path $PSScriptRoot 'app\docs\live-sound-growth.gif'),(Join-Path $PSScriptRoot 'app\docs\live-sound-editor.png') -Destination (Join-Path $release 'docs')
@'
@echo off
setlocal
set "MMM_JAVA=java"
if exist "%JAVA_HOME%\bin\java.exe" set "MMM_JAVA=%JAVA_HOME%\bin\java.exe"
"%MMM_JAVA%" -jar "%~dp0build\MMM.jar" %*
if errorlevel 1 pause
'@ | Set-Content -LiteralPath (Join-Path $release 'run.cmd') -Encoding ascii
@'
@echo off
setlocal
set "MMM_SERVER_URL="
call "%~dp0run.cmd" %*
'@ | Set-Content -LiteralPath (Join-Path $release 'local.cmd') -Encoding ascii
@'
@echo off
setlocal
set "MMM_JAVA=java"
if exist "%JAVA_HOME%\bin\java.exe" set "MMM_JAVA=%JAVA_HOME%\bin\java.exe"
"%MMM_JAVA%" -jar "%~dp0server\MMM-dev-server.jar" --stdin-control %*
if errorlevel 1 pause
'@ | Set-Content -LiteralPath (Join-Path $release 'start-server.cmd') -Encoding ascii
@'
@echo off
setlocal
set "MMM_JAVA=java"
if exist "%JAVA_HOME%\bin\java.exe" set "MMM_JAVA=%JAVA_HOME%\bin\java.exe"
"%MMM_JAVA%" -jar "%~dp0server\MMM-server.jar" %*
if errorlevel 1 pause
'@ | Set-Content -LiteralPath (Join-Path $release 'server.cmd') -Encoding ascii
@'
MMM 2.6.0 Windows

Java 21 권장. Java 런타임은 포함하지 않습니다.

1. ZIP 전체를 풀고 start-server.cmd를 실행하세요.
   실제 PostgreSQL 개발 서버가 http://127.0.0.1:8080에서 시작합니다.
2. remote.cmd를 실행해 회원가입하고 로그인하세요.
3. remote.cmd를 한 번 더 실행해 다른 계정으로 가입하세요.
4. A가 공동 곡을 생성하고 초대 코드를 발급하면 B가 초대 참여로 입력합니다.
5. A 저장 후 B 새로고침으로 같은 기록을 보고 합주할 수 있습니다.

활동 로그: view-logs.cmd를 열면 로그인·저장·수정의 성공/실패가 실시간으로 보입니다.
파일: 사용자 홈/.mmm/logs/activity.log (활동), server.log (서버 상태).
로그 폴더 변경: MMM_LOG_DIR 환경 변수. 각 로그는 날짜·10MB 기준으로 순환 보관됩니다.
로그 예: LOGIN SUCCESS, RECORD_CREATE SUCCESS, RECORD_UPDATE SUCCESS,
RECORD_REPLAY REPLAY (재저장하지 않고 최초 응답 반환), VERSION_CONFLICT FAILURE.
비밀번호·토큰·초대 코드·메모·음 본문은 로그에 넣지 않습니다.
상세 형식과 업데이트 방법은 docs/ACTIVITY-LOGGING.md를 확인하세요.
2.5.2: 콘솔 출력이 일시정지돼도 회원가입·로그인·저장이 멈추지 않도록 수정했습니다.
콘솔 대기열이 가득 차면 화면용 복사본만 생략하고 파일 로그는 계속 기록합니다.
이전 서버 창이 선택 상태로 멈췄다면 Esc를 누른 뒤 stop을 입력해 종료하세요.
회원가입 시간 초과 후에는 가입이 완료됐을 수 있으므로 같은 아이디로 로그인부터 확인하세요.

2.5.3: 참고 디자인에 맞춘 흰/검은 피아노 건반(C3–C6)으로 변경했습니다.
흰 건반: A S D F G H J K. 검은 건반: W E T Y U. 마우스 클릭/드래그도 연주됩니다.
5음계 모드에서는 도·레·미·솔·라만 활성화됩니다. docs/PIANO-DESIGN.md 참조.
2.5.4: 양옆 ‹ › 버튼 또는 건반의 ← →로 한 옥타브씩 이동합니다. Home은 기본 음역입니다.
MIDI 전체 128개 음(C-1–G9)을 연주·편집·저장하고 단축키도 현재 음역을 따릅니다.
확장 음역을 저장하려면 서버도 새 버전으로 교체하세요. 기존 계정·곡·음은 유지됩니다.
서버의 stop 입력으로 종료한 뒤 새 start-server.cmd → remote.cmd 순서로 실행하세요.
2.6.0: 피아노·베이스·기타·드럼을 선택하면 악기별 화면과 소리가 나옵니다.
베이스 4줄, 기타 6줄과 코드/노트, 드럼 키트를 클릭하거나 키보드로 연주합니다.
음 편집으로 소리 그림을 조절하고 새로 연주로 확인 후 입력을 비웁니다.
원격 드럼 저장에는 서버도 2.6.0이 필요하며 기존 곡·기록은 유지됩니다.

서버 콘솔에 stop을 입력하면 정상 종료합니다.
개발 DB와 기록은 사용자 홈/.mmm/dev-server/postgres에 보관합니다.
기본 HTTP 포트 8080, DB 포트 54329이며 같은 컴퓨터에서만 접속합니다.
다른 포트/폴더는 --port=18080 --db-port=54330 --data=경로 옵션을 사용합니다.

다른 기기에서 사용할 서버는 server.cmd와 별도 PostgreSQL, HTTPS를 설정하세요.
환경 변수와 전체 계약은 REMOTE.md에 있습니다.
클라이언트 주소 지정: remote.cmd https://서버주소:포트
개발용 로컬 DB의 trust 인증을 외부 서버에서 사용하지 마세요.

local.cmd: 서버 없이 로컬 계정/파일 사용.
demo.cmd: 2026-11-16 시연, 샘플 바로 듣기 또는 demo / demo1234.

기본 입력은 누른 길이대로입니다. 누르는 동안 그림이 자라고 떼면 1/4박에 맞춰 기록합니다.
연주 설정에서 고정 단계 입력·4박 녹음·연주 세기를 고릅니다. docs/LIVE-SOUND.md 참조.
클라이언트 기본/악기 323개 + Swing 218개, 서버 시나리오 24개 통과.
새 건반의 검은 건반 우선 판정·단축키·드래그·포커스 해제·5음계와 원격 반음 저장/조회를 검사했습니다.
독립 JVM 공유·멱등성·동시 저장·롤백·HTTPS·재로그인 초안·재시작 복원 검사.
실제 두 기기와 운영 네트워크/인증서 배포 검증은 별도입니다.
상세 범위는 docs/M2-VERIFICATION.md를 확인하세요.

Pretendard Regular/Bold와 OFL은 JAR 안에도 포함합니다. OFL.txt 참조.
Spring Boot, PostgreSQL, Embedded PostgreSQL 및 의존 라이브러리와 라이선스는
각 JAR 자원과 REMOTE.md의 공식 프로젝트 링크를 참고하세요.
'@ | Set-Content -LiteralPath (Join-Path $release 'README-ko.txt') -Encoding UTF8
$checksums = @(Get-ChildItem -LiteralPath (Join-Path $release 'build'),(Join-Path $release 'server') -Filter '*.jar' | ForEach-Object {
    $relative = $_.FullName.Substring($release.Length + 1).Replace('\','/')
    ((Get-FileHash -Algorithm SHA256 -LiteralPath $_.FullName).Hash.ToLowerInvariant() + '  ' + $relative)
})
$checksums | Set-Content -LiteralPath (Join-Path $release 'SHA256SUMS.txt') -Encoding ascii
New-Item -ItemType Directory -Path $OutputDirectory -Force | Out-Null
$zip = Join-Path $OutputDirectory 'MMM-2.6.0-Windows.zip'
Compress-Archive -Path $release -DestinationPath $zip -Force
Get-Item -LiteralPath $zip | Select-Object FullName,Length
