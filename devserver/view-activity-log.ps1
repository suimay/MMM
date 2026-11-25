param([switch]$Once)
$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = New-Object System.Text.UTF8Encoding($false)
$folder = $env:MMM_LOG_DIR
if (-not $folder) { $folder = Join-Path ([Environment]::GetFolderPath('UserProfile')) '.mmm\logs' }
$file = Join-Path $folder 'activity.log'
Write-Host "Activity log: $file"
if (-not (Test-Path -LiteralPath $file)) {
    Write-Host 'No activity log yet. Start the updated server, then open this viewer again.'
    exit 1
}
if ($Once) { Get-Content -LiteralPath $file -Encoding UTF8 -Tail 40 }
else {
    Write-Host 'Showing the last 40 entries and new activity. Press Ctrl+C to close.'
    Get-Content -LiteralPath $file -Encoding UTF8 -Tail 40 -Wait
}
