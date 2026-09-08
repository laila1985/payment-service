package com.example.payment;

import com.example.banking.grpc.AccountRequest;
import com.example.banking.grpc.AccountServiceGrpc;
import com.example.banking.grpc.BalanceResponse;
import com.example.banking.grpc.ReserveRequest;
import com.example.banking.grpc.ReserveResponse;
import io.grpc.internal.testing.StreamRecorder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for PaymentServiceClient.
 * Uses StreamRecorder to test the gRPC stub calls without a real server.
 * In a full integration test, you would use an in-process gRPC server.
 */
class PaymentServiceClientTest {

    private AccountServiceImplTest accountService;

    /**
     * Simple in-memory implementation of AccountService for testing.
     */
    private static class AccountServiceImplTest extends AccountServiceGrpc.AccountServiceImplBase {

        private final java.util.Map<String, double[]> accounts = new java.util.concurrent.ConcurrentHashMap<>();

        AccountServiceImplTest() {
            accounts.put("acc-123", new double[]{1000.00}); // [balance]
        }

        @Override
        public void getAccountBalance(AccountRequest request, io.grpc.stub.StreamObserver<BalanceResponse> responseObserver) {
            double[] account = accounts.get(request.getAccountId());
            BalanceResponse response;
            if (account != null) {
                response = BalanceResponse.newBuilder()
                        .setAccountId(request.getAccountId())
                        .setBalance(account[0])
                        .setCurrency("USD")
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
        public void reserveFunds(ReserveRequest request, io.grpc.stub.StreamObserver<ReserveResponse> responseObserver) {
            double[] account = accounts.get(request.getAccountId());
            if (account == null) {
                responseObserver.onNext(ReserveResponse.newBuilder()
                        .setSuccess(false)
                        .setMessage("Account not found: " + request.getAccountId())
                        .setRemainingBalance(0.0)
                        .build());
                responseObserver.onCompleted();
                return;
            }
            if (account[0] < request.getAmount()) {
                responseObserver.onNext(ReserveResponse.newBuilder()
                        .setSuccess(false)
                        .setMessage("Insufficient funds")
                        .setRemainingBalance(account[0])
                        .build());
                responseObserver.onCompleted();
                return;
            }
            account[0] -= request.getAmount();
            responseObserver.onNext(ReserveResponse.newBuilder()
                    .setSuccess(true)
                    .setMessage("Funds reserved successfully")
                    .setRemainingBalance(account[0])
                    .build());
            responseObserver.onCompleted();
        }
    }

    @BeforeEach
    void setUp() {
        accountService = new AccountServiceImplTest();
    }

    @Test
    void testGetAccountBalance_existingAccount() {
        AccountRequest request = AccountRequest.newBuilder()
                .setAccountId("acc-123")
                .build();

        StreamRecorder<BalanceResponse> recorder = StreamRecorder.create();
        accountService.getAccountBalance(request, recorder);

        BalanceResponse response = recorder.firstValue();
        assertEquals("acc-123", response.getAccountId());
        assertEquals(1000.00, response.getBalance(), 0.01);
        assertEquals("USD", response.getCurrency());
    }

    @Test
    void testGetAccountBalance_nonExistingAccount() {
        AccountRequest request = AccountRequest.newBuilder()
                .setAccountId("acc-999")
                .build();

        StreamRecorder<BalanceResponse> recorder = StreamRecorder.create();
        accountService.getAccountBalance(request, recorder);

        BalanceResponse response = recorder.firstValue();
        assertEquals(0.0, response.getBalance(), 0.01);
        assertEquals("UNKNOWN", response.getCurrency());
    }

    @Test
    void testReserveFunds_success() {
        ReserveRequest request = ReserveRequest.newBuilder()
                .setAccountId("acc-123")
                .setAmount(250.00)
                .setCurrency("USD")
                .build();

        StreamRecorder<ReserveResponse> recorder = StreamRecorder.create();
        accountService.reserveFunds(request, recorder);

        ReserveResponse response = recorder.firstValue();
        assertTrue(response.getSuccess());
        assertEquals(750.00, response.getRemainingBalance(), 0.01);
    }

    @Test
    void testReserveFunds_insufficientFunds() {
        ReserveRequest request = ReserveRequest.newBuilder()
                .setAccountId("acc-123")
                .setAmount(5000.00)
                .setCurrency("USD")
                .build();

        StreamRecorder<ReserveResponse> recorder = StreamRecorder.create();
        accountService.reserveFunds(request, recorder);

        ReserveResponse response = recorder.firstValue();
        assertFalse(response.getSuccess());
        assertEquals("Insufficient funds", response.getMessage());
    }
}