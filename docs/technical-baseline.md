# Technical baseline

This document records the pinned starting point from task 0-1 and the tooling added by task 0-2. A
pin is owned either directly by a Maven property, by the imported Quarkus platform BOM, by the
checksum-verified wrapper, or by the future Compose file named below. Quarkus-managed dependency
versions are not restated on dependencies when the BOM already supplies the selected version.

## Runtime and libraries

| Component | Version | Owner | Rationale | Source |
| --- | --- | --- | --- | --- |
| Java | 25 LTS | `maven.compiler.release`; Enforcer range `[25,26)` | Current LTS language/runtime baseline; Quarkus 3.33 supports JVM mode through Java 25 | [OpenJDK 25](https://openjdk.org/projects/jdk/25/), [Quarkus Java 25 support](https://quarkus.io/blog/mandrel-25-minimum-version/) |
| Maven | 3.9.16 | `.mvn/wrapper/maven-wrapper.properties`; Enforcer range `[3.9.16,3.9.17)` | Reproducible build runtime | [Apache Maven 3.9.16](https://maven.apache.org/docs/3.9.16/release-notes.html) |
| Maven Wrapper scripts | 3.3.4 | `wrapperVersion` in `.mvn/wrapper/maven-wrapper.properties` | Current script-only wrapper; avoids checking in a wrapper JAR | [Maven Wrapper 3.3.4](https://maven.apache.org/wrapper/) |
| Quarkus Platform | 3.33.2.1 LTS | `quarkus.platform.version` and imported `quarkus-bom` | Production-recommended LTS line with Java 25 support | [Quarkus releases](https://quarkus.io/releases/) |
| jOOQ Open Source | 3.21.6 | `jooq.version` | Provider-neutral SQL DSL, later wired directly to Agroal rather than through a Quarkiverse extension | [jOOQ versions](https://www.jooq.org/download/versions) |
| PostgreSQL server | 18.4 | Future `compose.yaml` image `postgres:18.4` in task 0-3 | Pinned production database; no container is introduced in this task | [PostgreSQL 18.4](https://www.postgresql.org/docs/release/18.4/) |
| PostgreSQL JDBC | 42.7.10 | `postgresql.jdbc.version` | Explicit task baseline for the JDBC driver used by the Quarkus PostgreSQL extension | [pgJDBC 42.7.10](https://jdbc.postgresql.org/changelogs/2026-02-11-42/) |
| Flyway core and PostgreSQL | 12.0.0 | Quarkus Platform BOM; `quarkus-flyway` and `quarkus-flyway-postgresql` extensions | Versioned PostgreSQL migrations, configured in task 0-3 | [Flyway engine release notes](https://documentation.red-gate.com/flyway/release-notes-and-older-versions/release-notes-for-flyway-engine) |
| JUnit Jupiter | 6.0.3 | Quarkus Platform BOM | Unit and architecture test engine aligned with Quarkus Test | [JUnit 6.0.3](https://docs.junit.org/6.0.3/release-notes/) |
| ArchUnit | 1.4.2 | `archunit.version` | Executable package and dependency boundaries | [ArchUnit 1.4.2](https://github.com/TNG/ArchUnit/releases/tag/v1.4.2) |
| Testcontainers | 2.0.4 | Quarkus Platform BOM | PostgreSQL integration-test baseline, present but not activated until task 0-3 | [Testcontainers 2.0.4](https://github.com/testcontainers/testcontainers-java/releases/tag/2.0.4) |
| SmallRye Health extension | 3.33.2.1 | Quarkus Platform BOM; `quarkus-smallrye-health` extension | Startup, liveness, readiness, and configured Agroal readiness endpoints | [Quarkus SmallRye Health](https://quarkus.io/guides/smallrye-health) |
| REST Assured | 5.5.6 | Quarkus Platform BOM; test-scoped `rest-assured` dependency | HTTP request assertions in Quarkus and packaged-JVM tests | [REST Assured](https://rest-assured.io/) |

The PostgreSQL JDBC version is a deliberate direct pin because this task specifies 42.7.10. All
other dependencies already managed at the requested version by the Quarkus BOM (Flyway, JUnit,
and Testcontainers) use the BOM without a dependency-level override.

## Build tools

| Component | Version | Owner | Rationale | Source |
| --- | --- | --- | --- | --- |
| Maven Compiler Plugin | 3.15.0 | `maven.compiler.version` | Compiles with `release=25` and parameter metadata | [Compiler Plugin](https://maven.apache.org/plugins/maven-compiler-plugin/) |
| Maven Surefire Plugin | 3.5.5 | `maven.surefire.version` | Runs JUnit Platform tests on Java 25 | [Surefire Plugin](https://maven.apache.org/surefire/maven-surefire-plugin/) |
| Maven Failsafe Plugin | 3.5.5 | `maven.failsafe.version` | Runs packaged-JVM `@QuarkusIntegrationTest` tests during `verify` | [Failsafe Plugin](https://maven.apache.org/surefire/maven-failsafe-plugin/) |
| Maven Enforcer Plugin | 3.6.3 | `maven.enforcer.version` | Rejects an unpinned Maven or Java runtime | [Enforcer Plugin](https://maven.apache.org/enforcer/maven-enforcer-plugin/) |
| Spotless Maven Plugin | 3.6.0 | `spotless.version` | Reproducible Java and POM formatting checks in `verify` | [Spotless 3.6.0](https://github.com/diffplug/spotless/releases/tag/maven/3.6.0) |
| google-java-format | 1.35.0 | `google.java.format.version` | Single Java formatting implementation | [google-java-format 1.35.0](https://github.com/google/google-java-format/releases/tag/v1.35.0) |

## Upgrade verification

Update only the owner named in the tables. For Maven, regenerate the scripts, independently
download the new distribution, calculate its SHA-256, and replace `distributionSha256Sum`:

```shell
mvn -Denforcer.skip=true org.apache.maven.plugins:maven-wrapper-plugin:3.3.4:wrapper -Dmaven=3.9.16 -Dtype=only-script
curl -fLO https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.16/apache-maven-3.9.16-bin.zip
sha256sum apache-maven-3.9.16-bin.zip
./mvnw --version
```

For Java, Quarkus, and all library pins, run:

```shell
./mvnw --version
./mvnw -U test
./mvnw -U verify
./mvnw quarkus:info
./mvnw dependency:tree -Dverbose
./mvnw help:effective-pom
```

Confirm the important resolved versions individually:

```shell
./mvnw dependency:tree -Dincludes=org.jooq:jooq,org.postgresql:postgresql
./mvnw dependency:tree -Dincludes=org.flywaydb:flyway-core,org.flywaydb:flyway-database-postgresql
./mvnw dependency:tree -Dincludes=org.junit.jupiter:junit-jupiter,com.tngtech.archunit:archunit
./mvnw dependency:tree -Dincludes=org.testcontainers:testcontainers-junit-jupiter,org.testcontainers:testcontainers-postgresql
./mvnw dependency:tree -Dincludes=io.quarkus:quarkus-smallrye-health,io.rest-assured:rest-assured
```

Failsafe upgrades are owned by `maven.failsafe.version`. After changing it, run `./mvnw verify`
and confirm that the packaged JVM tests launch the production fast-jar.

After task 0-3 introduces Compose, verify a PostgreSQL image change with:

```shell
docker compose config
docker compose pull postgres
docker compose run --rm postgres postgres --version
./mvnw verify
```

Finally, inspect `dependency:tree` and the Quarkus output to ensure no Hibernate ORM, H2, reactive
database client, or Quarkiverse jOOQ extension has entered the graph. A framework or library
upgrade is complete only after the architecture fixtures still demonstrate their expected
violations and the production architecture checks pass.
