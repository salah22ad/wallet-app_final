package com.hpp.daftree;

import androidx.room.Embedded;

import com.hpp.daftree.database.Transaction;

public class TransactionWithAccount {
    @Embedded
    public Transaction transaction;
    public String accountName;
}