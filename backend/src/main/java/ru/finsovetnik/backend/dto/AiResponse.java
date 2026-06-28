package ru.finsovetnik.backend.dto;

import java.util.List;

public class AiResponse {
    private Boolean isFinancial;
    private List<TransactionDto> transactions;

    // Геттеры и сеттеры
    public Boolean getIsFinancial() { return isFinancial; }
    public void setIsFinancial(Boolean isFinancial) { this.isFinancial = isFinancial; }
    
    public List<TransactionDto> getTransactions() { return transactions; }
    public void setTransactions(List<TransactionDto> transactions) { this.transactions = transactions; }
}
