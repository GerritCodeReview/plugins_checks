/**
 * @license
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
(function() {
  'use strict';

  Polymer({
    is: 'gr-checks-list-view',
    _legacyUndefinedCheck: true,

    properties: {
      createNew: Boolean,
      items: Array,
      itemsPerPage: Number,
      nextClicked: Function,
      prevClicked: Function,
      filter: {
        type: String,
        observer: '_filterChanged',
      },
      offset: Number,
      loading: Boolean,
      path: String,
      showNextButton: Boolean,
      showPrevButton: Boolean
    },

    behaviors: [
      Gerrit.BaseUrlBehavior,
      Gerrit.FireBehavior,
      Gerrit.URLEncodingBehavior,
    ],

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
