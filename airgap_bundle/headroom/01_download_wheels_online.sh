#!/usr/bin/env bash
# [외부망 인터넷 PC 전용] Headroom 및 의존성 Wheel 파일 일괄 다운로드 스크립트

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WHEELS_DIR="${SCRIPT_DIR}/wheels"

mkdir -p "${WHEELS_DIR}"
echo "==> Downloading headroom-ai[all] and dependencies to ${WHEELS_DIR}..."
pip download --dest "${WHEELS_DIR}" "headroom-ai[all]"

echo "==> Download complete. Total files in ${WHEELS_DIR}:"
ls -la "${WHEELS_DIR}" | wc -l
