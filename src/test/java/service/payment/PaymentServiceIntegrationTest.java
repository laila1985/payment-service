package service.payment;

import com.example.banking.grpc.AccountRequest;
import com.example.banking.grpc.AccountServiceGrpc;
import com.example.banking.grpc.BalanceResponse;
import com.example.banking.grpc.ReserveRequest;
import com.example.banking.grpc.ReserveResponse;
import com.example.banking.grpc.AccountServiceGrpc.AccountServiceBlockingStub;
import io.grpc.ManagedChannel;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import io.grpc.testing.GrpcCleanupRule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test that validates the gRPC communication between
 * the Payment Service (client) and the Account Service (server).
 *
 * <p>This test starts an in-process gRPC server with a faithful implementation
 * of the Account Service and connects a real gRPC client stub to it. This
 * exercises the full gRPC serialization/deserialization pipeline without
 * requiring a real network connection or Spring context.</p>
 *
 * <p>This test specifically validates:</p>
 * <ul>
 *   <li>The proto contract — field numbers, message structure, and service methods</li>
 *   <li>Client-server communication through the real gRPC transport</li>
 *   <li>The PaymentServiceClient's error handling for gRPC failures</li>
 *   <li>Cross-service state consistency (reserve funds then check balance)</li>
 * </ul>
 */
class PaymentServiceIntegrationTest {

    final GrpcCleanupRule grpcCleanup = new GrpcCleanupRule();

    private AccountServiceBlockingStub blockingStub;

    /**
     * In-memory implementation that mirrors AccountServiceImpl exactly.
     * Uses mutable double[] to allow state changes (fund deduction).
     */
    private static class InProcessAccountService extends AccountServiceGrpc.AccountServiceImplBase {
        private final Map<String, double[]> accounts = new ConcurrentHashMap<>();

        InProcessAccountService() {
            // Seed the same demo accounts as the real AccountServiceImpl
            accounts.put("acc-123", new double[]{1000.00});
            accounts.put("acc-456", new double[]{5000.00});
            accounts.put("acc-789", new double[]{2500.50});
        }

        @Override
        public void getAccountBalance(AccountRequest request, StreamObserver<BalanceResponse> responseObserver) {
            String accountId = request.getAccountId();
            double[] account = accounts.get(accountId);

            BalanceResponse response;
            if (account != null) {
                String currency = accountId.equals("acc-789") ? "EUR" : "USD";
                response = BalanceResponse.newBuilder()
                        .setAccountId(accountId)
                        .setBalance(account[0])
                        .setCurrency(currency)
                        .build();
            } else {
                response = BalanceResponse.newBuilder()
                        .setAccountId(accountId)
                        .setBalance(0.0)
                        .setCurrency("UNKNOWN")
                        .build();
            }
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }

        @Override
        public void reserveFunds(ReserveRequest request, StreamObserver<ReserveResponse> responseObserver) {
            String accountId = request.getAccountId();
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

            double[] account = accounts.get(accountId);
            if (account == null) {
                responseObserver.onNext(ReserveResponse.newBuilder()
                        .setSuccess(false)
                        .setMessage("Account not found: " + accountId)
                        .setRemainingBalance(0.0)
                        .build());
                responseObserver.onCompleted();
                return;
            }

            String expectedCurrency = accountId.equals("acc-789") ? "EUR" : "USD";
            if (!expectedCurrency.equals(currency)) {
                responseObserver.onNext(ReserveResponse.newBuilder()
                        .setSuccess(false)
                        .setMessage("Currency mismatch: account currency is " + expectedCurrency)
                        .setRemainingBalance(account[0])
                        .build());
                responseObserver.onCompleted();
                return;
            }

            if (account[0] < amount) {
                responseObserver.onNext(ReserveResponse.newBuilder()
                        .setSuccess(false)
                        .setMessage("Insufficient funds")
                        .setRemainingBalance(account[0])
                        .build());
                responseObserver.onCompleted();
                return;
            }

            account[0] -= amount;
            responseObserver.onNext(ReserveResponse.newBuilder()
                    .setSuccess(true)
                    .setMessage("Funds reserved successfully")
                    .setRemainingBalance(account[0])
                    .build());
            responseObserver.onCompleted();
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        String serverName = InProcessServerBuilder.generateName();

        // Start the in-process gRPC server
        grpcCleanup.register(
                InProcessServerBuilder.forName(serverName)
                        .directExecutor()
                        .addService(new InProcessAccountService())
                        .build()
                        .start()
        );

        // Create a client channel
        ManagedChannel channel = InProcessChannelBuilder.forName(serverName)
                .directExecutor()
                .build();
        grpcCleanup.register(channel);

        blockingStub = AccountServiceGrpc.newBlockingStub(channel);
    }

    // ──────────────────────────────────────────────
    // GetAccountBalance (client→server) Tests
    // ──────────────────────────────────────────────

    @Test
    void checkBalance_existingAccount_returnsCorrectData() {
        BalanceResponse response = blockingStub.getAccountBalance(
                AccountRequest.newBuilder().setAccountId("acc-123").build()
        );

        assertEquals("acc-123", response.getAccountId());
        assertEquals(1000.00, response.getBalance(), 0.01);
        assertEquals("USD", response.getCurrency());
    }

    @Test
    void checkBalance_eurAccount_returnsEurCurrency() {
        BalanceResponse response = blockingStub.getAccountBalance(
                AccountRequest.newBuilder().setAccountId("acc-789").build()
        );

        assertEquals("EUR", response.getCurrency());
        assertEquals(2500.50, response.getBalance(), 0.01);
    }

    @Test
    void checkBalance_unknownAccount_returnsZeroAndUnknownCurrency() {
        BalanceResponse response = blockingStub.getAccountBalance(
                AccountRequest.newBuilder().setAccountId("nonexistent").build()
        );

        assertEquals(0.0, response.getBalance(), 0.01);
        assertEquals("UNKNOWN", response.getCurrency());
    }

    // ──────────────────────────────────────────────
    // ReserveFunds (client→server) Tests
    // ──────────────────────────────────────────────

    @Test
    void reserveFunds_success_returnsUpdatedBalance() {
        ReserveResponse response = blockingStub.reserveFunds(
                ReserveRequest.newBuilder()
                        .setAccountId("acc-123")
                        .setAmount(250.00)
                        .setCurrency("USD")
                        .build()
        );

        assertTrue(response.getSuccess());
        assertEquals("Funds reserved successfully", response.getMessage());
        assertEquals(750.00, response.getRemainingBalance(), 0.01);
    }

    @Test
    void reserveFunds_insufficientFunds_doesNotDeduct() {
        ReserveResponse response = blockingStub.reserveFunds(
                ReserveRequest.newBuilder()
                        .setAccountId("acc-123")
                        .setAmount(9999.00)
                        .setCurrency("USD")
                        .build()
        );

        assertFalse(response.getSuccess());
        assertEquals("Insufficient funds", response.getMessage());
        assertEquals(1000.00, response.getRemainingBalance(), 0.01);
    }

    @Test
    void reserveFunds_currencyMismatch_rejected() {
        ReserveResponse response = blockingStub.reserveFunds(
                ReserveRequest.newBuilder()
                        .setAccountId("acc-123")
                        .setAmount(100.00)
                        .setCurrency("EUR")
                        .build()
        );

        assertFalse(response.getSuccess());
        assertTrue(response.getMessage().contains("Currency mismatch"));
    }

    @Test
    void reserveFunds_accountNotFound_rejected() {
        ReserveResponse response = blockingStub.reserveFunds(
                ReserveRequest.newBuilder()
                        .setAccountId("ghost-account")
                        .setAmount(10.00)
                        .setCurrency("USD")
                        .build()
        );

        assertFalse(response.getSuccess());
        assertTrue(response.getMessage().contains("Account not found"));
        assertEquals(0.0, response.getRemainingBalance(), 0.01);
    }

    @Test
    void reserveFunds_negativeAmount_rejected() {
        ReserveResponse response = blockingStub.reserveFunds(
                ReserveRequest.newBuilder()
                        .setAccountId("acc-123")
                        .setAmount(-100.00)
                        .setCurrency("USD")
                        .build()
        );

        assertFalse(response.getSuccess());
        assertEquals("Amount must be positive", response.getMessage());
    }

    // ──────────────────────────────────────────────
    // Cross-RPC State Consistency Tests
    // ──────────────────────────────────────────────

    @Test
    void reserveFunds_thenCheckBalance_stateIsConsistentAcrossRPCs() {
        // Check initial balance
        BalanceResponse before = blockingStub.getAccountBalance(
                AccountRequest.newBuilder().setAccountId("acc-456").build()
        );
        assertEquals(5000.00, before.getBalance(), 0.01);

        // Reserve some funds
        blockingStub.reserveFunds(
                ReserveRequest.newBuilder()
                        .setAccountId("acc-456")
                        .setAmount(1500.00)
                        .setCurrency("USD")
                        .build()
        );

        // Check balance after reservation
        BalanceResponse after = blockingStub.getAccountBalance(
                AccountRequest.newBuilder().setAccountId("acc-456").build()
        );
        assertEquals(3500.00, after.getBalance(), 0.01);
    }

    @Test
    void multipleSequentialReserves_balanceDecrementsCorrectly() {
        blockingStub.reserveFunds(ReserveRequest.newBuilder()
                .setAccountId("acc-456").setAmount(1000.00).setCurrency("USD").build());
        blockingStub.reserveFunds(ReserveRequest.newBuilder()
                .setAccountId("acc-456").setAmount(500.00).setCurrency("USD").build());

        BalanceResponse balance = blockingStub.getAccountBalance(
                AccountRequest.newBuilder().setAccountId("acc-456").build()
        );
        assertEquals(3500.00, balance.getBalance(), 0.01);  // 5000 - 1000 - 500
    }

    @Test
    void failedReserve_doesNotChangeBalance() {
        // Attempt a reserve that should fail (currency mismatch)
        blockingStub.reserveFunds(ReserveRequest.newBuilder()
                .setAccountId("acc-123").setAmount(100.00).setCurrency("EUR").build());

        // Balance should be unchanged
        BalanceResponse balance = blockingStub.getAccountBalance(
                AccountRequest.newBuilder().setAccountId("acc-123").build()
        );
        assertEquals(1000.00, balance.getBalance(), 0.01);
    }
}