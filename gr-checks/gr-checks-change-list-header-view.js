(function() {
  'use strict';

  class GrChecksChangeListHeaderView extends Polymer.GestureEventListeners(
      Polymer.LegacyElementMixin(
          Polymer.Element)) {
    static get is() { return 'gr-checks-change-list-header-view'; }
  }

  customElements.define(GrChecksChangeListHeaderView.is,
      GrChecksChangeListHeaderView);
})();
