# Gerrit Code Review Checks Plugin

This plugin provides a unified experience for checkers (CI systems, static
analyzers, etc.) to integrate with Gerrit Code Review.

This plugin uses [polymer-cli](https://www.polymer-project.org/1.0/docs/tools/polymer-cli#install) to test.

After `bower install`, running `polymer test -l chrome` will run all tests in Chrome, and running `polymer serve` and navigating to http://127.0.0.1:8081/components/codemirror-editor/gr-editor/gr-editor_test.html allows for manual debugging.