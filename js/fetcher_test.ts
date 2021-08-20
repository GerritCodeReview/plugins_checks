/**
 * @license
 * Copyright (C) 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import './test/test-setup';
import {ChecksFetcher} from './fetcher';
import {PluginApi} from '@gerritcodereview/typescript-api/plugin';
import {CheckRun} from '@gerritcodereview/typescript-api/checks';
import {Check} from './types';

suite('ChecksFetcher tests', () => {
  let fetcher: ChecksFetcher;

  setup(async () => {
    fetcher = new ChecksFetcher({restApi: () => {}} as PluginApi);
  });

  test('convert', () => {
    const check: Check = {
      state: 'SUCCESSFUL',
      checker_name: 'my-test-name',
      checker_description: 'my-test-description',
      checker_uuid: 'my-test-uuid',
      message: 'my-test-message',
      url: 'http://my-test-url.com',
    };
    const converted: CheckRun = fetcher.convert(check);
    assert.equal(converted.checkDescription, check.checker_description);
    assert.equal(converted.checkName, check.checker_name);
  });
});
