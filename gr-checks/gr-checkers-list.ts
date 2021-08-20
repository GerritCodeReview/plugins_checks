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
import './gr-create-checkers-dialog';
import {css, html, LitElement} from 'lit';
import {customElement, property, query} from 'lit/decorators';
import {PluginApi} from '@gerritcodereview/typescript-api/plugin';
import {Checker} from './types';
import {CancelEvent, GrCreateCheckersDialog} from './gr-create-checkers-dialog';

const CHECKERS_PER_PAGE = 15;
const GET_CHECKERS_URL = '/plugins/checks/checkers/';

// TODO: This should be defined and exposed by @gerritcodereview/typescript-api
type GrOverlay = Element & {
  open(): void;
  close(): void;
};

declare global {
  interface HTMLElementTagNameMap {
    'gr-checkers-list': GrCheckersList;
  }
}

/**
 * Show a list of all checkers along with creating/editing them
 */
@customElement('gr-checkers-list')
export class GrCheckersList extends LitElement {
  @query('#editModal')
  editModal?: GrCreateCheckersDialog;

  @query('#editOverlay')
  editOverlay?: GrOverlay;

  @query('#createNewModal')
  createNewModal?: GrCreateCheckersDialog;

  @query('#createOverlay')
  createOverlay?: GrOverlay;

  /** Guaranteed to be provided by the plugin endpoint. */
  @property({type: Object})
  plugin!: PluginApi;

  // Checker that will be passed to the editOverlay modal
  @property({type: Object})
  checker?: Checker;

  @property({type: Array})
  _checkers: Checker[] = [];

  // List of checkers that contain the filtered text
  @property({type: Array})
  _filteredCheckers: Checker[] = [];

  @property({type: Boolean})
  _loading = true;

  @property({type: String})
  _filter = '';

  @property({type: Array})
  _visibleCheckers: Checker[] = [];
  // TODO: '_computeVisibleCheckers(' + '_startingIndex, _filteredCheckers)',

  @property({type: Boolean})
  _createNewCapability = true;

  @property({type: Number})
  _startingIndex = 0;

  @property({type: Boolean})
  _showNextButton = true;
  // TODO: computed: '_computeShowNextButton(_startingIndex, _filteredCheckers)',

  @property({type: Boolean})
  _showPrevButton = true;
  // TODO: computed: '_computeShowPrevButton(_startingIndex, _filteredCheckers)',

  static styles = css`
    #container {
      width: 80vw;
      height: 80vh;
      overflow: auto;
    }
    iron-icon {
      cursor: pointer;
    }
    #filter {
      font-size: var(--font-size-normal);
      max-width: 25em;
    }
    #filter:focus {
      outline: none;
    }
    #topContainer {
      align-items: center;
      display: flex;
      height: 3rem;
      justify-content: space-between;
      margin: 0 1em;
    }
    #createNewContainer:not(.show) {
      display: none;
    }
    a {
      color: var(--primary-text-color);
      text-decoration: none;
    }
    nav {
      align-items: center;
      display: flex;
      height: 3rem;
      justify-content: flex-end;
      margin-right: 20px;
    }
    nav,
    iron-icon {
      color: var(--deemphasized-text-color);
    }
    .nav-iron-icon {
      height: 1.85rem;
      margin-left: 16px;
      width: 1.85rem;
    }
    .nav-buttons:hover {
      text-decoration: underline;
      cursor: pointer;
    }
  `;

  // TODO: static get observers() {
  //  return ['_showCheckers(_checkers, _filter)', '_getCheckers(plugin)'];
  // }

  render() {
    return html`
      <div id="container">
        <div id="topContainer">
          <div>
            <label>Filter:</label>
            <iron-input type="text" bind-value="{{_filter}}">
              <input
                is="iron-input"
                type="text"
                id="filter"
                bind-value="{{_filter}}"
              />
            </iron-input>
          </div>
          <div
            id="createNewContainer"
            class$="[[_computeCreateClass(_createNewCapability)]]"
          >
            <gr-button
              primary
              link
              id="createNew"
              on-click="_handleCreateClicked"
            >
              Create New
            </gr-button>
          </div>
        </div>
        <table id="list" class="genericList">
          <tr class="headerRow">
            <th class="name topHeader">Checker Name</th>
            <th class="name topHeader">Repository</th>
            <th class="name topHeader">Status</th>
            <th class="name topHeader">Required</th>
            <th class="topHeader description">Checker Description</th>
            <th class="name topHeader">Edit</th>
          </tr>
          <tbody id="listBody" class$="[[computeLoadingClass(_loading)]]">
            <template is="dom-repeat" items="[[_visibleCheckers]]">
              <tr class="table">
                <td class="name">
                  <a>[[item.name]]</a>
                </td>
                <td class="name">[[item.repository]]</td>
                <td class="name">[[item.status]]</td>
                <td class="name">[[_computeBlocking(item)]]</td>
                <td class="description">[[item.description]]</td>
                <td on-click="_handleEditIconClicked">
                  <iron-icon icon="gr-icons:edit"></iron-icon>
                </td>
              </tr>
            </template>
          </tbody>
        </table>
        <nav>
          <template is="dom-if" if="[[_showPrevButton]]">
            <a class="nav-buttons" id="prevArrow" on-click="_handlePrevClicked">
              <iron-icon
                class="nav-iron-icon"
                icon="gr-icons:chevron-left"
              ></iron-icon>
            </a>
          </template>
          <template is="dom-if" if="[[_showNextButton]]">
            <a class="nav-buttons" id="nextArrow" on-click="_handleNextClicked">
              <iron-icon icon="gr-icons:chevron-right"></iron-icon>
            </a>
          </template>
        </nav>
      </div>
      <gr-overlay id="createOverlay">
        <gr-dialog
          id="createDialog"
          confirm-label="Create"
          on-confirm="_handleCreateConfirm"
          on-cancel="_handleCreateCancel"
        >
          <div class="header" slot="header">Create Checkers</div>
          <div slot="main">
            <gr-create-checkers-dialog
              id="createNewModal"
              plugin-rest-api="[[plugin.restApi()]]"
            >
            </gr-create-checkers-dialog>
          </div>
        </gr-dialog>
      </gr-overlay>
      <gr-overlay id="editOverlay">
        <gr-dialog
          id="editDialog"
          confirm-label="Save"
          on-confirm="_handleEditConfirm"
          on-cancel="_handleEditCancel"
        >
          <div class="header" slot="header">Edit Checker</div>
          <div slot="main">
            <gr-create-checkers-dialog
              checker="[[checker]]"
              plugin-rest-api="[[plugin.restApi()]]"
              on-cancel="_handleEditCancel"
              id="editModal"
            >
            </gr-create-checkers-dialog>
          </div>
        </gr-dialog>
      </gr-overlay>
    `;
  }

  _contains(target: string, keyword: string) {
    return target.toLowerCase().includes(keyword.toLowerCase().trim());
  }

  _showCheckers(_checkers: Checker[], _filter: string) {
    if (!_checkers) return;
    // TODO(dhruvsri): highlight matching part
    this._filteredCheckers = this._checkers.filter(
      checker =>
        this._contains(checker.name, this._filter) ||
        this._contains(checker.repository ?? '', this._filter)
    );
    this._startingIndex = 0;
  }

  computeLoadingClass(loading: boolean) {
    return loading ? 'loading' : '';
  }

  _computeVisibleCheckers(
    _startingIndex: number,
    _filteredCheckers: Checker[]
  ) {
    if (!_filteredCheckers) {
      return [];
    }
    return this._filteredCheckers.slice(
      this._startingIndex,
      this._startingIndex + CHECKERS_PER_PAGE
    );
  }

  _computeShowNextButton(_startingIndex: number, _filteredCheckers: Checker[]) {
    if (!_filteredCheckers) {
      return false;
    }
    return _startingIndex + CHECKERS_PER_PAGE < _filteredCheckers.length;
  }

  _computeShowPrevButton(_startingIndex: number, _filteredCheckers: Checker[]) {
    if (!_filteredCheckers) {
      return false;
    }
    return _startingIndex >= CHECKERS_PER_PAGE;
  }

  _handleNextClicked() {
    if (
      this._startingIndex + CHECKERS_PER_PAGE <
      this._filteredCheckers.length
    ) {
      this._startingIndex += CHECKERS_PER_PAGE;
    }
  }

  _handlePrevClicked() {
    if (this._startingIndex >= CHECKERS_PER_PAGE) {
      this._startingIndex -= CHECKERS_PER_PAGE;
    }
  }

  _getCheckers() {
    this.plugin
      .restApi()
      .get(GET_CHECKERS_URL)
      .then(checkers => {
        if (!checkers) return;
        this._checkers = checkers as Checker[];
        this._startingIndex = 0;
        this._loading = false;
      });
  }

  _handleEditConfirm() {
    this.editModal?.handleEditChecker();
  }

  _handleEditIconClicked(e: any) {
    this.checker = e.model.item;
    this.editOverlay?.open();
  }

  _handleEditCancel(e: CancelEvent) {
    if (e.detail?.reload) this._getCheckers();
    this.editOverlay?.close();
  }

  _computeCreateClass(createNew: boolean) {
    return createNew ? 'show' : '';
  }

  _computeBlocking(checker: Checker) {
    return checker && checker.blocking && checker.blocking.length > 0
      ? 'YES'
      : 'NO';
  }

  _handleCreateConfirm() {
    this.createNewModal?.handleCreateChecker();
  }

  _handleCreateClicked() {
    this.createOverlay?.open();
  }

  _handleCreateCancel(e: CancelEvent) {
    if (e.detail?.reload) this._getCheckers();
    this.createOverlay?.close();
  }
}
