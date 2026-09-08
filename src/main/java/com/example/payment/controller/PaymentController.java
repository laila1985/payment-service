package com.example.payment.controller;

import com.example.banking.grpc.BalanceResponse;
import com.example.banking.grpc.ReserveResponse;
import com.example.payment.PaymentServiceClient;
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
    public ResponseEntity<Map<String, Object>> checkBalance(@RequestParam String accountId) {
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
    public ResponseEntity<Map<String, Object>> reserveFunds(@RequestBody ReserveRequest request) {
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
    public static class ReserveRequest {
        private String accountId;
        private double amount;
        private String currency;

        public String getAccountId() { return accountId; }
        public void setAccountId(String accountId) { this.accountId = accountId; }
        public double getAmount() { return amount; }
        public void setAmount(double amount) { this.amount = amount; }
        public String getCurrency() { return currency; }
        public void setCurrency(String currency) { this.currency = currency; }
    }
}