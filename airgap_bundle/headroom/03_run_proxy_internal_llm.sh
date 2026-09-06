#!/usr/bin/env bash
# [폐쇄망 오프라인 PC 전용] 사내 사설 LLM과 연동하여 Headroom 로컬 프록시 구동 스크립트

VENV_DIR="${HOME}/.venv/headroom"
if [ -d "${VENV_DIR}" ]; then
    source "${VENV_DIR}/bin/activate"
fi

# 사내 LLM 주소 (사내 환경에 맞게 수정하여 사용)
export OPENAI_BASE_URL="${OPENAI_BASE_URL:-http://internal-llm.company.local:8000/v1}"
export OPENAI_API_KEY="${OPENAI_API_KEY:-sk-internal-key}"

# 토큰 셰이퍼(불필요한 인사말/중복 서두 절감)
export HEADROOM_OUTPUT_SHAPER=1

echo "================================================================="
echo " Starting Headroom Proxy for Air-gapped Environment"
echo " Upstream Target: ${OPENAI_BASE_URL}"
echo " Listening on:    http://localhost:8787/v1"
echo "================================================================="

headroom proxy --port 8787
