param([switch]$SkipSync,[string]$Task='assembleRelease')
$ErrorActionPreference='Stop'
$sourceRoot=$PSScriptRoot
$toolsRoot=Join-Path $sourceRoot 'android\.tools'
$stageRoot=Join-Path $env:TEMP 'link-react-native-v5'
if(-not ([IO.Path]::GetFullPath($stageRoot)).StartsWith(([IO.Path]::GetFullPath($env:TEMP)),[StringComparison]::OrdinalIgnoreCase)){throw 'Invalid staging directory'}
New-Item -ItemType Directory -Path $stageRoot -Force | Out-Null
if(-not $SkipSync){
 # Only exclude this app's build outputs. Dependencies ship required JS inside build/.
 & robocopy $sourceRoot $stageRoot /E /XD .git .gradle artifacts .cxx .jest-cache "$toolsRoot" "$sourceRoot\android\build" "$sourceRoot\android\app\build" /XF local.properties /NFL /NDL /NJH /NJS /NP
 if($LASTEXITCODE -ge 8){throw "Source copy failed: $LASTEXITCODE"}
}
function Sync-Tool([string]$From,[string]$To){
 if(-not (Test-Path -LiteralPath $From)){throw "Missing build tool: $From. Run python tools/prepare-toolchain.py first."}
 New-Item -ItemType Directory -Path $To -Force | Out-Null
 & robocopy $From $To /E /MT:16 /R:2 /W:1 /NFL /NDL /NJH /NJS /NP
 if($LASTEXITCODE -ge 8){throw "Build tool copy failed: $LASTEXITCODE"}
}
# AGP writes Windows batch files for Prefab/CMake. Those paths must be ASCII,
# including java.home and the SDK's canonical path (junctions are insufficient).
$asciiTools=Join-Path $env:TEMP 'link-rn-tools'
$runtime=Get-ChildItem (Join-Path $toolsRoot 'java') -Directory | Where-Object {Test-Path (Join-Path $_.FullName 'bin\java.exe')} | Select-Object -First 1
$compiler=Get-ChildItem (Join-Path $toolsRoot 'java17') -Directory | Select-Object -First 1
if(-not $runtime -or -not $compiler){throw 'JDK 21 runtime and JDK 17 compiler are required.'}
Write-Host 'Preparing ASCII Java tool paths...'
Sync-Tool $runtime.FullName (Join-Path $asciiTools 'java21')
Sync-Tool $compiler.FullName (Join-Path $asciiTools 'java17')
$sdkFiles=Join-Path $asciiTools 'sdk'
foreach($part in @('platforms\android-37.0','build-tools\37.0.0','ndk\27.1.12297006','cmake\3.22.1','licenses')){
 Write-Host "Preparing SDK $part..."
 Sync-Tool (Join-Path $toolsRoot "sdk\$part") (Join-Path $sdkFiles $part)
}
$env:JAVA_HOME=Join-Path $asciiTools 'java21'
$env:ANDROID_HOME=$sdkFiles
$env:ANDROID_SDK_ROOT=$sdkFiles
$env:GRADLE_USER_HOME=Join-Path $env:TEMP 'link-rn-gradle'
New-Item -ItemType Directory -Path $env:GRADLE_USER_HOME -Force | Out-Null
@("org.gradle.java.home=$($env:JAVA_HOME.Replace('\','/'))","org.gradle.java.installations.paths=$((Join-Path $asciiTools 'java17').Replace('\','/'))","org.gradle.java.installations.auto-download=false") | Set-Content -LiteralPath (Join-Path $env:GRADLE_USER_HOME 'gradle.properties') -Encoding utf8
$env:PATH="$env:JAVA_HOME\bin;C:\Program Files\nodejs;$env:PATH"
$gradleSource=Join-Path $toolsRoot 'gradle94\gradle-9.4.1'
$gradleHome=Join-Path $asciiTools 'gradle-9.4.1'
Sync-Tool $gradleSource $gradleHome
Push-Location (Join-Path $stageRoot 'android')
try{& (Join-Path $gradleHome 'bin\gradle.bat') $Task --console=plain --no-daemon; if($LASTEXITCODE -ne 0){throw "Gradle failed: $LASTEXITCODE"}}finally{Pop-Location}
if($Task -eq 'assembleRelease'){
 $artifactRoot=Join-Path $sourceRoot 'artifacts';New-Item -ItemType Directory -Path $artifactRoot -Force | Out-Null
 Copy-Item -LiteralPath (Join-Path $stageRoot 'android\app\build\outputs\apk\release\app-release.apk') -Destination (Join-Path $artifactRoot 'link-react-native-v5.apk')
 Write-Host "APK: $artifactRoot\link-react-native-v5.apk"
}
