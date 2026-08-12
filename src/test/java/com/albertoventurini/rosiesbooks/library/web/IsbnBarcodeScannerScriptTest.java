package com.albertoventurini.rosiesbooks.library.web;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class IsbnBarcodeScannerScriptTest {

  @Test
  void acceptsOnlyChecksumValidBooklandEan13CodesAndSubmitsTheExistingLookupForm()
      throws IOException {
    String script = scannerScript();

    assertTrue(script.contains("/^(978|979)\\d{10}$/"));
    assertTrue(script.contains("sum + Number(digit) * (index % 2 === 0 ? 1 : 3)"));
    assertTrue(script.contains("ZXing.BarcodeFormat.EAN_13"));
    assertTrue(script.contains("new ZXing.BrowserBarcodeReader(100, hints)"));
    assertTrue(script.contains("ZXing.DecodeHintType.TRY_HARDER, true"));
    assertTrue(script.contains("width: { ideal: 1920 }"));
    assertTrue(script.contains("height: { ideal: 1080 }"));
    assertTrue(script.contains("This is not a book ISBN. Keep scanning."));
    assertTrue(script.contains("isbnInput.value = isbn"));
    assertTrue(script.contains("form.requestSubmit()"));
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
    assertFalse(script.contains("barcode-scanner-cancel"));
  }

  private String scannerScript() throws IOException {
    return Files.readString(
        Path.of("src/main/resources/META-INF/resources/assets/isbn-barcode-scanner.js"));
  }
}
