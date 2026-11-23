# Reproducible Windows file-replacement probe for the PART 40.3 JAR replacement spike.
# It uses text files as stand-ins for a plugin JAR because the relevant behavior is the
# Win32 file-sharing rule, not the ZIP contents.

$ErrorActionPreference = 'Stop'

$dir = Join-Path $env:TEMP 'dmc-jar-replace-spike'
$target = Join-Path $dir 'plugin.jar'
$source = Join-Path $dir 'replacement.jar'
$backup = Join-Path $dir 'plugin.jar.bak'

Remove-Item -Recurse -Force $dir -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Path $dir | Out-Null

Set-Content -Path $target -Value 'old-exclusive'
Set-Content -Path $source -Value 'new-exclusive'
Remove-Item $backup -Force -ErrorAction SilentlyContinue
$exclusive = [System.IO.FileStream]::new(
  $target,
  [System.IO.FileMode]::Open,
  [System.IO.FileAccess]::ReadWrite,
  [System.IO.FileShare]::None
)
try {
  [System.IO.File]::Replace($source, $target, $backup)
  Write-Output 'replace-open-without-share-delete=pass'
} catch {
  $inner = $_.Exception.InnerException
  Write-Output ('replace-open-without-share-delete=fail: ' + $_.Exception.GetType().Name + ' inner=' + $inner.GetType().Name + ' hresult=0x' + ('{0:X8}' -f ($inner.HResult -band 0xFFFFFFFF)))
}
$exclusive.Dispose()

Set-Content -Path $target -Value 'old-share-delete'
Set-Content -Path $source -Value 'new-share-delete'
Remove-Item $backup -Force -ErrorAction SilentlyContinue
$shared = [System.IO.FileStream]::new(
  $target,
  [System.IO.FileMode]::Open,
  [System.IO.FileAccess]::ReadWrite,
  [System.IO.FileShare]::ReadWrite -bor [System.IO.FileShare]::Delete
)
try {
  [System.IO.File]::Replace($source, $target, $backup)
  Write-Output 'replace-open-with-share-delete=pass'
} catch {
  $inner = $_.Exception.InnerException
  Write-Output ('replace-open-with-share-delete=fail: ' + $_.Exception.GetType().Name + ' inner=' + $inner.GetType().Name + ' hresult=0x' + ('{0:X8}' -f ($inner.HResult -band 0xFFFFFFFF)))
}
$shared.Dispose()

Write-Output ('target-content=' + (Get-Content $target -Raw).Trim())
Write-Output ('backup-content=' + (Get-Content $backup -Raw -ErrorAction SilentlyContinue).Trim())
Write-Output ('dotnet=' + [System.Environment]::Version.ToString())
