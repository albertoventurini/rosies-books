(() => {
  const url = new URL(window.location.href);
  const recognized = new Set(["state-changed", "state-change-cancelled"]);
  if (!recognized.has(url.searchParams.get("notice"))) return;
  url.searchParams.delete("notice");
  history.replaceState(null, "", url.pathname + url.search + url.hash);
  const banner = document.querySelector("[data-transient-notice]");
  if (banner) window.setTimeout(() => banner.remove(), 5000);
})();
