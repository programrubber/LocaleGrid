param(
    [ValidateSet("major", "minor", "patch")]
    [string]$Bump = "minor",
    [string[]]$Notes = @(),
    [switch]$CommitAndPush,
    [string]$Remote = "origin",
    [string]$Branch = "",
    [string]$TagPrefix = ""
)

# LocaleGrid release automation.
# Keep this file ASCII-only so Windows PowerShell 5.1 can parse it without a BOM.
# Project XML/Markdown files are still read and written as UTF-8.

$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $repoRoot

$utf8NoBom = [System.Text.UTF8Encoding]::new($false)
$gradleFile = "build.gradle.kts"
$pluginXmlFile = "src/main/resources/META-INF/plugin.xml"
$releaseDir = "release"
$releaseNotesFile = Join-Path $releaseDir "release.md"

function Read-Utf8File {
    param([string]$Path)
    return [System.IO.File]::ReadAllText([System.IO.Path]::GetFullPath($Path), [System.Text.Encoding]::UTF8)
}

function Write-Utf8File {
    param(
        [string]$Path,
        [string]$Content
    )
    [System.IO.File]::WriteAllText([System.IO.Path]::GetFullPath($Path), $Content, $utf8NoBom)
}

function U {
    param([string]$Value)
    return [System.Text.RegularExpressions.Regex]::Unescape($Value)
}

function Assert-FileExists {
    param([string]$Path)
    if (!(Test-Path -LiteralPath $Path)) {
        throw "File not found: $Path"
    }
}

function Assert-PluginXmlEncoding {
    param([string]$Content)

    $requiredPhrase = [string]::Concat(
        [char]0xB2E4,
        [char]0xAD6D,
        [char]0xC5B4,
        [char]0x20,
        [char]0xC5D0,
        [char]0xB514,
        [char]0xD130
    )
    $badTokens = @([char]0xFFFD, "LocaleGrid??")

    foreach ($token in $badTokens) {
        if ($Content.Contains([string]$token)) {
            throw "plugin.xml appears to contain mojibake token: '$token'"
        }
    }

    if (!$Content.Contains($requiredPhrase)) {
        throw "plugin.xml is missing the required Korean description phrase."
    }
}

function Get-NextVersion {
    param(
        [string]$CurrentVersion,
        [string]$BumpPart
    )

    if ($CurrentVersion -notmatch '^(\d+)\.(\d+)\.(\d+)(-.+)?$') {
        throw "Version must use Major.Minor.Patch format: $CurrentVersion"
    }

    $major = [int]$Matches[1]
    $minor = [int]$Matches[2]
    $patch = [int]$Matches[3]
    $suffix = $Matches[4]

    switch ($BumpPart) {
        "major" {
            $major += 1
            $minor = 0
            $patch = 0
        }
        "minor" {
            $minor += 1
            $patch = 0
        }
        "patch" {
            $patch += 1
        }
    }

    return "$major.$minor.$patch$suffix"
}

function Get-PluginXmlFromZip {
    param([string]$ZipPath)

    Add-Type -AssemblyName System.IO.Compression
    Add-Type -AssemblyName System.IO.Compression.FileSystem

    $zip = [System.IO.Compression.ZipFile]::OpenRead((Resolve-Path -LiteralPath $ZipPath).Path)
    try {
        $jarEntry = $zip.Entries |
            Where-Object { $_.FullName -like "LocaleGrid/lib/*.jar" } |
            Select-Object -First 1

        if ($null -eq $jarEntry) {
            throw "LocaleGrid plugin jar was not found inside ZIP: $ZipPath"
        }

        $memory = New-Object System.IO.MemoryStream
        $entryStream = $jarEntry.Open()
        try {
            $entryStream.CopyTo($memory)
        } finally {
            $entryStream.Dispose()
        }

        $memory.Position = 0
        $jar = New-Object System.IO.Compression.ZipArchive($memory, [System.IO.Compression.ZipArchiveMode]::Read)
        try {
            $pluginEntry = $jar.GetEntry("META-INF/plugin.xml")
            if ($null -eq $pluginEntry) {
                throw "META-INF/plugin.xml was not found inside plugin jar."
            }

            $reader = New-Object System.IO.StreamReader($pluginEntry.Open(), [System.Text.Encoding]::UTF8)
            try {
                return $reader.ReadToEnd()
            } finally {
                $reader.Dispose()
            }
        } finally {
            $jar.Dispose()
            $memory.Dispose()
        }
    } finally {
        $zip.Dispose()
    }
}

function Get-ReleaseNoteLines {
    param([string[]]$InputNotes)

    if ($InputNotes.Count -gt 0) {
        return $InputNotes
    }

    $subjects = @()
    try {
        $subjects = & git log --pretty=format:"%s" -n 5
    } catch {
        $subjects = @()
    }

    if ($subjects.Count -gt 0) {
        $prefix = (U "\uCD5C\uADFC \uCEE4\uBC0B: ")
        return $subjects | ForEach-Object { "$prefix$_" }
    }

    return @((U "\uB9B4\uB9AC\uC988 \uBE4C\uB4DC \uBC0F \uBC30\uD3EC \uC0B0\uCD9C\uBB3C \uC0DD\uC131"))
}

function Update-ReleaseNotes {
    param(
        [string]$Version,
        [string[]]$InputNotes
    )

    if (!(Test-Path -LiteralPath $releaseDir)) {
        New-Item -ItemType Directory -Path $releaseDir -Force | Out-Null
    }

    $date = Get-Date -Format "yyyy-MM-dd"
    $noteLines = Get-ReleaseNoteLines -InputNotes $InputNotes
    $releaseTitle = "LocaleGrid " + (U "\uB9B4\uB9AC\uC988 \uB178\uD2B8")
    $changesTitle = U "\uBCC0\uACBD \uC0AC\uD56D"
    $sectionLines = @(
        "## $Version - $date",
        "",
        "### $changesTitle"
    )
    $sectionLines += $noteLines | ForEach-Object { "- $_" }
    $sectionLines += ""
    $section = ($sectionLines -join [Environment]::NewLine)

    if (!(Test-Path -LiteralPath $releaseNotesFile)) {
        $initial = "# $releaseTitle" + [Environment]::NewLine + [Environment]::NewLine + $section
        Write-Utf8File -Path $releaseNotesFile -Content $initial
        return
    }

    $content = Read-Utf8File -Path $releaseNotesFile
    if ($content -match "(?m)^##\s+$([regex]::Escape($Version))\b") {
        Write-Host "release/release.md already contains $Version; skipping duplicate entry."
        return
    }

    if ($content -match '^# .+(\r?\n){1,2}') {
        $updated = [regex]::Replace($content, '^# .+(\r?\n){1,2}', { param($match) $match.Value + $section + [Environment]::NewLine }, 1)
    } else {
        $updated = "# $releaseTitle" + [Environment]::NewLine + [Environment]::NewLine + $section + [Environment]::NewLine + $content
    }

    Write-Utf8File -Path $releaseNotesFile -Content $updated
}

Assert-FileExists -Path $gradleFile
Assert-FileExists -Path $pluginXmlFile

$gradleContent = Read-Utf8File -Path $gradleFile
$xmlContent = Read-Utf8File -Path $pluginXmlFile
Assert-PluginXmlEncoding -Content $xmlContent

if ($gradleContent -match 'version\s*=\s*"([^"]+)"') {
    $currentVersion = $Matches[1]
} else {
    throw "Version was not found in build.gradle.kts."
}

if ($xmlContent -match '<version>([^<]+)</version>') {
    $pluginXmlVersion = $Matches[1]
    if ($pluginXmlVersion -ne $currentVersion) {
        throw "build.gradle.kts version ($currentVersion) and plugin.xml version ($pluginXmlVersion) differ."
    }
} else {
    throw "Version was not found in plugin.xml."
}

$newVersion = Get-NextVersion -CurrentVersion $currentVersion -BumpPart $Bump
Write-Host "Current version: $currentVersion"
Write-Host "New version: $newVersion"

$newGradleContent = $gradleContent -replace 'version\s*=\s*"[^"]+"', "version = `"$newVersion`""
$newXmlContent = $xmlContent -replace '<version>[^<]+</version>', "<version>$newVersion</version>"
if ($newXmlContent -notmatch '^\s*<\?xml') {
    $newXmlContent = "<?xml version=`"1.0`" encoding=`"UTF-8`"?>" + [Environment]::NewLine + $newXmlContent
}
Assert-PluginXmlEncoding -Content $newXmlContent

Write-Utf8File -Path $gradleFile -Content $newGradleContent
Write-Utf8File -Path $pluginXmlFile -Content $newXmlContent
Update-ReleaseNotes -Version $newVersion -InputNotes $Notes

Write-Host "Starting Gradle buildPlugin..."
$env:JAVA_HOME = 'C:\Users\rafal\.vscode\extensions\redhat.java-1.54.0-win32-x64\jre\21.0.10-win32-x86_64'
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
& .\gradlew.bat buildPlugin --console=plain
if ($LASTEXITCODE -ne 0) {
    throw "Gradle buildPlugin failed."
}

$zipFileName = "LocaleGrid-$newVersion.zip"
$sourceZip = "build/distributions/$zipFileName"
Assert-FileExists -Path $sourceZip

$builtPluginXml = Get-PluginXmlFromZip -ZipPath $sourceZip
Assert-PluginXmlEncoding -Content $builtPluginXml

if (!(Test-Path -LiteralPath $releaseDir)) {
    New-Item -ItemType Directory -Path $releaseDir -Force | Out-Null
}

$releaseZip = Join-Path $releaseDir $zipFileName
Copy-Item -LiteralPath $sourceZip -Destination $releaseZip -Force
Write-Host "Release artifact copied: $releaseZip"
Write-Host "Existing release ZIP files were preserved."

if ($CommitAndPush) {
    if ([string]::IsNullOrWhiteSpace($Branch)) {
        $Branch = (& git branch --show-current).Trim()
    }
    if ([string]::IsNullOrWhiteSpace($Branch)) {
        throw "Current Git branch could not be detected."
    }

    $tagName = "$TagPrefix$newVersion"
    $localTag = (& git tag --list $tagName).Trim()
    if (![string]::IsNullOrWhiteSpace($localTag)) {
        throw "Git tag already exists locally: $tagName"
    }

    $remoteTag = (& git ls-remote --tags $Remote $tagName).Trim()
    if (![string]::IsNullOrWhiteSpace($remoteTag)) {
        throw "Git tag already exists on ${Remote}: $tagName"
    }

    & git add -- build.gradle.kts src/main/resources/META-INF/plugin.xml bump-minor-release.ps1 release/release.md $releaseZip
    if ($LASTEXITCODE -ne 0) {
        throw "git add failed."
    }

    & git commit -m "Release $newVersion"
    if ($LASTEXITCODE -ne 0) {
        throw "git commit failed."
    }

    & git push $Remote $Branch
    if ($LASTEXITCODE -ne 0) {
        throw "git push failed."
    }

    & git tag $tagName
    if ($LASTEXITCODE -ne 0) {
        throw "git tag creation failed: $tagName"
    }

    & git push $Remote $tagName
    if ($LASTEXITCODE -ne 0) {
        throw "git tag push failed: $tagName"
    }

    Write-Host "Commit, push, and tag completed: $tagName"
}

Write-Host "LocaleGrid release build completed: $newVersion"
