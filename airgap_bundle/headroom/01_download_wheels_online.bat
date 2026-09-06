@echo off
REM [외부망 인터넷 PC 전용] Headroom 및 의존성 Wheel 파일 일괄 다운로드 배치파일
setlocal

set SCRIPT_DIR=%~dp0
set WHEELS_DIR=%SCRIPT_DIR%wheels

if not exist "%WHEELS_DIR%" mkdir "%WHEELS_DIR%"

echo ==> Downloading headroom-ai[all] and dependencies to %WHEELS_DIR%...
pip download --dest "%WHEELS_DIR%" "headroom-ai[all]"

echo ==> Download complete!
pause
