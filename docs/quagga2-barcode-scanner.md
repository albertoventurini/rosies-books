# Vendored Quagga2 barcode scanner

The Add a book camera scanner uses the locally served
`src/main/resources/META-INF/resources/assets/quagga-1.12.1.min.js` distribution from
`@ericblade/quagga2` version `1.12.1`. It was obtained from
`https://cdn.jsdelivr.net/npm/@ericblade/quagga2@1.12.1/dist/quagga.min.js` on 2026-08-12.
Its SHA-256 is `ae6c469103c5d427625a9a4c41175bd15420a14aa5579ea57dc1571d42346f4d`.

Its MIT licence is committed at
`src/main/resources/META-INF/licenses/Quagga2-1.12.1-MIT.txt`. The page loads this local asset
with the scanner controller and makes no scanner CDN request at runtime.
