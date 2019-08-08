(function() {
  'use strict';

  Polymer({
    is: 'gr-checkers-list-view',
    _legacyUndefinedCheck: true,

    properties: {
      createNew: Boolean,
      items: Array,
      filter: {
        type: String,
        observer: '_filterChanged',
      },
      showNextButton: Boolean,
      showPrevButton: Boolean
    },

    behaviors: [
      Gerrit.BaseUrlBehavior,
      Gerrit.FireBehavior,
      Gerrit.URLEncodingBehavior,
    ],

    /**
     * Fired when the filter is changed.
     *
     * @event filter-changed
     */

    /**
     * Fired when the previous navigation button is clicked.
     *
     * @event prev-clicked
     */

    /**
     * Fired when the next navigation button is clicked.
     *
     * @event next-clicked
     */

    /**
     * Fired when the create checker button is clicked.
     *
     * @event create-clicked
     */



    _filterChanged(filter) {
      this.fire('filter-changed', {filter});
    },

    _handlePrevArrowClicked() {
      this.fire('prev-clicked');
    },

    _handleNextArrowClicked() {
      this.fire('next-clicked');
    },

    _createNewItem() {
      this.fire('create-clicked');
    },

    _computeCreateClass(createNew) {
      return createNew ? 'show' : '';
    },

  });
})();
