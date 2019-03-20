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
        "Gerrit-HttpModule: com.google.gerrit.plugins.checks.api.HttpModule",
    ],
    resources = glob(["src/main/resources/**/*"]),
    deps = [
        ":checks-deps-neverlink",
        "//plugins/checks/proto:cache_java_proto",
    ],
)

java_library(
    name = "checks-deps-neverlink",
    neverlink = True,
    visibility = ["//visibility:private"],
    exports = [
        "//java/com/google/gerrit/server/api",
        "//java/com/google/gerrit/server/cache/serialize",
        "//lib/antlr:java-runtime",
        "//lib/auto:auto-value",
        "//lib/auto:auto-value-annotations",
    ],
)
