```markdown
# aiope Development Patterns

> Auto-generated skill from repository analysis

## Overview
This skill covers the core development patterns of the `aiope` Kotlin codebase. It documents coding conventions, file organization, commit styles, and automated workflows for dependency management, CI/CD, and code formatting. By following these patterns, contributors can maintain consistency, pass CI checks, and efficiently collaborate on the project.

## Coding Conventions

### File Naming
- **PascalCase** is used for file names.
  - Example: `PreferencesModule.kt`, `MainActivity.kt`

### Import Style
- **Relative imports** are preferred.
  - Example:
    ```kotlin
    import ngo.xnet.aiope.core.preferences.di.PreferencesModule
    ```

### Export Style
- **Named exports** are used for classes and functions.
  - Example:
    ```kotlin
    class PreferencesModule { ... }
    ```

### Commit Messages
- **Prefixes**: `build`, `fix`, `ci` (mixed types)
- **Average length**: ~68 characters
- **Example**:
  ```
  fix: correct import order in PreferencesModule.kt for Spotless compliance
  ```

## Workflows

### Dependency Update with Dependabot
**Trigger:** When a new version of a dependency is released and Dependabot creates a PR  
**Command:** `/bump-dependency`

1. Dependabot detects a new dependency version.
2. Dependabot creates a commit updating the relevant build file(s) or versions catalog.
3. A merge commit is created to integrate the update.

**Files involved:**
- `app/build.gradle.kts`
- `feature-chat/build.gradle.kts`
- `gradle/libs.versions.toml`

**Example:**
```diff
- implementation("com.squareup.retrofit2:retrofit:2.9.0")
+ implementation("com.squareup.retrofit2:retrofit:2.9.1")
```

---

### CI Workflow Update
**Trigger:** When the CI/CD pipeline needs to be fixed, updated, or made more robust  
**Command:** `/update-ci`

1. Edit workflow YAML file(s) under `.github/workflows/`.
2. Commit changes with a message referencing CI or workflow update.
3. Push to the repository to trigger new workflow behavior.

**Files involved:**
- `.github/workflows/release.yml`
- `.github/workflows/android.yml`

**Example:**
```yaml
# .github/workflows/android.yml
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      # ... other steps
```

---

### Spotless Import Order Fix
**Trigger:** When Spotless or code style checks fail due to import order or formatting issues  
**Command:** `/fix-imports`

1. Edit Kotlin file(s) to fix import order or formatting.
2. Commit with a message referencing Spotless or import order.
3. Push to the repository to pass formatting checks.

**Files involved:**
- `core-preferences/src/main/kotlin/ngo/xnet/aiope/core/preferences/di/PreferencesModule.kt`

**Example:**
```kotlin
// Before (incorrect order)
import ngo.xnet.aiope.core.preferences.SomeClass
import ngo.xnet.aiope.core.preferences.di.PreferencesModule

// After (correct order)
import ngo.xnet.aiope.core.preferences.di.PreferencesModule
import ngo.xnet.aiope.core.preferences.SomeClass
```

## Testing Patterns

- **Test File Pattern:** Files named with `*.test.*`
- **Testing Framework:** Unknown (not explicitly detected)
- **Example:**
  ```
  FeatureChatViewModel.test.kt
  ```
- Tests are placed alongside or near the code they validate.

## Commands

| Command           | Purpose                                             |
|-------------------|-----------------------------------------------------|
| /bump-dependency  | Trigger a dependency update using Dependabot         |
| /update-ci        | Update or fix CI/CD workflow files                   |
| /fix-imports      | Fix import order or formatting for Spotless checks   |
```
