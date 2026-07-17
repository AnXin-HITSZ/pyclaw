import unittest

from openclaw.session.ids import sanitize_session_id


class SessionIdTests(unittest.TestCase):
    def test_sanitize_session_id_keeps_safe_characters(self):
        self.assertEqual(sanitize_session_id(" demo.session_1 "), "demo.session_1")

    def test_sanitize_session_id_replaces_unsafe_characters(self):
        self.assertEqual(sanitize_session_id("../hello world/"), "hello-world")

    def test_sanitize_session_id_rejects_empty_values(self):
        with self.assertRaises(ValueError):
            sanitize_session_id("...")


if __name__ == "__main__":
    unittest.main()