load("//tools/bzl:plugin.bzl", "gerrit_plugin")
load("//tools/js:eslint.bzl", "eslint")
load("//tools/bzl:js.bzl", "gerrit_js_bundle")
load("@npm//@bazel/typescript:index.bzl", "ts_config", "ts_project")

package_group(
    name = "visibility",
    packages = ["//plugins/checks/..."],
)

package(default_visibility = [":visibility"])

gerrit_plugin(
    name = "checks",
    srcs = glob(["java/com/google/gerrit/plugins/checks/**/*.java"]),
    manifest_entries = [
        "Gerrit-PluginName: checks",
        "Gerrit-Module: com.google.gerrit.plugins.checks.Module",
        "Gerrit-HttpModule: com.google.gerrit.plugins.checks.HttpModule",
        "Gerrit-InitStep: com.google.gerrit.plugins.checks.Init",
    ],
    resource_jars = [":gr-checks"],
    resource_strip_prefix = "plugins/checks/resources",
    resources = glob(["resources/**/*"]),
    deps = ["//plugins/checks/proto:cache_java_proto"],
)

ts_config(
    name = "tsconfig",
    src = "tsconfig.json",
    deps = [
        "//plugins:tsconfig-plugins-base.json",
    ],
)

ts_project(
    name = "gr-checks-ts",
    srcs = glob(
        [
            "gr-checks/**/*.ts",
        ],
        exclude = ["gr-checks/gr-checkers-list_test.ts"],
    ),
    incremental = True,
    tsc = "//tools/node_tools:tsc-bin",
    tsconfig = ":tsconfig",
    deps = [
        "@plugins_npm//@gerritcodereview/typescript-api",
        "@plugins_npm//lit",
    ],
)

gerrit_js_bundle(
    name = "gr-checks",
    srcs = [":gr-checks-ts"],
    entry_point = "gr-checks/plugin.js",
)

# The eslint macro creates 2 rules: lint_test and lint_bin. Typical usage:
# bazel test $DIR:lint_test
# bazel run $DIR:lint_bin -- --fix $PATH_TO_SRCS
eslint(
    name = "lint",
    srcs = glob(["gr-delete-repo/**/*"]),
    config = ".eslintrc.js",
    data = [
        "tsconfig.json",
        "//plugins:.eslintrc.js",
        "//plugins:.prettierrc.js",
        "//plugins:tsconfig-plugins-base.json",
    ],
    extensions = [".ts"],
    ignore = "//plugins:.eslintignore",
    plugins = [
        "@npm//eslint-config-google",
        "@npm//eslint-plugin-html",
        "@npm//eslint-plugin-import",
        "@npm//eslint-plugin-jsdoc",
        "@npm//eslint-plugin-prettier",
        "@npm//gts",
    ],
)
