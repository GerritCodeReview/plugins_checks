(function() {
  'use strict';
  const CHECKERS_PER_PAGE = 15;
  const GET_CHECKERS_URL = "/a/plugins/checks/checkers/";

  /**
  * Show a list of all checkers along with creating/editing them
  */
  Polymer({
    is: 'gr-checkers-list',
    properties: {
      pluginRestApi: Object,
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
        computed: '_computeVisibleCheckers(_startingIndex, _filteredCheckers)'
      },
      _createNewCapability: {
        type: Boolean,
        value: true,
      },
      _startingIndex: {
        type: Number,
        value: 0
      },
      _showNextButton: {
        type: Boolean,
        value: true,
        computed: '_computeShowNextButton(_startingIndex, _filteredCheckers)'
      },
      _showPrevButton: {
        type: Boolean,
        value: true,
        computed: '_computeShowPrevButton(_startingIndex, _filteredCheckers)'
      },
      // Checker that will be passed to the editOverlay modal 
      checker: Object
    },
    observers: [
      '_showCheckers(_checkers, _filter)',
    ],

    attached() {
      this._getCheckers();
    },

    _contains(target, keyword) {
      return target.toLowerCase().includes(keyword.trim());
    },

    _showCheckers(_checkers, _filter) {
      if (!_checkers) return; 
      if (!_filter) _filter = '';
      // TODO(dhruvsri): highlight matching part
      this._filteredCheckers = this._checkers.filter(
        checker => {
          return this._contains(checker.name, this._filter) ||
                 this._contains(checker.repository, this._filter);
        }
      )
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
      if (this._startingIndex + CHECKERS_PER_PAGE < this._filteredCheckers.length) {
        this._startingIndex += CHECKERS_PER_PAGE;
      }
    },

    _handlePrevClicked() {
      if (this._startingIndex >= CHECKERS_PER_PAGE) {
        this._startingIndex -= CHECKERS_PER_PAGE;
      }
    },

    _getCheckers() {
      const getCheckers = () => {
        return this.pluginRestApi.fetchJSON({
          method: 'GET',
          url: GET_CHECKERS_URL,
        });
      };  
      this._checkers = [];
      return getCheckers()
        .then(checkers => {
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
      let checker = e.model.item;
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
        ? "YES": "NO";
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

  })
})();