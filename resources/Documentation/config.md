Plugin @PLUGIN@
===============

Configuration
-------------

The plugin-specific settings are defined in the `[plugin-checks]` section
in the `gerrit.config` file.

enableTriggerRerunEvent
:   Trigger gerrit event on Rerun check action. Rerun-check event can be
    processed by webhooks plugin to propagate this event to remote http endpoint.
    Or, alternatively it can be consumed by the CI system to trigger new build.
    Default value: false


Example:

```
  [plugin "checks"]
   enableTriggerRerunEvent = true
```
