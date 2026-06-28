package ru.finsovetnik.backend.entity;

import jakarta.persistence.*;
import ru.finsovetnik.backend.enums.TransactionType;

import java.time.LocalDateTime;

@Entity
@Table(name = "transactions")
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Double amount;
    private String category;
    private String owner;
    private String reply;

     @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private TransactionType type = TransactionType.EXPENSE;

    @Column(name = "is_financial", nullable = false)
    private Boolean isFinancial = true;

    public Boolean getIsFinancial() { return isFinancial; }
    public void setIsFinancial(Boolean isFinancial) { this.isFinancial = isFinancial; }

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    // Конструктор по умолчанию (обязателен для JPA)
    public Transaction() {
        this.createdAt = LocalDateTime.now();
    }

    // Геттеры и сеттеры
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Double getAmount() { return amount; }
    public void setAmount(Double amount) { this.amount = amount; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public TransactionType getType() { return type; }
    public void setType(TransactionType type) { this.type = type; }

    public String getOwner() { return owner; }
    public void setOwner(String owner) { this.owner = owner; }

    public String getReply() { return reply; }
    public void setReply(String reply) { this.reply = reply; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}