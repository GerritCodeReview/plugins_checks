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
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.net.URI;
import org.apache.http.HttpException;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.eclipse.jgit.transport.URIish;

public class Checks extends AbstractEndpoint {

  private int changeNumber;
  private int patchSetNumber;

  public Checks(URIish gerritBaseUrl, CloseableHttpClient client) {
    super(gerritBaseUrl, client);
  }

  @Override
  protected URI getEndpointURI() {
    return URI.create(
        getGerritBaseUrl()
            .setPath(
                String.format(
                    "%s/changes/%d/revisions/%d/checks/",
                    getGerritBaseUrl().getPath(), changeNumber, patchSetNumber))
            .toASCIIString());
  }

  public Checks change(int changeNumber) {
    this.changeNumber = changeNumber;
    return this;
  }

  public Checks patchSet(int patchSetNumber) {
    this.patchSetNumber = patchSetNumber;
    return this;
  }

  public CheckInfo update(CheckInput input) throws IOException, HttpException {
    try {
      HttpPost request = new HttpPost(getEndpointURI());
      String inputString =
          JsonBodyParser.createRequestBody(input, new TypeToken<CheckInput>() {}.getType());
      request.setEntity(new StringEntity(inputString));
      request.setHeader("Content-type", "application/json");

      CloseableHttpResponse response = client.execute(request);
      if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
        return JsonBodyParser.parseResponse(
            EntityUtils.toString(response.getEntity()), new TypeToken<CheckInfo>() {}.getType());
      }
      throw new HttpException(
          String.format("Request returned status %s", response.getStatusLine().getStatusCode()));
    } catch (Exception e) {
      throw new HttpException("Could not update check", e);
    } finally {
      client.close();
    }
  }
}
