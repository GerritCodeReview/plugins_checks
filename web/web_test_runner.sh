#!/bin/bash

set -euo pipefail
./$1 --config $2 \
  --dir 'plugins/checks/web/_bazel_ts_out_tests' \
  --test-files 'plugins/checks/web/_bazel_ts_out_tests/*_test.js' \
  --type-config="plugins/checks/webtsconfig.json"
