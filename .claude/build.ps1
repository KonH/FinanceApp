& '.\build.ps1'
$log = Get-Content '.tmp\gradle\log.txt'
$last = $log | Select-Object -Last 30
$last | Write-Output
if ($log | Select-String -Quiet 'BUILD SUCCESSFUL') {
    Write-Output "`nRESULT: BUILD SUCCESSFUL"
} else {
    Write-Output "`nRESULT: BUILD FAILED"
    exit 1
}
