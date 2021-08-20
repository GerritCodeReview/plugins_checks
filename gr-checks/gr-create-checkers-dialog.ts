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
import './gr-repo-chip';
import {customElement, property} from 'lit/decorators';
import {css, html, LitElement} from 'lit';
import {HttpMethod, RestPluginApi} from '@gerritcodereview/typescript-api/rest';
import {Checker} from './types';

const REPOS_PER_PAGE = 6;
const CREATE_CHECKER_URL = '/plugins/checks/checkers/';
const SCHEME_PATTERN = /^[\w-_.]*$/;

declare global {
  interface HTMLElementTagNameMap {
    'gr-create-checkers-dialog': GrCreateCheckersDialog;
  }
}

export type CancelEvent = CustomEvent<CancelEventDetail>;

export interface CancelEventDetail {
  reload: boolean;
}

@customElement('gr-create-checkers-dialog')
export class GrCreateCheckersDialog extends LitElement {
  @property({type: Object})
  checker?: Checker;
  // TODO: observer: '_checkerChanged',

  @property({type: String})
  _name?: string;

  @property({type: String})
  _scheme?: string;

  @property({type: String})
  _id?: string;

  @property({type: String})
  _uuid = '';

  @property({type: Object})
  pluginRestApi!: RestPluginApi;

  @property({type: String})
  _url?: string;

  @property({type: String})
  _description?: string;

  @property({type: Object})
  _getRepoSuggestions = (filter: string) => this._repoSuggestions(filter);

  // The backend might support multiple repos in the future
  // which is why I decided to keep it as an array.
  @property({type: Array})
  _repos: {name: string}[] = [];
  // TODO:      notify: true,

  @property({type: Boolean})
  _repositorySelected = false;

  @property({type: String})
  _errorMsg = '';

  @property({type: Array})
  _statuses = [
    {
      text: 'ENABLED',
      value: 'ENABLED',
    },
    {
      text: 'DISABLED',
      value: 'DISABLED',
    },
  ];

  @property({type: Boolean})
  _required = false;

  @property({type: String})
  _status?: string;

  @property({type: Boolean})
  _edit = false;

  @property({type: String})
  _query?: string;

  // TODO: static get observers() {
  //   return [
  //     '_updateUUID(_scheme, _id)',
  //   ];
  // }

  static styles = css`
    :host {
      display: inline-block;
    }
    input {
      width: 20em;
    }
    gr-autocomplete {
      border: none;
      --gr-autocomplete: {
        border: 1px solid var(--border-color);
        border-radius: 2px;
        font-size: var(--font-size-normal);
        height: 2em;
        padding: 0 0.15em;
        width: 20em;
      }
    }
    .error {
      color: red;
    }
    #checkerSchemaInput[disabled] {
      background-color: var(--table-subheader-background-color);
    }
    #checkerIdInput[disabled] {
      background-color: var(--table-subheader-background-color);
    }
    .uuid {
      overflow: scroll;
    }
  `;

  render() {
    return html`
      <div class="gr-form-styles">
        <div id="form">
          <section ?hidden="${this._errorMsg.length > 0}">
            <span class="error">${this._errorMsg}</span>
          </section>
          <section>
            <span class="title">Name*</span>
            <iron-input autocomplete="on" bind-value="${this._name}">
              <input id="checkerNameInput" autocomplete="on" />
            </iron-input>
          </section>
          <section>
            <span class="title">Description</span>
            <iron-input autocomplete="on" bind-value="${this._description}">
              <input id="checkerDescriptionInput" autocomplete="on" />
            </iron-input>
          </section>
          <section>
            <span class="title">Repository*</span>
            <div class="list">
              ${this._repos.map(
                repo => html`<gr-repo-chip
                  .repo="${repo}"
                  @remove="${this._handleOnRemove}"
                  tabindex="-1"
                ></gr-repo-chip>`)}
            </div>
            <div ?hidden="${this._repositorySelected}">
              <gr-autocomplete
                id="input"
                .query="[[_getRepoSuggestions]]"
                @commit="_handleRepositorySelected"
                clear-on-commit
                warn-uncommitted
              >
              </gr-autocomplete>
            </div>
          </section>

          <section>
            <span class="title">Scheme*</span>
            <iron-input autocomplete="on" bind-value="{{_scheme}}">
              <input
                id="checkerSchemaInput"
                disabled$="[[_edit]]"
                autocomplete="on"
              >
            </iron-input>
          </section>

          <section>
            <span class="title">ID*</span>
            <iron-input autocomplete="on" bind-value="{{_id}}">
              <input
                id="checkerIdInput"
                disabled$="[[_edit]]"
                autocomplete="on"
              >
            </iron-input>
          </section>

          <section>
            <span class="title">Url</span>
            <iron-input autocomplete="on" bind-value="{{_url}}">
              <input id="checkerUrlInput" autocomplete="on" />
            </iron-input>
          </section>

          <section>
            <span class="title"> UUID </span>
            <span class="title uuid"> {{_uuid}} </span>
          </section>

          <section>
            <span class="title">Status</span>
            <gr-dropdown-list
              items="[[_statuses]]"
              on-value-change="_handleStatusChange"
              text="Status"
              value="[[_status]]"
            >
            </gr-dropdown-list>
          </section>

          <section>
            <span class="title">Required</span>
            <input
              on-click = "_handleRequiredCheckBoxClicked"
              type="checkbox"
              id="privateChangeCheckBox"
              checked$="[[_required]]"
            >
          </section>

          <section>
            <span class="title">Query</span>
            <iron-input autocomplete="on" bind-value="{{_query}}">
              <input id="checkerQueryInput" autocomplete="on" />
            </iron-input>
          </section>

        </div>
      </div>
    `;
  }

  _checkerChanged() {
    if (!this.checker) {
      console.warn('checker not set');
      return;
    }
    this._edit = true;
    this._scheme = this.checker.uuid.split(':')[0];
    this._id = this.checker.uuid.split(':')[1];
    this._name = this.checker.name;
    this._description = this.checker.description || '';
    this._url = this.checker.url || '';
    this._query = this.checker.query || '';
    this._required = !!this.checker.blocking?.length;
    if (this.checker.repository) {
      this._repositorySelected = true;
      this.set('_repos', [{name: this.checker.repository}]);
    }
    this._status = this.checker.status;
  }

  _updateUUID(_scheme: string, _id: string) {
    this._uuid = _scheme + ':' + _id;
  }

  _handleStatusChange(e: CustomEvent) {
    this._status = e.detail.value;
  }

  _validateRequest() {
    if (!this._name) {
      this._errorMsg = 'Name cannot be empty';
      return false;
    }
    if (this._description && this._description.length > 1000) {
      this._errorMsg = 'Description should be less than 1000 characters';
      return false;
    }
    if (!this._repositorySelected) {
      this._errorMsg = 'Select a repository';
      return false;
    }
    if (!this._scheme) {
      this._errorMsg = 'Scheme cannot be empty.';
      return false;
    }
    if (this._scheme.match(SCHEME_PATTERN) === null) {
      this._errorMsg =
        'Scheme must contain [A-Z], [a-z], [0-9] or {"-" , "_" , "."}';
      return false;
    }
    if (this._scheme.length > 100) {
      this._errorMsg = 'Scheme must be shorter than 100 characters';
      return false;
    }
    if (!this._id) {
      this._errorMsg = 'ID cannot be empty.';
      return false;
    }
    if (this._id.match(SCHEME_PATTERN) === null) {
      this._errorMsg =
        'ID must contain [A-Z], [a-z], [0-9] or {"-" , "_" , "."}';
      return false;
    }
    return true;
  }

  // TODO(dhruvsri): make sure dialog is scrollable.

  _createChecker(checker: Checker) {
    return this.pluginRestApi.send(
      HttpMethod.POST,
      CREATE_CHECKER_URL,
      checker
    );
  }

  _editChecker(checker: Checker) {
    const url = CREATE_CHECKER_URL + checker.uuid;
    return this.pluginRestApi.send(HttpMethod.POST, url, checker);
  }

  handleEditChecker() {
    if (!this._validateRequest()) return;
    this._editChecker(this._getCheckerRequestObject()).then(
      res => {
        if (res) {
          this._errorMsg = '';
          this.dispatchEvent(
            new CustomEvent<CancelEventDetail>('cancel', {
              detail: {reload: true},
              bubbles: true,
              composed: true,
            })
          );
        }
      },
      error => {
        this._errorMsg = error;
      }
    );
  }

  _getCheckerRequestObject(): Checker {
    return {
      name: this._name ?? '',
      description: this._description ?? '',
      uuid: this._uuid,
      repository: this._repos[0]?.name,
      url: this._url,
      status: this._status ?? 'UNKNOWN',
      blocking: this._required ? ['STATE_NOT_PASSING'] : [],
      query: this._query,
    };
  }

  handleCreateChecker() {
    if (!this._validateRequest()) return;
    // Currently after creating checker there is no reload happening (as
    // this would result in the user exiting the screen).
    this._createChecker(this._getCheckerRequestObject()).then(
      res => {
        if (res) this._cleanUp();
      },
      error => {
        this._errorMsg = error;
      }
    );
  }

  _cleanUp() {
    this._name = '';
    this._scheme = '';
    this._id = '';
    this._uuid = '';
    this._description = '';
    this._repos = [];
    this._repositorySelected = false;
    this._errorMsg = '';
    this._required = false;
    this._query = '';
    this._status = '';
    this.dispatchEvent(
      new CustomEvent<CancelEventDetail>('cancel', {
        detail: {reload: true},
        bubbles: true,
        composed: true,
      })
    );
  }

  _repoSuggestions(filter: string) {
    const _makeSuggestion = (repo: {name: string}) => {
      return {name: repo.name, value: repo};
    };
    return this.pluginRestApi
      .getRepos(filter, REPOS_PER_PAGE)
      .then(repos => (repos as ProjectInfoWithName).map(repo => _makeSuggestion(repo)));
  }

  _handleRepositorySelected(e) {
    this.push('_repos', e.detail.value);
    this._repositorySelected = true;
  }

  _handleRequiredCheckBoxClicked() {
    this._required = !this._required;
  }

  _handleOnRemove(e) {
    const idx = this._repos.indexOf(e.detail.repo);
    if (idx === -1) return;
    this.splice('_repos', idx, 1);
    if (this._repos.length === 0) {
      this._repositorySelected = false;
    }
  }
}
