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
      _className: {type: String, computed: '_computeClassName(status)'},
    },

    _isUnevaluated(status) {
      return window.Gerrit.Checks.isUnevaluated(status);
    },

    _isInProgress(status) {
      return window.Gerrit.Checks.isInProgress(status);
    },

    _isRunning(status) {
      return window.Gerrit.Checks.isRunning(status);
    },

    _isScheduled(status) {
      return window.Gerrit.Checks.isScheduled(status);
    },

    _isSuccessful(status) {
      return window.Gerrit.Checks.isSuccessful(status);
    },

    _isFailed(status) {
      return window.Gerrit.Checks.isFailed(status);
    },

    _computeClassName(status) {
      return window.Gerrit.Checks.statusClass(status);
    },
  });
})();
