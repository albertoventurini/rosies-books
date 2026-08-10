FROM eclipse-temurin:25.0.3_9-jdk-noble AS build

WORKDIR /workspace

RUN apt-get update \
    && apt-get install --no-install-recommends --yes unzip \
    && rm -rf /var/lib/apt/lists/*

COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN ./mvnw --batch-mode --no-transfer-progress -DskipTests dependency:go-offline

COPY src src
ARG RELEASE_VERSION
RUN if [ -n "$RELEASE_VERSION" ]; then \
      ./mvnw --batch-mode --no-transfer-progress -DskipTests -Drelease.version="$RELEASE_VERSION" package; \
    else \
      ./mvnw --batch-mode --no-transfer-progress -DskipTests package; \
    fi

FROM eclipse-temurin:25.0.3_9-jre-noble

RUN apt-get update \
    && apt-get install --no-install-recommends --yes curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system rosies \
    && useradd --system --gid rosies --home-dir /work --no-create-home rosies

WORKDIR /work
COPY --from=build --chown=rosies:rosies /workspace/target/quarkus-app/ /work/
RUN chmod -R a-w /work

USER rosies:rosies
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "quarkus-run.jar"]
