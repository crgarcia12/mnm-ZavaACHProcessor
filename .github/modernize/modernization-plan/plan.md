# Modernization Plan: modernization-plan

**Project**: mnm-ZavaACHProcessor

---

## Technical Framework

- **Language**: Java 8
- **Framework**: Custom Java application
- **Build Tool**: Gradle
- **Database**: Microsoft SQL Server (JDBC)
- **Key Dependencies**: RabbitMQ Java Client, Microsoft SQL Server JDBC Driver

---

## Overview

This migration modernizes security posture for the Java ACH processor before
broader Azure adoption. The application currently includes hard-coded
credentials and a vulnerable JDBC dependency. The new target state will:

- Remove embedded credentials by centralizing secrets in Azure Key Vault
- Remediate known dependency CVEs before deployment
- Validate build and test readiness after security modernization

The migration follows a phased approach that first addresses credential handling
and then completes dependency vulnerability remediation.

---

## Migration Impact Summary

| Application | Original Service | New Azure Service | Authentication | Comments |
|-------------|------------------|-------------------|----------------|----------|
| mnm-ZavaACHProcessor | Local/plaintext secrets | Azure Key Vault | Managed Identity | Removes hard-coded credentials |
| mnm-ZavaACHProcessor | Vulnerable JDBC dep | Patched dependency baseline | N/A | Remediates CVE-2025-59250 |
