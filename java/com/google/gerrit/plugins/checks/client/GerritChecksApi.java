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

import com.google.gerrit.plugins.checks.api.CheckInfo;
import com.google.gerrit.plugins.checks.api.CheckInput;
import com.google.gerrit.plugins.checks.api.PendingChecksInfo;
import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import org.apache.http.HttpException;
import org.apache.http.HttpStatus;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.eclipse.jgit.transport.URIish;

public class GerritChecksApi {
  private static final String PENDING_CHECKS_PATH = "plugins/checks/checks.pending/";

  private URIish gerritBaseURL;
  private final HttpClientBuilder clientBuilder;
  private CloseableHttpClient client;

  public GerritChecksApi(URIish gerritBaseURL) {
    this.gerritBaseURL = gerritBaseURL;
    this.clientBuilder = HttpClientBuilder.create();
  }

  public GerritChecksApi setBasicAuthCredentials(String username, String password) {
    CredentialsProvider provider = new BasicCredentialsProvider();
    UsernamePasswordCredentials auth = new UsernamePasswordCredentials(username, password);
    provider.setCredentials(AuthScope.ANY, auth);
    clientBuilder.setDefaultCredentialsProvider(provider);
    gerritBaseURL = gerritBaseURL.setPath("/a");
    return this;
  }

  public GerritChecksApi build() {
    client = clientBuilder.build();
    return this;
  }

  public List<PendingChecksInfo> getChangesWithPendingChecksByCheckerId(String checkerUUID)
      throws IOException {
    URI queryUrl =
        URI.create(
            String.format(
                "%s/%s?query=checker:%s", gerritBaseURL, PENDING_CHECKS_PATH, checkerUUID));
    return requestPendingChecks(queryUrl);
  }

  public List<PendingChecksInfo> getChangesWithPendingChecksByCheckerScheme(String checkerScheme)
      throws IOException {
    URI queryUrl =
        URI.create(
            String.format(
                "%s/%s?query=scheme:%s", gerritBaseURL, PENDING_CHECKS_PATH, checkerScheme));
    return requestPendingChecks(queryUrl);
  }

  public CheckInfo updateCheck(int changeNum, int patchSetNum, CheckInput input)
      throws IOException, HttpException {
    URI queryUrl =
        URI.create(
            String.format(
                "%s/changes/%d/revisions/%d/checks/", gerritBaseURL, changeNum, patchSetNum));

    try {
      HttpPost request = new HttpPost(queryUrl);
      String inputString = newGson().toJson(input, new TypeToken<CheckInput>() {}.getType());
      request.setEntity(new StringEntity(inputString));
      request.setHeader("Content-type", "application/json");

      CloseableHttpResponse response = client.execute(request);
      if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
        return newGson()
            .fromJson(
                EntityUtils.toString(response.getEntity()).split("\n", 2)[1],
                new TypeToken<CheckInfo>() {}.getType());
      }
      throw new HttpException(
          String.format("Request returned status %s", response.getStatusLine().getStatusCode()));
    } catch (Exception e) {
      throw new HttpException("Could not update check", e);
    } finally {
      client.close();
    }
  }

  private List<PendingChecksInfo> requestPendingChecks(URI queryUrl) throws IOException {
    try {
      HttpGet request = new HttpGet(queryUrl);
      CloseableHttpResponse response = client.execute(request);
      if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
        return newGson()
            .fromJson(
                EntityUtils.toString(response.getEntity()).split("\n", 2)[1],
                new TypeToken<List<PendingChecksInfo>>() {}.getType());
      }
      return Collections.emptyList();
    } finally {
      client.close();
    }
  }

  private Gson newGson() {
    return new GsonBuilder()
        .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
        .setDateFormat("yyyy-MM-dd HH:mm:ss.SSS")
        .create();
  }
}
