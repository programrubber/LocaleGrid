---
name: task-observer
description: >
  Monitors task execution for skill and rule improvement opportunities. Captures patterns,
  user corrections, workflow insights, and methodology worth preserving as reusable rules.
  IMPORTANT: All user interactions and output deliverables MUST be rendered in Korean.
---

# Task Observer — Continuous Skill Discovery & Improvement

<!-- ==============================================================================
CRITICAL MANDATORY DIRECTIVE: STRICT KOREAN LANGUAGE INTERACTION & OUTPUT
==============================================================================
1. [MANDATORY KOREAN INTERACTION]:
   Even though this skill specification, internal reasoning, and logic rules are written
   in English, ALL responses, dialogues, questions, explanations, feedback summaries, and
   interactions with the user MUST BE CONDUCTED STRICTLY IN KOREAN (한국어).
2. [MANDATORY KOREAN LOGS & ARTIFACTS]:
   All generated observation logs (Markdown files in observation-log/), rule improvement
   proposals, and reports MUST have their textual content, explanations, and summaries
   written in natural, polite Korean (한국어 존댓말).
3. Only code snippets, technical identifiers, file paths, and proper nouns should remain in English.
============================================================================== -->

## Purpose & Overview
Skills and rules improve best from friction noticed during real work. This skill formalizes
the observation of multi-step task sessions, capturing user corrections, recurring friction,
and domain conventions so they can compound into permanent project knowledge (`AGENTS.md`, `.clinerules`).

## Storage Layout
- Workspace observation root: `skill-observations/`
- Active observations: `skill-observations/observation-log/*.md` (one markdown file per observation)
- Resolved entries: `skill-observations/observation-log/archive/*.md`
- Cross-cutting principles: `skill-observations/cross-cutting-principles.md`
- Last review tracker: `skill-observations/last-review-date.txt`

## When to Observe
Active throughout the session:
- **Execution & Tool Usage**: Errors encountered, build failures, repeated tool retries.
- **User Corrections**: When the user says "Don't do that, do it this way", "Check the rule document", etc.
- **Workflow Conventions**: Formatting habits, test parameters, documentation naming conventions.

## What to Log
1. **New Rule or Skill Candidate**: A recurring task sequence or clear convention explained by the user.
2. **Rule Improvement**: The agent violated a documented convention, or an edge case was revealed.
3. **Friction Point**: Tool outputs or commands that repeatedly caused confusion or wasted turns.

## Observation Log Format (Strict Korean Content)
When logging an observation to `skill-observations/observation-log/`, use this template:

```markdown
---
id: YYYYMMDD-HHMMSS
status: open
category: rule_improvement
title: "[한국어 제목] 관찰된 패턴 및 개선 대상"
---

### 1. 발생 맥락 (Context)
- 작업 내용 및 어떤 도구/작업 과정에서 발생했는지 기술

### 2. 마찰 지점 및 사용자 피드백 (Friction & Correction)
- 발생한 오류 또는 사용자가 수정한 구체적인 지침

### 3. 개선 규칙 제안 (Proposed Rule)
- 향후 AGENTS.md 또는 .clinerules에 반영할 수 있는 구체적인 권장 규칙
```

## User Communication Protocol
- When an observation is captured and written to disk, notify the user in one polite Korean line:
  "💡 [Task Observer] 이번 작업 중 확인된 피드백을 관찰 로그(`skill-observations/observation-log/...`)에 기록했습니다."
- Never overwhelm the user with raw English internal thoughts; keep all user-facing communication focused, concise, and in Korean.
