(function() {
  'use strict';

  const REPOS_PER_PAGE = 6;
  const CREATE_CHECKER_URL = '/plugins/checks/checkers/';
  const SCHEME_PATTERN = /^[\w-_.]*$/;

  Polymer({
    is: 'gr-create-checkers-dialog',
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
      pluginRestApi: Object,
      _url: String,
      _description: String,
      getRepoSuggestions: {
        type: Function,
        value() {
          return this._getRepoSuggestions.bind(this)
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
        ],
        readOnly: true
      },
      _required: {
        type: Boolean,
        value: false
      },
      _status: String,
      edit: {
        type: Boolean,
        value: false
      },
      _query: String
    },

    behaviours: [
      Gerrit.FireBehavior,
    ],
    /**
    * Fired when the cancel button is pressed.
    *
    * @event cancel
    */


    observers: [
      '_updateUUID(_scheme, _id)',
    ],

    _checkerChanged() {
      if (this.checker) {
        this.edit = true;
        this._scheme = this.checker.uuid.split(':')[0];
        this._id = this.checker.uuid.split(':')[1];
        this._name = this.checker.name;
        this._description = this.checker.description || '';
        this._url = this.checker.url || '';
        //ensure this works
        this._query = this.checker.query || '';
        this._required = this.checker.blocking && this.checker.blocking.length > 0;
        if (this.checker.repository) {
          this.repositorySelected = true;
          this.set('repos', [{name: this.checker.repository}]);
        }
        this._status = this.checker.status;
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
      if (!this.repositorySelected) {
        this._errorMsg = 'Select a repository';
        return false;
      }
      if (!this._scheme) {
        this._errorMsg = 'Scheme cannot be empty.';
        return false;
      }
      if (this._scheme.match(SCHEME_PATTERN) == null) {
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
      if (this._id.match(SCHEME_PATTERN) == null) {
        this._errorMsg = 'ID must contain [A-Z], [a-z], [0-9] or {"-" , "_" , "."}';
        return false;
      }
      return true;
    },

    //TODO(dhruvsri): make sure dialog is scrollable

    _createChecker(check) {
      return this.pluginRestApi.send(
        'POST',
        CREATE_CHECKER_URL,
        check,
      )
    },

    _editChecker(checker) {
      const url = CREATE_CHECKER_URL + checker.uuid;
      return this.pluginRestApi.send(
        'POST',
        url,
        checker
      )
    },

    _handleEditChecker() {
      if (!this._validateRequest()) {
        return;
      }
      this._editChecker(this._getCheckerRequestObject()).then(
        res => {
          if (res) {
            this.fire('cancel', {reload: true}, {bubbles: true});
          }
        },
        error => {
          this._errorMsg = error;
        }
      )
    },

    _getCheckerRequestObject() {
      return {
        "name" : this._name,
        "description" : this._description,
        "uuid" : this._uuid,
        "repository": this.repos[0].name,
        "url" : this._url,
        "status": this._status,
        "blocking": this._required ? ["STATE_NOT_PASSING"] : [],
        "query": this._query
      }
    },

    _handleCreateChecker() {
      if (!this._validateRequest()) {
        return;
      }
      // currently after creating checker there is no reload happening(as
      // this would result in the user exiting the screen)
      this._createChecker(this._getCheckerRequestObject()).then(
        res => {
          if (res) {
            //checker successfully created
            this._cleanUp();
          }
        },
        error => {
          this._errorMsg = error;
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
      this._errorMsg = '';
      this.fire('cancel', {reload: true}, {bubbles: false});
    },

    _getRepoSuggestions(filter) {
      const _makeSuggestion = (repo) => {
        return {
          name: repo.name,
          value: repo
        }
      };
      return this.pluginRestApi.getRepos(filter, REPOS_PER_PAGE).then(
        (repos) => {
          return repos.map (
            (repo) => { return _makeSuggestion(repo)})
        }
      )
    },

    _handleInputCommit(e) {
      this.push('repos', e.detail.value);
      this.repositorySelected = true;
    },

    _handleRequiredCheckBoxClicked() {
      this._required = !this._required;
    },

    _handleOnRemove(e) {
      let idx = this.repos.indexOf(e.detail.repo);
      if (idx == -1) return;
      this.splice('repos', idx, 1);
      if (this.repos.length == 0) {
        this.repositorySelected = false;
      }
    },

  });
})();
