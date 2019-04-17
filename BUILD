package_group(
    name = "visibility",
    packages = ["//plugins/checks/..."],
)

package(default_visibility = [":visibility"])

load(
    "//tools/bzl:plugin.bzl",
    "gerrit_plugin",
)

gerrit_plugin(
    name = "checks",
    srcs = glob(["java/com/google/gerrit/plugins/checks/**/*.java"]),
    manifest_entries = [
        "Gerrit-PluginName: checks",
        "Gerrit-Module: com.google.gerrit.plugins.checks.Module",
        "Gerrit-HttpModule: com.google.gerrit.plugins.checks.HttpModule",
    ],
    resource_jars = ["//plugins/checks/gr-checks:gr-checks-static"],
    resource_strip_prefix = "plugins/checks/resources",
    resources = glob(["resources/**/*"]),
)
