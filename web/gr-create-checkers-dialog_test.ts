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
import './gr-create-checkers-dialog';
import {queryAll, queryAndAssert} from './test/test-util';
import {GrCreateCheckersDialog} from './gr-create-checkers-dialog';
import {fixture, html, assert} from '@open-wc/testing';

suite('gr-create-checkers-dialog tests', () => {
  let element: GrCreateCheckersDialog;

  setup(async () => {
    element = await fixture(html`<gr-create-checkers-dialog></gr-create-checkers-dialog>`);
    await element.updateComplete;
  });

  test('all sections are rendered', () => {
    const div = queryAndAssert<HTMLElement>(element, 'div.gr-form-styles');
    const sections = queryAll<HTMLElement>(div, 'section');
    assert.equal(sections.length, 11);
  });
});
