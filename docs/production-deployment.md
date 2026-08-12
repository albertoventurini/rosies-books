# Production image and Compose deployment

Task 6-1 provides a production-oriented JVM fast-jar image and a separate Compose topology. It
does not provision a host, DNS, TLS, backups, or a reverse proxy. Put an HTTPS reverse proxy in
front of the published application port before exposing the service to the internet.

HTTPS is also required for the optional ISBN camera scanner on the Add a book page. Browsers do
not make camera access available to normal HTTP production origins; the scanner leaves the regular
ISBN and manual-entry workflows available whenever HTTPS or camera access is unavailable.

## Configure and start

1. Create a clean deployment directory containing `docker-compose.yml` (a copy of
   `compose.production.yaml`), `.env` (a copy of `.env.production.example`), and
   `01-create-application-role.sh` (a copy of the same-named repository file). Replace every
   angle-bracket value in `.env` with a distinct real value and restrict its permissions. The
   application rejects missing, blank,
placeholder, and known local-development values at production startup. The Google allowlist is
still checked for valid email syntax by the identity boundary.

The deployment also requires `ROSIES_BOOKS_GOOGLE_BOOKS_API_KEY`. Create it in the Google Cloud
project that has Books API enabled, restrict it to that API, and store it only in the deployment
secret file or manager; it is a server-side lookup credential, not the Google OIDC client ID.
2. The Compose file pins the application image to a published digest. Update its `app.image`
   value to the digest recorded in the matching GitHub Release when deploying a new release.
3. Pull and start the production topology:

   ```shell
   chmod 600 .env
   docker compose pull
   docker compose up -d
   ```

4. Check startup and readiness without printing configuration values:

   ```shell
   docker compose ps
   docker compose exec app curl --fail --silent --show-error http://localhost:8080/q/health/ready
   ```

The database is on an internal Compose network and has no host port. PostgreSQL initializes the
fixed `rosies_books_app` login role from the mounted script when it creates a new data volume. It
can connect to the selected database and create/use the `public` schema required for Flyway and
application tables, but it is not a superuser and cannot create databases or roles. The bootstrap
administrator installs the trusted `pg_trgm` extension required by the existing migrations before
the application role runs Flyway. The app receives its private JDBC endpoint from Compose;
operators do not configure a database hostname or application role name.

Both services restart with `unless-stopped`. The application waits until PostgreSQL is healthy;
its own health check calls `/q/health/ready`, which includes datasource readiness and therefore
only succeeds after Flyway can start against PostgreSQL. On a schema migration failure, investigate
and deploy a forward fix—do not edit a migration that ran against a deployed database.

Production Compose pins the public GHCR image directly. It is pull-only: deploy from a clean
directory containing these three files; no source checkout or local image build is needed.
`ROSIES_BOOKS_HTTP_PORT`
is the only host port exposed by the topology. Update the pinned digest for each release; `latest`
is only a convenience tag and must not be used for production deployment.

Releases published by the multi-architecture workflow provide `linux/amd64` and `linux/arm64/v8`
images under the same tag and digest, so a newly pinned release image can be pulled by either server
architecture.

## Automated image smoke check

Run this only from a checkout with Docker available:

```shell
./scripts/container-smoke.sh
```

The script builds the image by default, starts an isolated Compose project with its own network
and named volume, waits for `/q/health/ready`, confirms the configured image user is
`rosies:rosies`, and always removes only the project, containers, network, and volume it created.
To check an already-built, explicitly named image without rebuilding it, set both
`ROSIES_BOOKS_IMAGE` and `ROSIES_BOOKS_SMOKE_SKIP_BUILD=true`. It uses the
`container-smoke` profile exclusively to disable OIDC for automated image/database/readiness
verification. It is not a deployment mode and is intentionally absent from the production startup
procedure.

## GitHub release administration

After the first successful release, set the `ghcr.io/albertoventurini/rosies-books` container
package visibility to **public** in GitHub package settings. Also create an active `v*` tag ruleset
that limits tag creation, updates, and deletion to release maintainers. Stable tags are the
deliberate release approval point; the release workflow accepts only `v<major>.<minor>.<patch>`
tags that point to commits reachable from `main`.

## Credential-pattern check

Run:

```shell
./scripts/check-tracked-secrets.sh
```

The checker examines tracked content for assignment-style credential patterns. Current intentional
matches are restricted by path to `compose.yaml` (local PostgreSQL password),
`src/main/resources/application.properties` (development review-token and database values),
`.env.oidc.example` (non-usable placeholders), and `src/test/` (test fixtures). The explicit
allowlist lives beside the checker; add no broad exception for a new value. Real secrets and
production allowlisted addresses must remain untracked.

## Java base-image maintenance

The Dockerfile pins both Temurin bases to `25.0.3_9`: the Noble JDK build stage and Noble JRE
runtime stage. To update Java images, change both tags together, update the Java-container record
in `docs/technical-baseline.md`, then run:

```shell
./scripts/build-production-image.sh
./scripts/container-smoke.sh
./mvnw verify
```

The image is reproducible in the practical sense: it uses these fixed Java tags and the pinned
Maven/project dependencies. OCI image bytes are not promised to be identical across rebuilds.
