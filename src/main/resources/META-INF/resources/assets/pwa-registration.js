if ("serviceWorker" in navigator) {
  let reloadingForWorkerUpdate = false;

  navigator.serviceWorker.addEventListener("controllerchange", () => {
    if (reloadingForWorkerUpdate) {
      return;
    }

    reloadingForWorkerUpdate = true;
    window.location.reload();
  });

  window.addEventListener("load", () => {
    navigator.serviceWorker
      .register("/service-worker.js", { scope: "/", updateViaCache: "none" })
      .then((registration) => registration.update())
      .catch(() => {});
  });
}
