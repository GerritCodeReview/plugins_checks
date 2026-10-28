# Build

This plugin can be built with Bazel in the Gerrit tree.

Clone or link this plugin to the plugins directory of Gerrit's
source tree.

From the Gerrit source tree issue the command:

```
  bazel build plugins/@PLUGIN@
```

The output is created in

```
  bazel-bin/plugins/@PLUGIN@/@PLUGIN@.jar
```

To execute the tests run:

```
  bazel test //plugins/@PLUGIN@/...
```

## OpenAPI endpoint-inventory golden

The plugin's OpenAPI document is a generated build output (packaged into the jar at
`Documentation/rest-api-openapi.json`, served live at
`/plugins/@PLUGIN@/Documentation/rest-api-openapi.json`).
It is not checked in. What is versioned is a byte-diff golden of the REST *endpoint
inventory*, guarded by `checks_openapi_json_golden_test` so REST-surface changes show
up as reviewable diffs.

To check whether the checked-in inventory is still current:

```
  bazelisk test //plugins/@PLUGIN@:checks_openapi_json_golden_test
```

If this fails after an intentional REST API change, regenerate the golden (run from
the Gerrit workspace root):

```
  plugins/@PLUGIN@/testdata/regen-openapi-golden.sh
```

Then review and commit the updated
`testdata/checks-openapi-endpoints.golden.json` together with the REST API change.
The golden captures the REST endpoint surface only (paths, methods, bindings,
request/response types, status inference and query parameters); DTO field additions
show up in the generated schema but not in this golden.

[Back to @PLUGIN@ documentation index][index]

[index]: index.html
