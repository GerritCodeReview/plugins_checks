/**
 * @license
 * Copyright (C) 2017 The Android Open Source Project
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
(function() {
  'use strict';

  //todo(dhruvsri): make sure this works
  const reposPerPage = 6;

  Polymer({
    is: 'gr-create-checks-dialog',
    _legacyUndefinedCheck: true,

    properties: {
      checker: {
        type: Object,
        observer: '_checkerChanged'
      },
      _name: String,
      _scheme: String,
      _id: String,
      _uuid: {
        type: String,
        value: ""
      },
      _url: String,
      _description: String,
      query: {
        type: Function,
        value() {
          return this._getRepoSuggestions.bind(this);
        }
      },
      repos: {
        type: Array,
        value: [],
        notify: true,
      },
      repositorySelected: {
        type: Boolean,
        value: false
      },
      _handleOnRemove: Function,
      _errorMsg: {
        type: String,
        value: ''
      },
      statuses: {
        type: Array,
        value: [
          {
            "text": "ENABLED",
            "value": "ENABLED"
          },
          {
            "text": "DISABLED",
            "value": "DISABLED"
          }
        ]
      },
      _status: String,
      edit: {
        type: Boolean,
        value: false
      }
    },

    observers: [
      '_updateUUID(_scheme, _id)',
    ],

    behaviors: [
      Gerrit.BaseUrlBehavior,
      Gerrit.URLEncodingBehavior,
    ],

    attached() {

    },

    _checkerChanged() {
      console.log("checker changed");
      if (this.checker) {
        this.edit = true;
        this._scheme = this.checker.uuid.split(':')[0];
        this._id = this.checker.uuid.split(':')[1];
        this._name = this.checker.name;
        this._description = this.checker.description || '';
        this._url = this.checker.url || '';
      }
    },

    _updateUUID(_scheme, _id) {
      this._uuid = _scheme + ":" + _id;
    },

    _handleStatusChange(e) {
      this._status = e.detail.value;
    },

    _validateRequest() {
      if (!this._name) {
        this._errorMsg = 'Name cannot be empty';
        return false;
      }
      if (!this._description) {
        this._errorMsg = 'Description cannot be empty';
        return false;
      }
      if (this._description.length > 1000) {
        this._errorMsg = 'Description should be less than 1000 characters';
        return false;
      }
      const pattern = /^[a-zA-Z0-9\-\_\.]*$/;
      if (!this.repositorySelected) {
        this._errorMsg = 'Select a repository';
        return false;
      }
      if (!this._scheme) {
        this._errorMsg = 'Scheme cannot be empty.';
        return false;
      }
      if (this._scheme.match(pattern) == null) {
        this._errorMsg = 'Scheme must contain [A-Z], [a-z], [0-9] or {"-" , "_" , "."}';
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
      if (this._id.match(pattern) == null) {
        this._errorMsg = 'ID must contain [A-Z], [a-z], [0-9] or {"-" , "_" , "."}';
        return false;
      }
      return true;
    },

    _createChecker(check) {
      const url = "/plugins/checks/checkers/";
      return this.$.restAPI._send({
        method: 'POST',
        url,
        body: check,
      })
    },

    _handleCreateChecker() {
      if (!this._validateRequest()) {
        return;
      }
      //currently after creating checker there is no reload happening(as this would result in the user exiting the screen)
      //how to 
      this._createChecker({
        "name" : this._name,
        "description" : this._description,
        "uuid" : this._uuid,
        "repository": this.repos[0].name,
        "url" : this._url,
        "status": this._status
      }).then(
        res => {
          if (res.status == 201) {
            this._cleanUp();
          }
        }
      )
    },

    _cleanUp() {
      this._name = '';
      this._scheme = '';
      this._id = '';
      this._uuid = '';
      this._description = '';
      this.repos = [];
      this.repositorySelected = false;
      this.fire('cancel', null, {bubbles: false});
    },

    _makeSuggestion(repo) {
      return {
        name: repo.name,
        value: repo
      }
    },

    _getRepoSuggestions(filter) {
      return this.$.restAPI.getRepos(filter, reposPerPage).then(
        (repos) => {
          console.log(repos);
          return repos.map
            (
              (repo) => {
                return this._makeSuggestion(repo)
              }
            )
        }
      )
    },

    _handleInputCommit(e) {
      this.push('repos', e.detail.value);
      this.repositorySelected = true;
    },

    _handleOnRemove(e) {
      console.log(e);
      let idx = this.repos.indexOf(e.detail.repo);
      if (idx == -1) return;
      this.splice('repos', idx, 1);
      if (this.repos.length == 0) {
        this.repositorySelected = false;
      }
    },

  });
})();
