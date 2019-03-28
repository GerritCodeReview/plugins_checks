(function() {
'use strict';
const Statuses = window.Gerrit.Checks.Statuses;

const StatusPriorityOrder = [
  Statuses.INTERNAL_ERROR, Statuses.TIMEOUT, Statuses.FAILURE,
  Statuses.STATUS_UNKNOWN, Statuses.CANCELLED, Statuses.QUEUED,
  Statuses.QUEUING, Statuses.WORKING, Statuses.SUCCESS
];

const HumanizedStatuses = {
  // non-terminal statuses
  STATUS_UNKNOWN: 'unevaluated',
  QUEUING: 'in progress',
  QUEUED: 'in progress',
  WORKING: 'in progress',

  // terminal statuses
  SUCCESS: 'successful',
  FAILURE: 'failed',
  INTERNAL_ERROR: 'failed',
  TIMEOUT: 'failed',
  CANCELLED: 'unevaluated',
};


const Defs = {};
/**
 * @typedef {{
 *   revisions: !Object<string, !Object>,
 * }}
 */
Defs.Change;

/**
 * @param {!Defs.Change} change The current CL.
 * @param {!Object} revision The current patchset.
 * @return {string|undefined}
 */
function currentRevisionSha(change, revision) {
  return Object.keys(change.revisions)
      .find(sha => change.revisions[sha] === revision);
}

function computeCheckStatuses(checkResults) {
  return checkResults.reduce((accum, checkResult) => {
    accum[checkResult.status] || (accum[checkResult.status] = 0);
    accum[checkResult.status]++;
    return accum;
  }, {total: checkResults.length});
}

Polymer({
  is: 'gr-checks-chip-view',

  properties: {
    revision: Object,
    change: Object,
    // TODO(brohlfs): Implement getCheckResults based on new Rest APIs.
    /** @type {function(string, (string|undefined)): !Promise<!Object>} */
    getChecks: Function,
    _checkResultStatuses: Object,
    _hasChecks: Boolean,
    _status: {type: String, computed: '_computeStatus(_checkResultStatuses)'},
    _statusString: {
      type: String,
      computed: '_computeStatusString(_status, _checkResultStatuses)'
    },
    _chipClasses: {type: String, computed: '_computeChipClass(_status)'},
  },

  observers: [
    '_fetchChecks(change, revision, getChecks)',
  ],

  /**
   * @param {!Defs.Change} change The current CL.
   * @param {!Object} revision The current patchset.
   * @param {function(string, (string|undefined)): !Promise<!Object>}
   *     getCheckResults function to get check results.
   */
  _fetchChecks(change, revision, getChecks) {
    const repository = change['project'];
    const gitSha = currentRevisionSha(change, revision);

    getChecks(repository, gitSha).then(checkResults => {
      this.set('_hasChecks', checkResults.length > 0);
      if (checkResults.length > 0) {
        this.set(
            '_checkResultStatuses', computeCheckStatuses(checkResults));
      }
    });
  },

  /**
   * @param {!Object} checkResultStatuses The number of checks in each status.
   * @return {string}
   */
  _computeStatus(checkResultStatuses) {
    return StatusPriorityOrder.find(
               status => checkResultStatuses[status] > 0) ||
        Statuses.STATUS_UNKNOWN;
  },

  /**
   * @param {string} status The overall status of the check results.
   * @param {!Object} checkResultStatuses The number of checks in each status.
   * @return {string}
   */
  _computeStatusString(status, checkResultStatuses) {
    if (checkResultStatuses.total === 0) return 'No checks';
    return `${checkResultStatuses[status]} of ${
        checkResultStatuses.total} checks ${HumanizedStatuses[status]}`;
  },

  /**
   * @param {string} status The overall status of the check results.
   * @return {string}
   */
  _computeChipClass(status) {
    return `chip ${window.Gerrit.Checks.statusClass(status)}`;
  },
});
})();
