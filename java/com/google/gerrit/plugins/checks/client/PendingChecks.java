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

import com.google.gerrit.plugins.checks.api.PendingChecksInfo;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import org.apache.http.HttpStatus;
import org.apache.http.ParseException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.eclipse.jgit.transport.URIish;

public class PendingChecks extends AbstractEndpoint {
  private static final String PENDING_CHECKS_PATH = "plugins/checks/checks.pending/";
  private static final String QUERY_PREFIX = "?query=";

  private String query;

  public PendingChecks(URIish gerritBaseUrl, CloseableHttpClient client) {
    super(gerritBaseUrl, client);
  }

  public PendingChecks checker(String uuid) {
    query = "checker:" + uuid;
    return this;
  }

  public PendingChecks scheme(String scheme) {
    query = "scheme:" + scheme;
    return this;
  }

  public List<PendingChecksInfo> get() throws ParseException, IOException {
    try {
      HttpGet request = new HttpGet(buildRequestUrl());
      CloseableHttpResponse response = client.execute(request);
      if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
        return JsonBodyParser.parseResponse(
            EntityUtils.toString(response.getEntity()),
            new TypeToken<List<PendingChecksInfo>>() {}.getType());
      }
      return Collections.emptyList();
    } finally {
      client.close();
    }
  }

  @Override
  protected URI getEndpointURI() {
    return URI.create(
        getGerritBaseUrl()
            .setPath(String.format("%s/%s", getGerritBaseUrl().getPath(), PENDING_CHECKS_PATH))
            .toASCIIString());
  }

  private URI buildRequestUrl() {
    return URI.create(getEndpointURI() + QUERY_PREFIX + query);
  }
}
