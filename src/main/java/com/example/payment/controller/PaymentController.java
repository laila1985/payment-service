package com.example.payment.controller;

import com.example.banking.grpc.BalanceResponse;
import com.example.banking.grpc.ReserveResponse;
import com.example.payment.PaymentServiceClient;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST controller that exposes payment endpoints.
 * Internally calls the Account Service via gRPC.
 * <p>
 * This demonstrates the "REST in, gRPC out" pattern:
 * - External clients call this controller via HTTP/JSON (REST)
 * - The controller calls the Account Service via gRPC (binary, HTTP/2)
 */
@RestController
@RequestMapping("/payments")
@Tag(name = "Payments", description = "Payment operations that delegate to Account Service via gRPC")
public class PaymentController {

    private static final Logger log = LoggerFactory.getLogger(PaymentController.class);

    private final PaymentServiceClient paymentServiceClient;

    public PaymentController(PaymentServiceClient paymentServiceClient) {
        this.paymentServiceClient = paymentServiceClient;
    }

    /**
     * Check the balance of an account.
     * GET /payments/balance?accountId=acc-123
     */
    @GetMapping("/balance")
    @Operation(
            summary = "Check account balance",
            description = "Queries the Account Service via gRPC to retrieve the current balance and currency for a given account ID."
    )
    public ResponseEntity<Map<String, Object>> checkBalance(
            @Parameter(description = "The account ID to check, e.g. acc-123, acc-456, acc-789", example = "acc-123")
            @RequestParam String accountId) {
        log.info("REST request: check balance for account {}", accountId);

        BalanceResponse response = paymentServiceClient.checkBalance(accountId);
        if (response == null) {
            return ResponseEntity.status(503).body(Map.of(
                    "error", "Account Service unavailable",
                    "accountId", accountId
            ));
        }

        return ResponseEntity.ok(Map.of(
                "accountId", response.getAccountId(),
                "balance", response.getBalance(),
                "currency", response.getCurrency()
        ));
    }

    /**
     * Reserve funds from an account.
     * POST /payments/reserve
     * Body: { "accountId": "acc-123", "amount": 250.0, "currency": "USD" }
     */
    @PostMapping("/reserve")
    @Operation(
            summary = "Reserve funds from an account",
            description = "Requests the Account Service via gRPC to reserve (deduct) funds from the specified account. "
                    + "Returns success with remaining balance, or failure with reason (insufficient funds, currency mismatch, account not found)."
    )
    public ResponseEntity<Map<String, Object>> reserveFunds(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Reserve funds request containing accountId, amount, and currency"
            )
            @RequestBody ReserveRequest request) {
        log.info("REST request: reserve funds for account {}, amount {}, currency {}",
                request.getAccountId(), request.getAmount(), request.getCurrency());

        ReserveResponse response = paymentServiceClient.reserveFunds(
                request.getAccountId(),
                request.getAmount(),
                request.getCurrency()
        );

        if (response == null) {
            return ResponseEntity.status(503).body(Map.of(
                    "error", "Account Service unavailable",
                    "accountId", request.getAccountId()
            ));
        }

        if (response.getSuccess()) {
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", response.getMessage(),
                    "remainingBalance", response.getRemainingBalance(),
                    "accountId", request.getAccountId()
            ));
        } else {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", response.getMessage(),
                    "remainingBalance", response.getRemainingBalance(),
                    "accountId", request.getAccountId()
            ));
        }
    }

    /**
     * REST request DTO for reserve funds.
     */
    @io.swagger.v3.oas.annotations.media.Schema(description = "Request to reserve funds from an account")
    public static class ReserveRequest {
        @io.swagger.v3.oas.annotations.media.Schema(description = "Account ID", example = "acc-123")
        private String accountId;
        @io.swagger.v3.oas.annotations.media.Schema(description = "Amount to reserve", example = "250.0")
        private double amount;
        @io.swagger.v3.oas.annotations.media.Schema(description = "Currency code", example = "USD")
        private String currency;

        public String getAccountId() { return accountId; }
        public void setAccountId(String accountId) { this.accountId = accountId; }
        public double getAmount() { return amount; }
        public void setAmount(double amount) { this.amount = amount; }
        public String getCurrency() { return currency; }
        public void setCurrency(String currency) { this.currency = currency; }
    }
}