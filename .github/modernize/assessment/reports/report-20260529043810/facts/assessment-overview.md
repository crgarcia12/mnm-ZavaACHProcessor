# Assessment Overview

This directory contains supplementary analysis documents generated as part of the application assessment for **mnm-ZavaACHProcessor** (a Java 8 batch ACH payment processor).

## Documents

| Document | Description |
|----------|-------------|
| [Architecture Diagram](architecture-diagram.md) | Application layer architecture and component relationship diagrams showing the main processing pipeline, data flow between the ACH file processor, RabbitMQ broker, Ledger HTTP API, and filesystem. |
| [Dependency Map](dependency-map.md) | Visualization of all declared project dependencies (amqp-client 5.21.0 and mssql-jdbc 12.6.1.jre8) with CVE and EOL status notes. |
| [API & Service Contracts](api-service-contracts.md) | Outbound service communication contracts: HTTP POST to the Ledger API and AMQP publish to RabbitMQ, including request/response schemas and sequence diagrams. |
| [Data Architecture](data-architecture.md) | Persistence layer documentation covering filesystem-based ACH file storage, in-memory value objects (AchBatch, AchEntry), and PCI/PII data classification with encryption gap analysis. |
| [Configuration Inventory](configuration-inventory.md) | Complete inventory of all 15 application configuration properties, environment-variable override support, secret management gaps (plaintext credentials), and recommended cloud-native configuration improvements. |
| [Business Workflows](business-workflows.md) | Core business workflow documentation for the ACH batch processing pipeline and the failure notification workflow, including sequence diagrams for normal and error paths. |
