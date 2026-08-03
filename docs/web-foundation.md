# Web foundation

Task 0-2 establishes the shared server-rendered web boundary. The root page is intentionally a
temporary foundation endpoint; the user-scoped library shell replaces it in task 2-1. Database
migrations, identity, library screens, and product workflows are not part of this foundation.

## Checked templates and shell

Templates owned by the platform web bootstrap live under
`src/main/resources/templates/platform/web`. `shell.html` is the stable shared include. A page
supplies its document title and body through the named `title` and `content` blocks:

```html
{#include platform/web/shell}
  {#title}{page.title}{/title}
  {#content}
    <!-- feature-owned semantic page content -->
  {/content}
{/include}
```

Both the foundation page and unexpected-error page use this shell. Page data is passed through
immutable, package-private view models and checked template methods. Qute escapes dynamic text;
templates must not mark user or provider content as raw HTML.

## Static assets and fonts

Browser assets live below `src/main/resources/META-INF/resources/assets` and are served from
`/assets`. `app.css` contains the reset, mobile-first shell, color tokens, visible focus treatment,
and a 4 px spacing scale. Assets are checked into the application: there is no Node toolchain,
runtime compilation, or runtime Google Fonts request. Change a font filename when its bytes
change so deployed caches cannot confuse versions.

The vendored fonts contain the Google Fonts Latin subsets used by the shell. Their SIL Open Font
License 1.1 texts are packaged under `src/main/resources/META-INF/licenses`.

| Local asset | Upstream source | License source | SHA-256 |
| --- | --- | --- | --- |
| `newsreader-latin-v1.woff2` | [Google Fonts Newsreader v26 Latin WOFF2](https://fonts.gstatic.com/s/newsreader/v26/cY9AfjOCX1hbuyalUrK4397yjA.woff2) | [Newsreader OFL 1.1](https://github.com/google/fonts/blob/main/ofl/newsreader/OFL.txt) | `6e4f2958c3a7c4a80acde4e5a679abe7e01bc1e30b92be3c7a8b696ef401d101` |
| `ibm-plex-sans-latin-v1.woff2` | [Google Fonts IBM Plex Sans v23 Latin WOFF2](https://fonts.gstatic.com/s/ibmplexsans/v23/zYXzKVElMYYaJe8bpLHnCwDKr932-G7dytD-Dmu1syxeKYY.woff2) | [IBM Plex Sans OFL 1.1](https://github.com/google/fonts/blob/main/ofl/ibmplexsans/OFL.txt) | `e2291e842cf5af167122a22881a740c7f2dda7716f1e8cd76680264f4a859470` |

Verify vendored bytes after any deliberate font update:

```shell
sha256sum src/main/resources/META-INF/resources/assets/fonts/*.woff2
```

## Health probes

SmallRye Health exposes three independently addressable process probes:

| Route | Meaning |
| --- | --- |
| `/q/health/started` | Startup completed and the application can be considered started. |
| `/q/health/live` | The running process is operational and should not be restarted. |
| `/q/health/ready` | The process can accept work and all configured readiness dependencies are available. |

The application contributes explicit side-effect-free UP checks to all three routes. With no
datasource configured, readiness has no database dependency. Once a datasource is configured,
Agroal automatically adds its datasource check to readiness; an unavailable database then makes
only readiness return 503/DOWN. Health payloads must not contain credentials or stack traces.

## Unexpected errors

Unexpected exceptions produce status 500 with checked HTML, `Cache-Control: no-store`, and a new
UUID in `X-Correlation-ID`. The same UUID is shown as the page reference. Users see only a generic
title, message, and reference.

The mapper writes exactly one application log payload with these fields:

```json
{"event":"unexpected_server_error","correlation_id":"<uuid>","exception_class":"<class>"}
```

Do not add exception messages, throwable arguments, stack traces, request bodies, query strings,
cookies, tokens, credentials, or private application values to this event. Ordinary framework
responses such as an unknown-route 404 retain their framework handling.

## Local and packaged verification

Start development mode, then smoke-test the same server-rendered route and probes:

```shell
./mvnw quarkus:dev
curl -i http://localhost:8080/
curl -I http://localhost:8080/
curl -i http://localhost:8080/assets/app.css
curl -i http://localhost:8080/q/health/started
curl -i http://localhost:8080/q/health/live
curl -i http://localhost:8080/q/health/ready
```

The normal verification command runs request tests and architecture checks, builds the production
JVM fast-jar, and launches that artifact for `@QuarkusIntegrationTest`:

```shell
./mvnw verify
```
