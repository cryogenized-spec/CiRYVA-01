# CiRYVA-01 — Current Session Checkpoint

**Date:** 2026-11-01, South Africa (SAST, UTC+02:00)
**State:** Kotlin/JVM core merged; native Android/Compose app has not been implemented or built. Master roadmap is introduced through PR #2.
**Source of truth:** Check GitHub's current main branch, PRs, CI runs and source files before trusting this handoff.

## Current delivered foundation

- Repository: https://github.com/cryogenized-spec/CiRYVA-01
- **PR #1 merged by user request:** https://github.com/cryogenized-spec/CiRYVA-01/pull/1 — squash commit 92949d9781fa06fe0f8bb72d0d77d6e87a590eea.
- The merged pure Kotlin/JVM **core** module has an SSE text-framing decoder, a generation/turn arbiter preventing stale/cancelled output from overwriting newer output, and arithmetic token/context budget limits. It is not an OpenAI HTTP client, Android APK, or working UI.
- JDK 17 / Gradle 8.13 / Kotlin 2.2.21 / JUnit 5 are used by the foundation CI; the last PR #1 head commit 7a28696c8658236d8e6da9a53088c10cab4048bb completed both Repository integrity and Foundation JVM tests successfully before merge. Recheck actual main CI status when needed.
- GitHub ruleset **Mandatory CI test pass** (ID 26312198) is active on default branch, requires PRs, and blocks force pushes and deletion. **Repository integrity** is a required status check. At last verification, **Foundation JVM tests was passing but not yet marked required**. Only an admin can adjust this ruleset.
- Product requirements, architecture, visual direction and memories are recorded in README.md, docs/DECISIONS.md, docs/MEMORY-DESIGN.md and docs/CI.md. No provider key should be committed.

## Master roadmap

- Full phased roadmap: [docs/ROADMAP.md](ROADMAP.md), phase IDs P00–P17 and Release 1/2/3 acceptance tests.
- PR #2: https://github.com/cryogenized-spec/CiRYVA-01/pull/2 — this document's reconciliation resolves its overlap with PR #1. Confirm the merge/CI status rather than assuming it is already in main.
- The user explicitly instructed: **merge PRs and continue**. After the roadmap is integrated, continue with P01 visual UI review, not a premature Android SDK install.

## Confirmed product direction

Native Android; Kotlin + Jetpack Compose; OpenAI generative models only; local-first archive; safe final-only Markdown; model selector, credential protection and factual usage; optional roleplay and persona; detailed source-backed recollection, tools/MCP and consent-based camera/screen/voice later. **No dummy features**. Dark futuristic UI with Orbitron heading/display font, electric blue, black, silver white and aluminium grey, restrained X/geometry, blur and glitch texture. Chat: left-side thread drawer, Settings at bottom, top-right tappable portrait, bottom composer.

## Immediate next steps

1. Verify roadmap PR #2 CI and merge protected branch; then inspect merged main HEAD.
2. In GitHub Rulesets, add **Foundation JVM tests** as required (Repository integrity already required); after Android code exists also require the actual Android build job.
3. **P01**: create real visual-review deliverables for four 9:16 screens — chat, conversation drawer, agent/persona settings, appearance settings. Specify common navigation, state interactions, safe font/color/asset use, accessibility, screen/keyboard insets and performance. Obtain user review of actual visuals before UI implementation.
4. **P02**: select supported Android API range, test handset, Android Gradle plugin/SDK/Compose versions and resource footprint; install nothing on the user's laptop without approval. Add real Android application and real Android CI (assembleDebug, lintDebug, testDebugUnitTest).
5. **P03–P07**: secure provider configuration, streaming chat, canonical local thread archive, cached final-only Markdown, usage and core settings; verify installable Release-1 alpha. Later phases P08–P17 are detailed in ROADMAP.md.

## End-of-session update checklist

Record source commit/branch, exact files altered, completed checks and their links, blockers, any expenses/downloads/API calls, explicit user decisions and the smallest next task. Never include secrets or personal memory content in a public repository. Never claim an Android test without actual evidence.
