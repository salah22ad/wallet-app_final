package com.hpp.daftree;

import com.hpp.daftree.database.Transaction;

/**
 * يمثل عنصراً واحداً في واجهة سجل المعاملات.
 * يحتوي على المعاملة الأصلية بالإضافة إلى الرصيد المحسوب بعدها.
 */
public class TransactionItem {
    private final Transaction transaction;
    private final double balanceAfter;

    public TransactionItem(Transaction transaction, double balanceAfter) {
        this.transaction = transaction;
        this.balanceAfter = balanceAfter;
    }

    // Getters
    public Transaction getTransaction() {
        return transaction;
    }

    public double getBalanceAfter() {
        return balanceAfter;
    }
}