(function() {
  'use strict';

  /**
   * autocomplete chip for getting repository suggestions
   */
  class GrRepoChip extends Polymer.GestureEventListeners(
      Polymer.LegacyElementMixin(
          Polymer.Element)) {
    static get is() { return 'gr-repo-chip'; }

    static get properties() {
      return {
      // repo type is ProjectInfo
        repo: Object,
        removable: {
          type: Boolean,
          value: true,
        },
      };
    }

    _handleRemoveTap(e) {
      e.preventDefault();
      this.fire('remove', {repo: this.repo});
    }
  }

  customElements.define(GrRepoChip.is, GrRepoChip);
})();