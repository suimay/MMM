param([switch]$GuiTests)
$ErrorActionPreference = 'Stop'
$previousJava = $env:JAVA_HOME
try {
    $portableJdk = Join-Path $PSScriptRoot 'app\tools\jdk21'
    if (Test-Path -LiteralPath (Join-Path $portableJdk 'bin\javac.exe')) { $env:JAVA_HOME = $portableJdk }
    Push-Location -LiteralPath $PSScriptRoot
    try {
        $mavenArgs = @('-B','-ntp','-Pwindows-dev','package')
        if ($GuiTests) { $mavenArgs += '-Dmmm.gui.tests=true' }
        & '.\mvnw.cmd' @mavenArgs
        if ($LASTEXITCODE -ne 0) { throw 'M2 Maven build/tests failed.' }
        $localArgs = @()
        if ($GuiTests) { $localArgs += '-GuiTests' }
        & powershell.exe -NoProfile -ExecutionPolicy Bypass -File '.\app\build.ps1' @localArgs
        if ($LASTEXITCODE -ne 0) { throw 'Local regression tests failed.' }
    } finally { Pop-Location }
} finally { $env:JAVA_HOME = $previousJava }
