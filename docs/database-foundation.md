# Database foundation

Task 0-3 establishes PostgreSQL as the only application and persistence-test database, Flyway
migrations as the schema source of truth, and generated jOOQ types as the application query model.
The initial `cover_asset` table is durable schema for later cover work; this task stores bytes and
MIME metadata only and does not fetch or validate images.

## Prerequisites

- Java 25 and the checked-in Maven Wrapper configuration.
- Docker Engine with the Compose plugin for development and for `./mvnw verify`.
- A Docker environment capable of running Linux containers. Tests pull `postgres:18.4` when it is
  not already available locally.

jOOQ generation and Java compilation do not need Docker. The combined verification command does
because persistence, migration, health, and packaged-startup tests use PostgreSQL containers.

## Development database

Start the health-checked PostgreSQL service:

```shell
docker compose up -d postgres
```

The development profile connects to `localhost:5432` with the fixed, non-production database,
username, and password declared in `compose.yaml`. Set `POSTGRES_PORT` before both Compose and the
application when port 5432 is unavailable. For example:

```shell
POSTGRES_PORT=55432 docker compose up -d postgres
POSTGRES_PORT=55432 ./mvnw quarkus:dev
```

Stop PostgreSQL without deleting its named development volume:

```shell
docker compose stop postgres
```

To reset development data, stop the project and delete its named volume:

```shell
docker compose down --volumes
```

**Warning:** the reset command permanently deletes every database row in the Compose development
volume. It cannot be undone unless the data was backed up separately.

## Runtime configuration

The normal runtime profile has no database defaults. It requires all three values from the
environment:

| Variable | Required | Purpose |
| --- | --- | --- |
| `DATABASE_URL` | Yes | PostgreSQL JDBC URL, such as `jdbc:postgresql://db:5432/rosies_books` |
| `DATABASE_USERNAME` | Yes | Application database role |
| `DATABASE_PASSWORD` | Yes | Application database credential; treat as a secret |

After creating the fast-jar with `./mvnw -DskipTests package`, a configured packaged launch is:

```shell
DATABASE_URL=jdbc:postgresql://localhost:5432/rosies_books \
DATABASE_USERNAME=rosies \
DATABASE_PASSWORD=rosies-local \
java -jar target/quarkus-app/quarkus-run.jar
```

The example values are local Compose credentials only. Do not reuse them outside development.

## Migration and generation ownership

Versioned SQL in `src/main/resources/db/migration` owns the schema. Add a new migration for every
schema change; never edit a migration that has been applied to a shared or deployed database.
Flyway validates and migrates at application startup. Clean and automatic repair are disabled, so
an invalid migration fails startup and requires a deliberate forward fix.

During Maven's `generate-sources` phase, `jooq-codegen-maven` uses `DDLDatabase` to read the same
migrations in Flyway order and writes Java sources under `target/generated-sources/jooq`. The
generated directory is build output and must not be committed. Run generation and compilation
without Docker with:

```shell
./mvnw compile
```

Application persistence code uses only generated tables and fields. A PostgreSQL-backed schema
compatibility test compares those generated columns, types, nullability, keys, and the named
5 MiB check with the live migrated schema. SQL parsing, generated-source compilation, and the live
comparison therefore all participate in `verify`.

## Test isolation and verification

The test profile leaves connection details to Quarkus PostgreSQL Dev Services. It pins
`postgres:18.4`, requests no fixed host port, disables container reuse, and mounts no persistent
volume. Each Maven test JVM therefore gets a newly created, newly migrated PostgreSQL container;
Testcontainers removes the container after that JVM exits. Tests clean their `cover_asset` rows
between methods, while container destruction is the authoritative run-level cleanup boundary.

Run unit, PostgreSQL persistence, request, health, architecture, and packaged-JVM tests together:

```shell
./mvnw verify
```

The cover persistence compatibility check writes and reads exactly 5,242,880 bytes and verifies
byte-for-byte equality plus exact MIME metadata. It also confirms PostgreSQL rejects 5,242,881
bytes through `cover_asset_content_max_5_mib`, and verifies both commit and multi-write rollback.
This is compatibility evidence for milestone 7, not a latency benchmark.

The task 0-3 compatibility run on 2026-08-03 used Java 25.0.3, PostgreSQL 18.4 in the pinned
Testcontainers image, pgJDBC 42.7.10, and jOOQ 3.21.6. The patterned 5,242,880-byte payload and
`image/avif` metadata returned exactly as written; the 5,242,881-byte case returned PostgreSQL SQL
state `23514` naming `cover_asset_content_max_5_mib` and left no row. The complete `./mvnw verify`
run passed. These observations establish size and transaction compatibility only; milestone 7
must measure cover-fetch and serving latency in its own representative environment.
