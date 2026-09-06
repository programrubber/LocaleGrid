# 폐쇄망 환경 Cline + Headroom + Task Observer 설치 및 운용 가이드

본 문서는 외부 인터넷이 차단된 **폐쇄망(망분리 환경)**에서 **VS Code 확장 Cline**, **LLM 토큰 압축 레이어 Headroom**, 그리고 **에이전트 자가 학습 메타 스킬 Task Observer**를 구축하고 사내 LLM과 연동하여 운용하기 위한 종합 설치·설정 지침서입니다.

---

## 1. 아키텍처 및 연동 흐름

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                          폐쇄망 개발자 PC / VDI                             │
│                                                                             │
│  ┌───────────────────────┐                                                  │
│  │ VS Code + Cline       │                                                  │
│  │  ├─ .clinerules       │                                                  │
│  │  │   (한국어 소통 강제,│                                                  │
│  │  │    AGENTS.md 연동) │                                                  │
│  │  └─ Task Observer     │                                                  │
│  │      (세션 관찰/기록) │                                                  │
│  └───────────┬───────────┘                                                  │
│              │ API 요청 (http://localhost:8787/v1)                          │
│              ▼                                                              │
│  ┌──────────────────────────────────────────────────┐                       │
│  │ Headroom Local Proxy (포트 8787)                 │                       │
│  │  - JSON 압축 (SmartCrusher 60~95% 절감)          │                       │
│  │  - Gradle 빌드/에러 로그 압축                    │                       │
│  │  - 완전 로컬 On-Device 처리 (외부 데이터 유출 0) │                       │
│  └───────────┬──────────────────────────────────────┘                       │
└──────────────┼──────────────────────────────────────────────────────────────┘
               │ 압축된 프롬프트 전달 (사내 폐쇄망 네트워크)
               ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                       사내 사설 LLM 인프라                                 │
│  (vLLM / Ollama / TGI / 사내 AI 게이트웨이: Qwen, DeepSeek, Llama 등)        │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Phase 1: 외부망(인터넷 가능 PC)에서 반입 패키지 준비

인터넷이 연결된 환경에서 필요한 패키지와 확장 프로그램을 다운로드하여 폐쇄망 반입용 폴더를 구성합니다.

### 2.1 Headroom 및 Python 의존성 다운로드
```bash
# 1. 반입용 디렉토리 생성
mkdir -p ./airgap_bundle/headroom_wheels

# 2. Headroom 및 모든 의존성 wheel 파일 다운로드 (Python 3.11 이상 권장)
pip download --dest ./airgap_bundle/headroom_wheels "headroom-ai[all]"
```
*(Windows 환경인 경우 PowerShell 또는 CMD에서 동일하게 `pip download -d .\airgap_bundle\headroom_wheels "headroom-ai[all]"` 실행)*

### 2.2 VS Code Cline 확장(.vsix) 다운로드
1. Open VSX Registry 또는 GitHub Release 접속:
   - [Open VSX: Cline](https://open-vsx.org/extension/saoudrizwan/claude-dev)
   - [GitHub: cline/cline releases](https://github.com/cline/cline/releases)
2. 최신 버전의 `.vsix` 파일 다운로드 후 `./airgap_bundle/cline/` 폴더에 저장.

### 2.3 패키지 압축 및 보안 승인/반입
- `airgap_bundle` 폴더를 zip으로 압축하여 사내 망연계 솔루션(보안 USB, 파일 반입 시스템 등)을 통해 폐쇄망 PC로 전달합니다.

---

## 3. Phase 2: 폐쇄망 환경 오프라인 설치

### 3.1 VS Code Cline 오프라인 설치
터미널 또는 VS Code GUI를 통해 설치합니다:
```bash
# 터미널 명령어
code --install-extension ./cline/saoudrizwan.claude-dev-*.vsix
```
*(또는 VS Code Extensions 탭 → 우측 상단 `...` 메뉴 → `Install from VSIX...` 선택)*

### 3.2 Headroom 오프라인 설치
폐쇄망 PC의 Python 가상환경에 반입한 wheel 파일들로 오프라인 설치를 진행합니다:
```bash
# 가상환경 생성 (권장)
python -m venv ~/.venv/headroom
source ~/.venv/headroom/bin/activate  # Windows: ~/.venv/headroom/Scripts/activate

# 오프라인 Wheel 일괄 설치 (--no-index 옵션)
pip install --no-index --find-links ./headroom_wheels "headroom-ai[all]"

# 설치 확인
headroom --version
```

---

## 4. Phase 3: 사내 LLM 연동 및 Headroom 프록시 기동

사내에 배포된 사설 LLM(vLLM, Ollama, TGI 등)을 업스트림 타깃으로 지정하여 Headroom 프록시를 띄웁니다.

### 4.1 프록시 실행 스크립트

**Linux / macOS (`run_proxy.sh`):**
```bash
#!/usr/bin/env bash
# 사내 LLM 엔드포인트 URL 및 API 키 설정
export OPENAI_BASE_URL="http://internal-llm.company.local:8000/v1"
export OPENAI_API_KEY="sk-internal-dummy-or-valid-key"

# 출력 토큰 셰이핑(불필요한 서두 생략) 활성화 (선택 사항)
export HEADROOM_OUTPUT_SHAPER=1

# Headroom 로컬 프록시 구동 (포트 8787)
headroom proxy --port 8787
```

**Windows (`run_proxy.bat`):**
```bat
@echo off
set OPENAI_BASE_URL=http://internal-llm.company.local:8000/v1
set OPENAI_API_KEY=sk-internal-dummy-or-valid-key
set HEADROOM_OUTPUT_SHAPER=1

headroom proxy --port 8787
pause
```

---

## 5. Phase 4: Cline 설정 및 `.clinerules` 적용

### 5.1 Cline API Provider 설정
VS Code에서 Cline 설정(톱니바퀴 아이콘)을 열고 다음과 같이 설정합니다:
1. **API Provider:** `OpenAI Compatible` 선택
2. **Base URL:** `http://localhost:8787/v1` (Headroom 프록시 주소)
3. **API Key:** `sk-local-token` (임의의 문자열 또는 사내 키)
4. **Model ID:** 사내 LLM 모델명 (예: `Qwen/Qwen2.5-Coder-32B-Instruct`, `deepseek-coder` 등)

### 5.2 프로젝트 루트에 `.clinerules` 배치
프로젝트 루트(`/Users/dave/Desktop/Workspace/LocaleGrid/` 등)에 `.clinerules` 파일을 생성하고 다음 내용을 적용합니다:
* **AGENTS.md 연동:** 프로젝트 기존 룰셋 준수
* **한국어 응답 강제:** 모든 보고 및 소통은 한국어로 수행
* **Task Observer 동작:** 피드백 관찰 및 규칙 후보 생성

---

## 6. Phase 5: Task Observer 스킬 및 한국어 출력 강제 방안

### 6.1 한국어 출력 보장 방안 (핵심)
Task Observer 스킬 원문은 정교한 영어 프롬프트로 작성되어 있어 모델의 추론 성능을 최대로 끌어냅니다. 하지만 그대로 사용할 경우 LLM이 영어로 답변하거나 로그를 영어로 남기는 문제가 발생합니다.

이를 해결하기 위해 **프롬프트 최상단과 출력 템플릿에 강력한 언어 강제 조항(Language Override Directive)**을 삽입합니다:

```markdown
================================================================================
CRITICAL SYSTEM DIRECTIVE: STRICT KOREAN OUTPUT & INTERACTION
================================================================================
1. [언어 절대 준수]: 본 스킬 및 지침의 설명과 로직이 영문으로 기술되어 있더라도,
   사용자에게 전달하는 모든 대화, 안내, 질문, 요약, 보고는 예외 없이 100% 한국어로 작성해야 합니다.
2. [산출물 및 로그 한국어 작성]: 관찰 로그(Observation Log), 제안하는 규칙 요약,
   불편 사항(Friction Points), 분석 결과는 모두 한국어로 기록되어야 합니다.
3. 코드 변수명, 파일 경로, 기술 고유 명사를 제외한 모든 문장은 정중한 한국어 존댓말을 사용합니다.
================================================================================
```

### 6.2 관찰 로그 저장 경로
Task Observer가 작업 도중 감지한 피드백과 규칙 후보는 다음 경로에 안전하게 축적됩니다:
* `skill-observations/observation-log/` (개별 마크다운 파일로 누적)
* `skill-observations/cross-cutting-principles.md` (팀 공통 원칙)

---

## 7. 검증 및 일상 운용 방법

1. **프록시 정상 동작 확인:**
   ```bash
   headroom doctor
   ```
2. **토큰 절감량 실시간 모니터링:**
   ```bash
   headroom dashboard  # 또는 headroom savings
   ```
3. **규칙 정기 리뷰:**
   - 누적된 `skill-observations/observation-log/` 내용을 확인하고, 팀 공통 규칙으로 삼을 내용을 `AGENTS.md`나 `.clinerules`에 머지합니다.

---
**관련 산출물 및 압축 패키지:**
- 폐쇄망 반입용 전체 번들 압축 파일: `release/2026-09-07_폐쇄망_Cline_Headroom_TaskObserver_패키지.zip`
- 설치 안내 텍스트: 패키지 내 `INSTALL_GUIDE.txt` 포함
