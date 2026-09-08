# Payment Service — Setup & Run Guide

## 1. Prerequisites

| Requirement | Version | Installation |
|-------------|---------|--------------|
| Java JDK | 17+ | [Adoptium](https://adoptium.net/) or `sdk install java 17.0.8-tem` |
| Gradle | 8.x (wrapper included) | No separate installation needed — `./gradlew` is provided |
| Git | 2.x | [git-scm.com](https://git-scm.com/) |
| **Account Service** | Running | Must be running on `localhost:50051` before the client can connect |

Verify your Java installation:
```bash
java -version
# Expected: openjdk version "17.x.x" or higher
```

---

## 2. Clone the Repository

```bash
git clone git@github.com:laila1985/payment-service.git
cd payment-service
```

---

## 3. Build the Project

The first build will download all dependencies and generate the gRPC code from `banking.proto`:

```bash
./gradlew build
```

### What happens during the build

1. **Protobuf compilation** — `protoc` reads `src/main/proto/banking.proto` and generates Java message classes into `build/generated/source/proto/main/java/`
2. **gRPC stub generation** — The `grpc-java` plugin generates `AccountServiceGrpc.java` (including `AccountServiceBlockingStub`) into `build/generated/source/proto/main/grpc/`
3. **Java compilation** — Both generated code and hand-written code are compiled together
4. **Test execution** — Unit tests run via JUnit 5

### Generated files location

```
build/generated/source/proto/
├── main/java/com/example/banking/grpc/
│   ├── AccountRequest.java
│   ├── BalanceResponse.java
│   ├── ReserveRequest.java
│   └── ReserveResponse.java
└── main/grpc/com/example/banking/grpc/
    └── AccountServiceGrpc.java          ← Contains BlockingStub, FutureStub, Stub
```

> **Note:** These files are auto-generated and should **never be edited manually**. They are excluded from Git via `.gitignore`.

---

## 4. Start the Account Service First

The Payment Service is a **client** — it needs the Account Service to be running before it can make RPC calls.

### Start the Account Service

In a separate terminal:

```bash
cd /path/to/account-service
./gradlew run
```

You should see:
```
Account Service server started on port 50051
```

Leave this terminal running.

---

## 5. Run the Payment Service Client

In another terminal:

```bash
cd /path/to/payment-service
./gradlew run
```

The client will:
1. Connect to the Account Service on `localhost:50051`
2. Check the balance of account `acc-123`
3. Attempt to reserve funds
4. Display the results
5. Shut down the gRPC channel

### Expected Output

```
=== Payment Service Client ===
Connecting to Account Service at localhost:50051...

--- Step 1: Check Account Balance ---
Account ID: acc-123
Balance: 1000.00 USD

--- Step 2: Reserve Funds ---
Reserving 250.00 USD from account acc-123...
✓ Success: Funds reserved successfully
Remaining balance: 750.00 USD

--- Step 3: Attempt Another Reservation (will fail) ---
Reserving 900.00 USD from account acc-123...
✗ Failed: Insufficient funds
Current balance: 750.00 USD

Channel shutdown gracefully.
```

---

## 6. Troubleshooting

### Issue: `io.grpc.StatusRuntimeException: UNAVAILABLE: io exception`

**Symptom:** The client cannot connect to the Account Service.

**Solution:**
1. Make sure the Account Service is running:
   ```bash
   # In the account-service directory:
   ./gradlew run
   ```
2. Verify it's listening on port 50051:
   ```bash
   lsof -i :50051
   ```
3. If the port is in use by another process, kill it or change the port in both services.

### Issue: `java: package com.example.banking.grpc does not exist`

**Symptom:** IDE shows compilation errors for generated classes.

**Solution:**
1. Run `./gradlew build` first to generate the code
2. In IntelliJ: right-click `build/generated/source/proto` → "Mark Directory As" → "Generated Sources Root"
3. Or simply: reload the Gradle project in IntelliJ (View → Tool Windows → Gradle → Reload)

### Issue: Gradle permission denied

**Symptom:** `./gradlew: Permission denied`

**Solution:**
```bash
chmod +x gradlew
```

### Issue: Port 50051 already in use

**Symptom:** Account Service fails to start with `BindException`

**Solution:**
```bash
# Find the process using port 50051
lsof -i :50051

# Kill it
kill -9 <PID>
```

### Issue: Client hangs indefinitely

**Symptom:** The client doesn't return after making an RPC call.

**Solution:** The client may be waiting for a response from a server that's not running. Add a deadline:
```java
BalanceResponse response = stub
    .withDeadlineAfter(5, TimeUnit.SECONDS)
    .getAccountBalance(request);
```

---

## 7. Project Structure

```
payment-service/
├── build.gradle                      # Gradle build with protobuf plugin
├── settings.gradle                   # Project settings
├── gradle.properties                 # Java version and other properties
├── docs/                             # Documentation
│   ├── functional/                   # Functional specification
│   │   ├── overview.md
│   │   ├── use-cases.md
│   │   └── api-contract.md
│   └── technical/                    # Technical specification
│       ├── architecture.md
│       ├── code-generation.md
│       ├── grpc-vs-rest.md
│       └── setup-and-run.md
├── src/
│   └── main/
│       ├── proto/
│       │   └── banking.proto          # ← The API contract (shared with account-service)
│       └── java/com/example/payment/
│           └── PaymentServiceClient.java   # gRPC client entry point
└── .gitignore
```

---

## 8. Running Tests

```bash
./gradlew test
```

Tests use gRPC's **in-process server** to test the client without requiring the Account Service to be running.

---

## 9. Building a Runnable JAR

```bash
./gradlew shadowJar
```

This creates a fat JAR with all dependencies:

```
build/libs/payment-service-1.0.0-all.jar
```

Run it:
```bash
java -jar build/libs/payment-service-1.0.0-all.jar
```

---

## 10. Modifying the gRPC Contract

If you need to change the `banking.proto` file:

1. Edit `src/main/proto/banking.proto` in **both** `account-service` and `payment-service`
2. Rebuild both projects:
   ```bash
   # In account-service:
   cd /path/to/account-service && ./gradlew build
   
   # In payment-service:
   cd /path/to/payment-service && ./gradlew build
   ```
3. The generated code will reflect the new contract automatically

> **Important:** The proto files must be **identical** in both projects. Any mismatch will cause communication errors at runtime.