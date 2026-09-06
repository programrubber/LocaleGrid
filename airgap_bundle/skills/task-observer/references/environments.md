# Environments & Activation Guide

## Cline Integration
For Cline, place the activation directive directly inside `.clinerules` at the project root.
When Cline begins a task session, it automatically reads `.clinerules` and initializes
the Task Observer protocol.

## Directory Initialization
Ensure the following directories exist in your workspace:
```bash
mkdir -p skill-observations/observation-log/archive
echo "never" > skill-observations/last-review-date.txt
```

## Language Constraint Reminder
Regardless of the host agent or environment, all final deliverables, progress reports,
and user-facing explanations must be delivered in Korean (한국어).
