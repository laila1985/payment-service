# Payment Service — Code Generation

## 1. The Key Insight: Same Proto, Different Artifacts

Both the Account Service (server) and the Payment Service (client) use the **exact same** `banking.proto` file. However, they use **different generated artifacts** from it:

| Artifact | Account Service Uses | Payment Service Uses |
|----------|--------------------|-----------------------|
| `AccountServiceGrpc.AccountServiceImplBase` | ✅ Extends this to implement the server | ❌ Never used |
| `AccountServiceGrpc.AccountServiceBlockingStub` | ❌ Never used | ✅ Creates this to call the server |
| `AccountServiceGrpc.AccountServiceFutureStub` | ❌ | Optional async client |
| `AccountServiceGrpc.AccountServiceStub` | ❌ | Optional streaming client |
| `AccountRequest` | ✅ Receives this from clients | ✅ Builds this to send to the server |
| `BalanceResponse` | ✅ Builds this to send to clients | ✅ Receives this from the server |
| `ReserveRequest` | ✅ Receives this from clients | ✅ Builds this to send to the server |
| `ReserveResponse` | ✅ Builds this to send to clients | ✅ Receives this from the server |

**The important point:** The `.proto` file generates everything — both server and client code. Each service just picks the artifacts it needs.

---

## 2. The `.proto` Source (Identical in Both Projects)

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

This is **byte-for-byte identical** to the file in `account-service/src/main/proto/banking.proto`.

---

## 3. What Gets Generated for the Client

Running `./gradlew build` in the payment-service project generates the same files as the account-service project:

```
build/generated/source/proto/main/grpc/com/example/banking/grpc/
├── AccountServiceGrpc.java          ← Contains client stubs (what we use)
├── AccountRequest.java               ← Request message (we build these)
├── BalanceResponse.java              ← Response message (we receive these)
├── ReserveRequest.java               ← Request message (we build these)
└── ReserveResponse.java              ← Response message (we receive these)
```

### 3.1 The Client Stub: `AccountServiceBlockingStub`

The Payment Service's primary interface to the Account Service is the **blocking stub**:

```java
// GENERATED CODE — DO NOT EDIT
public static final class AccountServiceBlockingStub 
    extends io.grpc.stub.AbstractBlockingStub<AccountServiceBlockingStub> {

    // Synchronous RPC: blocks until response is received
    public BalanceResponse getAccountBalance(AccountRequest request) {
        return blockingUnaryCall(getChannel(), METHOD_GET_ACCOUNT_BALANCE, ...);
    }

    // Synchronous RPC: blocks until response is received
    public ReserveResponse reserveFunds(ReserveRequest request) {
        return blockingUnaryCall(getChannel(), METHOD_RESERVE_FUNDS, ...);
    }
}
```

**How the Payment Service creates and uses the stub:**

```java
// 1. Create a channel (connection to the server)
ManagedChannel channel = ManagedChannelBuilder
    .forAddress("localhost", 50051)
    .usePlaintext()
    .build();

// 2. Create a blocking stub from the channel
AccountServiceGrpc.AccountServiceBlockingStub stub = 
    AccountServiceGrpc.newBlockingStub(channel);

// 3. Use the stub to call RPCs — method names come from .proto
BalanceResponse balance = stub.getAccountBalance(
    AccountRequest.newBuilder()
        .setAccountId("acc-123")
        .build()
);

ReserveResponse result = stub.reserveFunds(
    ReserveRequest.newBuilder()
        .setAccountId("acc-123")
        .setAmount(250.00)
        .setCurrency("USD")
        .build()
);
```

**Key observation:** The method names (`getAccountBalance`, `reserveFunds`) and parameter types (`AccountRequest`, `ReserveRequest`) are all **generated from the `.proto` file**. If you mistype a method name, it's a **compile-time error** — not a runtime failure.

---

### 3.2 Other Available Stubs

The generated `AccountServiceGrpc.java` contains three client stub types:

| Stub Type | Created With | Return Type | Use Case |
|-----------|-------------|-------------|----------|
| `AccountServiceBlockingStub` | `newBlockingStub(channel)` | Direct response object | Synchronous calls (our choice) |
| `AccountServiceFutureStub` | `newFutureStub(channel)` | `ListenableFuture<Response>` | Async calls with callback |
| `AccountServiceStub` | `newStub(channel)` | `StreamObserver<Response>` | Full async streaming |

For this demo, we use `BlockingStub` for simplicity. In production, you might prefer `FutureStub` for non-blocking calls.

---

### 3.3 Message Builder Pattern

All request messages are built using the **builder pattern**:

```java
// Building a request
AccountRequest request = AccountRequest.newBuilder()
    .setAccountId("acc-123")     // string field
    .build();

// Building a request with multiple fields
ReserveRequest request = ReserveRequest.newBuilder()
    .setAccountId("acc-123")     // string field
    .setAmount(250.00)           // double field
    .setCurrency("USD")           // string field
    .build();
```

Response messages are **read-only** (immutable):

```java
// Reading response fields
String accountId = response.getAccountId();
double balance = response.getBalance();
String currency = response.getCurrency();
boolean success = response.getSuccess();
String message = response.getMessage();
```

---

## 4. The Gradle Build Configuration

The `build.gradle` in payment-service is **identical** to account-service for protobuf code generation:

```gradle
plugins {
    id 'java'
    id 'com.google.protobuf' version '0.9.4'
    id 'application'
}

dependencies {
    implementation 'io.grpc:grpc-netty-shaded:1.59.0'
    implementation 'io.grpc:grpc-protobuf:1.59.0'
    implementation 'io.grpc:grpc-stub:1.59.0'
    implementation 'javax.annotation:javax.annotation-api:1.3.2'
}

protobuf {
    protoc { artifact = 'com.google.protobuf:protoc:3.24.0' }
    plugins {
        grpc { artifact = 'io.grpc:protoc-gen-grpc-java:1.59.0' }
    }
    generateProtoTasks {
        all()*.plugins { grpc {} }
    }
}
```

The only difference is the `mainClass` setting — the Payment Service runs `PaymentServiceClient` instead of `AccountServiceServer`.

---

## 5. Comparison: Server vs Client Code Generation

### Server (Account Service) — What it uses from generated code

```java
// EXTENDS the generated base class
public class AccountServiceImpl extends AccountServiceGrpc.AccountServiceImplBase {
    @Override
    public void getAccountBalance(AccountRequest request,
            StreamObserver<BalanceResponse> responseObserver) {
        // Server logic — builds and sends BalanceResponse
    }
}
```

### Client (Payment Service) — What it uses from generated code

```java
// USES the generated stub
AccountServiceGrpc.AccountServiceBlockingStub stub = 
    AccountServiceGrpc.newBlockingStub(channel);

// Client logic — uses the stub to call RPCs
BalanceResponse response = stub.getAccountBalance(
    AccountRequest.newBuilder().setAccountId("acc-123").build()
);
```

### Side-by-side

| Aspect | Account Service (Server) | Payment Service (Client) |
|--------|-------------------------|--------------------------|
| Generated class used | `AccountServiceImplBase` | `AccountServiceBlockingStub` |
| Relationship | **Extends** the base class | **Creates an instance** of the stub |
| Direction | **Receives** requests, **sends** responses | **Sends** requests, **receives** responses |
| Message role | Reads `AccountRequest`, builds `BalanceResponse` | Builds `AccountRequest`, reads `BalanceResponse` |
| Async pattern | `StreamObserver<Response>` callback | Blocking call (returns response directly) |

---

## 6. Sharing the `.proto` Between Projects

In this demo, the `banking.proto` file is **duplicated** in both projects:

```
account-service/
└── src/main/proto/banking.proto

payment-service/
└── src/main/proto/banking.proto    ← Identical copy
```

### Keeping them in sync

When you modify `banking.proto` in one project, you **must** copy the changes to the other project and rebuild both.

### Production-grade alternatives

| Strategy | How it works | Pros | Cons |
|----------|-------------|------|------|
| **Proto duplication** (our approach) | Copy `.proto` into each project | Simple, no external dependencies | Must keep copies in sync manually |
| **Shared Git submodule** | `.proto` in a shared Git repo, included as submodule | Single source of truth | Submodule complexity |
| **Published Maven artifact** | Package `.proto` + generated stubs as JAR, publish to repo | Proper dependency management | Requires artifact repository |
| **Buf Schema Registry** | Cloud-based proto registry | Best-in-class proto management | Additional tooling |

For production, the **published Maven artifact** approach is recommended.

---

## 7. Build Commands

```bash
# Generate code + compile
./gradlew build

# Run the client (requires Account Service to be running)
./gradlew run

# Clean generated code
./gradlew clean
```

> **Important:** Always run `./gradlew build` after modifying `banking.proto`. The generated code must be regenerated to reflect contract changes.