# ============================================================
# run_d2.ps1 — Phase D2 155-query on-device battery (FINAL)
# Requires: TestQueryReceiver installed, app running at Ready state
# ============================================================

$ErrorActionPreference = "Continue"
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
$pkg = "com.treg.llmpersonalization"
$queriesPath = "$env:USERPROFILE\Downloads\queries_v15.json"

if (-not (Test-Path $queriesPath)) { throw "queries_v15.json not found at $queriesPath" }
if (-not (Test-Path $adb)) { throw "adb not found" }

$battery = Get-Content $queriesPath -Raw | ConvertFrom-Json
$queries = $battery.queries
$categories = $battery.categories

Write-Host "`n=== Phase D2 — 155-query battery ===" -ForegroundColor Cyan
Write-Host "Queries: $($queries.Count)"

# Check app is running
$appPid = (& $adb shell pidof $pkg).Trim()
if (-not $appPid) {
    Write-Host "`nApp not running. Launching..." -ForegroundColor Yellow
    & $adb shell "am start -n $pkg/.MainActivity" | Out-Null
    Write-Host "Wait 15s for model load, then re-run this script." -ForegroundColor Yellow
    exit 1
}
Write-Host "App PID: $appPid`n"

& $adb logcat -c

$results = @()
$started = Get-Date
$i = 0

foreach ($q in $queries) {
    $i++
    $id = $q.id
    $cat = [int]$q.category
    $query = $q.query
    $expected = @($q.expected)
    $forbidden = @($q.forbidden)

    $pct = [int](100 * $i / $queries.Count)
    Write-Progress -Activity "D2 battery" -Status "$i/$($queries.Count) - $id ($pct%)" -PercentComplete $pct

    $b64 = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($query))

    & $adb shell "am broadcast -a com.treg.llmpersonalization.TEST_QUERY -p $pkg --es id $id --es q64 $b64 --es user treg --ei category $cat" | Out-Null

    # Poll logcat
    $timeout = 25
    $elapsed = 0
    $line = $null
    while ($elapsed -lt $timeout) {
        Start-Sleep -Milliseconds 500
        $elapsed++
        if ($elapsed % 2 -eq 0) {
            $raw = & $adb logcat -d -s TestQuery:I 2>&1
            $match = $raw | Select-String "id=$id " | Select-Object -Last 1
            if ($match) { $line = $match.Line; break }
        }
    }

    $record = [ordered]@{
        id = $id; category = $cat; query = $query
        elapsed_s = [Math]::Round($elapsed / 2, 1); logcat_line = $line
        path = $null; top = $null; score = $null; tools = 0
        response = $null; status = "UNKNOWN"; error = $null
        expected = ($expected -join " | "); forbidden = ($forbidden -join " | ")
    }

    if (-not $line) {
        $record.status = "TIMEOUT"
        $record.error = "No logcat line within ${timeout}s"
        $results += [PSCustomObject]$record
        continue
    }

    if ($line -match "id=(\S+)\s+cat=(\d+)\s+ms=(\d+)\s+path=(\S+)\s+top=(\S+)\s+score=(\S+)\s+tools=(\d+)\s+resp='(.*)'$") {
        $record.path = $Matches[4]
        $record.top = $Matches[5]
        $record.score = $Matches[6]
        $record.tools = [int]$Matches[7]
        $record.response = $Matches[8]
    } elseif ($line -match "ERROR") {
        $record.status = "ERROR"
        $record.error = $line
        $results += [PSCustomObject]$record
        continue
    }

    $resp = if ($record.response) { $record.response.ToString() } else { "" }
    $respLower = $resp.ToLower().Replace('`', "'")

    $expPass = $true
    if ($expected.Count -gt 0) {
        $expPass = $false
        foreach ($e in $expected) {
            if ($respLower.Contains($e.ToString().ToLower())) { $expPass = $true; break }
        }
    }

    $forbPass = $true
    foreach ($f in $forbidden) {
        $fLower = $f.ToString().ToLower()
        if ($respLower.Contains($fLower)) {
            $idx = $respLower.IndexOf($fLower)
            $start = [Math]::Max(0, $idx - 40)
            $window = $respLower.Substring($start, [Math]::Min(80, $respLower.Length - $start))
            if (-not ($window -match "\b(not|isn't|aren't|don't|doesn't|no|never)\b")) {
                $forbPass = $false; break
            }
        }
    }

    $record.status = if ($expPass -and $forbPass) { "PASS" } else { "FAIL" }
    $results += [PSCustomObject]$record
}

Write-Progress -Activity "D2 battery" -Completed
$ended = Get-Date
$elapsed_min = [Math]::Round(($ended - $started).TotalMinutes, 1)

Write-Host "`n`n========================================================" -ForegroundColor Cyan
Write-Host "=== D2 RESULTS ===" -ForegroundColor Cyan
Write-Host "========================================================" -ForegroundColor Cyan
Write-Host "Elapsed: $elapsed_min min"
Write-Host "Total queries: $($results.Count)"

$passed = ($results | Where-Object { $_.status -eq "PASS" }).Count
$failed = ($results | Where-Object { $_.status -eq "FAIL" }).Count
$timeout = ($results | Where-Object { $_.status -eq "TIMEOUT" }).Count
$errored = ($results | Where-Object { $_.status -eq "ERROR" }).Count

Write-Host ""
Write-Host "Overall: $passed / $($results.Count) = $([Math]::Round(100 * $passed / $results.Count, 1))%"
Write-Host "  PASS:    $passed" -ForegroundColor Green
Write-Host "  FAIL:    $failed" -ForegroundColor Yellow
Write-Host "  TIMEOUT: $timeout" -ForegroundColor Red
Write-Host "  ERROR:   $errored" -ForegroundColor Red

Write-Host "`n=== Per-category ===" -ForegroundColor Cyan
$byCat = $results | Group-Object category | Sort-Object { [int]$_.Name }
foreach ($g in $byCat) {
    $catNum = [int]$g.Name
    $catName = $categories.$catNum
    $catPassed = ($g.Group | Where-Object { $_.status -eq "PASS" }).Count
    $pct = [Math]::Round(100 * $catPassed / $g.Count, 0)
    $color = if ($pct -ge 90) { "Green" } elseif ($pct -ge 70) { "Yellow" } else { "Red" }
    Write-Host ("  C{0,2}: {1,3}/{2,3} ({3,3}%)  {4}" -f $catNum, $catPassed, $g.Count, $pct, $catName) -ForegroundColor $color
}

Write-Host "`n=== Failures ===" -ForegroundColor Cyan
$failures = $results | Where-Object { $_.status -ne "PASS" }
if ($failures.Count -eq 0) {
    Write-Host "  NONE — clean run!" -ForegroundColor Green
} else {
    foreach ($f in $failures) {
        Write-Host "`n  [$($f.id)] C$($f.category) — $($f.status)"
        Write-Host "    Query:     $($f.query)"
        Write-Host "    Expected:  $($f.expected)"
        Write-Host "    Forbidden: $($f.forbidden)"
        Write-Host "    Response:  $($f.response)"
        if ($f.error) { Write-Host "    Error:     $($f.error)" -ForegroundColor Red }
    }
}

$outPath = ".\d2_results_$(Get-Date -Format yyyyMMdd_HHmmss).json"
$results | ConvertTo-Json -Depth 4 | Out-File $outPath -Encoding utf8
Write-Host "`nFull results: $outPath" -ForegroundColor Cyan

$csvPath = ".\d2_results_$(Get-Date -Format yyyyMMdd_HHmmss).csv"
$results | Export-Csv -Path $csvPath -NoTypeInformation -Encoding utf8
Write-Host "CSV: $csvPath" -ForegroundColor Cyan