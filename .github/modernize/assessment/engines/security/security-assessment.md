# Security Assessment Report

**Generated:** 2026-05-29T04:38:10Z

## Summary

| Metric | Count |
|--------|-------|
| Total Findings | 6 |
| CVE Vulnerabilities | 1 |
| CWE Vulnerabilities | 5 |
| Total Rules Assessed | 59 |
| Rules Passed | 54 |

### By Severity

| Severity | Count |
|----------|-------|
| mandatory | 1 |
| optional | 3 |
| potential | 2 |

## CVE Findings (Dependency Vulnerabilities)

### CVE-2025-59250: JDBC Driver for SQL Server has improper input validation issue
- **Severity:** mandatory
- **Story Points:** 1
- **Files:** build.gradle:13

[CVE-2025-59250](https://github.com/advisories/GHSA-m494-w24q-6f7w): JDBC Driver for SQL Server has improper input validation issue

Severity: HIGH (CVSS 8.1 — AV:N/AC:L/PR:N/UI:R/S:U/C:H/I:H/A:N)

Description: Improper input validation in JDBC Driver for SQL Server allows an unauthorized attacker to perform spoofing over a network.

Affected dependencies:
  - com.microsoft.sqlserver:mssql-jdbc:12.6.1.jre8 (declared at build.gradle:13)

Note: The advisory explicitly lists jre11 variant ranges (>= 12.6.0.jre11, < 12.6.5.jre11). The jre8 and jre11 variants share the same underlying JDBC driver source code, and the improper input validation flaw is expected to affect both packaging variants of 12.6.1.

Recommended fix:
  - Upgrade com.microsoft.sqlserver:mssql-jdbc to 12.6.5.jre8 (or the equivalent jre11/jre21 variant matching your target Java version)
  - Alternatively, since this dependency is currently unused in the codebase, consider removing it entirely until SQL Server connectivity is required

## CWE Findings (Code-Level Vulnerabilities)

### CWE-130: Improper Handling of Length Parameter Inconsistency
- **Category:** Code Quality
- **Severity:** potential
- **Story Points:** 3
- **Files:** src/main/java/com/zavabank/achprocessor/Main.java

In parseNachaFile(), the NACHA batch control record (type '8') provides entryCount (line 140) and totalDebit/totalCredit totals that define the expected number of entries and their aggregate amounts. These values are stored in AchBatch.entryCount, AchBatch.totalDebit, and AchBatch.totalCredit (lines 415-416) but are never validated against the actual number of AchEntry objects collected or the summed amounts. The application proceeds to post transactions regardless of whether the declared entry count matches the parsed count, allowing a malformed or tampered NACHA file to be processed without detection.

### CWE-477: Use of Obsolete Function
- **Category:** Code Quality
- **Severity:** optional
- **Story Points:** 1
- **Files:** src/main/java/com/zavabank/achprocessor/Main.java

Two obsolete API usages are present: (1) java.net.HttpURLConnection (line 14, line 164) — a legacy low-level HTTP API that predates Java 11's java.net.http.HttpClient; it lacks HTTP/2 support, async I/O, and requires manual stream management. (2) java.text.SimpleDateFormat with java.util.Date (line 21, line 377 in nowIso()) — replaced by java.time.Instant and java.time.format.DateTimeFormatter since Java 8; SimpleDateFormat is not thread-safe and is generally considered obsolete in modern Java.

### CWE-259: Use of Hard-coded Password
- **Category:** Credentials & Secrets
- **Severity:** optional
- **Story Points:** 5
- **Files:** src/main/resources/achprocessor.properties, src/main/java/com/zavabank/achprocessor/Main.java

The RabbitMQ password 'guest' is hardcoded in two places: (1) src/main/resources/achprocessor.properties line 4 (rabbitmq.password) — this file is packaged into the application JAR and tracked in source control; (2) Main.java line 259 — the fallback default value in config.getProperty("rabbitmq.password", "guest") also hardcodes the password as a code-level constant. An environment-variable override (RABBITMQ_PASSWORD) exists but is not enforced, so the application will silently use the hardcoded default if the override is absent.

### CWE-778: Insufficient Logging
- **Category:** Credentials & Secrets
- **Severity:** potential
- **Story Points:** 3
- **Files:** src/main/java/com/zavabank/achprocessor/Main.java

The application processes highly sensitive financial data (PCI/PII: account numbers, routing numbers, individual names) but does not log sufficient audit trail information for security-critical events. Specifically: (1) Failed Ledger API authentication (non-2xx HTTP responses) are logged only with status code and trace number (postTransactionToLedger) but without a timestamp, correlation ID, or distinction between auth failures and other errors. (2) RabbitMQ connection failures (publishRabbitEvent) are logged as plain text without severity level, timestamp, or the exchange/routing key context needed for audit. (3) File processing outcomes are logged to stdout via System.out.println with no structured format, making it impossible to reliably query or alert on security events in a cloud logging system. There is no audit trail of which user or process placed a file in the incoming directory.

### CWE-798: Use of Hard-coded Credentials
- **Category:** Credentials & Secrets
- **Severity:** optional
- **Story Points:** 5
- **Files:** src/main/resources/achprocessor.properties, src/main/java/com/zavabank/achprocessor/Main.java

RabbitMQ credentials are hardcoded in both the classpath properties file and the Java source: (1) src/main/resources/achprocessor.properties lines 3-4 contain rabbitmq.username=guest and rabbitmq.****** committed to source control and embedded into the application JAR. (2) Main.java lines 258-259 use these values as fallback defaults in config.getProperty("rabbitmq.username", "guest") and config.getProperty("rabbitmq.password", "guest"). Any deployment that does not explicitly set the RABBITMQ_USERNAME and RABBITMQ_PASSWORD environment variables will authenticate with the default broker credentials.
