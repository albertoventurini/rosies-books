package com.albertoventurini.rosiesbooks.platform.database;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarFile;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
class MigrationStartupFailureIT {

  private static final String INVALID_MIGRATION = "V2__limit_cover_asset_content_size.sql";
  private static final String INVALID_MIGRATION_ENTRY = "db/migration/" + INVALID_MIGRATION;

  @Container
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer("postgres:18.4")
          .withDatabaseName("migration_failure")
          .withUsername("migration_test")
          .withPassword("migration-test-only");

  @TempDir Path temporaryDirectory;

  @Test
  void invalidMigrationPreventsThePackagedApplicationFromStarting() throws Exception {
    Path application = copyPackagedApplicationWithInvalidMigration();
    Path output = temporaryDirectory.resolve("application-output.log");

    Process process = startPackagedApplication(application, output);
    boolean exited = process.waitFor(Duration.ofSeconds(45).toMillis(), TimeUnit.MILLISECONDS);
    if (!exited) {
      process.destroyForcibly();
    }

    assertTrue(exited, "packaged application did not finish its failed startup in time");
    assertNotEquals(0, process.exitValue(), "invalid migration must make startup fail");
  }

  private Path copyPackagedApplicationWithInvalidMigration() throws IOException {
    Path sourceDirectory = Path.of("target", "quarkus-app").toAbsolutePath();
    Path testDirectory = temporaryDirectory.resolve("quarkus-app");
    try (Stream<Path> sourcePaths = Files.walk(sourceDirectory)) {
      for (Path source : sourcePaths.toList()) {
        Path destination = testDirectory.resolve(sourceDirectory.relativize(source).toString());
        if (Files.isDirectory(source)) {
          Files.createDirectories(destination);
        } else {
          Files.copy(source, destination, StandardCopyOption.COPY_ATTRIBUTES);
        }
      }
    }

    Path applicationJar = findApplicationJarWithMigration(testDirectory.resolve("app"));
    URI applicationJarUri = URI.create("jar:" + applicationJar.toUri());
    try (FileSystem jar = FileSystems.newFileSystem(applicationJarUri, Map.of())) {
      Path migrations = jar.getPath("/db/migration");
      Files.writeString(
          migrations.resolve(INVALID_MIGRATION), "THIS IS DELIBERATELY INVALID SQL;\n");
    }
    return testDirectory.resolve("quarkus-run.jar");
  }

  private static Path findApplicationJarWithMigration(Path applicationDirectory)
      throws IOException {
    try (Stream<Path> paths = Files.list(applicationDirectory)) {
      for (Path path :
          paths.filter(candidate -> candidate.getFileName().toString().endsWith(".jar")).toList()) {
        try (JarFile jar = new JarFile(path.toFile())) {
          if (jar.getJarEntry(INVALID_MIGRATION_ENTRY) != null) {
            return path;
          }
        }
      }
    }
    throw new IOException("Packaged application does not contain " + INVALID_MIGRATION_ENTRY);
  }

  private static Process startPackagedApplication(Path application, Path output)
      throws IOException {
    Path java = Path.of(System.getProperty("java.home"), "bin", "java");
    List<String> command = new ArrayList<>();
    command.add(java.toString());
    command.add("-Dquarkus.datasource.devservices.enabled=false");
    command.add("-jar");
    command.add(application.toString());

    ProcessBuilder processBuilder =
        new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(output.toFile());
    processBuilder.environment().put("QUARKUS_PROFILE", "container-smoke");
    processBuilder.environment().put("ROSIES_BOOKS_DATABASE_URL", POSTGRES.getJdbcUrl());
    processBuilder.environment().put("ROSIES_BOOKS_DATABASE_USERNAME", POSTGRES.getUsername());
    processBuilder.environment().put("ROSIES_BOOKS_DATABASE_PASSWORD", POSTGRES.getPassword());
    processBuilder
        .environment()
        .put("ROSIES_BOOKS_REVIEW_TOKEN_SECRET", "migration-startup-failure-test-secret");
    processBuilder
        .environment()
        .put("ROSIES_BOOKS_OPEN_LIBRARY_OPERATOR_CONTACT", "migration-test@invalid.example");
    return processBuilder.start();
  }
}
