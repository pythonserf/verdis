# Builds the Verdis mod into build\verdis.jar
# Usage:  .\build.ps1            - build only
#         .\build.ps1 -Install   - build and copy the jar into the game's mods folder
param([switch]$Install)
$ErrorActionPreference = "Stop"

$Root    = $PSScriptRoot
$Game    = "C:\Program Files (x86)\Steam\steamapps\common\Mindustry"
$GameJar = Join-Path $Game "jre\desktop.jar"
$ModsDir = Join-Path $Game "saves\mods"
$Jdk     = Join-Path $env:LOCALAPPDATA "Programs\Eclipse Adoptium\jdk-25.0.4.101-hotspot\bin"

$Build   = Join-Path $Root "build"
$Classes = Join-Path $Build "classes"
$OutJar  = Join-Path $Build "verdis.jar"

if(Test-Path $Build){ Remove-Item -Recurse -Force $Build }
New-Item -ItemType Directory -Force $Classes | Out-Null

# list of sources for javac
$Sources = Get-ChildItem -Recurse (Join-Path $Root "src") -Filter *.java | ForEach-Object { '"' + ($_.FullName -replace '\\','/') + '"' }
$ArgFile = Join-Path $Build "sources.txt"
[IO.File]::WriteAllLines($ArgFile, $Sources)

# the game's own jar is used as the library, so the API always matches the installed version
& (Join-Path $Jdk "javac.exe") --release 17 -encoding UTF-8 -nowarn -cp $GameJar -d $Classes "@$ArgFile"
if($LASTEXITCODE -ne 0){ throw "javac failed" }

# jar = compiled classes + assets (mod.hjson, bundles, sprites, maps)
& (Join-Path $Jdk "jar.exe") cf $OutJar -C $Classes . -C (Join-Path $Root "assets") .
if($LASTEXITCODE -ne 0){ throw "jar failed" }
Write-Host "Built: $OutJar"

if($Install){
    Copy-Item $OutJar (Join-Path $ModsDir "verdis.jar") -Force
    Write-Host "Installed: $(Join-Path $ModsDir 'verdis.jar')"
}
