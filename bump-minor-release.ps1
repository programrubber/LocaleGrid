# bump-minor-release.ps1
# 마이너 버전을 올리고 빌드를 수행한 뒤 release 폴더로 산출물을 이동시키는 자동화 스크립트

$gradleFile = "build.gradle.kts"
$pluginXmlFile = "src/main/resources/META-INF/plugin.xml"

# 파일 존재 여부 확인
if (!(Test-Path $gradleFile)) {
    Write-Error "build.gradle.kts 파일을 찾을 수 없습니다."
    exit 1
}
if (!(Test-Path $pluginXmlFile)) {
    Write-Error "plugin.xml 파일을 찾을 수 없습니다."
    exit 1
}

# 1. build.gradle.kts에서 현재 버전 추출
$gradleContent = Get-Content $gradleFile -Raw
if ($gradleContent -match 'version\s*=\s*"([^"]+)"') {
    $currentVersion = $Matches[1]
    Write-Host "현재 버전: $currentVersion"
} else {
    Write-Error "build.gradle.kts에서 버전을 찾을 수 없습니다."
    exit 1
}

# 2. 마이너 버전 증가 (Major.Minor.Patch)
if ($currentVersion -match '^(\d+)\.(\d+)\.(\d+)(-.*)?$') {
    $major = [int]$Matches[1]
    $minor = [int]$Matches[2]
    $patch = 0 # 마이너 버전 상향 시 패치 버전은 0으로 초기화
    
    $newMinor = $minor + 1
    $newVersion = "$major.$newMinor.$patch"
    if ($Matches[4]) {
        $newVersion += $Matches[4] # suffix가 있다면 유지
    }
    Write-Host "새로운 버전 예정: $newVersion"
} else {
    Write-Error "버전 포맷이 Major.Minor.Patch 형식이 아닙니다: $currentVersion"
    exit 1
}

# 3. 파일 업데이트 (BOM 생성 방지를 위해 .NET File API 사용)
Write-Host "버전 업데이트 중..."
$newGradleContent = $gradleContent -replace 'version\s*=\s*"[^"]+"', "version = `"$newVersion`""
$gradleFullPath = [System.IO.Path]::GetFullPath($gradleFile)
[System.IO.File]::WriteAllText($gradleFullPath, $newGradleContent)

$xmlContent = Get-Content $pluginXmlFile -Raw
$newXmlContent = $xmlContent -replace '<version>[^<]+</version>', "<version>$newVersion</version>"
$pluginXmlFullPath = [System.IO.Path]::GetFullPath($pluginXmlFile)
[System.IO.File]::WriteAllText($pluginXmlFullPath, $newXmlContent)
Write-Host "버전 업데이트 완료: $newVersion"

# 4. 빌드 실행 (지정된 JDK 환경 구성 적용)
Write-Host "Gradle 빌드 시작..."
$env:JAVA_HOME='C:\Users\rafal\.vscode\extensions\redhat.java-1.54.0-win32-x64\jre\21.0.10-win32-x86_64'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"

# gradlew 실행
& .\gradlew.bat build --console=plain

if ($LASTEXITCODE -ne 0) {
    Write-Error "Gradle 빌드가 실패했습니다."
    exit 1
}
Write-Host "Gradle 빌드 완료!"

# 5. release 폴더 준비 및 파일 이동
$releaseDir = "release"
if (!(Test-Path $releaseDir)) {
    New-Item -ItemType Directory -Path $releaseDir -Force | Out-Null
} else {
    # 기존 구버전 zip 파일들 정리 (Clean release)
    Remove-Item -Path "$releaseDir/LocaleGrid-*.zip" -Force -ErrorAction SilentlyContinue
}

$zipFileName = "LocaleGrid-$newVersion.zip"
$sourceZip = "build/distributions/$zipFileName"

if (Test-Path $sourceZip) {
    Copy-Item -Path $sourceZip -Destination "$releaseDir/$zipFileName" -Force
    Write-Host "성공적으로 빌드 결과물을 이동했습니다: $releaseDir/$zipFileName"
} else {
    Write-Error "산출물 zip 파일을 찾을 수 없습니다: $sourceZip"
    exit 1
}
