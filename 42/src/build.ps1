<#
Rebuild SmartTextureLeakFix.jar from the source file next to this script,
so you can run your own build instead of the one shipped with the mod.

  1. Install a JDK 21..25 (e.g. Eclipse Temurin, https://adoptium.net).
  2. Close Project Zomboid, then right-click this file > Run with PowerShell, or:
        powershell -ExecutionPolicy Bypass -File build.ps1 [-Jdk "C:\Program Files\Eclipse Adoptium\jdk-25..."]
  3. The JAR is written to ..\media\java\client\SmartTextureLeakFix.jar and its SHA-256 is
     printed.  ZombieBuddy shows that same hash in its approval dialog on the next
     game start - approve it if they match.

Paths are auto-detected for a default Steam install; pass -GameRoot / -ZombieBuddyJar
if yours differs.
#>
param(
    [string]$Jdk = '',
    [string]$GameRoot = 'C:\Program Files (x86)\Steam\steamapps\common\ProjectZomboid',
    [string]$ZombieBuddyJar = ''
)
$ErrorActionPreference = 'Stop'

function Find-Jdk {
    $candidates = @()
    if ($Jdk) { $candidates += $Jdk }
    if ($env:JAVA_HOME) { $candidates += $env:JAVA_HOME }
    $candidates += (Join-Path $GameRoot 'jre64')
    foreach ($root in @('C:\Program Files\Eclipse Adoptium', 'C:\Program Files\Java', 'C:\Program Files\Microsoft', 'C:\Program Files\Zulu')) {
        if (Test-Path $root) {
            $candidates += Get-ChildItem -LiteralPath $root -Directory | Sort-Object Name -Descending | ForEach-Object { $_.FullName }
        }
    }
    foreach ($c in $candidates) {
        if ((Test-Path (Join-Path $c 'bin\javac.exe')) -and (Test-Path (Join-Path $c 'bin\jar.exe'))) { return $c }
    }
    throw "No JDK found (need javac.exe + jar.exe). Install Eclipse Temurin 21..25 from https://adoptium.net or pass -Jdk <path>."
}

if (-not $ZombieBuddyJar) {
    $ZombieBuddyJar = Join-Path $GameRoot '..\..\workshop\content\108600\3619862853\mods\ZombieBuddy\libs\ZombieBuddy.jar'
}
$gameJar = Join-Path $GameRoot 'projectzomboid.jar'
if (-not (Test-Path $gameJar)) { throw "projectzomboid.jar not found under '$GameRoot' - pass -GameRoot." }
if (-not (Test-Path $ZombieBuddyJar)) { throw "ZombieBuddy.jar not found at '$ZombieBuddyJar' - subscribe to ZombieBuddy or pass -ZombieBuddyJar." }

$jdk = Find-Jdk
$javac = Join-Path $jdk 'bin\javac.exe'
$jar = Join-Path $jdk 'bin\jar.exe'
$src = Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot 'smarttextureleakfix') -Filter '*.java' -File
$classes = Join-Path $env:TEMP 'SmartTextureLeakFix_build'
$outDir = Join-Path $PSScriptRoot '..\media\java\client'
$outJar = Join-Path $outDir 'SmartTextureLeakFix.jar'

if (Test-Path $outJar) {
    try { $fs = [System.IO.File]::Open($outJar, 'Open', 'ReadWrite', 'None'); $fs.Close() }
    catch { throw "The game is holding '$outJar' (ZombieBuddy loads it at startup). Close Project Zomboid, then run this again." }
}
if (Test-Path $classes) { Remove-Item -Recurse -Force $classes }
New-Item -ItemType Directory -Force -Path $classes, $outDir | Out-Null

Write-Output "JDK: $jdk"
& $javac -nowarn -encoding UTF-8 -classpath "$gameJar;$ZombieBuddyJar" -d $classes @($src.FullName)
if ($LASTEXITCODE -ne 0) { throw 'Compilation failed; the existing JAR was not changed.' }
# --no-manifest + fixed entry date: the JAR bytes depend only on the class files, so
# the same JDK release reproduces the published SHA-256 exactly.
& $jar --create --no-manifest --date 2026-10-17T00:00:00Z --file $outJar -C $classes smarttextureleakfix
if ($LASTEXITCODE -ne 0) { throw 'JAR packaging failed.' }

$hash = (Get-FileHash -Algorithm SHA256 -LiteralPath $outJar).Hash.ToLower()
Write-Output "Built  $outJar"
Write-Output "SHA-256 $hash   <- ZombieBuddy shows this in its approval dialog"
