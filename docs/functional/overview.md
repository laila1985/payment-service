# Payment Service — Functional Overview

## 1. Purpose

The **Payment Service** is a gRPC **client** application responsible for initiating payment transactions. It communicates with the **Account Service** to:

1. **Check an account's balance** — verify that sufficient funds exist before processing a payment
2. **Reserve funds** — lock funds on the account to prevent double-spending during the payment lifecycle

The Payment Service acts as the **orchestrator** of the payment flow: it calls the Account Service via gRPC to validate and reserve funds before proceeding with payment processing.

## 2. Business Context

When a customer initiates a payment (e.g., purchasing a product, transferring money), the following flow occurs:

```
Customer → Payment Service → Account Service
                                │
                                ├── Step 1: Check balance (GetAccountBalance)
                                └── Step 2: Reserve funds (ReserveFunds)
```

1. **Balance check**: The Payment Service first verifies the account has enough money
2. **Fund reservation**: If the balance is sufficient, the Payment Service reserves the required amount, preventing the same funds from being used by another concurrent transaction
3. **Payment processing**: Once funds are reserved, the Payment Service proceeds with the actual payment logic (not yet implemented in this demo)

## 3. Actors

| Actor | Role |
|-------|------|
| **End User** | Initiates a payment request (e.g., "Pay $250 from account acc-123") |
| **Payment Service** | Orchestrates the payment flow by calling the Account Service via gRPC |
| **Account Service** | The gRPC server that manages account balances and fund reservations |
| **System Administrator** | Monitors the Payment Service, configures connection to Account Service |

## 4. Business Rules

### BR-1: Balance Check Before Reservation
- The Payment Service **must** check the account balance before attempting to reserve funds.
- This avoids unnecessary reservation attempts on accounts that clearly have insufficient funds.

### BR-2: Reserve Exact Amount
- The Payment Service reserves the **exact** amount specified by the user.
- No partial reservations are supported.

### BR-3: Currency Validation
- The Payment Service must send the correct currency code when reserving funds.
- If the currency does not match the account's currency, the reservation will fail.

### BR-4: Reservation Failure Handling
- If fund reservation fails (insufficient funds, currency mismatch, account not found), the Payment Service should report the failure to the user.
- The Payment Service does **not** retry automatically in this demo.

## 5. Scope

| In Scope | Out of Scope |
|----------|-------------|
| gRPC client calling Account Service | gRPC server implementation (in Account Service) |
| Balance check via gRPC | Fund release (un-reservation) |
| Fund reservation via gRPC | Payment settlement with external systems |
| CLI-based demo client | REST API endpoints |
| Synchronous blocking calls | Asynchronous/streaming calls |

## 6. Service Characteristics

| Property | Value |
|----------|-------|
| Role | gRPC **client** (not a server) |
| Communication protocol | gRPC (HTTP/2) |
| Target server | Account Service on `localhost:50051` |
| Stub type | `AccountServiceBlockingStub` (synchronous) |
| Data storage | None (stateless client) |

## 7. Relationship with Account Service

The Payment Service is a **consumer** of the Account Service's gRPC API. It has no database of its own — it relies entirely on the Account Service for account data.

```
┌─────────────────────┐         gRPC / HTTP/2          ┌─────────────────────┐
│   Payment Service    │ ──────────────────────────────► │   Account Service   │
│   (gRPC Client)      │         port 50051              │   (gRPC Server)     │
│                      │ ◄────────────────────────────── │                     │
│   - Check balance    │         BalanceResponse          │   - In-memory       │
│   - Reserve funds    │         ReserveResponse          │     account store   │
└─────────────────────┘                                  └─────────────────────┘
```

## 8. Related Documentation

- [Account Service — Functional Overview](../../../account-service/docs/functional/overview.md) — the server that this service calls
- [Use Cases](./use-cases.md) — detailed payment flow scenarios
- [API Contract](./api-contract.md) — the gRPC contract from the client's perspective