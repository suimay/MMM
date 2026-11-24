param([switch]$GuiTests)
$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot
$jdkBin = if ($env:JAVA_HOME -and (Test-Path -LiteralPath (Join-Path $env:JAVA_HOME 'bin\javac.exe'))) {
    Join-Path $env:JAVA_HOME 'bin'
} else { Split-Path -Parent (Get-Command javac.exe -ErrorAction Stop).Source }
if (-not (Test-Path -LiteralPath (Join-Path $jdkBin 'jar.exe'))) {
    # Oracle's PATH launcher directory may contain javac but no jar.
    $probe = New-Object Diagnostics.Process
    $probe.StartInfo.FileName = Join-Path $jdkBin 'javac.exe'
    $probe.StartInfo.Arguments = '-J-XshowSettings:properties -version'
    $probe.StartInfo.UseShellExecute = $false
    $probe.StartInfo.CreateNoWindow = $true
    $probe.StartInfo.RedirectStandardError = $true
    $probe.StartInfo.RedirectStandardOutput = $true
    $null = $probe.Start()
    $properties = $probe.StandardError.ReadToEnd()
    $null = $probe.StandardOutput.ReadToEnd()
    $probe.WaitForExit()
    if ($properties -match '(?m)^\s*java.home = (.+?)\r?$') { $jdkBin = Join-Path $Matches[1].Trim() 'bin' }
    $probe.Dispose()
}
if (-not (Test-Path -LiteralPath (Join-Path $jdkBin 'jar.exe'))) { throw 'A full JDK 21 or newer is required. Set JAVA_HOME.' }
function Invoke-Jdk([string]$Tool, [string[]]$ToolArgs) {
    & (Join-Path $jdkBin ($Tool + '.exe')) @ToolArgs
    if ($LASTEXITCODE -ne 0) { throw "$Tool failed (exit $LASTEXITCODE)." }
}
$buildRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot 'build'))
foreach ($name in @('classes', 'test-classes')) {
    $folder = [IO.Path]::GetFullPath((Join-Path $buildRoot $name))
    if (-not $folder.StartsWith($buildRoot + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
        throw 'Build output must stay inside app/build.'
    }
    if (Test-Path -LiteralPath $folder) { Remove-Item -LiteralPath $folder -Recurse -Force }
    New-Item -ItemType Directory -Path $folder -Force | Out-Null
}
$sources = @(Get-ChildItem -LiteralPath 'src\main\java' -Recurse -Filter '*.java' | ForEach-Object { $_.FullName })
Invoke-Jdk 'javac' (@('--release', '21', '-encoding', 'UTF-8', '-d', 'build/classes') + $sources)
Copy-Item -Path 'resources\*' -Destination 'build\classes' -Recurse -Force
Invoke-Jdk 'jar' @('--create', '--file', 'build/MMM.jar', '--main-class', 'mmm.MMM', '-C', 'build/classes', '.')
$tests = @(Get-ChildItem -LiteralPath 'src\test\java' -Recurse -Filter '*.java' | ForEach-Object { $_.FullName })
Invoke-Jdk 'javac' (@('--release', '21', '-encoding', 'UTF-8', '-cp', 'build/classes', '-d', 'build/test-classes') + $tests)
foreach ($test in @('ModelRepositoryTest', 'FeatureTest', 'SoundShapeTest', 'EnsembleDataTest', 'ExpressionTest', 'WireJsonTest', 'PianoTest', 'PitchRangeTest', 'InstrumentSoundTest', 'StringInstrumentTest')) {
    Invoke-Jdk 'java' @('-ea', '-Djava.awt.headless=true', '-cp', 'build/classes;build/test-classes', "mmm.$test")
}
if ($GuiTests) {
    Invoke-Jdk 'java' @('-ea', '-cp', 'build/classes;build/test-classes', 'mmm.GuiFlowTest')
    Invoke-Jdk 'java' @('-ea', '-cp', 'build/classes;build/test-classes', 'mmm.ExpressionGuiTest')
    Invoke-Jdk 'java' @('-ea', '-cp', 'build/classes;build/test-classes', 'mmm.PreviewGuiTest')
    Invoke-Jdk 'java' @('-ea', '-cp', 'build/classes;build/test-classes', 'mmm.InstrumentGuiTest')
    Invoke-Jdk 'java' @('-ea', '-cp', 'build/classes;build/test-classes', 'mmm.LiveSoundGuiTest')
}
Write-Output "Built $buildRoot\MMM.jar (Java 21 target)."
