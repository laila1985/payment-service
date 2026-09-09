# Payment Service

A Spring Boot 3 microservice that exposes REST APIs for payment operations, backed by gRPC communication with the [Account Service](https://github.com/laila1985/account-service).

## Architecture

```
┌───────────────────┐         gRPC          ┌───────────────────┐
│                   │  ──────────────────►   │                   │
│  Payment Service  │     port 50051        │  Account Service   │
│  (REST + gRPC     │                        │  (gRPC Server)     │
│   client)         │  ◄──────────────────  │                    │
│                   │                        │                    │
└───────────────────┘                        └───────────────────┘
        │                                           │
        │ HTTP :8085                                 │ HTTP :8088
        ▼                                           ▼
   Swagger UI /                                   H2 Console /
   REST Clients                                   gRPC clients
```

**Payment Service** acts as a REST gateway that translates HTTP requests into gRPC calls to the Account Service. It provides:

- **REST API** (port 8085) — for external clients and Swagger UI
- **gRPC Client** — communicates with Account Service on port 50051

**Account Service** is the gRPC server that handles the actual business logic:
- `GetAccountBalance` — retrieves account balance
- `ReserveFunds` — reserves funds from an account

See the [Account Service repository](https://github.com/laila1985/account-service) for more details.

## Prerequisites

- **Java 21** (OpenJDK recommended)
- **Gradle 8.x** (wrapper included)
- The **Account Service** must be running on `localhost:50051` for the payment service to function

## Setup & Running

### 1. Start the Account Service

Clone and start the Account Service first:

```bash
git clone git@github.com:laila1985/account-service.git
cd account-service
./gradlew bootRun
```

The Account Service will start on:
- **HTTP:** `http://localhost:8088`
- **gRPC:** `localhost:50051`

### 2. Start the Payment Service

```bash
cd payment-service
./gradlew bootRun
```

The Payment Service will start on:
- **HTTP:** `http://localhost:8085`

## Swagger UI

Once the Payment Service is running, you can access the interactive API documentation:

```
http://localhost:8085/swagger-ui.html
```

This redirects to the full Swagger UI at `http://localhost:8085/swagger-ui/index.html`, where you can:

- View all available REST endpoints
- See request/response schemas
- Execute API calls directly from the browser
- Authenticate using Bearer JWT tokens (if applicable)

The OpenAPI JSON spec is also available at:

```
http://localhost:8085/v3/api-docs
```

## REST API Endpoints

| Method | Path                              | Description                    |
|--------|-----------------------------------|--------------------------------|
| GET    | `/api/v1/payments/balance/{id}`   | Check account balance          |
| POST   | `/api/v1/payments/reserve`        | Reserve funds from an account  |

### Check Balance

```bash
curl http://localhost:8085/api/v1/payments/balance/ACC123
```

Response:
```json
{
  "accountId": "ACC123",
  "balance": 5000.0,
  "currency": "USD"
}
```

### Reserve Funds

```bash
curl -X POST http://localhost:8085/api/v1/payments/reserve \
  -H "Content-Type: application/json" \
  -d '{"accountId": "ACC123", "amount": 100.0, "currency": "USD"}'
```

Response:
```json
{
  "success": true,
  "message": "Funds reserved successfully",
  "remainingBalance": 4900.0
}
```

## gRPC Service (Account Service)

The Payment Service communicates with the Account Service via gRPC using the `AccountService` defined in `banking.proto`:

```protobuf
service AccountService {
    rpc GetAccountBalance(AccountRequest) returns (BalanceResponse);
    rpc ReserveFunds(ReserveRequest) returns (ReserveResponse);
}
```

## Configuration

Key configuration in `src/main/resources/application.yml`:

```yaml
server:
  port: 8085                          # HTTP port

grpc:
  client:
    account-service:
      address: 'static://localhost:50051'  # gRPC server address
      negotiationType: plaintext

springdoc:
  api-docs:
    path: /v3/api-docs                # OpenAPI JSON endpoint
  swagger-ui:
    path: /swagger-ui.html            # Swagger UI endpoint
```

## Tech Stack

- **Spring Boot 3.2.2** — REST framework
- **gRPC** — Inter-service communication
- **grpc-client-spring-boot-starter** — gRPC client auto-configuration
- **springdoc-openapi 2.6.0** — Swagger/OpenAPI UI
- **Java 21** — Runtime
- **Protobuf** — Message serialization