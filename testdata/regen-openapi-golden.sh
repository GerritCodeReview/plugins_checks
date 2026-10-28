#!/usr/bin/env bash
# Regenerate the checked-in checks OpenAPI endpoint-inventory golden after an
# intended REST-surface change. The OpenAPI document itself stays a generated build
# output; only this inventory snapshot is versioned.
#
# Run this from the Gerrit workspace root (the golden path below is repo-relative).
set -euo pipefail

bazelisk build //plugins/checks:checks_openapi_json_inventory
cp -f \
  "$(bazelisk cquery --output=files //plugins/checks:checks_openapi_json_inventory 2>/dev/null | head -1)" \
  plugins/checks/testdata/checks-openapi-endpoints.golden.json
echo "regenerated plugins/checks/testdata/checks-openapi-endpoints.golden.json"
