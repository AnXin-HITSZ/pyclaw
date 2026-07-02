import os
import tempfile
import unittest
from pathlib import Path

from openclaw.config import load_env_file


class EnvLoaderTests(unittest.TestCase):
    def test_load_env_file_sets_values_without_overriding_existing(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            env_path = Path(temp_dir) / ".env"
            env_path.write_text(
                "\n".join(
                    [
                        "# comment",
                        "OPENAI_API_KEY=from-file",
                        "export OPENAI_MODEL='gpt-test'",
                        "OPENAI_BASE_URL=https://example.test # inline comment",
                    ]
                ),
                encoding="utf-8",
            )
            old_key = os.environ.get("OPENAI_API_KEY")
            old_model = os.environ.get("OPENAI_MODEL")
            old_base = os.environ.get("OPENAI_BASE_URL")
            try:
                os.environ["OPENAI_API_KEY"] = "existing"
                os.environ.pop("OPENAI_MODEL", None)
                os.environ.pop("OPENAI_BASE_URL", None)
                loaded = load_env_file(env_path)

                self.assertEqual(loaded["OPENAI_API_KEY"], "from-file")
                self.assertEqual(os.environ["OPENAI_API_KEY"], "existing")
                self.assertEqual(os.environ["OPENAI_MODEL"], "gpt-test")
                self.assertEqual(os.environ["OPENAI_BASE_URL"], "https://example.test")
            finally:
                _restore_env("OPENAI_API_KEY", old_key)
                _restore_env("OPENAI_MODEL", old_model)
                _restore_env("OPENAI_BASE_URL", old_base)

    def test_load_env_file_can_override_existing_values(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            env_path = Path(temp_dir) / ".env"
            env_path.write_text('OPENAI_API_KEY="from-file"', encoding="utf-8")
            old_key = os.environ.get("OPENAI_API_KEY")
            try:
                os.environ["OPENAI_API_KEY"] = "existing"
                load_env_file(env_path, override=True)

                self.assertEqual(os.environ["OPENAI_API_KEY"], "from-file")
            finally:
                _restore_env("OPENAI_API_KEY", old_key)


def _restore_env(key: str, value: str | None) -> None:
    if value is None:
        os.environ.pop(key, None)
    else:
        os.environ[key] = value


if __name__ == "__main__":
    unittest.main()
