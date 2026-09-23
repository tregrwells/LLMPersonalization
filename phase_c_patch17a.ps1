$ErrorActionPreference = "Stop"
$qc  = ".\app\src\main\java\com\treg\llmpersonalization\logic\QueryClassifier.kt"
$rtr = ".\app\src\main\java\com\treg\llmpersonalization\logic\Retriever.kt"

$stamp = Get-Date -Format "yyyyMMdd_HHmmss"
Copy-Item $qc  "$qc.$stamp.bak"  -Force
Copy-Item $rtr "$rtr.$stamp.bak" -Force
Write-Host "Backups written"

# --- 1. QueryClassifier: swap qtype order ---
$raw = [System.IO.File]::ReadAllText($qc)
$oldOrder = "            FACT_RE.containsMatchIn(ql)   -> ""fact""
            REASON_RE.containsMatchIn(ql) -> ""reasoning"""
$newOrder = "            REASON_RE.containsMatchIn(ql) -> ""reasoning""
            FACT_RE.containsMatchIn(ql)   -> ""fact"""
if ($raw.Contains($newOrder)) {
    Write-Host "[1] qtype order already fixed"
} elseif ($raw.Contains($oldOrder)) {
    $raw = $raw.Replace($oldOrder, $newOrder)
    [System.IO.File]::WriteAllText($qc, $raw, [System.Text.UTF8Encoding]::new($false))
    Write-Host "[1] qtype order fixed"
} else {
    Write-Host "[1] WARN: qtype block not found - check file manually"
    Select-String -Path $qc -Pattern "FACT_RE|REASON_RE|val qt" | ForEach-Object { Write-Host "  " $_.LineNumber ": " $_.Line }
}

# --- 2. Retriever: add score logging ---
$raw2 = [System.IO.File]::ReadAllText($rtr)
if ($raw2 -notmatch 'import android.util.Log') {
    $raw2 = $raw2.Replace("package com.treg.llmpersonalization.logic", "package com.treg.llmpersonalization.logic

import android.util.Log")
}
$marker = "// --- 7. Top-k with score attached ---"
if ($raw2.Contains('Log.i("Retriever"')) {
    Write-Host "[2] Retriever logging already present"
} elseif ($raw2.Contains($marker)) {
    $logBlock = $marker + "
        run {
            val top5 = scored.take(5).joinToString("" "") { (m, s) -> ${m.key}= + "%.3f".format(s) }
            Log.i(""Retriever"", ""q='${query.take(40)}' sub=${ctx.subject} temp=${ctx.temporal} k=$k top5: $top5"")
        }"
    $raw2 = $raw2.Replace($marker, $logBlock)
    [System.IO.File]::WriteAllText($rtr, $raw2, [System.Text.UTF8Encoding]::new($false))
    Write-Host "[2] Retriever logging added"
} else {
    Write-Host "[2] WARN: Retriever marker not found"
    Select-String -Path $rtr -Pattern "Top-k|scored.take" | ForEach-Object { Write-Host "  " $_.LineNumber ": " $_.Line }
}

# --- 3. Rebuild ---
if (-not $env:JAVA_HOME) {
    $env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
    $env:Path = "$env:JAVA_HOME\bin;$env:Path"
}
Write-Host "Rebuilding..."
& ".\gradlew.bat" :app:assembleDebug 2>&1 | Select-String -Pattern "BUILD|FAILED|error:" | Select-Object -Last 8
