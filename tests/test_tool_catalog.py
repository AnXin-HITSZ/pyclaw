import unittest

from openclaw.tools.builder import core_tool_definitions
from openclaw.tools.resolver import ToolResolveInput, resolve_tools


class ToolCatalogTests(unittest.TestCase):
    def test_catalog_metadata_is_applied_to_materialized_tools(self):
        tools = {tool.name: tool for tool in core_tool_definitions()}

        self.assertIn("sandbox_read_file", tools)
        self.assertEqual(tools["sandbox_read_file"].metadata.section_id, "sandbox")
        self.assertIn("readonly", tools["sandbox_read_file"].metadata.tags)
        self.assertTrue(tools["sandbox_read_file"].metadata.readonly)
        self.assertEqual(tools["sandbox_apply_patch"].metadata.risk, "medium")
        self.assertNotIn("read", tools)
        self.assertNotIn("web_fetch", tools)

    def test_resolve_tools_returns_sandbox_tools(self):
        result = resolve_tools(ToolResolveInput(profile="coding"))

        names = [tool.name for tool in result.tools]
        self.assertIn("sandbox_read_file", names)
        self.assertIn("sandbox_write_file", names)
        self.assertGreaterEqual(len(result.prompt_fragments), 1)


if __name__ == "__main__":
    unittest.main()
