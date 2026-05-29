# Configuration & Externalized Settings Inventory

ZavaACHProcessor uses a single classpath properties file (`achprocessor.properties`) as its sole configuration source, supplemented by environment-variable overrides for all sensitive and infrastructure settings; there are no profiles, feature flags, or external config services.

## Configuration Sources

| Source | Type | Path / Location | Notes |
|---|---|---|---|
| `achprocessor.properties` | Java classpath properties | `src/main/resources/achprocessor.properties` | Primary config file; loaded at startup via `Main.class.getClassLoader().getResourceAsStream()` |
| Environment variables | OS / container env | Runtime | Twelve env-var overrides applied after file load (e.g., `RABBITMQ_HOST`, `LEDGER_URL`); take precedence over file values |
| Dockerfile | Container build config | `Dockerfile` | Multi-stage build; no environment variables baked in at build time |

## Build Profiles

| Profile | Activation | Purpose | Key Dependencies / Plugins |
|---|---|---|---|
| (default — single configuration) | Always active | Compiles source and produces a fat JAR via the Shadow plugin | `com.github.johnrengelman.shadow 7.1.2` |

> No build profiles (Gradle flavors, build types, or Maven `-P` profiles) are defined. There is one build configuration that produces a single artifact: a shadow/uber JAR.

## Runtime Profiles

| Profile | Activation Method | Config Files | Key Overrides |
|---|---|---|---|
| (none — single runtime configuration) | N/A | `achprocessor.properties` | Environment-variable overrides applied at startup (see Properties Inventory) |

> No Spring profiles, `.env.*` variants, or environment-specific property files exist. Configuration is differentiated solely via environment variables at container or host level.

## Properties Inventory

### ZavaACHProcessor — `achprocessor.properties`

| Property Key | Default Value | Env-Var Override | Notes |
|---|---|---|---|
| `rabbitmq.host` | `localhost` | `RABBITMQ_HOST` | RabbitMQ broker hostname |
| `rabbitmq.port` | `5672` | `RABBITMQ_PORT` | RabbitMQ AMQP port |
| `rabbitmq.username` | `guest` | `RABBITMQ_USERNAME` | RabbitMQ login user |
| `rabbitmq.password` | `guest` | `RABBITMQ_PASSWORD` | RabbitMQ login password — **sensitive** |
| `rabbitmq.vhost` | `/zavabank` | `RABBITMQ_VHOST` | RabbitMQ virtual host |
| `rabbitmq.events.exchange` | `zava.events` | — | Topic exchange for success events |
| `rabbitmq.deadletter.exchange` | `zava.dlx` | — | Topic exchange for failure / dead-letter events |
| `rabbitmq.ach.routingKey` | `ach.processed` | — | Routing key for successful ACH entries |
| `rabbitmq.ach.failure.routingKey` | `ach.failed` | — | Routing key for failed ACH files |
| `ach.incoming.path` | `/shared/ach-incoming` | `ACH_INCOMING_PATH` | Directory polled for new NACHA files |
| `ach.processed.path` | `/shared/ach-incoming/processed` | `ACH_PROCESSED_PATH` | Destination for successfully processed files |
| `ach.error.path` | `/shared/ach-incoming/error` | `ACH_ERROR_PATH` | Destination for files that fail parsing or posting |
| `ach.poll.interval.ms` | `5000` | `ACH_POLL_INTERVAL_MS` | Polling interval in milliseconds |
| `ledger.url` | `http://zavaledger:8080` | `LEDGER_URL` | Base URL of the downstream Ledger HTTP service |
| `ledger.ach.endpoint` | `/api/ledger/ach` | `LEDGER_ACH_ENDPOINT` | Path appended to `ledger.url` for ACH transaction posts |
| `http.timeout.ms` | `15000` | — | Connect and read timeout (ms) for Ledger HTTP calls |

## Startup Parameters & Resource Requirements

| Service | JVM / Runtime Options | Memory Allocation | Instance Count | Notes |
|---|---|---|---|---|
| ZavaACHProcessor | `java -jar app.jar` (no JVM tuning flags specified) | Not specified in Dockerfile or config | 1 (no scaling config) | JDK 8 JRE base image; no `-Xms`/`-Xmx` flags, GC tuning, or resource limits defined |

## Startup Dependency Chain

```
RabbitMQ broker  ──► ZavaACHProcessor (publishing events)
Ledger HTTP API  ──► ZavaACHProcessor (posting transactions)
Shared filesystem ──► ZavaACHProcessor (reading/writing ACH files)
```

- **No wait mechanism is configured.** The application starts immediately and begins polling. If RabbitMQ or the Ledger API are unavailable at startup, publish/post failures are logged per-operation but do not halt the process.
- There are no Docker Compose `depends_on` health checks, Kubernetes readiness probes, `dockerize` TCP-wait commands, or Spring Cloud Config retry logic.
- Filesystem directories (`processed/`, `error/`) are created automatically at startup via `Files.createDirectories()` if they do not exist.

## Secrets & Sensitive Configuration

| Secret Reference | Type | Default / Storage |
|---|---|---|
| `rabbitmq.password` / `RABBITMQ_PASSWORD` | Message broker credential | Default: `guest` in plaintext properties file; override via env var |
| `rabbitmq.username` / `RABBITMQ_USERNAME` | Message broker credential | Default: `guest` in plaintext properties file; override via env var |

### Secrets Provisioning Workflow

No formal secrets management workflow is in place. Credentials are stored as plaintext values in `achprocessor.properties` (committed to source control with default `guest/guest` values) and can be overridden at runtime via environment variables (`RABBITMQ_USERNAME`, `RABBITMQ_PASSWORD`). There is no integration with HashiCorp Vault, Azure Key Vault, AWS Secrets Manager, Kubernetes Secrets, or any encryption mechanism (e.g., Jasypt). For production deployments, environment variables should be injected via a secrets store (e.g., Azure Key Vault references in Container Apps, Kubernetes Secret mounted as env vars) and the plaintext defaults removed from the properties file.

## Feature Flags

No feature flag framework or conditional configuration mechanism is present. There are no `@ConditionalOnProperty` annotations (the application does not use Spring), no LaunchDarkly/Unleash integration, and no boolean toggle properties in the configuration file.

## Framework & Runtime Versions

| Component | Version | Source |
|---|---|---|
| Java (source / target compatibility) | 1.8 (Java 8) | `build.gradle` — `sourceCompatibility`/`targetCompatibility` |
| Java Runtime (container) | Eclipse Temurin 8 JRE | `Dockerfile` — `FROM eclipse-temurin:8-jre` |
| Gradle | 7.6 | `Dockerfile` — `FROM gradle:7.6-jdk8 AS build` |
| Shadow (fat-JAR) Plugin | 7.1.2 | `build.gradle` — `id 'com.github.johnrengelman.shadow' version '7.1.2'` |
| RabbitMQ AMQP Client | 5.21.0 | `build.gradle` — `com.rabbitmq:amqp-client:5.21.0` |
| SQL Server JDBC Driver | 12.6.1.jre8 | `build.gradle` — `com.microsoft.sqlserver:mssql-jdbc:12.6.1.jre8` |
| Build base image | `gradle:7.6-jdk8` | `Dockerfile` (build stage) |
| Runtime base image | `eclipse-temurin:8-jre` | `Dockerfile` (runtime stage) |
