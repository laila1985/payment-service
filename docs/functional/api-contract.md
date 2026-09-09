# Payment Service — API Contract (Client Perspective)

## Overview

The Payment Service is a **gRPC client** that consumes the Account Service's API. It uses the same `banking.proto` contract but from the **client side** — instead of implementing the service, it uses the generated **stub classes** to make remote procedure calls.

This document describes the contract from the Payment Service's point of view: **what it sends, what it receives, and how it uses the generated stubs**.

## The Shared Proto3 Contract

The Payment Service uses the **exact same** `banking.proto` file as the Account Service:

```protobuf
syntax = "proto3";

package banking;

option java_multiple_files = true;
option java_package = "com.example.banking.grpc";

service AccountService {
    rpc GetAccountBalance(AccountRequest) returns (BalanceResponse);
    rpc ReserveFunds(ReserveRequest) returns (ReserveResponse);
}

message AccountRequest {
    string account_id = 1;
}

message BalanceResponse {
    string account_id = 1;
    double balance = 2;
    string currency = 3;
}

message ReserveRequest {
    string account_id = 1;
    double amount = 2;
    string currency = 3;
}

message ReserveResponse {
    bool success = 1;
    string message = 2;
    double remaining_balance = 3;
}
```

> **Key point:** Both services share this single contract. The Account Service uses it to generate the **server base class** (`AccountServiceImplBase`), while the Payment Service uses it to generate the **client stub** (`AccountServiceBlockingStub`).

---

## Client-Side Generated Artifacts

When the Payment Service builds with `./gradlew build`, the protobuf plugin generates the following artifacts from `banking.proto`:

| Generated Artifact                              | How the Payment Service Uses It                                                              |
|-------------------------------------------------|----------------------------------------------------------------------------------------------|
| `AccountServiceGrpc.AccountServiceBlockingStub` | **Primary client interface** — the Payment Service calls methods on this stub to invoke RPCs |
| `AccountServiceGrpc.AccountServiceFutureStub`   | Alternative async client (not used in this demo)                                             |
| `AccountServiceGrpc.AccountServiceStub`         | Streaming client (not used in this demo)                                                     |
| `AccountRequest`                                | Request message builder — used to construct the balance check request                        |
| `BalanceResponse`                               | Response message — received from the server with balance data                                |
| `ReserveRequest`                                | Request message builder — used to construct the fund reservation request                     |
| `ReserveResponse`                               | Response message — received from the server with reservation result                          |

---

## RPC Methods (Client Perspective)

### 1. GetAccountBalance

**Purpose:** Retrieve the current balance of an account before processing a payment.

#### What the Payment Service Sends

```java
AccountRequest request = AccountRequest.newBuilder()
    .setAccountId("acc-123")
    .build();
```

| Field        | Type     | Description          | Example      |
|--------------|----------|----------------------|--------------|
| `account_id` | `string` | The account to query | `"acc-123"`  |

#### What the Payment Service Receives

```java
BalanceResponse response = stub.getAccountBalance(request);
```

| Field        | Type     | Description                      | Example      |
|--------------|----------|----------------------------------|--------------|
| `account_id` | `string` | Echo of the requested account ID | `"acc-123"`  |
| `balance`    | `double` | Current account balance          | `1000.00`    |
| `currency`   | `string` | Account currency code            | `"USD"`      |

#### Client Decision Logic

After receiving the response, the Payment Service decides whether to proceed:

| Response | Client Action |
|----------|--------------|
| `currency != "UNKNOWN"` and `balance >= payment_amount` | Proceed to `ReserveFunds` |
| `currency == "UNKNOWN"` | Account not found — abort payment |
| `balance < payment_amount` | Insufficient funds — abort or report to user |
| gRPC error (UNAVAILABLE) | Account Service is down — report connection error |

---

### 2. ReserveFunds

**Purpose:** Reserve (deduct) funds from an account during a payment transaction.

#### What the Payment Service Sends

```java
ReserveRequest request = ReserveRequest.newBuilder()
    .setAccountId("acc-123")
    .setAmount(250.00)
    .setCurrency("USD")
    .build();
```

| Field | Type | Description | Example |
|-------|------|-------------|---------|
| `account_id` | `string` | The account to reserve funds from | `"acc-123"` |
| `amount` | `double` | The amount to reserve | `250.00` |
| `currency` | `string` | The currency of the reservation | `"USD"` |

#### What the Payment Service Receives

```java
ReserveResponse response = stub.reserveFunds(request);
```

| Field | Type | Description | Example |
|-------|------|-------------|---------|
| `success` | `bool` | Whether the reservation succeeded | `true` |
| `message` | `string` | Human-readable result description | `"Funds reserved successfully"` |
| `remaining_balance` | `double` | Account balance after the operation | `750.00` |

#### Client Decision Logic

| `success` | `message` | Client Action |
|-----------|-----------|--------------|
| `true` | `"Funds reserved successfully"` | Payment can proceed — funds are locked |
| `false` | `"Insufficient funds"` | Report failure — balance too low |
| `false` | `"Currency mismatch: ..."` | Report failure — wrong currency |
| `false` | `"Account not found: ..."` | Report failure — invalid account |

---

## Typical Client Flow

The Payment Service follows this sequence for each payment:

```java
// 1. Create a gRPC channel
ManagedChannel channel = ManagedChannelBuilder
    .forAddress("localhost", 50051)
    .usePlaintext()
    .build();

// 2. Create a blocking stub
AccountServiceGrpc.AccountServiceBlockingStub stub =
    AccountServiceGrpc.newBlockingStub(channel);

// 3. Check balance first
BalanceResponse balance = stub.getAccountBalance(
    AccountRequest.newBuilder().setAccountId("acc-123").build()
);

System.out.println("Balance: " + balance.getBalance() + " " + balance.getCurrency());

// 4. If balance is sufficient, reserve funds
if (balance.getBalance() >= paymentAmount) {
    ReserveResponse result = stub.reserveFunds(
        ReserveRequest.newBuilder()
            .setAccountId("acc-123")
            .setAmount(paymentAmount)
            .setCurrency(balance.getCurrency())
            .build()
    );
    if (result.getSuccess()) {
        System.out.println("Funds reserved! Remaining: " + result.getRemainingBalance());
    } else {
        System.out.println("Reservation failed: " + result.getMessage());
    }
}

// 5. Shut down the channel
channel.shutdown();
```

---

## Error Handling

The Payment Service handles two types of errors:

### Business-Level Errors (in-band)

These are **not gRPC errors** — they are valid responses where `success = false`:

| Scenario | Response |
|----------|----------|
| Insufficient funds | `ReserveResponse(success=false, message="Insufficient funds", remaining_balance=100.0)` |
| Currency mismatch | `ReserveResponse(success=false, message="Currency mismatch: account currency is USD", remaining_balance=1000.0)` |
| Account not found | `ReserveResponse(success=false, message="Account not found: acc-999", remaining_balance=0.0)` |

These are handled by checking `result.getSuccess()` and `result.getMessage()`.

### Transport-Level Errors (out-of-band)

These are **gRPC status codes** that indicate the RPC itself failed:

| gRPC Status | Meaning | Handling |
|-------------|---------|----------|
| `UNAVAILABLE` | Account Service is down or unreachable | Retry or report to user |
| `DEADLINE_EXCEEDED` | Request timed out | Retry with backoff or report |
| `INTERNAL` | Server-side error | Log and report |

These are caught using try/catch around the stub call:

```java
try {
    ReserveResponse response = stub.reserveFunds(request);
    // Handle business response
} catch (StatusRuntimeException e) {
    Status status = e.getStatus();
    if (status.getCode() == Status.Code.UNAVAILABLE) {
        System.err.println("Account Service is unavailable");
    }
}
```

---

## Sharing the Contract Between Projects

Since `account-service` and `payment-service` are separate Gradle projects, both need the `banking.proto` file. In this demo, the proto file is **duplicated** in both projects:

```
account-service/
└── src/main/proto/banking.proto    ← same content

payment-service/
└── src/main/proto/banking.proto    ← same content
```

### Keeping Protos in Sync

When modifying the contract:
1. Update `banking.proto` in **both** projects
2. Rebuild both projects: `./gradlew build`
3. The generated code will automatically reflect the changes

### Production Alternative

In a production environment, the proto file should be managed in a **shared module** or published as a **Maven artifact** to avoid duplication. See [Code Generation](../technical/code-generation.md) for details.