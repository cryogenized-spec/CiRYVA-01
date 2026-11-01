"""Executable tests for CiRYVA repository gates, using temporary fixtures only."""
import tempfile
import unittest
from pathlib import Path
from scripts.ci_gate import REQUIRED, android, docs, secrets


class GateTests(unittest.TestCase):
    def setUp(self):
        self.folder = tempfile.TemporaryDirectory()
        self.addCleanup(self.folder.cleanup)
        self.root = Path(self.folder.name)
        for name in REQUIRED:
            file = self.root / name
            file.parent.mkdir(parents=True, exist_ok=True)
            file.write_text("fixture\n", encoding="utf-8")
        (self.root / "README.md").write_text("# CiRYVA\n\n## 1. Overview\n\n## 2. Design\n", encoding="utf-8")
        entries = []
        for file in (self.root / "skills").glob("*/SKILL.md"):
            rel = file.relative_to(self.root).as_posix()
            entries.append(f"[{file.parent.name}]({rel})")
            file.write_text(
                f"---\nname: {file.parent.name}\ndescription: Verified skill instructions.\n---\n"
                f"# {file.parent.name}\n", encoding="utf-8"
            )
        (self.root / "AGENTS.md").write_text("# Agent routing\n\n" + "\n".join(entries), encoding="utf-8")

    def test_valid_planning_documents_pass(self):
        self.assertEqual(docs(self.root), [])

    def test_missing_required_document_fails(self):
        (self.root / "docs/DECISIONS.md").unlink()
        self.assertTrue(any("docs/DECISIONS.md" in finding for finding in docs(self.root)))

    def test_broken_link_fails(self):
        with (self.root / "README.md").open("a", encoding="utf-8") as fh:
            fh.write("\n[missing](./docs/does-not-exist.md)\n")
        self.assertTrue(any("Broken local link" in finding for finding in docs(self.root)))

    def test_skill_name_and_routing_are_verified(self):
        p = self.root / "skills/android-ui/SKILL.md"
        p.write_text("---\nname: mismatched\ndescription: Wrong name.\n---\n", encoding="utf-8")
        self.assertTrue(any("Skill name mismatch" in finding for finding in docs(self.root)))

    def test_nonsequential_readme_sections_fail(self):
        (self.root / "README.md").write_text("# CiRYVA\n\n## 1. One\n\n## 3. Three\n", encoding="utf-8")
        self.assertTrue(any("consecutive" in finding for finding in docs(self.root)))

    def test_detects_secret_without_printing_secret(self):
        simulated = "sk-" + "proj-" + "A" * 24
        (self.root / "sample.py").write_text(f"token = '{simulated}'", encoding="utf-8")
        errors = secrets(self.root)
        self.assertTrue(any("OpenAI-style secret" in finding for finding in errors))
        self.assertTrue(all(simulated not in finding for finding in errors))

    def test_blocks_local_properties(self):
        (self.root / "local.properties").write_text("sdk.dir=/private/path", encoding="utf-8")
        self.assertTrue(any("Forbidden" in finding for finding in secrets(self.root)))

    def test_android_is_not_applicable_before_code(self):
        self.assertEqual(android(self.root), [])

    def test_pure_jvm_gradle_build_does_not_trigger_android_gate(self):
        (self.root / "settings.gradle.kts").write_text(
            'rootProject.name = "CiRYVA-01"\ninclude(":core")\n', encoding="utf-8"
        )
        (self.root / "core/build.gradle.kts").parent.mkdir(parents=True, exist_ok=True)
        (self.root / "core/build.gradle.kts").write_text(
            'plugins { kotlin("jvm") }\n', encoding="utf-8"
        )
        self.assertEqual(android(self.root), [])

    def test_android_code_without_build_workflow_fails(self):
        p = self.root / "app/src/main/AndroidManifest.xml"
        p.parent.mkdir(parents=True, exist_ok=True)
        p.write_text("<manifest />", encoding="utf-8")
        self.assertTrue(any("requires a real Gradle project" in finding for finding in android(self.root)))


if __name__ == "__main__":
    unittest.main()
