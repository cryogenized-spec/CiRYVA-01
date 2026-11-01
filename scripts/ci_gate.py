#!/usr/bin/env python3
"""CiRYVA-01 repository CI gates. Python stdlib only; no Android toolchain required."""
from __future__ import annotations

import argparse
import re
from pathlib import Path
from urllib.parse import unquote

ROOT = Path(__file__).resolve().parent.parent
REQUIRED = (
    "README.md", "AGENTS.md", "docs/CONTINUITY.md",
    "docs/DECISIONS.md", "docs/MEMORY-DESIGN.md",
    "skills/session-continuity/SKILL.md", "skills/android-ui/SKILL.md",
    "skills/openai-chat/SKILL.md", "skills/local-memory/SKILL.md",
    "skills/tools-mcp/SKILL.md", "skills/android-media/SKILL.md",
    "skills/verification/SKILL.md", ".github/workflows/ci.yml",
    "scripts/ci_gate.py", "tests/test_ci_gate.py",
)
DOC_EXTENSIONS = {".md", ".markdown"}
SOURCE_EXTENSIONS = {
    ".md", ".py", ".kt", ".kts", ".java", ".xml", ".json", ".yml",
    ".yaml", ".toml", ".properties", ".sh", ".gradle", ".txt",
}
LINK_PATTERN = re.compile(r"!?\[[^\]\n]*\]\(([^)\n]+)\)")
SECTION_PATTERN = re.compile(r"(?m)^## (\d+)\. ")
SECRET_PATTERNS = {
    "OpenAI-style secret": re.compile("sk-" + r"(?:proj-|svcacct-)?[A-Za-z0-9_-]{20,}"),
    "GitHub classic token": re.compile("ghp_" + r"[A-Za-z0-9]{20,}"),
    "GitHub fine-grained token": re.compile("github_pat_" + r"[A-Za-z0-9_]{20,}"),
    "Moltbook token": re.compile("moltbook_" + r"[A-Za-z0-9_-]{20,}"),
    "private key block": re.compile("-----BEGIN " + r"(?:RSA |EC |OPENSSH )?PRIVATE KEY-----"),
}


def docs(root: Path) -> list[str]:
    errors: list[str] = []
    for name in REQUIRED:
        if not (root / name).is_file():
            errors.append(f"Missing required file: {name}")
    if errors:
        return errors
    readme = (root / "README.md").read_text(encoding="utf-8")
    sections = [int(n) for n in SECTION_PATTERN.findall(readme)]
    if not sections or sections != list(range(1, len(sections) + 1)):
        errors.append("README numbered sections must be consecutive, beginning at 1")
    agent_index = (root / "AGENTS.md").read_text(encoding="utf-8")
    skills = sorted((root / "skills").glob("*/SKILL.md"))
    if len(skills) < 1:
        errors.append("At least one agent skill is required")
    for file in skills:
        text = file.read_text(encoding="utf-8")
        frontmatter = re.match(r"\A---\r?\nname:\s*(\S+)\r?\ndescription:\s*(.+?)\r?\n---\r?\n", text)
        if not frontmatter:
            errors.append(f"Invalid skill frontmatter: {file.relative_to(root)}")
        elif frontmatter.group(1) != file.parent.name:
            errors.append(f"Skill name mismatch: {file.relative_to(root)}")
        if file.relative_to(root).as_posix() not in agent_index:
            errors.append(f"Orphan skill (not routed by AGENTS.md): {file.relative_to(root)}")
    for file in sorted(root.rglob("*.md")):
        if ".git" in file.parts:
            continue
        content = file.read_text(encoding="utf-8")
        for raw in LINK_PATTERN.findall(content):
            target = raw.strip().split(maxsplit=1)[0].strip("<>")
            if not target or target.startswith("#") or re.match(r"^[a-zA-Z][\w+.-]*:", target):
                continue
            local = unquote(target.split("#", 1)[0].split("?", 1)[0])
            if not local:
                continue
            resolved = (file.parent / local).resolve()
            try:
                resolved.relative_to(root.resolve())
            except ValueError:
                errors.append(f"Link escapes repository: {file.relative_to(root)} -> {target}")
                continue
            if not resolved.exists():
                errors.append(f"Broken local link: {file.relative_to(root)} -> {target}")
    return errors


def secrets(root: Path) -> list[str]:
    errors: list[str] = []
    for file in sorted(root.rglob("*")):
        if not file.is_file() or ".git" in file.parts:
            continue
        rel = file.relative_to(root)
        if file.name == "local.properties" or (
            file.name.startswith(".env") and file.name not in {".env.example", ".env.template"}
        ):
            errors.append(f"Forbidden tracked configuration file: {rel}")
        if file.suffix not in SOURCE_EXTENSIONS and file.name not in {"gradlew", "gradlew.bat"}:
            continue
        if file.stat().st_size > 1_000_000:
            errors.append(f"Text file too large for safety scan: {rel}")
            continue
        try:
            content = file.read_text(encoding="utf-8")
        except UnicodeDecodeError:
            errors.append(f"Cannot scan as UTF-8: {rel}")
            continue
        for label, pattern in SECRET_PATTERNS.items():
            if pattern.search(content):
                errors.append(f"Potential {label} in {rel} (value withheld)")
    return errors


def android(root: Path) -> list[str]:
    """Fail closed if Android implementation arrives without a genuine build workflow."""
    settings = (root / "settings.gradle.kts").exists() or (root / "settings.gradle").exists()
    manifest = (root / "app/src/main/AndroidManifest.xml").exists()
    kotlin_sources = list((root / "app/src").rglob("*.kt")) if (root / "app/src").exists() else []
    android_build = (root / "app/build.gradle.kts").exists() or (root / "app/build.gradle").exists()
    # A Gradle settings file alone may describe a pure JVM module; it does NOT
    # imply an Android app. Require Android build/manifest/app source evidence.
    if not any((android_build, manifest, bool(kotlin_sources))):
        print("Android gate: NOT APPLICABLE (planning-only repository; no Android code).")
        return []
    expected = (
        "gradlew", "gradle/wrapper/gradle-wrapper.properties",
        "gradle/wrapper/gradle-wrapper.jar", "app/src/main/AndroidManifest.xml",
        ".github/workflows/android.yml",
    )
    missing = [name for name in expected if not (root / name).is_file()]
    if not settings:
        missing.append("settings.gradle(.kts)")
    if not ((root / "app/build.gradle.kts").is_file() or (root / "app/build.gradle").is_file()):
        missing.append("app/build.gradle(.kts)")
    if missing:
        return ["Android code requires a real Gradle project AND CI build: missing " + ", ".join(missing)]
    workflow = (root / ".github/workflows/android.yml").read_text(encoding="utf-8")
    required_commands = ("./gradlew", "assembleDebug", "lintDebug", "testDebugUnitTest")
    missing_commands = [c for c in required_commands if c not in workflow]
    if missing_commands:
        return ["Android CI workflow missing real build/lint/test commands: " + ", ".join(missing_commands)]
    print("Android gate: project and build workflow declared (execution checked by GitHub Actions).")
    return []


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("gate", choices=("docs", "secrets", "android", "all"))
    args = parser.parse_args()
    gates = {"docs": docs, "secrets": secrets, "android": android}
    selected = gates.items() if args.gate == "all" else [(args.gate, gates[args.gate])]
    error_count = 0
    for name, gate in selected:
        findings = gate(ROOT)
        for finding in findings:
            print(f"ERROR [{name}]: {finding}")
        error_count += len(findings)
        print(f"{name}: {'FAIL' if findings else 'PASS'}")
    return 1 if error_count else 0


if __name__ == "__main__":
    raise SystemExit(main())
