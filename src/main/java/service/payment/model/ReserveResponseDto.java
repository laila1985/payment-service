package service.payment.model;

public class ReserveResponseDto {
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
