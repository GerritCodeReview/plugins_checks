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
import {customElement, property} from 'lit/decorators';
import {css, html, LitElement} from 'lit';
import {ProjectInfo} from './types';

declare global {
  interface HTMLElementTagNameMap {
    'gr-repo-chip': GrRepoChip;
  }
}

/**
 * autocomplete chip for getting repository suggestions
 */
@customElement('gr-repo-chip')
export class GrRepoChip extends LitElement {
  static styles = css`
    iron-icon {
      height: 1.2rem;
      width: 1.2rem;
    }
    :host {
      display: inline-block;
    }
  `;

  @property({type: Object})
  repo?: ProjectInfo;

  @property({type: Boolean})
  removable = true;

  render() {
    return html`
      <span> {{repo.name}} </span>
      <gr-button
        id="remove"
        link
        hidden$="[[!removable]]"
        tabindex="-1"
        aria-label="Remove"
        class="remove"
        @click="${this._handleRemove}"
      >
        <iron-icon icon="gr-icons:close"></iron-icon>
      </gr-button>
    `;
  }

  _handleRemove(e: Event) {
    e.preventDefault();
    this.dispatchEvent(
      new CustomEvent('remove', {
        detail: {repo: this.repo},
        bubbles: true,
        composed: true,
      })
    );
  }
}
