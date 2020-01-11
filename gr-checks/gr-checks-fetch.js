(function(window) {
  if (window.FetchChecks) return window.FetchChecks;
  const CHECKS_POLL_INTERVAL_MS = 60 * 1000;
  window.FetchChecks = {
    getChecks(change, revision) {
      this.plugin.restApi().get(
          '/changes/' + change + '/revisions/' + revision +
              '/checks?o=CHECKER').then(checks => {
        if (!checks) return;
        Gerrit.emit('fetch-checks', {checks});
      });
    },
    init(plugin) {
      this.plugin = plugin;
    },
    beginPolling() {
      if (this.pollChecksInterval) clearInterval(this.pollChecksInterval);
      this.getChecks();
      this.pollChecksInterval = setInterval(getChecks, CHECKS_POLL_INTERVAL_MS);
    },
    stopPolling() {
      clearInterval(this.pollChecksInterval);
    },
  };
})();
