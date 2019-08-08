//show a list of all checkers along with creating/editing them
(function() {
  'use strict';
  const CHECKERS_PER_PAGE = 15;
  const url = "/a/plugins/checks/checkers/";
  Polymer({
    is: 'gr-checkers-list',
    properties: {
      _checkers: Array,
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
      pluginRestApi: Object,
      _handleNextClicked: Function,
      _handlePrevClicked: Function,
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
      checker: Object
    },
    observers: [
      '_showCheckers(_checkers, _filter)',
    ],
    behaviors: [
      Gerrit.ListViewBehavior,
    ],

    attached() {
      this._getCheckers();
    },

    _showCheckers(_checkers, _filter) {
      if (!_checkers) {
        return;
      }
      if (!_filter) {
        _filter = '';
      }
      //TODO(dhruvsri): highlight matching part
      this._filteredCheckers = this._checkers.filter(
        checker => {
          return checker.name.toLowerCase().includes(this._filter.trim()) ||
          checker.repository.toLowerCase().includes(this._filter.trim())
        }
      )
      this._startingIndex = 0;
    },

    _computeVisibleCheckers(_startingIndex, _filteredCheckers) {
      if (!_filteredCheckers) {
        return;
      }
      return this._filteredCheckers.slice(this._startingIndex, this._startingIndex + CHECKERS_PER_PAGE);
    },

    _computeShowNextButton(_startingIndex, _filteredCheckers) {
      if (!_filteredCheckers) {
        return false;
      }
      return (_startingIndex + CHECKERS_PER_PAGE < _filteredCheckers.length)
    },

    _computeShowPrevButton(_startingIndex, _filteredCheckers) {
      if (!_filteredCheckers) {
        return false;
      }
      return (_startingIndex >= CHECKERS_PER_PAGE);
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

    getCheckers() {
      // let hardCodedResponse = '[{"uuid":"C:D","name":"A","description":"B","repository":"Backend","status":"ENABLED","blocking":[],"query":"status:open","created":"2019-07-25 13:08:43.000000000","updated":"2019-07-25 13:08:43.000000000"},{"uuid":"aa:bb","name":"n1","description":"d1","repository":"All-Users","status":"ENABLED","blocking":[],"query":"status:open","created":"2019-07-29 13:07:17.000000000","updated":"2019-07-29 13:07:17.000000000"},{"uuid":"adsf:asdasdas","name":"ds","description":"s","repository":"Scripts","status":"ENABLED","blocking":[],"query":"status:open","created":"2019-07-29 13:28:09.000000000","updated":"2019-07-29 13:28:09.000000000"},{"uuid":"ijkl:mnop","name":"abcd","description":"efgh","repository":"All-Projects","status":"ENABLED","blocking":[],"query":"status:open","created":"2019-07-29 09:33:25.000000000","updated":"2019-07-29 09:33:25.000000000"},{"uuid":"ngfnf:mhghgnhghn","name":"nbvfg","description":"fjhgj","repository":"All-Users","status":"ENABLED","blocking":[],"query":"status:open","created":"2019-08-06 14:21:34.000000000","updated":"2019-08-06 14:21:34.000000000"},{"uuid":"sdfsdf--:sdfsdf333","name":"sdfsdf","description":"sdfsdfsd","repository":"Scripts","status":"ENABLED","blocking":[],"query":"status:open","created":"2019-07-30 13:00:19.000000000","updated":"2019-07-30 13:00:19.000000000"},{"uuid":"test:checker1","name":"Unit Tests","description":"Random description that should be improved at some point","repository":"Backend","status":"ENABLED","blocking":[],"query":"status:open","created":"2019-07-22 13:16:52.000000000","updated":"2019-07-22 14:21:14.000000000"},{"uuid":"test:checker2","name":"Code Style","repository":"Backend","status":"ENABLED","blocking":[],"query":"status:open","created":"2019-07-22 13:26:56.000000000","updated":"2019-07-22 13:26:56.000000000"},{"uuid":"xddf:sdfsdfsdf","name":"sdfsdf","description":"sdfsdf","repository":"Scripts","status":"ENABLED","blocking":[],"query":"status:open","created":"2019-07-29 14:11:59.000000000","updated":"2019-07-29 14:11:59.000000000"},{"uuid":"zxczxc:bnvnbvnbvn","name":"zxc","description":"zxc","repository":"Scripts","status":"ENABLED","blocking":[],"query":"status:open","created":"2019-07-29 14:00:24.000000000","updated":"2019-07-29 14:00:24.000000000"},{"uuid":"zxczxc:sdfsdf","name":"zxc","description":"zxc","repository":"Scripts","status":"ENABLED","blocking":[],"query":"status:open","created":"2019-07-29 13:30:47.000000000","updated":"2019-07-29 13:30:47.000000000"}]'
      // return new Promise(
      //   (resolve) => {
      //     resolve(JSON.parse(hardCodedResponse))
      //   }
      // )
      return this.pluginRestApi.fetchJSON({
        method: 'GET',
        url,
      });
    },

    _getCheckers() {
      this._checkers = [];
      return this.getCheckers()
        .then(checkers => {
          if (!checkers) { return; }
          this._checkers = checkers;
          this._startingIndex = 0;
          this._loading = false;
        });
    },

    _handleEditConfirm() {
      this.$.editModal._handleEditChecker();
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

    computeBlocking(checker) {
      return (checker && checker.blocking && checker.blocking.length > 0)? "YES": "NO";
    },

    _handleCreateConfirm() {
      this.$.createNewModal._handleCreateChecker();
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