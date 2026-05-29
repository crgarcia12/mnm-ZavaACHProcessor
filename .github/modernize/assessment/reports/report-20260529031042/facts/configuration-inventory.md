# Configuration & Externalized Settings Inventory

Configuration is centralized in a single properties file with environment-variable overrides for runtime deployment flexibility.

## Configuration Sources

| Source | Type | Path/Location | Notes |
|---|---|---|---|
| achprocessor.properties | Properties file | `src/main/resources/achprocessor.properties` | Default runtime settings |
| Environment variables | External overrides | Process environment | Overrides RabbitMQ, ledger, and ACH path settings |

## Build Profiles

| Profile | Activation | Purpose | Key Dependencies/Plugins |
|---|---|---|---|
| default Gradle build | `gradle build` | Compile and package Java application | `java`, `application`, `shadow` plugins |

## Runtime Profiles

| Profile | Activation Method | Config Files | Key Overrides |
|---|---|---|---|
| default | Startup defaults | `achprocessor.properties` | RabbitMQ host/port/vhost, ACH paths, ledger URL |
| environment override | Environment variables | N/A | `RABBITMQ_*`, `ACH_*`, `LEDGER_*` |

## Properties Inventory

| Property Key | Default | Profiles | Source |
|---|---|---|---|
| rabbitmq.host | localhost | default/env | properties + `RABBITMQ_HOST` |
| rabbitmq.port | 5672 | default/env | properties + `RABBITMQ_PORT` |
| rabbitmq.username | guest | default/env | properties + `RABBITMQ_USERNAME` |
| rabbitmq.password | [MASKED] | default/env | properties + `RABBITMQ_PASSWORD` |
| rabbitmq.vhost | /zavabank | default/env | properties + `RABBITMQ_VHOST` |
| rabbitmq.events.exchange | zava.events | default | properties |
| rabbitmq.deadletter.exchange | zava.dlx | default | properties |
| rabbitmq.ach.routingKey | ach.processed | default | properties |
| rabbitmq.ach.failure.routingKey | ach.failed | default | properties |
| ach.incoming.path | /shared/ach-incoming | default/env | properties + `ACH_INCOMING_PATH` |
| ach.processed.path | /shared/ach-incoming/processed | default/env | properties + `ACH_PROCESSED_PATH` |
| ach.error.path | /shared/ach-incoming/error | default/env | properties + `ACH_ERROR_PATH` |
| ach.poll.interval.ms | 5000 | default/env | properties + `ACH_POLL_INTERVAL_MS` |
| ledger.url | http://zavaledger:8080 | default/env | properties + `LEDGER_URL` |
| ledger.ach.endpoint | /api/ledger/ach | default/env | properties + `LEDGER_ACH_ENDPOINT` |
| http.timeout.ms | 15000 | default | properties |

## Startup Parameters & Resource Requirements

| Service | JVM/Runtime Options | Memory | Instance Count |
|---|---|---|---|
| mnm-ZavaACHProcessor | None explicitly configured | Not specified | Not specified |

## Startup Dependency Chain

1. `mnm-ZavaACHProcessor` starts and loads `achprocessor.properties`.
2. ACH input directory availability is required for normal processing.
3. Ledger API and RabbitMQ should be reachable for complete processing success.

## Secrets & Sensitive Configuration

| Secret Reference | Type | Storage (masked) |
|---|---|---|
| rabbitmq.password | Credential | properties/env ([MASKED]) |
| rabbitmq.username | Credential | properties/env ([MASKED]) |

### Secrets Provisioning Workflow

Secrets are sourced from either the properties file or environment variables at startup. The process environment can override defaults before connection setup to RabbitMQ and ledger services.

## Feature Flags

| Flag Name | Default | Controlled By |
|---|---|---|
| None detected | N/A | N/A |

## Framework & Runtime Versions

| Component | Version | Source |
|---|---|---|
| Java | 1.8 target/source compatibility | `build.gradle` |
| Gradle Shadow Plugin | 7.1.2 | `build.gradle` |
| RabbitMQ Java Client | 5.21.0 | `build.gradle` |
| SQL Server JDBC | 12.6.1.jre8 | `build.gradle` |
