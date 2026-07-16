import unittest

from openclaw.tools.builder import build_tool_registry
from openclaw.tools.catalog import build_tool_groups, materialize_core_tools
from openclaw.tools.policy import ToolPolicy, apply_tool_policy_pipeline, expand_tool_names


class ToolPolicyTests(unittest.TestCase):
    def test_expand_tool_group_names(self):
        expanded = expand_tool_names({"group:claw_sandbox"})

        self.assertIn("workspace_info", expanded)
        self.assertIn("read_file", expanded)
        self.assertIn("apply_patch", expanded)

    def test_allow_group_selects_group_tools(self):
        registry = build_tool_registry(ToolPolicy(allow={"group:readonly"}, profile="minimal"))

        self.assertIsNotNone(registry.resolve("workspace_info"))
        self.assertIsNotNone(registry.resolve("list_files"))
        self.assertIsNotNone(registry.resolve("read_file"))
        self.assertIsNone(registry.resolve("write_file"))
        self.assertIsNone(registry.resolve("apply_patch"))

    def test_also_allow_adds_tool_to_profile(self):
        registry = build_tool_registry(ToolPolicy(profile="minimal", also_allow={"apply_patch"}))

        self.assertIsNotNone(registry.resolve("apply_patch"))
        self.assertIsNotNone(registry.resolve("workspace_info"))

    def test_readonly_policy_filters_mutating_tools(self):
        registry = build_tool_registry(ToolPolicy(profile="full", readonly=True))

        self.assertIsNotNone(registry.resolve("workspace_info"))
        self.assertIsNotNone(registry.resolve("read_file"))
        self.assertIsNone(registry.resolve("write_file"))
        self.assertIsNone(registry.resolve("apply_patch"))

    def test_pipeline_reports_removed_tools(self):
        result = apply_tool_policy_pipeline(materialize_core_tools(), ToolPolicy(profile="readonly"))

        stage_names = [entry.stage for entry in result.audit]
        self.assertIn("profile", stage_names)
        removed_by_profile = next(entry.removed for entry in result.audit if entry.stage == "profile")
        self.assertIn("write_file", removed_by_profile)
        self.assertIn("apply_patch", removed_by_profile)

    def test_catalog_builds_groups_from_tags(self):
        groups = build_tool_groups()

        self.assertIn("read_file", groups["group:readonly"])
        self.assertIn("write_file", groups["group:mutation"])
        self.assertIn("apply_patch", groups["group:patch"])


if __name__ == "__main__":
    unittest.main()
