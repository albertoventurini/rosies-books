(() => {
  const form = document.getElementById("isbn-lookup-form");
  const isbnInput = document.getElementById("isbn");
  const trigger = document.getElementById("scan-barcode");
  const dialog = document.getElementById("barcode-scanner");
  const video = document.getElementById("barcode-scanner-video");
  const status = document.getElementById("barcode-scanner-status");
  const error = document.getElementById("barcode-scanner-error");
  const closeButton = document.getElementById("barcode-scanner-close");
  const cancelButton = document.getElementById("barcode-scanner-cancel");
  const zxingAsset = "/assets/zxing-library-0.20.0.min.js";
  let reader;
  let returnFocus;
  let scannerOpen = false;

  if (!form || !isbnInput || !trigger || !dialog || !video || !status || !error || !closeButton || !cancelButton) {
    return;
  }

  const isBooklandIsbn13 = (value) => {
    if (!/^(978|979)\d{10}$/.test(value)) {
      return false;
    }
    return [...value].reduce((sum, digit, index) => sum + Number(digit) * (index % 2 === 0 ? 1 : 3), 0) % 10 === 0;
  };

  const setStatus = (message) => {
    status.textContent = message;
  };

  const setError = (message) => {
    error.textContent = message;
    error.hidden = !message;
  };

  const stopCamera = () => {
    if (reader) {
      try {
        reader.reset();
      } catch (_) {
        // The stream tracks below remain the reliable final cleanup path.
      }
      reader = undefined;
    }
    const stream = video.srcObject;
    if (stream && typeof stream.getTracks === "function") {
      stream.getTracks().forEach((track) => track.stop());
    }
    video.srcObject = null;
  };

  const closeScanner = ({ restoreFocus = true } = {}) => {
    scannerOpen = false;
    stopCamera();
    dialog.hidden = true;
    document.body.classList.remove("barcode-scanner-open");
    if (restoreFocus && returnFocus) {
      returnFocus.focus();
    }
  };

  const scannerError = (cause) => {
    stopCamera();
    if (cause && cause.name === "NotAllowedError") {
      setError("Camera permission was denied. You can enter an ISBN instead.");
    } else if (cause && cause.name === "NotFoundError") {
      setError("No camera is available. You can enter an ISBN instead.");
    } else {
      setError("The scanner could not start. You can enter an ISBN instead.");
    }
    setStatus("Scanner unavailable");
  };

  const loadZxing = () => new Promise((resolve, reject) => {
    if (window.ZXing) {
      resolve(window.ZXing);
      return;
    }
    const script = document.createElement("script");
    script.src = zxingAsset;
    script.async = true;
    script.onload = () => window.ZXing ? resolve(window.ZXing) : reject(new Error("ZXing unavailable"));
    script.onerror = () => reject(new Error("ZXing unavailable"));
    document.head.append(script);
  });

  const startScanner = async () => {
    if (!window.isSecureContext || !navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
      setStatus("Scanner unavailable");
      setError("Camera scanning needs HTTPS and a camera. You can enter an ISBN instead.");
      return;
    }
    setError("");
    setStatus("Starting camera…");
    try {
      const ZXing = await loadZxing();
      if (!scannerOpen) {
        return;
      }
      const hints = new Map([[ZXing.DecodeHintType.POSSIBLE_FORMATS, [ZXing.BarcodeFormat.EAN_13]]]);
      reader = new ZXing.BrowserMultiFormatReader(hints, 250);
      await reader.decodeFromConstraints(
          { audio: false, video: { facingMode: { ideal: "environment" } } },
          video,
          (result) => {
            if (!scannerOpen) {
              return;
            }
            if (!result || result.getBarcodeFormat() !== ZXing.BarcodeFormat.EAN_13) {
              return;
            }
            const isbn = result.getText();
            if (!isBooklandIsbn13(isbn)) {
              setStatus("This is not a book ISBN. Keep scanning.");
              return;
            }
            isbnInput.value = isbn;
            closeScanner({ restoreFocus: false });
            if (typeof form.requestSubmit === "function") {
              form.requestSubmit();
            } else {
              form.submit();
            }
          });
      setStatus("Point the rear camera at a book barcode.");
    } catch (cause) {
      scannerError(cause);
    }
  };

  trigger.addEventListener("click", () => {
    if (scannerOpen) {
      return;
    }
    returnFocus = trigger;
    scannerOpen = true;
    dialog.hidden = false;
    document.body.classList.add("barcode-scanner-open");
    closeButton.focus();
    startScanner();
  });

  [closeButton, cancelButton].forEach((button) => button.addEventListener("click", () => closeScanner()));
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
