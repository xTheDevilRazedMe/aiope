---
name: dependency-update-with-dependabot
description: Workflow command scaffold for dependency-update-with-dependabot in aiope.
allowed_tools: ["Bash", "Read", "Write", "Grep", "Glob"]
---

# /dependency-update-with-dependabot

Use this workflow when working on **dependency-update-with-dependabot** in `aiope`.

## Goal

Automated update of third-party dependencies using Dependabot, bumping versions in build files or versions catalog.

## Common Files

- `app/build.gradle.kts`
- `feature-chat/build.gradle.kts`
- `gradle/libs.versions.toml`

## Suggested Sequence

1. Understand the current state and failure mode before editing.
2. Make the smallest coherent change that satisfies the workflow goal.
3. Run the most relevant verification for touched files.
4. Summarize what changed and what still needs review.

## Typical Commit Signals

- Dependabot detects new dependency version.
- Dependabot creates a commit updating the relevant build file(s) or versions catalog.
- A merge commit is created to integrate the update.

## Notes

- Treat this as a scaffold, not a hard-coded script.
- Update the command if the workflow evolves materially.