// Copyright (C) 2019 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.gerrit.plugins.checks.client;

import org.apache.http.impl.client.CloseableHttpClient;
import org.eclipse.jgit.transport.URIish;

public class GerritChecksApi {

  private URIish gerritBaseUrl;
  private CloseableHttpClient client;

  public GerritChecksApi(URIish gerritBaseUrl, CloseableHttpClient client) {
    this.gerritBaseUrl = gerritBaseUrl;
    this.client = client;
  }

  public PendingChecks pendingChecks() {
    return new PendingChecks(gerritBaseUrl, client);
  }

  public Checks checks() {
    return new Checks(gerritBaseUrl, client);
  }
}
