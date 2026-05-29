# Modernization Plan: Zava ACH Processor – Azure Modernization

**Project**: ZavaACHProcessor

---

## Technical Framework

- **Language**: Java 8 (source/target compatibility 1.8)
- **Framework**: Plain Java application (no Spring or Jakarta EE framework)
- **Build Tool**: Gradle 7.6 with Shadow JAR plugin (`com.github.johnrengelman.shadow 7.1.2`)
- **Database**: SQL Server (MSSQL JDBC driver dependency: `mssql-jdbc:12.6.1.jre8`)
- **Messaging**: RabbitMQ AMQP (`amqp-client:5.21.0`) – publishes processed and failure events to RabbitMQ exchanges
- **Key Dependencies**:
  - `com.rabbitmq:amqp-client:5.21.0` – AMQP messaging client
  - `com.microsoft.sqlserver:mssql-jdbc:12.6.1.jre8` – SQL Server JDBC driver

---

## Overview

> This migration modernizes the ZavaACHProcessor Java application from Java 8 to a cloud-native, Azure-ready service. The application currently polls a local filesystem for incoming NACHA/ACH files, posts transactions to a ledger HTTP endpoint, and publishes processed/failure events to RabbitMQ using AMQP. Plaintext credentials for RabbitMQ are stored in a properties file. The new architecture will:
>
> - **Upgrade the Java runtime** to Java 21 (LTS) for improved performance, security, and long-term support on Azure
> - **Replace RabbitMQ AMQP messaging** with Azure Service Bus for a fully managed, cloud-native messaging service
> - **Eliminate plaintext credentials** by migrating secrets (RabbitMQ/Service Bus connection details, passwords) to Azure Key Vault using managed identity
> - **Migrate local file processing paths** to Azure-mounted storage paths for cloud-native file handling
> - **Scan and remediate CVE vulnerabilities** in all project dependencies before deployment
> - **Deploy to Azure Container Apps** as a containerized, cloud-native workload
>
> The migration follows a phased approach: runtime upgrade first, then cloud service migrations, followed by security hardening and deployment to Azure.

---

## Migration Impact Summary

| Application        | Original Service          | New Azure Service              | Authentication     | Comments                                      |
|--------------------|---------------------------|--------------------------------|--------------------|-----------------------------------------------|
| ZavaACHProcessor   | Java 8 JDK                | Java 21 JDK                    | N/A                | LTS version for Azure Container Apps          |
| ZavaACHProcessor   | RabbitMQ AMQP             | Azure Service Bus              | Managed Identity   | Replaces AMQP event publishing                |
| ZavaACHProcessor   | Plaintext credentials     | Azure Key Vault                | Managed Identity   | Removes hardcoded RabbitMQ credentials        |
| ZavaACHProcessor   | Local filesystem paths    | Mounted Azure Storage          | Managed Identity   | ACH file ingestion from Azure-mounted paths   |
| ZavaACHProcessor   | N/A (dependency scan)     | N/A (CVE remediation)          | N/A                | Vulnerability scan & fix before deployment    |
| ZavaACHProcessor   | Local container           | Azure Container Apps           | Managed Identity   | Containerized deployment to ACA               |

---

## Open Questions & Questionnaire

- [x] Q: What is the target Java version for the upgrade? → A: Java 21 (LTS, widely supported on Azure). Java 25 is the latest stable version per guidelines — confirm if Java 25 is preferred.
- [x] Q: What deployment target should be used? → A: Azure Container Apps (default cloud-native option).
- [x] Q: What authentication method should be used for Azure services? → A: Managed Identity (default and recommended).
- [ ] Should the upgrade target Java 25 (latest stable) instead of Java 21 (LTS)? Confirm preferred Java target version.
- [ ] Are there existing Azure resources (Service Bus namespace, Key Vault, Container Apps environment) to reuse, or should new resources be provisioned?
