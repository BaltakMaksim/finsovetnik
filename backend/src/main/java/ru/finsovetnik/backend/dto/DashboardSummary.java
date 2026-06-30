package ru.finsovetnik.backend.dto;

import java.util.Map;

public class DashboardSummary {
    
    private double totalIncome;
    private double totalExpense;
    private double balance;
    private int totalTransactions;
    private Map<String, Double> byCategory;
    private String period;

    // Пустой конструктор (нужен для Spring/Jackson)
    public DashboardSummary() {}

    // Основной конструктор
    public DashboardSummary(double totalIncome, double totalExpense, int totalTransactions, Map<String, Double> byCategory, String period) {
        this.totalIncome = totalIncome;
        this.totalExpense = totalExpense;
        this.balance = totalIncome - totalExpense; 
        this.totalTransactions = totalTransactions;
        this.byCategory = byCategory;
        this.period = period;
    }

   
    public double getTotalIncome() { return totalIncome; }
    public double getTotalExpense() { return totalExpense; }
    public double getBalance() { return balance; }
    public int getTotalTransactions() { return totalTransactions; }
    public Map<String, Double> getByCategory() { return byCategory; }
    public String getPeriod() { return period; }
}