# Architecture

The TicketRush architecture follows a classic layered MVC style using Spring Boot, evolving from simple CRUD to a highly concurrent, secured event booking system.

## Layers
- **Controllers**: Handle HTTP mapping, validation, and JSON conversion.
- **Services**: Contain business logic, boundary transactions, and coordination.
- **Repositories**: Handle Data Access using Spring Data JPA.
- **Entities**: Mapped to database tables.
- **DTOs**: Data Transfer Objects isolate our API contract from our persistence layer.

## System Architecture (Phases 0 - 3)
```mermaid
flowchart TD
    %% Styling Definitions
    classDef layer fill:#f4f7f6,stroke:#2c3e50,stroke-width:2px,rx:10px,ry:10px,color:#2c3e50;
    classDef component fill:#e1f5fe,stroke:#0288d1,stroke-width:1px,rx:5px,ry:5px,color:#000;
    classDef external fill:#fff3e0,stroke:#f57c00,stroke-width:2px,rx:20px,ry:20px,color:#000;
    classDef db fill:#e8f5e9,stroke:#388e3c,stroke-width:1px,rx:5px,ry:5px,color:#000;
    classDef worker fill:#f3e5f5,stroke:#8e24aa,stroke-width:1px,rx:5px,ry:5px,color:#000;

    %% --------------------------------
    %% External Top
    %% --------------------------------
    Client((HTTP Client)):::external

    %% --------------------------------
    %% Security Layer (Phase 3)
    %% --------------------------------
    subgraph SecurityTier [Security Layer]
        direction LR
        AuthFilter[JWT Authentication Filter]:::component
        Context[Spring Security Context]:::component
        AuthFilter --> Context
    end

    %% --------------------------------
    %% API Layer (Phase 1 & 3)
    %% --------------------------------
    subgraph APITier [REST API Layer - DTO Boundaries]
        direction LR
        C1[AuthController]:::component
        C2[Show/Venue/Event APIs]:::component
        C3[HoldController]:::component
        C4[BookingController]:::component
        
        C1 ~~~ C2 ~~~ C3 ~~~ C4
    end

    %% --------------------------------
    %% Middleware Layer
    %% --------------------------------
    subgraph MiddlewareTier [Middleware]
        ExceptionHandler[Global Exception Handler]:::component
    end

    %% --------------------------------
    %% Business Logic Layer (Phase 1 & 2)
    %% --------------------------------
    subgraph BusinessTier [Business & Transaction Layer]
        direction LR
        S1[Auth Service]:::component
        S2[Domain Services\nCRUD Logic]:::component
        S3[Hold Service\nAtomic Updates]:::component
        S4[Booking Service\nState Machine]:::component
        S5[Fake Payment\nSimulator]:::component
        
        S1 ~~~ S2 ~~~ S3 ~~~ S4 ~~~ S5
    end

    %% --------------------------------
    %% Data Access Layer (Phase 1 & 2)
    %% --------------------------------
    subgraph DataTier [Data Access Layer]
        direction LR
        R1[(User & Token\nRepos)]:::db
        R2[(Show/Venue\nRepos)]:::db
        R3[(Seat Repo\nNative Query)]:::db
        R4[(Hold Repo)]:::db
        R5[(Booking Repo)]:::db
        R6[(Outbox Repo)]:::db
        
        R1 ~~~ R2 ~~~ R3 ~~~ R4 ~~~ R5 ~~~ R6
    end
    
    %% --------------------------------
    %% Background Workers (Phase 2)
    %% --------------------------------
    subgraph WorkerTier [Async Background Workers]
        direction LR
        W1[Hold Expiry Sweeper\nCron Job]:::worker
        W2[Transactional Outbox Worker\nCron Job]:::worker
        W1 ~~~ W2
    end

    %% --------------------------------
    %% External Bottom
    %% --------------------------------
    Database[(PostgreSQL Database)]:::external
    Kafka((External Message Broker / Kafka\nPlanned Interface)):::external

    %% --------------------------------
    %% Global Flow Connections
    %% --------------------------------
    Client -->|HTTP Requests| SecurityTier
    SecurityTier --> APITier
    APITier -.->|Catches HTTP Errors| MiddlewareTier
    APITier --> BusinessTier
    
    %% Service to Repository connections
    S1 --> R1
    S2 --> R2
    S3 --> R3
    S3 --> R4
    S4 --> R3
    S4 --> R4
    S4 --> R5
    S4 --> R6
    
    BusinessTier --> DataTier
    DataTier --> Database
    
    %% Worker specific connections
    W1 -->|Reads/Updates| R3
    W1 -->|Reads/Updates| R4
    W2 -->|Reads/Updates| R6
    W2 ====>|Publishes Events| Kafka


    %% Assigning styles to subgraphs
    class SecurityTier,APITier,MiddlewareTier,BusinessTier,DataTier,WorkerTier layer;
```
