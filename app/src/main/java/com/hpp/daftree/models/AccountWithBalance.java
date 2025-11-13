package com.hpp.daftree.models;

import androidx.room.Embedded;

import com.hpp.daftree.database.Account;

public class AccountWithBalance {
    @Embedded
    public Account account;
    public double balance;
    public long transactionCount;
    public long lastTransactionDate;
}