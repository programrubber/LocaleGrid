#!/usr/bin/env bash
# [폐쇄망 오프라인 PC 전용] Headroom 오프라인 설치 스크립트

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WHEELS_DIR="${SCRIPT_DIR}/wheels"
VENV_DIR="${HOME}/.venv/headroom"

if [ ! -d "${WHEELS_DIR}" ]; then
    echo "ERROR: wheels directory not found at ${WHEELS_DIR}!"
    echo "Please bring the downloaded wheels from the online environment."
    exit 1
fi

echo "==> Setting up Python virtual environment at ${VENV_DIR}..."
python3 -m venv "${VENV_DIR}"
source "${VENV_DIR}/bin/activate"

echo "==> Installing Headroom offline from ${WHEELS_DIR}..."
pip install --no-index --find-links "${WHEELS_DIR}" "headroom-ai[all]"

echo "==> Verifying installation..."
headroom --version

echo "==> Installation complete! Activate with: source ${VENV_DIR}/bin/activate"
