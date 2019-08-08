(function() {
  'use strict';
  Polymer({
    is: 'gr-checks-list',
    properties: {
      /**
       * URL params passed from the router.
       */
      params: {
          type: Object,
          observer: '_paramsChanged',
      },

      /**
       * Offset of currently visible query results.
       */
      _offset: Number,
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
      //make sure this reverts to 25
      _checksPerPage: {
        type: Number,
        value: 3,
      },
      _path: {
        type: String,
        readOnly: true,
        value: '/admin/checks',
      },
      _shownChecks: {
        type: Array,
      },
      _createNewCapability: {
        type: Boolean,
        value: true,
      },
      _handleNextClicked: Function,
      _handlePrevClicked: Function
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

    _paramsChanged() {
      this._filter = this.params.filter || "";
    },

    _showChecks(_checks, _filter) {
      this._filteredChecks = this._checks.filter(
        check => { return check.name.toLowerCase().indexOf(this._filter) !== -1 } 
      )
      this._startingIndex = 0; 
      this._shownChecks = this._filteredChecks.slice(this._startingIndex, this._startingIndex + this._checksPerPage);
    },

    _handleNextClicked() {
      if (this._startingIndex + this._checksPerPage < this._filteredChecks.length) {
        this._startingIndex += this._checksPerPage;
      }
      this._shownChecks = this._filteredChecks.slice(this._startingIndex, this._startingIndex + this._checksPerPage);
    },

    _handlePrevClicked() {
      if (this._startingIndex >= this._checksPerPage) {
        this._startingIndex -= this._checksPerPage;
      }
      this._shownChecks = this._filteredChecks.slice(this._startingIndex, this._startingIndex + this._checksPerPage);
    },

    _handleFilterChanged(e) {
      this._filter = e.detail.filter;
    },

    //todo(dhruvsri): add filter/offset args
    getCheckers() {
      let hardCodedResponse = '[{"uuid":"C:D","name":"A","description":"B","repository":"Backend","status":"ENABLED","blocking":[],"query":"status:open","created":"2019-07-25 13:08:43.000000000","updated":"2019-07-25 13:08:43.000000000"},{"uuid":"aa:bb","name":"n1","description":"d1","repository":"All-Users","status":"ENABLED","blocking":[],"query":"status:open","created":"2019-07-29 13:07:17.000000000","updated":"2019-07-29 13:07:17.000000000"},{"uuid":"adsf:asdasdas","name":"ds","description":"s","repository":"Scripts","status":"ENABLED","blocking":[],"query":"status:open","created":"2019-07-29 13:28:09.000000000","updated":"2019-07-29 13:28:09.000000000"},{"uuid":"ijkl:mnop","name":"abcd","description":"efgh","repository":"All-Projects","status":"ENABLED","blocking":[],"query":"status:open","created":"2019-07-29 09:33:25.000000000","updated":"2019-07-29 09:33:25.000000000"},{"uuid":"ngfnf:mhghgnhghn","name":"nbvfg","description":"fjhgj","repository":"All-Users","status":"ENABLED","blocking":[],"query":"status:open","created":"2019-08-06 14:21:34.000000000","updated":"2019-08-06 14:21:34.000000000"},{"uuid":"sdfsdf--:sdfsdf333","name":"sdfsdf","description":"sdfsdfsd","repository":"Scripts","status":"ENABLED","blocking":[],"query":"status:open","created":"2019-07-30 13:00:19.000000000","updated":"2019-07-30 13:00:19.000000000"},{"uuid":"test:checker1","name":"Unit Tests","description":"Random description that should be improved at some point","repository":"Backend","status":"ENABLED","blocking":[],"query":"status:open","created":"2019-07-22 13:16:52.000000000","updated":"2019-07-22 14:21:14.000000000"},{"uuid":"test:checker2","name":"Code Style","repository":"Backend","status":"ENABLED","blocking":[],"query":"status:open","created":"2019-07-22 13:26:56.000000000","updated":"2019-07-22 13:26:56.000000000"},{"uuid":"xddf:sdfsdfsdf","name":"sdfsdf","description":"sdfsdf","repository":"Scripts","status":"ENABLED","blocking":[],"query":"status:open","created":"2019-07-29 14:11:59.000000000","updated":"2019-07-29 14:11:59.000000000"},{"uuid":"zxczxc:bnvnbvnbvn","name":"zxc","description":"zxc","repository":"Scripts","status":"ENABLED","blocking":[],"query":"status:open","created":"2019-07-29 14:00:24.000000000","updated":"2019-07-29 14:00:24.000000000"},{"uuid":"zxczxc:sdfsdf","name":"zxc","description":"zxc","repository":"Scripts","status":"ENABLED","blocking":[],"query":"status:open","created":"2019-07-29 13:30:47.000000000","updated":"2019-07-29 13:30:47.000000000"}]'
      return new Promise(
        (resolve) => {
          resolve(JSON.parse(hardCodedResponse))
        }
      )
      const url = "/a/plugins/checks/checkers/";
      return this._fetchSharedCacheURL({
        url,
        anonymizedUrl: '/plugins/*/checks?*',
        reportUrlAsIs: true,
      });
    },

    _getChecks() {
      this._checks = [];
      return this.getCheckers(this._filter, this._checksPerPage, this._offset)
        .then(checks => {
          if (!checks) { return; }
          this._checks = checks;
          this._shownChecks = checks.slice(0, this._checksPerPage);
          this._startingIndex = 0; 
          this._loading = false;
        });
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

    _handleCloseCreate() {
      this.$.createOverlay.close();
    },

    _handleOnCancel() {
      this.$.createOverlay.close();
    },

  })
})();