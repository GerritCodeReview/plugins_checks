(function() {
  'use strict';

  const Defs = {};

  const Statuses = window.Gerrit.Checks.Statuses;
  const StatusPriorityOrder = [
    Statuses.FAILED,
    Statuses.SCHEDULED,
    Statuses.RUNNING,
    Statuses.SUCCESSFUL,
    Statuses.NOT_STARTED,
    Statuses.NOT_RELEVANT,
  ];

  const CHECKS_POLL_INTERVAL_MS = 60 * 1000;

  /**
   * @typedef {{
   *   _number: number,
   * }}
   */
  Defs.Change;
  /**
   * @typedef {{
   *   _number: number,
   * }}
   */
  Defs.Revision;

  const LoadingStatus = {
    LOADING: 0,
    EMPTY: 1,
    RESULTS: 2,
    NOT_CONFIGURED: 3,
  };

  Polymer({
    is: 'gr-checks-view',
    _legacyUndefinedCheck: true,

    properties: {
      revision: Object,
      change: Object,
      /** @type {function(number, number): !Promise<!Object>} */
      getChecks: Function,
      /** @type {function(string): !Promise<Boolean>} */
      isConfigured: Function,
      /** @type {function(string, string): !Promise<!Object>} */
      retryCheck: Function,
      _checks: Object,
      _status: {
        type: Object,
        value: LoadingStatus.LOADING,
      },
      pollChecksInterval: Object,
      visibilityChangeListenerAdded: {
        type: Boolean,
        value: false
      }
    },

    observers: [
      '_pollChecksRegularly(change, revision, getChecks)',
    ],

    detached() {
      clearInterval(this.pollChecksInterval);
      this.unlisten(document, 'visibilitychange', '_onVisibililityChange');
    },

    _orderChecks(a, b) {
      if (a.state != b.state) {
        let indexA = StatusPriorityOrder.indexOf(a.state);
        let indexB = StatusPriorityOrder.indexOf(b.state);
        if (indexA != -1 && indexB != -1) {
          return indexA - indexB;
        }
        return indexA == -1 ? 1 : -1;
      }
      if (a.state === Statuses.FAILED) {
        if (a.blocking && b.blocking && a.blocking.length !== b.blocking.length) {
          return a.blocking.length == 0 ? 1 : -1;
        }
      }
      return a.checker_name.localeCompare(b.checker_name);
    },

    /**
     * @param {!Defs.Change} change
     * @param {!Defs.Revision} revision
     * @param {function(number, number): !Promise<!Object>} getChecks
     */
    _fetchChecks(change, revision, getChecks) {
      getChecks(change._number, revision._number).then(checks => {
        if (checks && checks.length) {
          checks.sort(this._orderChecks);
          if (!this._checks) {
            this._checks = checks;
          } else {
            // Merge checks & this_checks to keep showCheckMessage property
            this._checks = this._checks.map(
              (check) => {
                const prevChecks = checks.filter(
                  (c) => { return c.checker_uuid === check.checker_uuid }
                )
                if (prevChecks.length === 0) return check;
                return Object.assign({}, check, prevChecks[0])
              }
            )
          }
          this.set('_status', LoadingStatus.RESULTS);
        } else {
          this._checkConfigured();
        }
      });
    },

    _onVisibililityChange() {
      if (document.hidden) {
        clearInterval(this.pollChecksInterval);
        return;
      }
      this._pollChecksRegularly(this.change, this.revision, this.getChecks);
    },

    _toggleCheckMessage(e) {
      const uuid = e.detail.uuid;
      if (!uuid) {
        console.warn("uuid not found");
        return;
      }
      const idx = this._checks.findIndex(check => check.checker_uuid === uuid);
      if (idx == -1) {
        console.warn("check not found");
        return;
      }
      // Update subproperty of _checks[idx] so that it reflects to polymer
      this.set(`_checks.${idx}.showCheckMessage`,
        !this._checks[idx].showCheckMessage)
    },

    _pollChecksRegularly(change, revision, getChecks) {
      if (this.pollChecksInterval) {
        clearInterval(this.pollChecksInterval);
      }
      const poll = () => this._fetchChecks(change, revision, getChecks);
      poll();
      this.pollChecksInterval = setInterval(poll, CHECKS_POLL_INTERVAL_MS)
      if (!this.visibilityChangeListenerAdded) {
        this.visibilityChangeListenerAdded = true;
        this.listen(document, 'visibilitychange', '_onVisibililityChange');
      }
    },

    _checkConfigured() {
      const repository = this.change['project'];
      this.isConfigured(repository).then(configured => {
        const status =
            configured ? LoadingStatus.EMPTY : LoadingStatus.NOT_CONFIGURED;
        this.set('_status', status);
      });
    },

    _isLoading(status) {
      return status === LoadingStatus.LOADING;
    },
    _isEmpty(status) {
      return status === LoadingStatus.EMPTY;
    },
    _hasResults(status) {
      return status === LoadingStatus.RESULTS;
    },
    _isNotConfigured(status) {
      return status === LoadingStatus.NOT_CONFIGURED;
    },
  });
})();
