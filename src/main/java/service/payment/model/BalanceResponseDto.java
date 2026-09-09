package service.payment.model;

public class BalanceResponseDto {
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
