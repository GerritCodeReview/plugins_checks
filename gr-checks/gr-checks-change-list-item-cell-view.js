(function() {
  'use strict';

  class GrChecksChangeListItemCellView extends Polymer.GestureEventListeners(
      Polymer.LegacyElementMixin(
          Polymer.Element)) {
    static get is() { return 'gr-checks-change-list-item-cell-view'; }

    static get properties() {
      return {
        change: Object,
      };
    }
  }

  customElements.define(GrChecksChangeListItemCellView.is,
      GrChecksChangeListItemCellView);
})();
