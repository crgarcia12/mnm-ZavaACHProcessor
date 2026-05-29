# Architecture Diagram

This application is a single-process Java ACH file processor that polls a shared filesystem, posts transactions to a ledger API, and emits processing events to RabbitMQ.

## Application Architecture

```mermaid
flowchart TD
    subgraph Input["Input Layer"]
        AchFiles["ACH Files Directory"]
    end
    subgraph App["Application Layer - Java 8"]
        Poller["Main Polling Loop"]
        Parser["NACHA Parser"]
        Poster["Ledger HTTP Client"]
        Publisher["RabbitMQ Publisher"]
    end
    subgraph Data["Data Layer"]
        FileStore[("Shared File Storage")]
    end
    subgraph External["External Services"]
        Ledger["Ledger Service API"]
        Rabbit["RabbitMQ"]
    end

    AchFiles -->|"reads files"| Poller
    Poller -->|"parses batch records"| Parser
    Parser -->|"moves processed/error files"| FileStore
    Parser -->|"sends transactions"| Poster
    Poster -->|"POST transaction"| Ledger
    Parser -->|"publishes events"| Publisher
    Publisher -->|"topic events"| Rabbit
```

### Technology Stack Summary

| Layer | Technology | Version | Purpose |
|---|---|---|---|
| Runtime | Java | 1.8 | Executes ACH processor loop |
| Build | Gradle | N/A | Build and packaging |
| Messaging | RabbitMQ Java Client | 5.21.0 | Publishes processed and failure events |
| Integration | HttpURLConnection | JDK | Posts ACH transactions to ledger service |
| Storage | Shared filesystem | N/A | Input queue and processed/error file movement |

### Data Storage & External Services

The processor reads ACH files from a configured filesystem path and moves them into processed or error folders after execution. It depends on a downstream ledger HTTP API for transaction posting and RabbitMQ exchanges for publishing processing outcomes.

### Key Architectural Decisions

- Uses a simple polling loop instead of a server framework.
- Keeps state in files and in-memory structures rather than a local database.
- Uses direct HTTP and RabbitMQ client integrations with configuration-driven endpoints.

## Component Relationships

```mermaid
flowchart LR
    subgraph Presentation["Presentation"]
        Entrypoint["Main.main"]
    end
    subgraph Business["Business Logic"]
        FileProc["processIncomingFiles"]
        SingleProc["processSingleFile"]
        ParseFn["parseNachaFile"]
    end
    subgraph DataAccess["Data Access"]
        MoveFn["moveFile"]
        ConfigFn["loadConfig"]
    end
    subgraph Infra["Infrastructure"]
        HttpFn["postTransactionToLedger"]
        RabbitFn["publishRabbitEvent"]
    end

    Entrypoint -->|"invokes"| FileProc
    FileProc -->|"per file"| SingleProc
    SingleProc -->|"parse"| ParseFn
    SingleProc -->|"file move"| MoveFn
    SingleProc -->|"post"| HttpFn
    SingleProc -->|"emit events"| RabbitFn
    Entrypoint -->|"startup config"| ConfigFn
```

### Component Inventory

| Component | Layer | Type | Responsibility |
|---|---|---|---|
| Main.main | Presentation | Entrypoint | Starts loop, loads config, handles shutdown |
| processIncomingFiles | Business Logic | Orchestrator | Scans input directory and dispatches files |
| processSingleFile | Business Logic | Workflow handler | Parses file, posts entries, determines success/failure path |
| parseNachaFile | Business Logic | Parser | Converts NACHA records into in-memory batch entries |
| postTransactionToLedger | Infrastructure | HTTP adapter | Sends transaction payloads to ledger endpoint |
| publishRabbitEvent | Infrastructure | Messaging adapter | Publishes processed/failure events to RabbitMQ |
| moveFile | Data Access | File adapter | Moves files to processed or error locations |
| loadConfig | Data Access | Config loader | Reads properties and environment overrides |
