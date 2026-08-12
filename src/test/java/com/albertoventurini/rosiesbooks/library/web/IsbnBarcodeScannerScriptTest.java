package com.albertoventurini.rosiesbooks.library.web;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class IsbnBarcodeScannerScriptTest {

  @Test
  void acceptsOnlyChecksumValidBooklandEan13CodesAndPopulatesTheFieldForReview()
      throws IOException {
    String script = scannerScript();

    assertTrue(script.contains("/^(978|979)\\d{10}$/"));
    assertTrue(script.contains("sum + Number(digit) * (index % 2 === 0 ? 1 : 3)"));
    assertTrue(script.contains("window.Quagga"));
    assertTrue(script.contains("readers: [\"ean_reader\"]"));
    assertTrue(script.contains("locate: false"));
    assertTrue(script.contains("facingMode: { ideal: \"environment\" }"));
    assertTrue(script.contains("width: { ideal: 1920 }"));
    assertTrue(script.contains("height: { ideal: 1080 }"));
    assertTrue(
        script.contains("area: { top: \"32%\", right: \"10%\", bottom: \"32%\", left: \"10%\" }"));
    assertTrue(script.contains("patchSize: \"small\", halfSample: false"));
    assertTrue(script.contains("This is not a book ISBN. Keep scanning."));
    assertTrue(script.contains("isbnInput.value = isbn"));
    assertTrue(script.contains("isbnInput.focus()"));
    assertFalse(script.contains("requestSubmit"));
    assertFalse(script.contains("ZX" + "ing"));
  }

  @Test
  void leavesManualEntryAvailableAndStopsTracksForCancellationAndScannerFailures()
      throws IOException {
    String script = scannerScript();

    assertTrue(script.contains("track.stop()"));
    assertTrue(script.contains("Camera permission was denied. You can enter an ISBN instead."));
    assertTrue(script.contains("No camera is available. You can enter an ISBN instead."));
    assertTrue(script.contains("The scanner could not start. You can enter an ISBN instead."));
    assertTrue(
        script.contains(
            "Camera scanning needs HTTPS and a camera. You can enter an ISBN instead."));
    assertTrue(script.contains("pagehide"));
    assertTrue(script.contains("beforeunload"));
    assertTrue(script.contains("enumerateDevices"));
    assertTrue(script.contains("deviceId: { exact: deviceId }"));
    assertTrue(script.contains("switchCameraButton.hidden = cameras.length < 2"));
    assertTrue(script.contains("startScanner(cameras[nextCameraIndex].deviceId)"));
    assertFalse(script.contains("barcode-scanner-cancel"));
  }

  private String scannerScript() throws IOException {
    return Files.readString(
        Path.of("src/main/resources/META-INF/resources/assets/isbn-barcode-scanner.js"));
  }
}
