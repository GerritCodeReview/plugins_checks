(function() {
  'use strict';

  Polymer({
    is: 'gr-checks-change-list-item-cell-view',

    properties: {
      change: Object,
    },

    _computeState(change) {
      if (change && change.plugins && change.plugins.length &&
          change.plugins[0].combined_state) {
        return change.plugins[0].combined_state;
      }
      return '';
    },
  });
})();
