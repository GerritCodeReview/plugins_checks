# Gerrit Code Review Checks Plugin

This plugin provides a unified experience for checkers (CI systems, static
analyzers, etc.) to integrate with Gerrit Code Review.

When upgrading the plugin, please use init:

    java -jar gerrit.war init -d site_path

More details about "init" in https://gerrit-review.googlesource.com/Documentation/pgm-init.html

## UI tests

To run UI tests here will need install dependencies from both npm and bower.

`npm run wct-test` should take care both for you, read more in `package.json`.

You will need `polymer-bridges` which is a submodule you can clone from: https://gerrit-review.googlesource.com/admin/repos/polymer-bridges

The dependency for `polymer-bridges` in package.json assumed that `polymer-bridges` will be installed in two levels above, as
plugins usually will be installed into `gerrit/plugins` folder, and `polymer-bridges` will be installed in `gerrit` as well.

## Test plugin on Gerrit

1. Build the bundle locally with: `bazel build gr-checks:gr-checks`
2. Serve your generated 'checks.js' somewhere, you can put it under `gerrit/plugins/checks/` folder and it will automatically served at `http://localhost:8081/plugins_/checks/`
3. Use FE dev helper, https://gerrit.googlesource.com/gerrit-fe-dev-helper/, inject the local served 'checks.js' to the page

or

1. If checks is already enabled on the host, use the extension redirect the existing `checks.js` to your local served `gr-checks.js` and you will get all source code loaded