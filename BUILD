package_group(
    name = "visibility",
    packages = ["//plugins/checks/..."],
)

package(default_visibility = [":visibility"])

load("@rules_java//java:defs.bzl", "java_binary", "java_library")
load("//tools/bzl:javadoc.bzl", "java_doc")
load(
    "//tools/bzl:plugin.bzl",
    "PLUGIN_DEPS_NEVERLINK",
    "gerrit_plugin",
)

CHECKS_PKG = "java/com/google/gerrit/plugins/checks/"

SHARED_SRCS = [CHECKS_PKG + "api/" + s for s in [
    "BlockingCondition.java",
    "CheckState.java",
    "CheckablePatchSetInfo.java",
    "CheckerStatus.java",
    "CheckInfo.java",
    "CheckInput.java",
    "PendingCheckInfo.java",
    "PendingChecksInfo.java",
    "RerunInput.java",
]]

CLIENT_SRC = glob([CHECKS_PKG + "client/*.java"])

gerrit_plugin(
    name = "checks",
    srcs = glob(
        ["java/com/google/gerrit/plugins/checks/**/*.java"],
        exclude = SHARED_SRCS + CLIENT_SRC,
    ),
    manifest_entries = [
        "Gerrit-PluginName: checks",
        "Gerrit-Module: com.google.gerrit.plugins.checks.Module",
        "Gerrit-HttpModule: com.google.gerrit.plugins.checks.HttpModule",
        "Gerrit-InitStep: com.google.gerrit.plugins.checks.Init",
    ],
    resource_jars = ["//plugins/checks/gr-checks:gr-checks-static"],
    resource_strip_prefix = "plugins/checks/resources",
    resources = glob(["resources/**/*"]),
    deps = [
        ":checks-rest-api-shared-lib",
        "//plugins/checks/proto:cache_java_proto",
    ],
)

java_library(
    name = "checks-rest-api-shared-lib",
    srcs = SHARED_SRCS,
    deps = PLUGIN_DEPS_NEVERLINK,
)

java_library(
    name = "checks-rest-api-client-lib",
    srcs = CLIENT_SRC,
    deps = [":checks-rest-api-shared-lib"] + PLUGIN_DEPS_NEVERLINK,
)

java_binary(
    name = "checks-rest-api-client",
    main_class = "Dummy",
    runtime_deps = [
        ":checks-rest-api-client-lib",
        ":checks-rest-api-shared-lib",
    ],
)

java_binary(
    name = "checks-rest-api-client-sources",
    main_class = "Dummy",
    runtime_deps = [
        ":libchecks-rest-api-client-lib-src.jar",
        ":libchecks-rest-api-shared-lib-src.jar",
    ],
)

java_doc(
    name = "checks-rest-api-client-javadoc",
    libs = [
        ":checks-rest-api-client-lib",
        ":checks-rest-api-shared-lib",
    ],
    pkgs = ["com.google.gerrit.plugins.checks"],
    title = "Checks Rest API Client Documentation",
)
