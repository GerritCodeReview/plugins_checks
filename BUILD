package_group(
    name = "visibility",
    packages = ["//plugins/checks/..."],
)

package(default_visibility = [":visibility"])

load(
    "//tools/bzl:plugin.bzl",
    "gerrit_plugin",
)

# TODO(davido): Remove this workaround, when this issue is fixed:
# https://github.com/bazelbuild/bazel/issues/10609
java_binary(
    name = "protobuf_java_env",
    main_class = "Dummy",
    runtime_deps = ["@com_google_protobuf//:protobuf_java"],
)

gerrit_plugin(
    name = "checks",
    srcs = glob(["java/com/google/gerrit/plugins/checks/**/*.java"]),
    deploy_env = ["protobuf_java_env"],
    manifest_entries = [
        "Gerrit-PluginName: checks",
        "Gerrit-Module: com.google.gerrit.plugins.checks.Module",
        "Gerrit-HttpModule: com.google.gerrit.plugins.checks.HttpModule",
        "Gerrit-InitStep: com.google.gerrit.plugins.checks.Init",
    ],
    resource_jars = ["//plugins/checks/gr-checks:gr-checks-static"],
    resource_strip_prefix = "plugins/checks/resources",
    resources = glob(["resources/**/*"]),
    deps = ["//plugins/checks/proto:cache_java_proto"],
)
