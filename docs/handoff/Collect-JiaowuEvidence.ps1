param(
    [Parameter(Mandatory=$true)][string]$Serial,
    [string]$Adb = "$env:ANDROID_HOME\platform-tools\adb.exe",
    [string]$OutputDirectory = (Join-Path $PSScriptRoot '..\..\logs\campus')
)
$ErrorActionPreference = 'Stop'
if (!(Test-Path -LiteralPath $Adb)) { throw 'Set ANDROID_HOME or pass -Adb with the adb.exe path.' }
$deviceState = & $Adb -s $Serial get-state 2>$null
if ($LASTEXITCODE -ne 0 -or $deviceState -ne 'device') { throw 'Selected device is unavailable.' }
# Read only. Do not clear logcat, application data, or device settings.
$rawLines = & $Adb -s $Serial logcat -d -v brief 'JIAOWU:D' '*:S'
if ($LASTEXITCODE -ne 0) { throw 'Unable to read JIAOWU logs.' }
$rules = @(
    '^transport=WEBVPN$',
    '^WEBVPN_NATIVE_LOGIN_PAGE verified=true$',
    '^failure=WEBVPN_REQUIRED$',
    '^networkFailure=(TIMEOUT|TLS|DNS|CANCELLED|CONNECTION)$',
    '^(GRADES|EXAMS) records=[0-9]{1,6} saved=true$',
    '^(GET_LOGIN_PAGE|POST_LOGIN|PERSONAL_INFO|COURSES|GRADES|EXAMS) status=[1-5][0-9]{2}$',
    '^(sessionCookiePresent|studentIdParsed|identityMatch|localSessionSaved|bindingReady)=(true|false)$',
    '^dynamicFields sign=(true|false) hourKey=(true|false)$',
    '^loginClassification=(REDIRECT_SUCCESS|SCRIPT_SUCCESS)$',
    '^(?:(?:COURSES|GRADES|EXAMS) )?failure=(NETWORK_UNREACHABLE|LOGIN_PAGE_HTTP_ERROR|LOGIN_PAGE_PARSE_FAILED|SESSION_COOKIE_MISSING|CREDENTIAL_REJECTED|SCHOOL_VALIDATION_REJECTED|LOGIN_RESPONSE_UNKNOWN|PERSONAL_INFO_HTTP_ERROR|SESSION_EXPIRED|PERSONAL_INFO_PARSE_FAILED|IDENTITY_MISMATCH|BUSINESS_HTTP_ERROR|BUSINESS_STRUCTURE|BUSINESS_REDIRECT|TERM_UNCONFIRMED|COURSE_MAPPING|LOCAL_STORAGE_FAILED)$',
    '^COURSES mapped=[0-9]{1,6} records=[0-9]{1,6} saved=true termMatch=true$'
)
$safeLines = @(
    foreach ($line in $rawLines) {
        if ($line -match '^D/JIAOWU\s*\(\s*\d+\):\s*(.+)$') {
            $event = $Matches[1].Trim()
            if (@($rules | Where-Object { $event -match $_ }).Count -gt 0) { $event }
        }
    }
)
New-Item -ItemType Directory -Path $OutputDirectory -Force | Out-Null
$outputFile = Join-Path $OutputDirectory ('jiaowu-safe-' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '.txt')
@('JIAOWU allowlisted events; no raw log export.', 'Events can include earlier attempts from the current log buffer.', 'An empty report is not proof of success.') + $safeLines | Set-Content -LiteralPath $outputFile -Encoding UTF8
Write-Output "Saved $($safeLines.Count) allowlisted events to $outputFile"
