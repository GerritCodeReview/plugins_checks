(function() {
  'use strict';
  const Statuses = window.Gerrit.Checks.Statuses;

  const StatusPriorityOrder = [
    Statuses.FAILED,
    Statuses.SCHEDULED,
    Statuses.RUNNING,
    Statuses.SUCCESSFUL,
    Statuses.NOT_STARTED,
    Statuses.NOT_RELEVANT,
  ];

  const HumanizedStatuses = {
    // non-terminal statuses
    NOT_STARTED: 'in progress',
    NOT_RELEVANT: 'not relevant',
    SCHEDULED: 'in progress',
    RUNNING: 'in progress',

    // terminal statuses
    SUCCESSFUL: 'successful',
    FAILED: 'failed',
  };

  const CHECKS_POLL_INTERVAL_MS = 60 * 1000;

  const Defs = {};
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

  function computeCheckStatuses(checks) {
    return checks.reduce((accum, check) => {
      accum[check.state] || (accum[check.state] = 0);
      accum[check.state]++;
      return accum;
    }, {total: checks.length});
  }

  function downgradeFailureToWarning(checks) {
    const hasFailedCheck = checks.some(
      (check) => {
        return check.state == Statuses.FAILED;
      }
    )
    if (!hasFailedCheck) return false;
    const hasRequiredFailedCheck = checks.some(
      (check) => {
        return check.state == Statuses.FAILED && check.blocking && check.blocking.length > 0;
      }
    )
    return !hasRequiredFailedCheck;
  }


  Polymer({
    is: 'gr-checks-chip-view',
    _legacyUndefinedCheck: true,

    properties: {
      revision: Object,
      change: Object,
      /** @type {function(number, number): !Promise<!Object>} */
      getChecks: Function,
      _checkStatuses: Object,
      _hasChecks: Boolean,
      _failedRequiredChecks: Number,
      _status: {type: String, computed: '_computeStatus(_checkStatuses)'},
      _statusString: {
        type: String,
        computed: '_computeStatusString(_status, _checkStatuses, _failedRequiredChecks)',
      },
      _chipClasses: {type: String, computed: '_computeChipClass(_status)'},
      _downgradeFailureToWarning: {
        type: Boolean,
        value: false
      },
      pollChecksInterval: Object,
      visibilityChangeListenerAdded: {
        type: Boolean,
        value: false
      }
    },

    detached() {
      clearInterval(this.pollChecksInterval);
      this.unlisten(document, 'visibilitychange', '_onVisibililityChange');
    },

    observers: [
      '_pollChecksRegularly(change, revision, getChecks)',
    ],

    listeners: {
      'tap': 'showChecksTable'
    },

    showChecksTable() {
      this.dispatchEvent(
        new CustomEvent(
          'show-checks-table',
          {
            bubbles: true,
            composed: true,
            detail: {
              tab: 'change-view-tab-content-checks'
            }
          })
        );
    },

    /**
     * @param {!Defs.Change} change
     * @param {!Defs.Revision} revision
     * @param {function(number, number): !Promise<!Object>} getChecks
     */
    _fetchChecks(change, revision, getChecks) {
      getChecks(change._number, revision._number).then(checks => {
        this.set('_hasChecks', checks.length > 0);
        if (checks.length > 0) {
          this._failedRequiredChecks = this.computeFailedRequiredChecks(checks);
          this.set('_checkStatuses', computeCheckStatuses(checks));
          this.set('_downgradeFailureToWarning', downgradeFailureToWarning(checks));
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

    /**
     * @param {!Object} checkStatuses The number of checks in each status.
     * @return {string}
     */
    _computeStatus(checkStatuses) {
      return StatusPriorityOrder.find(
          status => checkStatuses[status] > 0) ||
          Statuses.STATUS_UNKNOWN;
    },

    computeFailedRequiredChecks(checks) {
      let failedRequiredChecks = checks.filter(
        (check) => {return check.state == Statuses.FAILED && check.blocking && check.blocking.length > 0}
      )
      return failedRequiredChecks.length;
    },

    /**
     * @param {string} status The overall status of the checks.
     * @param {!Object} checkStatuses The number of checks in each status.
     * @return {string}
     */
    _computeStatusString(status, checkStatuses, failedRequiredChecks) {
      if (checkStatuses.total === 0) return 'No checks';
      let statusString = `${checkStatuses[status]} of ${
          checkStatuses.total} checks ${HumanizedStatuses[status]}`;
      if (status !== Statuses.FAILED) {
        return statusString;
      }
      statusString += `(${failedRequiredChecks} required)`;
      return statusString;
    },

    /**
     * @param {string} status The overall status of the checks.
     * @return {string}
     */
    _computeChipClass(status) {
      return `chip ${window.Gerrit.Checks.statusClass(status)}`;
    },
  });
})();
