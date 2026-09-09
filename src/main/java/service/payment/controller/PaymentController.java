package service.payment.controller;

import com.example.banking.grpc.BalanceResponse;
import com.example.banking.grpc.ReserveResponse;
import service.payment.service.PaymentServiceClient;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/payments")
@Tag(name = "Payment", description = "Payment management endpoints")
public class PaymentController {

    private final PaymentServiceClient paymentServiceClient;

    public PaymentController(PaymentServiceClient paymentServiceClient) {
        this.paymentServiceClient = paymentServiceClient;
    }

    @GetMapping("/balance/{accountId}")
    @Operation(
            summary = "Check account balance",
            description = "Retrieves the balance information for the specified account via the gRPC account service."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Balance retrieved successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = BalanceResponseDto.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Account not found",
                    content = @Content
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Internal server error",
                    content = @Content
            )
    })
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<BalanceResponseDto> checkBalance(
            @Parameter(description = "Account ID to check balance for", required = true)
            @PathVariable String accountId) {
        BalanceResponse response = paymentServiceClient.checkBalance(accountId);
        if (response == null) {
            return ResponseEntity.internalServerError().build();
        }
        return ResponseEntity.ok(new BalanceResponseDto(
                response.getAccountId(),
                response.getBalance(),
                response.getCurrency()
        ));
    }

    @PostMapping("/reserve")
    @Operation(
            summary = "Reserve funds from an account",
            description = "Reserves the specified amount of funds from an account via the gRPC account service."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Funds reserved successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ReserveResponseDto.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid request parameters",
                    content = @Content
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Internal server error",
                    content = @Content
            )
    })
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<ReserveResponseDto> reserveFunds(
            @Parameter(description = "Reserve funds request details", required = true)
            @RequestBody ReserveRequestDto request) {
        ReserveResponse response = paymentServiceClient.reserveFunds(
                request.getAccountId(),
                request.getAmount(),
                request.getCurrency()
        );
        if (response == null) {
            return ResponseEntity.internalServerError().build();
        }
        return ResponseEntity.ok(new ReserveResponseDto(
                response.getSuccess(),
                response.getMessage(),
                response.getRemainingBalance()
        ));
    }

    public static class BalanceResponseDto {
        private String accountId;
        private double balance;
        private String currency;

        public BalanceResponseDto() {}

        public BalanceResponseDto(String accountId, double balance, String currency) {
            this.accountId = accountId;
            this.balance = balance;
            this.currency = currency;
        }

        public String getAccountId() { return accountId; }
        public void setAccountId(String accountId) { this.accountId = accountId; }
        public double getBalance() { return balance; }
        public void setBalance(double balance) { this.balance = balance; }
        public String getCurrency() { return currency; }
        public void setCurrency(String currency) { this.currency = currency; }
    }

    public static class ReserveRequestDto {
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

    public static class ReserveResponseDto {
        private boolean success;
        private String message;
        private double remainingBalance;

        public ReserveResponseDto() {}

        public ReserveResponseDto(boolean success, String message, double remainingBalance) {
            this.success = success;
            this.message = message;
            this.remainingBalance = remainingBalance;
        }

        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public double getRemainingBalance() { return remainingBalance; }
        public void setRemainingBalance(double remainingBalance) { this.remainingBalance = remainingBalance; }
    }
}