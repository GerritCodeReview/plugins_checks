(function(window) {
  if (window.FetchChecks) return window.FetchChecks;
  const CHECKS_POLL_INTERVAL_MS = 60 * 1000;
  window.FetchChecks = {
    getChecks(change, revision) {
      this.plugin.restApi().get(
          '/changes/' + change + '/revisions/' + revision +
              '/checks?o=CHECKER').then(checks => {
        if (!checks) return;
        Gerrit.emit('checks-updated', {checks});
      });
    },
    init(plugin) {
      this.plugin = plugin;
    },
    beginPolling(change, revision) {
      if (this.pollChecksInterval) clearInterval(this.pollChecksInterval);
      this.getChecks(change, revision);
      this.pollChecksInterval = setInterval(() => {
        this.getChecks(change, revision);
      }, CHECKS_POLL_INTERVAL_MS);
    },
    stopPolling() {
      clearInterval(this.pollChecksInterval);
    },
  };
})(window);
