# Vendored Quagga2 barcode scanner

The Add a book camera scanner uses the locally served
`src/main/resources/META-INF/resources/assets/quagga-1.12.1.min.js` distribution from
`@ericblade/quagga2` version `1.12.1`. It was obtained from
`https://cdn.jsdelivr.net/npm/@ericblade/quagga2@1.12.1/dist/quagga.min.js` on 2026-08-12.
Its SHA-256 is `ae6c469103c5d427625a9a4c41175bd15420a14aa5579ea57dc1571d42346f4d`.

Its MIT licence is committed at
`src/main/resources/META-INF/licenses/Quagga2-1.12.1-MIT.txt`. The page loads this local asset
with the scanner controller and makes no scanner CDN request at runtime.

After a valid ISBN scan, the controller stores the active camera's browser-provided device ID in
browser-local storage. Future scans on that browser try that camera first, avoiding any temporary
camera probing. If the ID has become unavailable, the controller removes it and falls back to the
browser's normal environment-camera selection. The preference is never sent to the server or
logged.
