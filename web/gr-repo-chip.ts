/**
 * @license
 * Copyright (C) 2020 The Android Open Source Project
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
import {customElement, property} from 'lit/decorators.js';
import {css, html, LitElement} from 'lit';
import {fire} from './util';

declare global {
  interface HTMLElementTagNameMap {
    'gr-repo-chip': GrRepoChip;
    'gr-icon': HTMLElement; // Known to exist from Polygerrit
  }
}

/**
 * Autocomplete chip for getting repository suggestions.
 */
@customElement('gr-repo-chip')
export class GrRepoChip extends LitElement {
  static override styles = css`
    :host {
      display: inline-block;
    }
    gr-button {
      --gr-button-padding: 0;
      vertical-align: top;
    }
    gr-icon {
      font-size: var(--line-height-normal);
    }
  `;

  @property()
  repo = '';

  override render() {
    return html`
      <span>${this.repo}</span>
      <gr-button
        link
        tabindex="-1"
        aria-label="Remove"
        @click="${this.handleRemove}"
      >
        <gr-icon icon="close"></gr-icon>
      </gr-button>
    `;
  }

  handleRemove(e: Event) {
    e.preventDefault();
    fire(this, 'remove');
  }
}
