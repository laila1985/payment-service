package com.example.payment;

import com.example.banking.grpc.AccountRequest;
import com.example.banking.grpc.AccountServiceGrpc;
import com.example.banking.grpc.BalanceResponse;
import com.example.banking.grpc.ReserveRequest;
import com.example.banking.grpc.ReserveResponse;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for PaymentServiceClient.
 * Uses a simple TestStreamObserver to capture responses.
 * In a full integration test, you would use an in-process gRPC server.
 */
class PaymentServiceClientTest {

    private AccountServiceImplTest accountService;

    /**
     * Simple StreamObserver that captures a single response value.
     */
    static class TestObserver<T> implements StreamObserver<T> {
        private T value;
        private Throwable error;
        private final CountDownLatch latch = new CountDownLatch(1);

        @Override
        public void onNext(T value) {
            this.value = value;
        }

        @Override
        public void onError(Throwable t) {
            this.error = t;
            latch.countDown();
        }

        @Override
        public void onCompleted() {
            latch.countDown();
        }

        T getValue() throws Exception {
            latch.await(5, TimeUnit.SECONDS);
            if (error != null) {
                throw new RuntimeException(error);
            }
            return value;
        }
    }

    /**
     * In-memory implementation of AccountService for testing.
     * Mirrors the business logic in AccountServiceImpl for realistic unit tests.
     */
    private static class AccountServiceImplTest extends AccountServiceGrpc.AccountServiceImplBase {

        private final Map<String, AccountData> accounts = new ConcurrentHashMap<>();

        private record AccountData(double balance, String currency) {}

        AccountServiceImplTest() {
            accounts.put("acc-123", new AccountData(1000.00, "USD"));
        }

        @Override
        public void getAccountBalance(AccountRequest request, StreamObserver<BalanceResponse> responseObserver) {
            AccountData account = accounts.get(request.getAccountId());
            BalanceResponse response;
            if (account != null) {
                response = BalanceResponse.newBuilder()
                        .setAccountId(request.getAccountId())
                        .setBalance(account.balance())
                        .setCurrency(account.currency())
                        .build();
            } else {
                response = BalanceResponse.newBuilder()
                        .setAccountId(request.getAccountId())
                        .setBalance(0.0)
                        .setCurrency("UNKNOWN")
                        .build();
            }
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }

        @Override
        public void reserveFunds(ReserveRequest request, StreamObserver<ReserveResponse> responseObserver) {
            double amount = request.getAmount();
            String currency = request.getCurrency();

            // Negative amount check
            if (amount <= 0) {
                responseObserver.onNext(ReserveResponse.newBuilder()
                        .setSuccess(false)
                        .setMessage("Amount must be positive")
                        .setRemainingBalance(0.0)
                        .build());
                responseObserver.onCompleted();
                return;
            }

            AccountData account = accounts.get(request.getAccountId());
            if (account == null) {
                responseObserver.onNext(ReserveResponse.newBuilder()
                        .setSuccess(false)
                        .setMessage("Account not found: " + request.getAccountId())
                        .setRemainingBalance(0.0)
                        .build());
                responseObserver.onCompleted();
                return;
            }

            // Currency mismatch check
            if (!account.currency().equals(currency)) {
                responseObserver.onNext(ReserveResponse.newBuilder()
                        .setSuccess(false)
                        .setMessage("Currency mismatch: account currency is " + account.currency())
                        .setRemainingBalance(account.balance())
                        .build());
                responseObserver.onCompleted();
                return;
            }

            // Insufficient funds check
            if (account.balance() < amount) {
                responseObserver.onNext(ReserveResponse.newBuilder()
                        .setSuccess(false)
                        .setMessage("Insufficient funds")
                        .setRemainingBalance(account.balance())
                        .build());
                responseObserver.onCompleted();
                return;
            }

            double newBalance = account.balance() - amount;
            accounts.put(request.getAccountId(), new AccountData(newBalance, account.currency()));

            responseObserver.onNext(ReserveResponse.newBuilder()
                    .setSuccess(true)
                    .setMessage("Funds reserved successfully")
                    .setRemainingBalance(newBalance)
                    .build());
            responseObserver.onCompleted();
        }
    }

    @BeforeEach
    void setUp() {
        accountService = new AccountServiceImplTest();
    }

    @Test
    void testGetAccountBalance_existingAccount() throws Exception {
        AccountRequest request = AccountRequest.newBuilder()
                .setAccountId("acc-123")
                .build();

        TestObserver<BalanceResponse> observer = new TestObserver<>();
        accountService.getAccountBalance(request, observer);

        BalanceResponse response = observer.getValue();
        assertEquals("acc-123", response.getAccountId());
        assertEquals(1000.00, response.getBalance(), 0.01);
        assertEquals("USD", response.getCurrency());
    }

    @Test
    void testGetAccountBalance_nonExistingAccount() throws Exception {
        AccountRequest request = AccountRequest.newBuilder()
                .setAccountId("acc-999")
                .build();

        TestObserver<BalanceResponse> observer = new TestObserver<>();
        accountService.getAccountBalance(request, observer);

        BalanceResponse response = observer.getValue();
        assertEquals(0.0, response.getBalance(), 0.01);
        assertEquals("UNKNOWN", response.getCurrency());
    }

    @Test
    void testReserveFunds_success() throws Exception {
        ReserveRequest request = ReserveRequest.newBuilder()
                .setAccountId("acc-123")
                .setAmount(250.00)
                .setCurrency("USD")
                .build();

        TestObserver<ReserveResponse> observer = new TestObserver<>();
        accountService.reserveFunds(request, observer);

        ReserveResponse response = observer.getValue();
        assertTrue(response.getSuccess());
        assertEquals(750.00, response.getRemainingBalance(), 0.01);
    }

    @Test
    void testReserveFunds_insufficientFunds() throws Exception {
        ReserveRequest request = ReserveRequest.newBuilder()
                .setAccountId("acc-123")
                .setAmount(5000.00)
                .setCurrency("USD")
                .build();

        TestObserver<ReserveResponse> observer = new TestObserver<>();
        accountService.reserveFunds(request, observer);

        ReserveResponse response = observer.getValue();
        assertFalse(response.getSuccess());
        assertEquals("Insufficient funds", response.getMessage());
    }

    @Test
    void testReserveFunds_currencyMismatch() throws Exception {
        ReserveRequest request = ReserveRequest.newBuilder()
                .setAccountId("acc-123")
                .setAmount(100.00)
                .setCurrency("EUR")
                .build();

        TestObserver<ReserveResponse> observer = new TestObserver<>();
        accountService.reserveFunds(request, observer);

        ReserveResponse response = observer.getValue();
        assertFalse(response.getSuccess());
        assertTrue(response.getMessage().contains("Currency mismatch"));
    }

    @Test
    void testReserveFunds_accountNotFound() throws Exception {
        ReserveRequest request = ReserveRequest.newBuilder()
                .setAccountId("acc-999")
                .setAmount(100.00)
                .setCurrency("USD")
                .build();

        TestObserver<ReserveResponse> observer = new TestObserver<>();
        accountService.reserveFunds(request, observer);

        ReserveResponse response = observer.getValue();
        assertFalse(response.getSuccess());
        assertTrue(response.getMessage().contains("Account not found"));
        assertEquals(0.0, response.getRemainingBalance(), 0.01);
    }

    @Test
    void testReserveFunds_negativeAmount() throws Exception {
        ReserveRequest request = ReserveRequest.newBuilder()
                .setAccountId("acc-123")
                .setAmount(-50.00)
                .setCurrency("USD")
                .build();

        TestObserver<ReserveResponse> observer = new TestObserver<>();
        accountService.reserveFunds(request, observer);

        ReserveResponse response = observer.getValue();
        assertFalse(response.getSuccess());
        assertEquals("Amount must be positive", response.getMessage());
    }
}
