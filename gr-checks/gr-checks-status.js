(function() {
'use strict';

  Polymer({
    is: 'gr-checks-status',
    _legacyUndefinedCheck: true,

    properties: {
      showText: {
        type: Boolean,
        value: false,
        reflectToAttribute: true,
      },
      status: String,
      downgradeFailureToWarning: Boolean
    },

    _isUnevaluated(status) {
      return window.Gerrit.Checks.isUnevaluated(status);
    },

    _isInProgress(status) {
      return window.Gerrit.Checks.isInProgress(status);
    },

    _isSuccessful(status) {
      return window.Gerrit.Checks.isSuccessful(status);
    },

    _isFailed(status) {
      return !this.downgradeFailureToWarning && window.Gerrit.Checks.isFailed(status);
    },

    _isWarning(status) {
      return this.downgradeFailureToWarning && window.Gerrit.Checks.isFailed(status);
    },

  });
})();
