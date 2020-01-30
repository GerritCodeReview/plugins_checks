(function() {
  'use strict';
  const CHECKERS_PER_PAGE = 15;
  const GET_CHECKERS_URL = '/plugins/checks/checkers/';

  /**
   * Show a list of all checkers along with creating/editing them
   */
  Polymer({
    is: 'gr-checkers-list',
    properties: {
      /**
       * Add observer on pluginRestApi to call getCheckers when it's defined
       * as initially getCheckers was being called before pluginRestApi was
       * initialised by gr-checks-view
       */
      pluginRestApi: {
        type: Object,
      },
      // Checker that will be passed to the editOverlay modal
      checker: Object,
      _checkers: Array,
      // List of checkers that contain the filtered text
      _filteredCheckers: Array,
      _loading: {
        type: Boolean,
        value: true,
      },
      _filter: {
        type: String,
        value: '',
      },
      _visibleCheckers: {
        type: Array,
        computed: '_computeVisibleCheckers(_startingIndex, _filteredCheckers)',
      },
      _createNewCapability: {
        type: Boolean,
        value: true,
      },
      _startingIndex: {
        type: Number,
        value: 0,
      },
      _showNextButton: {
        type: Boolean,
        value: true,
        computed: '_computeShowNextButton(_startingIndex, _filteredCheckers)',
      },
      _showPrevButton: {
        type: Boolean,
        value: true,
        computed: '_computeShowPrevButton(_startingIndex, _filteredCheckers)',
      },
    },
    observers: [
      '_showCheckers(_checkers, _filter)',
    ],

    attached() {
      /**
       * Adding an observer to listBody element as gr-overlay does not
       * automatically resize itself once the getCheckers response comes.
       * Polymer 2 will deprecate use of obserNodes so replacing it
       * with FlattenedNodesObserver
       */
      if (Polymer.FlattenedNodesObserver) {
        this._checkersListObserver = new Polymer.FlattenedNodesObserver(
            this.$.listBody, () => {
              this.$.listOverlay.refit();
            });
      } else {
        this._checkersListObserver = Polymer.dom(this.$.listBody).observeNodes(
            () => {
              this.$.listOverlay.refit();
            });
      }
    },

    detached() {
      Polymer.dom(this.$.listBody).unobserveNodes(this._checkersListObserver);
    },

    _contains(target, keyword) {
      return target.toLowerCase().includes(keyword.toLowerCase().trim());
    },

    _showConfigureOverlay() {
      this.$.listOverlay.open().then(
          () => {
            this._getCheckers();
          }
      );
    },

    _showCheckers(_checkers, _filter) {
      if (!_checkers) return;
      if (!_filter) _filter = '';
      // TODO(dhruvsri): highlight matching part
      this._filteredCheckers = this._checkers.filter(checker =>
        this._contains(checker.name, this._filter) ||
        this._contains(checker.repository, this._filter));
      this._startingIndex = 0;
    },

    computeLoadingClass(loading) {
      return loading ? 'loading' : '';
    },

    _computeVisibleCheckers(_startingIndex, _filteredCheckers) {
      if (!_filteredCheckers) {
        return [];
      }
      return this._filteredCheckers.slice(this._startingIndex,
          this._startingIndex + CHECKERS_PER_PAGE);
    },

    _computeShowNextButton(_startingIndex, _filteredCheckers) {
      if (!_filteredCheckers) {
        return false;
      }
      return _startingIndex + CHECKERS_PER_PAGE < _filteredCheckers.length;
    },

    _computeShowPrevButton(_startingIndex, _filteredCheckers) {
      if (!_filteredCheckers) {
        return false;
      }
      return _startingIndex >= CHECKERS_PER_PAGE;
    },

    _handleNextClicked() {
      if (this._startingIndex + CHECKERS_PER_PAGE <
        this._filteredCheckers.length) {
        this._startingIndex += CHECKERS_PER_PAGE;
      }
    },

    _handlePrevClicked() {
      if (this._startingIndex >= CHECKERS_PER_PAGE) {
        this._startingIndex -= CHECKERS_PER_PAGE;
      }
    },

    _getCheckers() {
      if (!this.pluginRestApi) return;
      this.pluginRestApi.get(GET_CHECKERS_URL).then(checkers => {
        if (!checkers) { return; }
        this._checkers = checkers;
        this._startingIndex = 0;
        this._loading = false;
      });
    },

    _handleEditConfirm() {
      this.$.editModal.handleEditChecker();
    },

    _handleEditIconClicked(e) {
      const checker = e.model.item;
      this.checker = checker;
      this.$.editOverlay.open();
    },

    _handleEditCancel(e) {
      if (e.detail.reload) {
        this._getCheckers();
      }
      this.$.editOverlay.close();
    },

    _computeCreateClass(createNew) {
      return createNew ? 'show' : '';
    },

    _computeBlocking(checker) {
      return (checker && checker.blocking && checker.blocking.length > 0)
        ? 'YES': 'NO';
    },

    _handleCreateConfirm() {
      this.$.createNewModal.handleCreateChecker();
    },

    _handleCreateClicked() {
      this.$.createOverlay.open();
    },

    _handleCreateCancel(e) {
      if (e.detail.reload) {
        this._getCheckers();
      }
      this.$.createOverlay.close();
    },

  });
})();