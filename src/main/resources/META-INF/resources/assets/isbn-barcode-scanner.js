(() => {
  const isbnInput = document.getElementById("isbn");
  const trigger = document.getElementById("scan-barcode");
  const dialog = document.getElementById("barcode-scanner");
  const preview = document.getElementById("barcode-scanner-preview");
  const status = document.getElementById("barcode-scanner-status");
  const error = document.getElementById("barcode-scanner-error");
  const closeButton = document.getElementById("barcode-scanner-close");
  const switchCameraButton = document.getElementById("barcode-scanner-switch-camera");
  let returnFocus;
  let scannerOpen = false;
  let scannerRunning = false;
  let detectedHandler;
  let cameras = [];
  let activeDeviceId;
  let scanAttempt = 0;

  if (!isbnInput || !trigger || !dialog || !preview || !status || !error || !closeButton || !switchCameraButton) {
    return;
  }

  const isBooklandIsbn13 = (value) => {
    if (!/^(978|979)\d{10}$/.test(value)) return false;
    return [...value].reduce((sum, digit, index) => sum + Number(digit) * (index % 2 === 0 ? 1 : 3), 0) % 10 === 0;
  };

  const setStatus = (message) => { status.textContent = message; };
  const setError = (message) => {
    error.textContent = message;
    error.hidden = !message;
  };

  const releaseCameraTracks = () => {
    preview.querySelectorAll("video").forEach((video) => {
      const stream = video.srcObject;
      if (stream && typeof stream.getTracks === "function") stream.getTracks().forEach((track) => track.stop());
      video.srcObject = null;
    });
  };

  const stopScanner = () => {
    scanAttempt += 1;
    if (window.Quagga && detectedHandler) window.Quagga.offDetected(detectedHandler);
    detectedHandler = undefined;
    if (window.Quagga && scannerRunning) {
      try { window.Quagga.stop(); } catch (_) { /* Tracks below are the final cleanup path. */ }
    }
    scannerRunning = false;
    releaseCameraTracks();
    preview.replaceChildren();
  };

  const updateCameraSwitch = async () => {
    if (!navigator.mediaDevices || !navigator.mediaDevices.enumerateDevices) return;
    try {
      cameras = (await navigator.mediaDevices.enumerateDevices()).filter((device) => device.kind === "videoinput");
      const stream = preview.querySelector("video")?.srcObject;
      activeDeviceId = stream?.getVideoTracks?.()[0]?.getSettings?.().deviceId || activeDeviceId;
      switchCameraButton.hidden = cameras.length < 2;
      switchCameraButton.disabled = cameras.length < 2;
    } catch (_) {
      cameras = [];
      switchCameraButton.hidden = true;
      switchCameraButton.disabled = true;
    }
  };

  const scannerError = (cause) => {
    stopScanner();
    switchCameraButton.hidden = true;
    switchCameraButton.disabled = true;
    if (cause && cause.name === "NotAllowedError") setError("Camera permission was denied. You can enter an ISBN instead.");
    else if (cause && cause.name === "NotFoundError") setError("No camera is available. You can enter an ISBN instead.");
    else setError("The scanner could not start. You can enter an ISBN instead.");
    setStatus("Scanner unavailable");
  };

  const initialiseQuagga = (config) => new Promise((resolve, reject) => {
    window.Quagga.init(config, (cause) => cause ? reject(cause) : resolve());
  });

  const startScanner = async (deviceId) => {
    if (!window.isSecureContext || !navigator.mediaDevices || !navigator.mediaDevices.getUserMedia || !window.Quagga) {
      setStatus("Scanner unavailable");
      setError("Camera scanning needs HTTPS and a camera. You can enter an ISBN instead.");
      return false;
    }
    const attempt = ++scanAttempt;
    setError("");
    setStatus(deviceId ? "Switching camera…" : "Starting camera…");
    try {
      await initialiseQuagga({
        inputStream: {
          type: "LiveStream",
          target: preview,
          constraints: deviceId
            ? {
                deviceId: { exact: deviceId },
                width: { ideal: 1920 },
                height: { ideal: 1080 }
              }
            : {
                facingMode: { ideal: "environment" },
                width: { ideal: 1920 },
                height: { ideal: 1080 }
              },
          area: { top: "32%", right: "10%", bottom: "32%", left: "10%" }
        },
        locator: { patchSize: "small", halfSample: false },
        locate: false,
        decoder: { readers: ["ean_reader"] }
      });
      if (!scannerOpen || attempt !== scanAttempt) {
        window.Quagga.stop();
        releaseCameraTracks();
        return false;
      }
      detectedHandler = (result) => {
        if (!scannerOpen || !result?.codeResult?.code) return;
        const isbn = result.codeResult.code;
        if (!isBooklandIsbn13(isbn)) {
          setStatus("This is not a book ISBN. Keep scanning.");
          return;
        }
        stopScanner();
        scannerOpen = false;
        dialog.hidden = true;
        document.body.classList.remove("barcode-scanner-open");
        isbnInput.value = isbn;
        isbnInput.focus();
      };
      window.Quagga.onDetected(detectedHandler);
      window.Quagga.start();
      scannerRunning = true;
      await updateCameraSwitch();
      if (scannerOpen && attempt === scanAttempt) {
        setStatus(deviceId ? "Camera switched. Point it at a book barcode." : "Point the rear camera at a book barcode.");
      }
      return true;
    } catch (cause) {
      if (scannerOpen && attempt === scanAttempt) scannerError(cause);
      return false;
    }
  };

  const closeScanner = ({ restoreFocus = true } = {}) => {
    scannerOpen = false;
    stopScanner();
    dialog.hidden = true;
    document.body.classList.remove("barcode-scanner-open");
    if (restoreFocus && returnFocus) returnFocus.focus();
  };

  trigger.addEventListener("click", () => {
    if (scannerOpen) return;
    returnFocus = trigger;
    scannerOpen = true;
    cameras = [];
    activeDeviceId = undefined;
    switchCameraButton.hidden = true;
    switchCameraButton.disabled = true;
    dialog.hidden = false;
    document.body.classList.add("barcode-scanner-open");
    closeButton.focus();
    startScanner();
  });

  switchCameraButton.addEventListener("click", async () => {
    if (!scannerOpen || cameras.length < 2) return;
    const currentIndex = Math.max(0, cameras.findIndex((device) => device.deviceId === activeDeviceId));
    const nextCameraIndex = (currentIndex + 1) % cameras.length;
    switchCameraButton.disabled = true;
    stopScanner();
    const started = await startScanner(cameras[nextCameraIndex].deviceId);
    if (started) switchCameraButton.disabled = false;
  });

  closeButton.addEventListener("click", () => closeScanner());
  document.addEventListener("keydown", (event) => {
    if (event.key === "Escape" && !dialog.hidden) {
      event.preventDefault();
      closeScanner();
    }
  });
  window.addEventListener("pagehide", () => closeScanner({ restoreFocus: false }));
  window.addEventListener("beforeunload", () => closeScanner({ restoreFocus: false }));

  window.RosiesBooksIsbnBarcodeScanner = { isBooklandIsbn13 };
})();
