---
name: ci-workflow-update
description: Workflow command scaffold for ci-workflow-update in aiope.
allowed_tools: ["Bash", "Read", "Write", "Grep", "Glob"]
---

# /ci-workflow-update

Use this workflow when working on **ci-workflow-update** in `aiope`.

## Goal

Update or fix GitHub Actions workflow files for CI/CD, such as release or build automation.

## Common Files

- `.github/workflows/release.yml`
- `.github/workflows/android.yml`

## Suggested Sequence

1. Understand the current state and failure mode before editing.
2. Make the smallest coherent change that satisfies the workflow goal.
3. Run the most relevant verification for touched files.
4. Summarize what changed and what still needs review.

## Typical Commit Signals

- Edit workflow YAML file(s) under .github/workflows/
- Commit changes with a message referencing CI or workflow update.
- Push to repository to trigger new workflow behavior.

## Notes

- Treat this as a scaffold, not a hard-coded script.
- Update the command if the workflow evolves materially.