#!/bin/bash

set -euo pipefail
./$1 --config $2 \
  --test-files 'plugins/checks/web/_bazel_ts_out_tests/*_test.js' \
  --ts-config="plugins/checks/web/tsconfig.json"
