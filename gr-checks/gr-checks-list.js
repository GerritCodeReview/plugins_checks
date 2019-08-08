(function() {
  'use strict';
  Polymer({
    is: 'gr-checks-list',
    properties: {
      _checks: Array,
      _filteredChecks: Array,
      _loading: {
        type: Boolean,
        value: true,
      },
      _filter: {
        type: String,
        value: '',
      },
      _checksPerPage: {
        type: Number,
        value: 25,
      },
      _path: {
        type: String,
        readOnly: true,
        value: '/admin/checks',
      },
      _visibleChecks: {
        type: Array,
      },
      _createNewCapability: {
        type: Boolean,
        value: true,
      },
      _startingIndex: {
        type: Number,
        value: 0
      },
      _handleNextClicked: Function,
      _handlePrevClicked: Function,
      _showNextButton: {
        type: Boolean,
        value: true,
        computed: '_computeShowNextButton(_startingIndex, _filteredChecks)'
      },
      _showPrevButton: {
        type: Boolean,
        value: true,
        computed: '_computeShowPrevButton(_startingIndex, _filteredChecks)'
      },
      _handleEditChecker: Function,
      checker: Object
    },
    observers: [
      '_showChecks(_checks, _filter)',
    ],
    behaviors: [
      Gerrit.ListViewBehavior,
    ],

    attached() {
      this._getChecks();
    },

    _showChecks(_checks, _filter) {
      if (!_checks) {
        return;
      }
      if (!_filter) {
        _filter = '';
      }
      //todo(dhruvsri): highlight matching part
      this._filteredChecks = this._checks.filter(
        check => {
          return check.name.toLowerCase().includes(this._filter.trim()) ||
          check.repository.toLowerCase().includes(this._filter.trim())
        }
      )
      this._startingIndex = 0;
      this._visibleChecks = this._filteredChecks.slice(this._startingIndex, this._startingIndex + this._checksPerPage);
    },

    _computeShowNextButton(_startingIndex, _filteredChecks) {
      if (!_filteredChecks) {
        return;
      }
      if (_startingIndex + this._checksPerPage < _filteredChecks.length) {
        return true;
      }
      return false;
    },

    _computeShowPrevButton(_startingIndex, _filteredChecks) {
      if (!_filteredChecks) {
        return;
      }
      if (_startingIndex >= this._checksPerPage) {
        return true;
      }
      return false;
    },

    _handleNextClicked() {
      if (this._startingIndex + this._checksPerPage < this._filteredChecks.length) {
        this._startingIndex += this._checksPerPage;
      }
      this._visibleChecks = this._filteredChecks.slice(this._startingIndex, this._startingIndex + this._checksPerPage);
    },

    _handlePrevClicked() {
      if (this._startingIndex >= this._checksPerPage) {
        this._startingIndex -= this._checksPerPage;
      }
      this._visibleChecks = this._filteredChecks.slice(this._startingIndex, this._startingIndex + this._checksPerPage);
    },

    _handleFilterChanged(e) {
      this._filter = e.detail.filter;
    },

    getCheckers() {
      // let hardCodedResponse = '[{"uuid":"C:D","name":"A","description":"B","repository":"Backend","status":"ENABLED","blocking":[],"query":"status:open","created":"2019-07-25 13:08:43.000000000","updated":"2019-07-25 13:08:43.000000000"},{"uuid":"aa:bb","name":"n1","description":"d1","repository":"All-Users","status":"ENABLED","blocking":[],"query":"status:open","created":"2019-07-29 13:07:17.000000000","updated":"2019-07-29 13:07:17.000000000"},{"uuid":"adsf:asdasdas","name":"ds","description":"s","repository":"Scripts","status":"ENABLED","blocking":[],"query":"status:open","created":"2019-07-29 13:28:09.000000000","updated":"2019-07-29 13:28:09.000000000"},{"uuid":"ijkl:mnop","name":"abcd","description":"efgh","repository":"All-Projects","status":"ENABLED","blocking":[],"query":"status:open","created":"2019-07-29 09:33:25.000000000","updated":"2019-07-29 09:33:25.000000000"},{"uuid":"ngfnf:mhghgnhghn","name":"nbvfg","description":"fjhgj","repository":"All-Users","status":"ENABLED","blocking":[],"query":"status:open","created":"2019-08-06 14:21:34.000000000","updated":"2019-08-06 14:21:34.000000000"},{"uuid":"sdfsdf--:sdfsdf333","name":"sdfsdf","description":"sdfsdfsd","repository":"Scripts","status":"ENABLED","blocking":[],"query":"status:open","created":"2019-07-30 13:00:19.000000000","updated":"2019-07-30 13:00:19.000000000"},{"uuid":"test:checker1","name":"Unit Tests","description":"Random description that should be improved at some point","repository":"Backend","status":"ENABLED","blocking":[],"query":"status:open","created":"2019-07-22 13:16:52.000000000","updated":"2019-07-22 14:21:14.000000000"},{"uuid":"test:checker2","name":"Code Style","repository":"Backend","status":"ENABLED","blocking":[],"query":"status:open","created":"2019-07-22 13:26:56.000000000","updated":"2019-07-22 13:26:56.000000000"},{"uuid":"xddf:sdfsdfsdf","name":"sdfsdf","description":"sdfsdf","repository":"Scripts","status":"ENABLED","blocking":[],"query":"status:open","created":"2019-07-29 14:11:59.000000000","updated":"2019-07-29 14:11:59.000000000"},{"uuid":"zxczxc:bnvnbvnbvn","name":"zxc","description":"zxc","repository":"Scripts","status":"ENABLED","blocking":[],"query":"status:open","created":"2019-07-29 14:00:24.000000000","updated":"2019-07-29 14:00:24.000000000"},{"uuid":"zxczxc:sdfsdf","name":"zxc","description":"zxc","repository":"Scripts","status":"ENABLED","blocking":[],"query":"status:open","created":"2019-07-29 13:30:47.000000000","updated":"2019-07-29 13:30:47.000000000"}]'
      // return new Promise(
      //   (resolve) => {
      //     resolve(JSON.parse(hardCodedResponse))
      //   }
      // )
      const url = "/a/plugins/checks/checkers/";
      return this.$.restAPI._fetchJSON({
        method: 'GET',
        url,
      });
    },

    _getChecks() {
      this._checks = [];
      return this.getCheckers()
        .then(checks => {
          if (!checks) { return; }
          this._checks = checks;
          this._visibleChecks = checks.slice(0, this._checksPerPage);
          this._startingIndex = 0;
          this._loading = false;
        });
    },

    _handleEditChecker() {
      this.$.editModal._handleEditChecker();
    },

    _handleEditClicked(e) {
      let checker = e.model.item;
      this.checker = checker;
      this.$.editOverlay.open();
    },

    computeBlocking(check) {
      return (check && check.blocking && check.blocking.length > 0)? "YES": "NO";
    },

    _handleCreateChecker() {
      this.$.createNewModal._handleCreateChecker();
    },

    _handleCreateClicked() {
      this.$.createOverlay.open();
    },

    _handleCloseCreate(e) {
      if (e.detail.reload) {
        this._getChecks();
      }
      this.$.createOverlay.close();
    },

    _handleCloseEdit(e) {
      if (e.detail.reload) {
        this._getChecks();
      }
      this.$.editOverlay.close();
    },

  })
})();