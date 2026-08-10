# Vendored ZXing barcode scanner

The Add a book camera scanner uses the locally served
`src/main/resources/META-INF/resources/assets/zxing-library-0.20.0.min.js` distribution from
`@zxing/library` version `0.20.0`. It was obtained from
`https://unpkg.com/@zxing/library@0.20.0/umd/index.min.js` on 2026-08-10.
Its SHA-256 is `a560a87011ff742441d5770cc5ab0f64cfbfb7b228966c5433783e3ba96dd410`.

Its bundled licence and notices are committed at
`src/main/resources/META-INF/licenses/ZXing-Library-0.20.0.txt`. ZXing is Apache License 2.0;
the bundled notice also records the separately licensed jai-imageio material. The page loads this
asset only after the user chooses Scan barcode and makes no scanner CDN request at runtime.
