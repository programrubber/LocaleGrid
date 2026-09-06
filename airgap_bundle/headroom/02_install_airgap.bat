@echo off
REM [폐쇄망 오프라인 PC 전용] Headroom 오프라인 설치 배치파일
setlocal

set SCRIPT_DIR=%~dp0
set WHEELS_DIR=%SCRIPT_DIR%wheels
set VENV_DIR=%USERPROFILE%\.venv\headroom

if not exist "%WHEELS_DIR%" (
    echo ERROR: wheels directory not found at %WHEELS_DIR%!
    echo Please bring the downloaded wheels from the online environment.
    pause
    exit /b 1
)

echo ==> Setting up Python virtual environment at %VENV_DIR%...
python -m venv "%VENV_DIR%"
call "%VENV_DIR%\Scripts\activate.bat"

echo ==> Installing Headroom offline from %WHEELS_DIR%...
pip install --no-index --find-links "%WHEELS_DIR%" "headroom-ai[all]"

echo ==> Verifying installation...
headroom --version

echo ==> Installation complete!
pause
