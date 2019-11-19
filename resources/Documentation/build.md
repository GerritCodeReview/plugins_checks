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
  bazel test --test_tag_filters=@PLUGIN@
```

To build REST API client library run:

```
  bazel build plugins/@PLUGIN@:checks-rest-api-client_deploy.jar
```

The output is created in

```
  bazel-bin/plugins/@PLUGIN@/@PLUGIN@-rest-api-client_deploy.jar
```

[Back to @PLUGIN@ documentation index][index]

[index]: index.html
