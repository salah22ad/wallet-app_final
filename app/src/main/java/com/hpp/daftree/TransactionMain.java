package com.hpp.daftree;

import java.util.Date;
public class TransactionMain {

        private String ownerUID;
        private String accountId;
        private int type; // 1 for debit (عليه), -1 for credit (له)
        private double amount;
        private String details;
        private String currency;
        private Date timestamp;
    private  String documentId;
        public TransactionMain() {
            // Required for Firestore
        }

        public TransactionMain(String documentId, String ownerUID, String accountId, int type, double amount, String details, String currency, Date timestamp) {
           this.documentId=documentId;
            this.ownerUID = ownerUID;
            this.accountId = accountId;
            this.type = type;
            this.amount = amount;
            this.details = details;
            this.currency = currency;
            this.timestamp = timestamp;
        }

        // --- Getters ---
        public String getDocumentId() { return documentId; }
        public String getOwnerUID() { return ownerUID; }
        public String getAccountId() { return accountId; }
        public int getType() { return type; }
        public double getAmount() { return amount; }
        public String getDetails() { return details; }
        public String getCurrency() { return currency; }

        // استخدم ServerTimestamp ليقوم سيرفر فايرستور بوضع الوقت تلقائيا عند الإنشاء
        // لكننا سنقوم بتمرير التاريخ يدوياً من اختيار المستخدم
        public Date getTimestamp() { return timestamp; }
    }

