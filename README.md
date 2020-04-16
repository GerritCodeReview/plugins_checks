# Gerrit Code Review Checks Plugin

This plugin provides a unified experience for checkers (CI systems, static
analyzers, etc.) to integrate with Gerrit Code Review.

When upgrading the plugin, please use init:

    java -jar gerrit.war init -d site_path

More details about "init" in https://gerrit-review.googlesource.com/Documentation/pgm-init.html

## UI tests

This plugin rely on Gerrit when testing due to following dependencies provided by Gerrit:

1. polymer
2. webcomponents
3. wct-browser-legacy

Test steps:

1. Clone Gerrit into 'gerrit'
2. Clone checks plugin into 'gerrit/plugins'
3. start the server on Gerrit, `npm run start`
4. visit: http://localhost:8081/plugins_/checks/gr-checks/ to see all tests or http://localhost:8081/plugins_/checks/gr-checks/gr-checkers-list_test.html for a particular test

TODO: test them with one command (bazel test target etc)

## Test plugin on Gerrit

1. Build the bundle locally with: `bazel build gr-checks:gr-checks`
2. Serve your generated 'checks.js' somewhere, you can put it under `gerrit/plugins/checks/` folder and it will automatically served at `http://localhost:8081/plugins_/checks/`
3. Use FE dev helper, https://gerrit.googlesource.com/gerrit-fe-dev-helper/, inject the local served 'checks.js' to the page

or

1. If checks is already enabled on the host, use the extension redirect the existing `checks.js` to your local served `gr-checks.js` and you will get all source code loaded