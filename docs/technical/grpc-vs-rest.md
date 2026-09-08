# Payment Service — gRPC vs REST Comparison (Client Perspective)

## 1. Side-by-Side Comparison

| Aspect | REST Client | gRPC Client |
|--------|-------------|-------------|
| **How to call the API** | `RestTemplate.getForObject(url, Response.class)` | `stub.getAccountBalance(request)` |
| **URL construction** | Manual string concatenation: `"/accounts/" + id + "/balance"` | Automatic — method names from `.proto` |
| **Type safety** | Runtime — JSON fields are loosely typed | Compile time — method signatures are generated |
| **Serialization** | JSON (manual or Jackson) | Protobuf (automatic, binary) |
| **Error handling** | HTTP status codes (200, 404, 500) | gRPC status codes + business-level responses |
| **Contract** | Separate OpenAPI spec (often out of sync) | `.proto` file (always in sync with code) |
| **Client code** | Manually write DTOs + service calls | Generated stubs — just call methods |

---

## 2. Detailed Comparison: Client-Side Code

### 2.1 Calling GetAccountBalance

#### REST Client (hypothetical)

```java
// 1. Manually construct the URL
String url = "http://localhost:8080/accounts/" + accountId + "/balance";

// 2. Manually create a RestTemplate
RestTemplate restTemplate = new RestTemplate();

// 3. Make the call — no compile-time verification of URL or response type
BalanceResponse response = restTemplate.getForObject(url, BalanceResponse.class);

// 4. Manually handle HTTP errors
if (response == null) {
    throw new RuntimeException("Failed to get balance");
}
```

**Issues with the REST approach:**
- URL is a string — typos are runtime errors, not compile errors
- `BalanceResponse.class` must be manually written
- No verification that the URL `/accounts/{id}/balance` actually exists on the server
- Error handling requires checking HTTP status codes

#### gRPC Client (our implementation)

```java
// 1. Create a channel (typed, verified connection)
ManagedChannel channel = ManagedChannelBuilder
    .forAddress("localhost", 50051)
    .usePlaintext()
    .build();

// 2. Create a stub — all methods are generated from .proto
AccountServiceGrpc.AccountServiceBlockingStub stub =
    AccountServiceGrpc.newBlockingStub(channel);

// 3. Build a type-safe request
AccountRequest request = AccountRequest.newBuilder()
    .setAccountId("acc-123")
    .build();

// 4. Make the call — method name and parameter types are generated
BalanceResponse response = stub.getAccountBalance(request);

// 5. Access fields with generated getters
String accountId = response.getAccountId();
double balance = response.getBalance();
String currency = response.getCurrency();
```

**Advantages of the gRPC approach:**
- Method name `getAccountBalance` is generated — typos are **compile errors**
- Parameter type `AccountRequest` is generated — you can't forget a field
- Response type `BalanceResponse` is generated — all getters are typed
- No URL construction — the method **is** the endpoint

---

### 2.2 Calling ReserveFunds

#### REST Client (hypothetical)

```java
String url = "http://localhost:8080/accounts/" + accountId + "/reserve";

ReserveRequestDTO request = new ReserveRequestDTO();
request.setAccountId("acc-123");
request.setAmount(250.00);
request.setCurrency("USD");

// Manually handle different response scenarios
ReserveResponseDTO response = restTemplate.postForObject(url, request, ReserveResponseDTO.class);
if (!response.isSuccess()) {
    System.err.println("Failed: " + response.getMessage());
}
```

#### gRPC Client (our implementation)

```java
ReserveRequest request = ReserveRequest.newBuilder()
    .setAccountId("acc-123")
    .setAmount(250.00)
    .setCurrency("USD")
    .build();

ReserveResponse response = stub.reserveFunds(request);

if (response.getSuccess()) {
    System.out.println("Reserved! Remaining: " + response.getRemainingBalance());
} else {
    System.err.println("Failed: " + response.getMessage());
}
```

---

### 2.3 Error Handling

#### REST Client

```java
try {
    BalanceResponse response = restTemplate.getForObject(url, BalanceResponse.class);
} catch (HttpClientErrorException e) {
    // 404 Not Found
    if (e.getStatusCode() == HttpStatus.NOT_FOUND) { ... }
    // 400 Bad Request
    if (e.getStatusCode() == HttpStatus.BAD_REQUEST) { ... }
} catch (HttpServerErrorException e) {
    // 500 Internal Server Error
} catch (RestClientException e) {
    // Connection refused, timeout, etc.
}
```

**Issues:**
- HTTP status codes are coarse — a 400 could mean many things
- You need to parse the response body manually to get error details
- No standard error format across services

#### gRPC Client

```java
try {
    BalanceResponse response = stub.getAccountBalance(request);
    // Business-level errors are in the response itself
} catch (StatusRuntimeException e) {
    Status status = e.getStatus();
    switch (status.getCode()) {
        case UNAVAILABLE:
            // Server is down
            break;
        case NOT_FOUND:
            // Account not found
            break;
        case DEADLINE_EXCEEDED:
            // Request timed out
            break;
        default:
            // Unknown error
    }
}
```

**Advantages:**
- gRPC status codes are well-defined and consistent across all services
- Business errors (insufficient funds, currency mismatch) are handled **in-band** via the `success` and `message` fields
- Transport errors (server down, timeout) are handled **out-of-band** via gRPC status codes
- No need to parse error response bodies

---

## 3. Performance Comparison (Client Perspective)

| Metric | REST Client | gRPC Client |
|--------|-------------|-------------|
| Connection setup | New HTTP/1.1 connection per request (or keep-alive) | Single HTTP/2 connection, multiplexed streams |
| Serialization | JSON → Java objects (Jackson) | Protobuf binary → Java objects (generated parser) |
| Payload on wire | `{"account_id":"acc-123","balance":1000.0,"currency":"USD"}` (~65 bytes) | Binary: field numbers instead of names (~30 bytes) |
| Typing overhead | Runtime reflection (Jackson) | Zero — generated code, no reflection |
| First request latency | Higher (connection + JSON parsing) | Lower (persistent connection + binary parsing) |
| Subsequent requests | Can reuse connection with keep-alive | Reuses same multiplexed connection |

---

## 4. Developer Experience Comparison

### REST: What you manually write

```
1. BalanceResponseDTO.java       — Response DTO with getters/setters
2. ReserveRequestDTO.java        — Request DTO with getters/setters
3. ReserveResponseDTO.java       — Response DTO with getters/setters
4. AccountServiceClient.java     — RestTemplate calls + URL construction
5. Error handling               — HTTP status code mapping
6. Jackson annotations           — @JsonProperty, @JsonIgnore, etc.
7. OpenAPI spec (optional)      — Manual or auto-generated, often out of sync
```

### gRPC: What you manually write

```
1. banking.proto                 — The contract (single file)
2. PaymentServiceClient.java     — Uses generated stubs to call RPCs
```

**Everything else is generated:**
- `AccountServiceGrpc.java` — Stub classes (blocking, future, async)
- `AccountRequest.java` — Immutable message with builder
- `BalanceResponse.java` — Immutable message with builder
- `ReserveRequest.java` — Immutable message with builder
- `ReserveResponse.java` — Immutable message with builder
- Serialization/deserialization — Built into protobuf

**Result:** ~6 files of manual boilerplate are eliminated by code generation.

---

## 5. When to Use Which?

| Use Case | Recommended | Why |
|----------|------------|-----|
| Service-to-service calls (internal) | **gRPC** | Type safety, performance, generated stubs |
| Public API (external consumers) | **REST** | Universal compatibility, browser support |
| Mobile app → backend | **gRPC** or REST | gRPC for performance; REST for simplicity |
| Admin/debugging tool | **REST** | Easy to test with curl/Postman |
| High-throughput microservices | **gRPC** | Binary protocol, HTTP/2 multiplexing |

**For the payment-service → account-service communication, gRPC is the right choice** because:
1. Both services are internal (no browser needed)
2. Type safety prevents runtime errors
3. Generated stubs eliminate boilerplate
4. Binary protocol reduces latency and bandwidth
5. The `.proto` contract guarantees both services stay in sync