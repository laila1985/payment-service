package service.payment.service;

import com.example.banking.grpc.AccountRequest;
import com.example.banking.grpc.AccountServiceGrpc;
import com.example.banking.grpc.BalanceResponse;
import com.example.banking.grpc.ReserveRequest;
import com.example.banking.grpc.ReserveResponse;
import io.grpc.StatusRuntimeException;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * gRPC client that communicates with the Account Service.
 * Uses the @GrpcClient annotation from grpc-client-spring-boot-starter
 * to inject the blocking stub.
 */
@Service
public class PaymentServiceClient {

    private static final Logger log = LoggerFactory.getLogger(PaymentServiceClient.class);

    @GrpcClient("account-service")
    private AccountServiceGrpc.AccountServiceBlockingStub accountServiceStub;

    /**
     * Check the balance of an account.
     *
     * @param accountId the account ID to check
     * @return the balance response, or null if the call fails
     */
    public BalanceResponse checkBalance(String accountId) {
        log.info("Checking balance for account: {}", accountId);
        try {
            AccountRequest request = AccountRequest.newBuilder()
                    .setAccountId(accountId)
                    .build();

            BalanceResponse response = accountServiceStub.getAccountBalance(request);
            log.info("Balance response: accountId={}, balance={}, currency={}",
                    response.getAccountId(), response.getBalance(), response.getCurrency());
            return response;
        } catch (StatusRuntimeException e) {
            log.error("gRPC error checking balance: {}", e.getStatus(), e);
            return null;
        }
    }

    /**
     * Reserve funds from an account.
     *
     * @param accountId the account ID
     * @param amount    the amount to reserve
     * @param currency  the currency
     * @return the reserve response, or null if the call fails
     */
    public ReserveResponse reserveFunds(String accountId, double amount, String currency) {
        log.info("Reserving funds: accountId={}, amount={}, currency={}", accountId, amount, currency);
        try {
            ReserveRequest request = ReserveRequest.newBuilder()
                    .setAccountId(accountId)
                    .setAmount(amount)
                    .setCurrency(currency)
                    .build();

            ReserveResponse response = accountServiceStub.reserveFunds(request);
            log.info("Reserve response: success={}, message={}, remainingBalance={}",
                    response.getSuccess(), response.getMessage(), response.getRemainingBalance());
            return response;
        } catch (StatusRuntimeException e) {
            log.error("gRPC error reserving funds: {}", e.getStatus(), e);
            return null;
        }
    }
}