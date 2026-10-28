load("@gerrit_rules_openapi//openapi:defs.bzl", "openapi_resource_jar")
load("@gerrit_rules_openapi//openapi:report.bzl", "openapi_plugin_spec")
load("//tools/bzl:plugin.bzl", "gerrit_plugin")

package_group(
    name = "visibility",
    packages = ["//plugins/checks/..."],
)

package(default_visibility = [":visibility"])

# Full OpenAPI 3.1 document for the checks REST API: structure (parse-only from
# ApiModule's bindings -- the top-level /checkers and /checks.pending collections mounted
# by HttpModule plus the revision-level child(REVISION_KIND, "checks")) enriched with the
# plugin's own prose mined from its rest-api*.md docs -- conceptually the same
# base -> mine -> emit pipeline core runs, with Markdown instead of AsciiDoc. The core
# change module is context only: it resolves the change->revision parent chain but its
# endpoints are NOT emitted. A build output, not checked in.
openapi_plugin_spec(
    name = "checks_openapi_json",
    context_modules = [
        "com.google.gerrit.server.restapi.change.ChangeRestApiModule",
    ],
    # Inherit field descriptions for shared gerrit-core entities the checks API
    # references but does not document locally (e.g. NotifyInfo) from core's mined
    # overrides, composed underneath the plugin's own mined + curated prose.
    context_overrides = "//tools/openapi:openapi-generated-overrides.json",
    curated_overrides = "openapi-overrides.json",
    docs = glob(["resources/Documentation/rest-api*.md"]),
    # Alias the CheckerInput DTO to its doc heading (CheckerCreateInput), which carries
    # the full field set. Plugin-scoped so core's shared alias table stays plugin-free.
    entity_aliases = "openapi-entity-aliases.json",
    inventory_golden = "testdata/checks-openapi-endpoints.golden.json",
    modules = ["com.google.gerrit.plugins.checks.api.ApiModule"],
    plugin = "checks",
    roots = [
        ":checks__plugin",
        "//java/com/google/gerrit/server/restapi",
        "//java/com/google/gerrit/httpd",
        "//java/com/google/gerrit/extensions:api",
    ],
    # info.version of the emitted document; keep in sync with core's //tools/openapi
    # VERSION (the host Gerrit's GERRIT_VERSION).
    version = "3.16.0-SNAPSHOT",
)

# Package the spec into the plugin jar at Documentation/rest-api-openapi.json (via
# resource_jars, so it does not cycle through :checks__plugin, which the report reads).
# Reachable live at /plugins/checks/Documentation/rest-api-openapi.json.
openapi_resource_jar(
    name = "checks_openapi_resource",
    spec = ":checks_openapi_json",
)

gerrit_plugin(
    name = "checks",
    srcs = glob(["java/com/google/gerrit/plugins/checks/**/*.java"]),
    manifest_entries = [
        "Gerrit-PluginName: checks",
        "Gerrit-Module: com.google.gerrit.plugins.checks.Module",
        "Gerrit-HttpModule: com.google.gerrit.plugins.checks.HttpModule",
        "Gerrit-InitStep: com.google.gerrit.plugins.checks.Init",
        "Gerrit-Capabilities: openapi",
    ],
    resource_jars = [
        "//plugins/checks/web:checks",
        ":checks_openapi_resource",
    ],
    resource_strip_prefix = "plugins/checks/resources",
    resources = glob(["resources/**/*"]),
    deps = ["//plugins/checks/proto:cache_java_proto"],
)
