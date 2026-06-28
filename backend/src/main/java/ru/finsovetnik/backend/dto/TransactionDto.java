package ru.finsovetnik.backend.dto;

public class TransactionDto {
    private Double amount;
    private String category;
    private String type;

    // Геттеры и сеттеры
    public Double getAmount() { return amount; }
    public void setAmount(Double amount) { this.amount = amount; }
    
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
}
