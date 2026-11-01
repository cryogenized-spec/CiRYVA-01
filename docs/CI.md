# CiRYVA-01 — Continuous Integration Contract

**Introduced:** 2026-11-01 (SAST)  
**Current state:** Planning-stage repository. The first actual GitHub Actions run completed successfully: [run 41308781532](https://github.com/cryogenized-spec/CiRYVA-01/actions/runs/41308781532).

## Entry point and triggers

Workflow: [CI workflow](../.github/workflows/ci.yml)  
Gate implementation: [Python CI gate](../scripts/ci_gate.py)  
Gate regression tests: [gate tests](../tests/test_ci_gate.py)

The workflow is triggered by:
- Pushes to main.
- Pull requests targeting main.
- Manual workflow dispatch.

Job/status check name: **Repository integrity**.

This job is deliberately dependency-free (Ubuntu-hosted Python 3 and GitHub checkout). No npm, pip, Android SDK, compiler download, app installation, emulator or OpenAI API call is performed by the planning-stage gate. Checkout is pinned to a release commit SHA, persists no Git credentials, and the workflow token is read-only.

## Current enforced checks

| Check | Actual behaviour | Limitation |
| --- | --- | --- |
| Documentation contract | Verify required specifications, AGENTS.md and skill files, frontmatter, routing and internal Markdown links; ensure numbered README sections are consecutive | Does not validate product requirements against running Android UI |
| Credential-pattern scan | Look for common long API-key/token patterns, private-key PEM starts and prohibited local configuration files in text files | Heuristic scan, not a complete secret scanner or substitute for GitHub secret scanning |
| Android readiness | No code => state explicitly *not applicable*; Android code => require a Gradle project, wrapper, app manifest and real Android build workflow with lint/test/assemble commands | Presence check only; actual Gradle tasks must be executed in separate CI |
| CI gate tests | Run real unittest assertions for passing/failing documentation, secrets and Android guard situations | These tests exercise the gate, **not** Android application behaviour |

The agent instructions also mandate disclosure of download/install size, hidden spend risks, security review and accurate testing claims. Those are process requirements, not automatically proven by this initial CI job.

## Android code admission: fail closed

The **first PR introducing native Android source** must also add:

- Root \`settings.gradle.kts\` (or Groovy equivalent), \`gradlew\`, wrapper configuration **and wrapper JAR**, a real \`app/build.gradle.kts\` and \`app/src/main/AndroidManifest.xml\`.
- \`.github/workflows/android.yml\` with a real setup and steps that execute, at minimum, \`./gradlew ... assembleDebug\`, \`./gradlew ... lintDebug\`, and \`./gradlew ... testDebugUnitTest\` (commands may be consolidated). Project/Gradle configuration determines the exact SDK/JDK versions.
- Standard Gradle Wrapper checksum / supply-chain verification as part of Android CI, with pinned actions and least-privilege permissions.
- Real, meaningful Android unit tests tied to the feature being introduced; later emulator/instrumented tests where needed.

The planning-stage gate **fails** on Android source lacking this scaffold/workflow. Merely listing Gradle task names does not prove their execution: the Android workflow must run successfully, and then **its build job must itself become a required status check** before code is accepted.

Do not add empty "pass" tasks or dummy tests to satisfy the gate. Do not publish an APK on green planning checks alone.

## Repository merge protection — separate from having a workflow

GitHub workflows *report* status but do not automatically prevent direct pushes to main. At initial inspection, the repository had **no rulesets**. A repository administrator must configure a branch ruleset or protection rule in **Settings → Rules → Rulesets** (or branch-protection settings, where supported).

Recommended:
1. Target \`main\`.
2. Require a pull request before merging (user can decide whether to require an additional approval).
3. Require status check **Repository integrity** to pass; select the GitHub Actions source if prompted.
4. Do **not** require an Android build check until the actual Android workflow has been added and its first run has completed.
5. Once Android implementation begins, additionally require the real Android build/lint/unit-test job.
6. Review bypass permissions and consider prohibiting force pushes/deletion.

**This repo document does not assert that branch rulesets have been enabled.** Activation requires checking actual GitHub settings.

## Verification and source of truth

Before claiming CI success, inspect the exact commit's completed GitHub Actions run, status, conclusion and steps. A file being committed is **not** itself evidence of successful execution.

After a CI-related change update [the continuity checkpoint](./CONTINUITY.md) with:
- Exact commit/run, job names and pass/fail state.
- Actual commands/tests and any incomplete gates.
- Whether required-status branch protection is active or still needs configuration.
- The next smallest verified engineering step.

Keep this policy conservative: one truthful, useful build/quality gate is better than multiple green-but-inert badges.
