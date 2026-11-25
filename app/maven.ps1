param([Parameter(ValueFromRemainingArguments=$true)][string[]]$MavenArgs)
$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$toolsRoot = Join-Path $PSScriptRoot 'tools'
$mavenRoot = Join-Path $toolsRoot 'apache-maven-3.9.9'
if (-not (Test-Path -LiteralPath (Join-Path $mavenRoot 'bin\mvn.cmd'))) {
    New-Item -ItemType Directory -Path $toolsRoot -Force | Out-Null
    $uri = 'https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.9/apache-maven-3.9.9-bin.zip'
    $archive = Join-Path $toolsRoot 'apache-maven-3.9.9-bin.zip'
    Invoke-WebRequest -UseBasicParsing -Uri $uri -OutFile $archive
    $checksumContent = (Invoke-WebRequest -UseBasicParsing -Uri ($uri + '.sha512')).Content
    if ($checksumContent -is [byte[]]) { $checksumContent = [Text.Encoding]::UTF8.GetString($checksumContent) }
    $expectedHash = ($checksumContent.Trim() -split '\s+')[0]
    if ((Get-FileHash -Algorithm SHA512 -LiteralPath $archive).Hash -ne $expectedHash) { throw 'Maven checksum mismatch.' }
    Expand-Archive -LiteralPath $archive -DestinationPath $toolsRoot -Force
}
$previousJava = $env:JAVA_HOME
try {
    if (Test-Path -LiteralPath (Join-Path $toolsRoot 'jdk21\bin\javac.exe')) { $env:JAVA_HOME = Join-Path $toolsRoot 'jdk21' }
    elseif (-not $env:JAVA_HOME) {
        $probe = New-Object Diagnostics.Process
        $probe.StartInfo.FileName = (Get-Command javac.exe -ErrorAction Stop).Source
        $probe.StartInfo.Arguments = '-J-XshowSettings:properties -version'
        $probe.StartInfo.UseShellExecute = $false
        $probe.StartInfo.CreateNoWindow = $true
        $probe.StartInfo.RedirectStandardError = $true
        $probe.StartInfo.RedirectStandardOutput = $true
        $null = $probe.Start()
        $settingsText = $probe.StandardError.ReadToEnd()
        $null = $probe.StandardOutput.ReadToEnd()
        $probe.WaitForExit()
        if ($settingsText -match '(?m)^\s*java.home = (.+?)\r?$') { $env:JAVA_HOME = $Matches[1].Trim() }
        $probe.Dispose()
    }
    if (-not $env:JAVA_HOME) { throw 'Set JAVA_HOME to a JDK 21 installation.' }
    Push-Location -LiteralPath $repoRoot
    try { & (Join-Path $mavenRoot 'bin\mvn.cmd') @MavenArgs; $mavenExit = $LASTEXITCODE }
    finally { Pop-Location }
} finally { $env:JAVA_HOME = $previousJava }
exit $mavenExit
