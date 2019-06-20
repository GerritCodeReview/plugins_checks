(function() {
  'use strict';

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
    },

    observers: [
      '_fetchChecks(change, revision, getChecks)',
    ],

    /**
     * @param {!Defs.Change} change
     * @param {!Defs.Revision} revision
     * @param {function(number, number): !Promise<!Object>} getChecks
     */
    _fetchChecks(change, revision, getChecks) {
      getChecks(change._number, revision._number).then(checks => {
        if (checks && checks.length) {

          let Statuses = window.Gerrit.Checks.Statuses;
          let StatusPriorityOrder = [
            Statuses.FAILED,
            Statuses.SCHEDULED,
            Statuses.RUNNING,
            Statuses.SUCCESSFUL,
            Statuses.NOT_STARTED,
            Statuses.NOT_RELEVANT,
          ];
          //add special ordering to checks here
          //first sort them by state and then by their name
          checks.sort(
            (a, b) => {
              if (a.state != b.state) {
                //different states so give priority based on state
                let indexA = StatusPriorityOrder.indexOf(a.state);
                let indexB = StatusPriorityOrder.indexOf(b.state);
                if (indexA != -1 && indexB != -1) {
                  //if both have a valid state then the one with higher priority goes first
                  return indexA - indexB;
                }
                if (indexA == -1) {
                  //B should go first
                  return 1;
                }
                if (indexB == -1) {
                  //A should go first
                  return -1;
                }
                //both are invalid so return anything
                return -1;
              }
              //their states are the same so now we sort them based on their name
              return a.checker_name.localeCompare(b.checker_name);
            }
          )
          this.set('_checks', checks);
          this.set('_status', LoadingStatus.RESULTS);
        } else {
          this._checkConfigured();
        }
      });
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
