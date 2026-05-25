# Setup script for bilibili-pure-apk
# This script downloads Gradle and generates the wrapper

param(
    [string]$GradleVersion = "8.7"
)

$ErrorActionPreference = "Stop"
$tempDir = "$env:TEMP\gradle-setup"
$zipPath = "$tempDir\gradle-${GradleVersion}-bin.zip"
$extractDir = "$tempDir\gradle-${GradleVersion}"
$gradleBin = "$extractDir\bin\gradle"

if (-not (Test-Path $gradleBin)) {
    $url = "https://services.gradle.org/distributions/gradle-${GradleVersion}-bin.zip"
    Write-Host "Downloading Gradle $GradleVersion from $url ..." -ForegroundColor Cyan

    New-Item -ItemType Directory -Force -Path $tempDir | Out-Null
    Invoke-WebRequest -Uri $url -OutFile $zipPath -UseBasicParsing -TimeoutSec 300

    Write-Host "Extracting..." -ForegroundColor Cyan
    Expand-Archive -Path $zipPath -DestinationPath $tempDir -Force
}

Write-Host "Generating Gradle wrapper..." -ForegroundColor Cyan
& $gradleBin wrapper --gradle-version $GradleVersion

Write-Host "Done! Gradle wrapper generated for version $GradleVersion" -ForegroundColor Green
