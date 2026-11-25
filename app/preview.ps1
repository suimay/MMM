param([switch]$BuildOnly,[string]$DataDirectory)
$ErrorActionPreference = 'Stop'
$appRoot = $PSScriptRoot
$buildRoot = [IO.Path]::GetFullPath((Join-Path $appRoot 'build\development-preview'))
[IO.Directory]::CreateDirectory($buildRoot) | Out-Null
$log = Join-Path $buildRoot 'build.log'
$lastGood = Join-Path $buildRoot 'MMM-preview.jar'
$launchLock = $null
function Invoke-Compiler([string]$Tool,[string[]]$ToolArgs,[switch]$Append) {
    $previousPreference=$ErrorActionPreference
    try {
        $ErrorActionPreference='Continue'
        if ($Append) { & (Join-Path $jdkBin $Tool) @ToolArgs *>> $log }
        else { & (Join-Path $jdkBin $Tool) @ToolArgs *> $log }
        return $LASTEXITCODE
    } finally { $ErrorActionPreference=$previousPreference }
}
try {
    $launchLock = [IO.File]::Open((Join-Path $buildRoot 'launcher.lock'),[IO.FileMode]::OpenOrCreate,[IO.FileAccess]::ReadWrite,[IO.FileShare]::None)
    $portableBin = Join-Path $appRoot 'tools\jdk21\bin'
    if (Test-Path -LiteralPath (Join-Path $portableBin 'javac.exe')) { $jdkBin=$portableBin }
    elseif ($env:JAVA_HOME -and (Test-Path -LiteralPath (Join-Path $env:JAVA_HOME 'bin\javac.exe'))) { $jdkBin=Join-Path $env:JAVA_HOME 'bin' }
    else {
        $javac=(Get-Command javac.exe -ErrorAction Stop).Source
        $probe=New-Object Diagnostics.Process
        $probe.StartInfo.FileName=$javac
        $probe.StartInfo.Arguments='-J-XshowSettings:properties -version'
        $probe.StartInfo.UseShellExecute=$false
        $probe.StartInfo.CreateNoWindow=$true
        $probe.StartInfo.RedirectStandardError=$true
        $probe.StartInfo.RedirectStandardOutput=$true
        $null=$probe.Start()
        $settings=$probe.StandardError.ReadToEnd()
        $null=$probe.StandardOutput.ReadToEnd()
        $probe.WaitForExit();$probe.Dispose()
        if ($settings -notmatch '(?m)^\s*java.home = (.+?)\r?$') { throw 'A full JDK 21 or newer is required.' }
        $jdkBin=Join-Path $Matches[1].Trim() 'bin'
    }
    if (-not (Test-Path -LiteralPath (Join-Path $jdkBin 'jar.exe'))) { throw 'A full JDK 21 or newer is required.' }
    do {
        $classes=Join-Path $buildRoot 'classes'
        $resolved=[IO.Path]::GetFullPath($classes)
        if (-not $resolved.StartsWith($buildRoot+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)) { throw 'Invalid preview build directory.' }
        if (Test-Path -LiteralPath $resolved) { Remove-Item -LiteralPath $resolved -Recurse -Force }
        [IO.Directory]::CreateDirectory($classes) | Out-Null
        $sourceRoot=Join-Path $appRoot 'src\main\java'
        $sources=@(Get-ChildItem -LiteralPath $sourceRoot -Recurse -Filter '*.java' | ForEach-Object {$_.FullName})
        $compiled=$false
        $compilerExit=Invoke-Compiler 'javac.exe' (@('--release','21','-encoding','UTF-8','-d',$classes)+$sources)
        if ($compilerExit -eq 0) {
            Copy-Item -Path (Join-Path $appRoot 'resources\*') -Destination $classes -Recurse -Force
            $candidate=Join-Path $buildRoot 'candidate.jar'
            $jarExit=Invoke-Compiler 'jar.exe' @('--create','--file',$candidate,'--main-class','mmm.DevelopmentPreview','-C',$classes,'.') -Append
            if ($jarExit -eq 0) { Copy-Item -LiteralPath $candidate -Destination $lastGood -Force; $compiled=$true }
        }
        if (-not $compiled) {
            if ($BuildOnly) { throw "Preview build failed. See $log" }
            Add-Type -AssemblyName System.Windows.Forms
            $message="화면을 새로 만들지 못했습니다. 마지막 성공 버전을 다시 엽니다.`n빌드 기록: $log"
            if (-not (Test-Path -LiteralPath $lastGood)) { throw "Preview build failed. See $log" }
            [Windows.Forms.MessageBox]::Show($message,'개발 미리보기') | Out-Null
        }
        if ($BuildOnly) { Write-Output "Built $lastGood"; break }
        $clientArgs=@('-jar',$lastGood)
        if ($DataDirectory) { $clientArgs += "--data=$DataDirectory" }
        $previousPreference=$ErrorActionPreference
        try {
            $ErrorActionPreference='Continue'
            & (Join-Path $jdkBin 'java.exe') @clientArgs *>> (Join-Path $buildRoot 'runtime.log')
            $clientExit=$LASTEXITCODE
        } finally { $ErrorActionPreference=$previousPreference }
        if ($clientExit -notin @(0,75)) { throw "Preview exited with code $clientExit. See runtime.log in $buildRoot" }
    } while ($clientExit -eq 75)
} catch {
    if ($BuildOnly) { throw }
    Add-Type -AssemblyName System.Windows.Forms
    $message=$_.Exception.Message
    if ($null -eq $launchLock -and (Test-Path -LiteralPath (Join-Path $buildRoot 'launcher.lock'))) {
        $message='개발 미리보기가 이미 열려 있습니다. 작업 표시줄에서 MMM 창을 선택하세요.'
    }
    [Windows.Forms.MessageBox]::Show($message,'개발 미리보기') | Out-Null
    exit 1
} finally { if ($launchLock) { $launchLock.Dispose() } }
