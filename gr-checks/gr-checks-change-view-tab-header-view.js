(function() {
  'use strict';

  class GrChecksChangeViewTabHeaderView extends Polymer.GestureEventListeners(
      Polymer.LegacyElementMixin(
          Polymer.Element)) {
    static get is() { return 'gr-checks-change-view-tab-header-view'; }
  }

  customElements.define(GrChecksChangeViewTabHeaderView.is,
      GrChecksChangeViewTabHeaderView);
})();
