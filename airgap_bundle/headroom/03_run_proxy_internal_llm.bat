@echo off
REM [폐쇄망 오프라인 PC 전용] 사내 사설 LLM과 연동하여 Headroom 로컬 프록시 구동 배치파일
setlocal

set VENV_DIR=%USERPROFILE%\.venv\headroom
if exist "%VENV_DIR%\Scripts\activate.bat" (
    call "%VENV_DIR%\Scripts\activate.bat"
)

REM 사내 LLM 엔드포인트 URL (환경에 맞게 수정)
if "%OPENAI_BASE_URL%"=="" set OPENAI_BASE_URL=http://internal-llm.company.local:8000/v1
if "%OPENAI_API_KEY%"=="" set OPENAI_API_KEY=sk-internal-key
set HEADROOM_OUTPUT_SHAPER=1

echo =================================================================
echo  Starting Headroom Proxy for Air-gapped Environment
echo  Upstream Target: %OPENAI_BASE_URL%
echo  Listening on:    http://localhost:8787/v1
echo =================================================================

headroom proxy --port 8787
pause
