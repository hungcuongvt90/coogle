import re
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
HAN = re.compile(r"[\u3400-\u4dbf\u4e00-\u9fff\uf900-\ufaff]")


class UiTranslationTest(unittest.TestCase):
    def test_ui_sources_contain_no_chinese_characters(self):
        ui_root = ROOT / "app/src/main/kotlin/io/github/zopulus/coogle/ui"
        files = sorted(ui_root.rglob("*.kt"))
        files += sorted((ROOT / "app/src/main/res/values").glob("*.xml"))
        untranslated = {
            str(path.relative_to(ROOT)): match.group(0)
            for path in files
            for match in HAN.finditer(path.read_text(encoding="utf-8"))
        }
        self.assertEqual({}, untranslated)

    def test_settings_screen_keeps_translated_labels(self):
        screen = (ROOT / "app/src/main/kotlin/io/github/zopulus/coogle/ui/CoogleScreen.kt").read_text(
            encoding="utf-8"
        )
        for label in (
            "Make Chinese-market ColorOS more Google-like",
            "Digital assistant",
            "Google Lens",
            "System settings",
            "Other settings",
            "Module status",
            "Restart scopes",
        ):
            self.assertIn(label, screen)


if __name__ == "__main__":
    unittest.main()
