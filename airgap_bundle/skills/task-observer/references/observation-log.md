# Observation Log Storage & Formats

## File Structure
- Each observation is a separate Markdown file located under:
  `skill-observations/observation-log/YYYYMMDD-HHMMSS_brief_korean_title.md`
- Once a human reviewer merges the recommendation into `AGENTS.md` or `.clinerules`,
  the observation file is moved to `skill-observations/observation-log/archive/`.

## Frontmatter Fields
- `id`: ISO timestamp (e.g. 20260907-143000)
- `status`: `open` | `resolved` | `rejected`
- `category`: `rule_improvement` | `new_skill` | `environment_quirk`
- `title`: Short Korean title summarizing the observation.
