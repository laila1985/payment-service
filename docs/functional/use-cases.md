# Payment Service — Use Cases

## UC-1: Check Account Balance

### Description
A user (or system) wants to verify the current balance of an account before initiating a payment. The Payment Service calls the Account Service's `GetAccountBalance` RPC.

### Actor
Payment Service (on behalf of End User)

### Preconditions
- The Account Service is running and accessible on `localhost:50051`
- The Payment Service has a valid `account_id`

### Main Flow

| Step | Action |
|------|--------|
| 1 | Payment Service creates a gRPC channel to `localhost:50051` |
| 2 | Payment Service creates an `AccountServiceBlockingStub` from the channel |
| 3 | Payment Service builds an `AccountRequest` with `account_id = "acc-123"` |
| 4 | Payment Service calls `stub.getAccountBalance(request)` |
| 5 | Account Service returns `BalanceResponse` with `account_id`, `balance`, and `currency` |
| 6 | Payment Service displays the balance to the user |

### Postconditions
- The Payment Service has the current balance and currency of the account
- No state is modified on the Account Service

### Example Output

```
=== Account Balance ===
Account ID: acc-123
Balance: 1000.00 USD
```

---

## UC-2: Successful Fund Reservation

### Description
A user initiates a payment. The Payment Service first checks the balance, then reserves the required funds.

### Actor
Payment Service (on behalf of End User)

### Preconditions
- The Account Service is running and accessible on `localhost:50051`
- The account exists and has sufficient funds
- The payment amount and currency match the account's currency

### Main Flow

| Step | Action |
|------|--------|
| 1 | Payment Service creates a gRPC channel and blocking stub |
| 2 | Payment Service calls `getAccountBalance("acc-123")` to verify funds exist |
| 3 | Account Service returns `BalanceResponse(balance=1000.00, currency="USD")` |
| 4 | Payment Service verifies the balance (1000.00) is sufficient for the payment amount (250.00) |
| 5 | Payment Service calls `reserveFunds("acc-123", 250.00, "USD")` |
| 6 | Account Service deducts 250.00 and returns `ReserveResponse(success=true, remaining_balance=750.00)` |
| 7 | Payment Service confirms the reservation to the user |

### Postconditions
- The account's balance is reduced by the reserved amount
- The funds are considered "locked" for this payment transaction
- The Payment Service knows the reservation was successful

### Example Output

```
=== Step 1: Check Balance ===
Account ID: acc-123
Balance: 1000.00 USD

=== Step 2: Reserve Funds ===
Reserving 250.00 USD from account acc-123...
✓ Success: Funds reserved successfully
Remaining balance: 750.00 USD
```

---

## UC-3: Insufficient Funds — Reservation Failed

### Description
A user attempts a payment, but the account does not have enough funds. The Payment Service checks the balance first, detects insufficient funds, and reports the failure.

### Actor
Payment Service (on behalf of End User)

### Preconditions
- The Account Service is running and accessible on `localhost:50051`
- The account exists but has insufficient funds for the requested amount

### Main Flow

| Step | Action |
|------|--------|
| 1 | Payment Service creates a gRPC channel and blocking stub |
| 2 | Payment Service calls `getAccountBalance("acc-123")` |
| 3 | Account Service returns `BalanceResponse(balance=100.00, currency="USD")` |
| 4 | Payment Service detects that 100.00 < requested 500.00 |
| 5 | Payment Service optionally calls `reserveFunds("acc-123", 500.00, "USD")` |
| 6 | Account Service returns `ReserveResponse(success=false, message="Insufficient funds")` |
| 7 | Payment Service reports the failure to the user |

### Postconditions
- The account's balance remains unchanged
- No funds are reserved
- The user is informed of the insufficient balance

### Example Output

```
=== Step 1: Check Balance ===
Account ID: acc-123
Balance: 100.00 USD

=== Step 2: Reserve Funds ===
Reserving 500.00 USD from account acc-123...
✗ Failed: Insufficient funds
Current balance: 100.00 USD
```

---

## UC-4: Currency Mismatch

### Description
A user attempts to reserve funds in a currency that does not match the account's currency (e.g., reserving EUR from a USD account).

### Actor
Payment Service (on behalf of End User)

### Preconditions
- The Account Service is running and accessible on `localhost:50051`
- The account exists with currency "USD"
- The reservation request specifies currency "EUR"

### Main Flow

| Step | Action |
|------|--------|
| 1 | Payment Service calls `getAccountBalance("acc-123")` |
| 2 | Account Service returns `BalanceResponse(currency="USD")` |
| 3 | Payment Service (or user) attempts to reserve in EUR |
| 4 | Payment Service calls `reserveFunds("acc-123", 100.00, "EUR")` |
| 5 | Account Service returns `ReserveResponse(success=false, message="Currency mismatch: account currency is USD")` |
| 6 | Payment Service reports the currency mismatch to the user |

### Postconditions
- The account's balance remains unchanged
- No funds are reserved

### Example Output

```
=== Step 1: Check Balance ===
Account ID: acc-123
Balance: 1000.00 USD

=== Step 2: Reserve Funds ===
Reserving 100.00 EUR from account acc-123...
✗ Failed: Currency mismatch: account currency is USD
```

---

## UC-5: Account Not Found

### Description
The Payment Service attempts to check the balance of an account that does not exist in the Account Service.

### Actor
Payment Service (on behalf of End User)

### Preconditions
- The Account Service is running and accessible on `localhost:50051`
- The requested `account_id` does not exist in the Account Service

### Main Flow

| Step | Action |
|------|--------|
| 1 | Payment Service calls `getAccountBalance("acc-999")` |
| 2 | Account Service returns `BalanceResponse(balance=0.0, currency="UNKNOWN")` |
| 3 | Payment Service detects the "UNKNOWN" currency (or zero balance for a non-existent account) |
| 4 | Payment Service reports that the account was not found |

### Postconditions
- No reservation is attempted
- The user is informed that the account does not exist

### Example Output

```
=== Step 1: Check Balance ===
Account ID: acc-999
Balance: 0.00 UNKNOWN

⚠ Account not found or has no balance. Aborting payment.
```

---

## UC-6: Account Service Unavailable

### Description
The Payment Service cannot connect to the Account Service (e.g., the server is down, network issue).

### Actor
Payment Service (system-level)

### Preconditions
- The Account Service is **not** running or not reachable on `localhost:50051`

### Main Flow

| Step | Action |
|------|--------|
| 1 | Payment Service attempts to create a gRPC channel to `localhost:50051` |
| 2 | gRPC call fails with `Status.UNAVAILABLE` or `Status.DEADLINE_EXCEEDED` |
| 3 | Payment Service catches the error and reports a connection failure |
| 4 | Payment Service shuts down the channel gracefully |

### Postconditions
- No balance check or reservation is performed
- The user is informed that the Account Service is unavailable

### Example Output

```
⚠ Error connecting to Account Service: UNAVAILABLE: io exception
Channel shutdown gracefully.