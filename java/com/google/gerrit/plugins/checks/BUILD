package(default_visibility = ["//plugins/checks:visibility"])

load("//plugins/checks:build_defs/checks.bzl", "checks_java_library")

checks_java_library(
    name = "checks",
    srcs = glob(["**/*.java"]),
    gerrit_deps = [
        "//java/com/google/gerrit/server/api",
        "//lib/auto:auto-value",
        "//lib/auto:auto-value-annotations",
        "//plugins:plugin-lib-neverlink",
    ],
)
