package com.hpp.daftree.ui;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import com.hpp.daftree.models.DaftreeRepository;
import com.hpp.daftree.MyApplication;
import com.hpp.daftree.TransactionItem;
import com.hpp.daftree.database.Account;
import com.hpp.daftree.database.Currency;
import com.hpp.daftree.database.Transaction;
import com.hpp.daftree.database.User;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AccountDetailsViewModel extends AndroidViewModel {

    private final DaftreeRepository mRepository;
    private final int accountId;
    private final LiveData<List<String>> activeCurrencies;
    // --- متغيرات الحالة التي تتحكم بها الواجهة ---
//    private final MutableLiveData<String> currentCurrencyName = new MutableLiveData<>();
    private final MutableLiveData<String> transactionSearchQuery = new MutableLiveData<>(""); // لحالة البحث
    private final MutableLiveData<String> detailsQuery = new MutableLiveData<>(); // لحالة اقتراحات التفاصيل

    // --- LiveData النهائية التي تراقبها الواجهة ---
    private final LiveData<Account> accountDetails;
    private final LiveData<List<String>> detailSuggestions;

    // MediatorLiveData لدمج مصادر التغيير لقائمة العمليات الرئيسية
    private final MediatorLiveData<List<TransactionItem>> transactionItems = new MediatorLiveData<>();
    private LiveData<List<TransactionItem>> currentTransactionSource = null; // لتتبع المصدر الحالي

    // LiveData لإجماليات العملات (تبقى كما هي)
    private final LiveData<Double> debitLocal, creditLocal;
    private final LiveData<Double> debitUsd, creditUsd;
    private final LiveData<Double> debitSar, creditSar;
    private final MutableLiveData<String> currentCurrencyName = new MutableLiveData<>();

    // -- بداية التغيير: إضافة قائمة وخريطة العملات --
    private final LiveData<List<Currency>> allCurrencies;
    private final Map<String, Integer> currencyNameToIdMap = new HashMap<>();

    private final LiveData<List<Account>> allAccounts;
    public AccountDetailsViewModel(@NonNull Application application, int accountId) {
        super(application);
        this.accountId = accountId;
        mRepository = new DaftreeRepository(application);
        allAccounts = mRepository.getAllAccounts();
        // تهيئة بيانات الحساب مرة واحدة
        accountDetails = mRepository.getAccountById(accountId);
        activeCurrencies = mRepository.getActiveCurrenciesForAccount(accountId);

        // إعداد مراقب اقتراحات التفاصيل (يعتمد على نص البحث فقط)


        // جلب الإجماليات لكل عملة بشكل مسبق
        debitLocal = mRepository.getDebitForAccount(accountId, MyApplication.defaultCurrencyName);
        creditLocal = mRepository.getCreditForAccount(accountId, MyApplication.defaultCurrencyName);
        debitUsd = mRepository.getDebitForAccount(accountId, "دولار");
        creditUsd = mRepository.getCreditForAccount(accountId, "دولار");
        debitSar = mRepository.getDebitForAccount(accountId, "سعودي");
        creditSar = mRepository.getCreditForAccount(accountId, "سعودي");
       
        allCurrencies = mRepository.getAllCurrencies();
        transactionItems.addSource(allCurrencies, currencies -> {
            currencyNameToIdMap.clear();
            if (currencies != null) {
                for (Currency c : currencies) {
                    currencyNameToIdMap.put(c.name, c.id);
                }
            }
            updateTransactionListSource();
        });
        transactionItems.addSource(currentCurrencyName, s -> updateTransactionListSource());
        transactionItems.addSource(transactionSearchQuery, s -> updateTransactionListSource());
        detailSuggestions = Transformations.switchMap(detailsQuery, query -> {
            if (query == null || query.isEmpty() || query.contains(" ")) {
                return new MutableLiveData<>(new ArrayList<>());
            }
            return mRepository.getDetailSuggestions(this.accountId, query);
        });
    }
    public LiveData<List<Account>> getAllAccounts() { return allAccounts; }
    /**
     * دالة مركزية لتحديث مصدر قائمة العمليات عند تغيير العملة أو نص البحث.
     */
    private void updateTransactionListSource1() {
        // 1. إزالة المصدر القديم لتجنب التحديثات المكررة
        if (currentTransactionSource != null) {
            transactionItems.removeSource(currentTransactionSource);
        }

        // 2. جلب القيم الحالية للحالة
        String currency = currentCurrencyName.getValue();
        String query = transactionSearchQuery.getValue();
        if (currency == null) return; // لا تفعل شيئًا إذا لم يتم تحديد العملة بعد

        // 3. جلب المصدر الجديد من الـ Repository
        LiveData<List<Transaction>> rawTransactions = mRepository.getTransactionsForAccount(accountId, currency, query != null ? query : "");

        // 4. تحويل القائمة الخام إلى قائمة مع رصيد تراكمي
        currentTransactionSource = Transformations.map(rawTransactions, transactions -> {
            List<TransactionItem> itemsWithBalance = new ArrayList<>();
            double runningBalance = 0.0;
            for (Transaction tx : transactions) {
                runningBalance += (tx.getAmount() * tx.getType());
                itemsWithBalance.add(new TransactionItem(tx, runningBalance));
            }
            Collections.reverse(itemsWithBalance);
            return itemsWithBalance;
        });

        // 5. إضافة المصدر الجديد والمحول إلى الـ MediatorLiveData
        transactionItems.addSource(currentTransactionSource, items -> transactionItems.setValue(items));
    }
    private void updateTransactionListSource11() {
        if (currentTransactionSource != null) {
            transactionItems.removeSource(currentTransactionSource);
        }

        String currency = currentCurrencyName.getValue();
        String query = transactionSearchQuery.getValue();
        if (currency == null) return;

        LiveData<List<Transaction>> rawTransactions = mRepository.getTransactionsForAccount(accountId, currency, query != null ? query : "");
        currentTransactionSource = Transformations.map(rawTransactions, transactions -> {
            List<TransactionItem> itemsWithBalance = new ArrayList<>();
            double runningBalance = 0.0;
            for (Transaction tx : transactions) {
                runningBalance += (tx.getAmount() * tx.getType());
                itemsWithBalance.add(new TransactionItem(tx, runningBalance));
            }
            Collections.reverse(itemsWithBalance);
            return itemsWithBalance;
        });
        transactionItems.addSource(currentTransactionSource, items -> transactionItems.setValue(items));
    }
    private void updateTransactionListSource() {
        if (currentTransactionSource != null) {
            transactionItems.removeSource(currentTransactionSource);
        }

        String currencyName = currentCurrencyName.getValue();
        String query = transactionSearchQuery.getValue();

        // -- بداية التغيير: البحث عن الرقم المقابل للاسم --
        Integer currencyId = currencyNameToIdMap.get(currencyName);
        if (currencyId == null) {
            return; // انتظر حتى يتم تحميل خريطة العملات
        }
        // -- نهاية التغيير --

        LiveData<List<Transaction>> rawTransactions = mRepository.getTransactionsForAccount(accountId, currencyId, query != null ? query : "");
        currentTransactionSource = Transformations.map(rawTransactions, transactions -> {
            List<TransactionItem> itemsWithBalance = new ArrayList<>();
            double runningBalance = 0.0;

            for (Transaction tx : transactions) {
                runningBalance += (tx.getAmount() * tx.getType());
                itemsWithBalance.add(new TransactionItem(tx, runningBalance));
            }
            Collections.reverse(itemsWithBalance);
            return itemsWithBalance;
        });
        transactionItems.addSource(currentTransactionSource, items -> transactionItems.setValue(items));
    }

    // --- Getters & Setters للواجهة ---

    public LiveData<Account> getAccountDetails() { return accountDetails; }
    public LiveData<List<TransactionItem>> getTransactionItems() { return transactionItems; }
    public LiveData<List<String>> getDetailSuggestions() { return detailSuggestions; }
    public LiveData<Double> getDebitLocal() { return debitLocal; }
    public LiveData<Double> getCreditLocal() { return creditLocal; }
    public LiveData<Double> getDebitUsd() { return debitUsd; }
    public LiveData<Double> getCreditUsd() { return creditUsd; }
    public LiveData<Double> getDebitSar() { return debitSar; }
    public LiveData<Double> getCreditSar() { return creditSar; }

    public void setCurrency(String currency) {
        currentCurrencyName.setValue(currency);
    }
    public void setTransactionSearchQuery(String query) {
        transactionSearchQuery.setValue(query);
    }
    public void setDetailsQuery(String query) {
        if (query == null && detailsQuery.getValue() == null) return;
        if (query != null && query.equals(detailsQuery.getValue())) return;
        detailsQuery.setValue(query);
    }

    // دوال الحفظ والتعديل والحذف
    public void addTransaction(Transaction transaction) { mRepository.insertTransaction(transaction); }
    public void updateTransaction(Transaction transaction) { mRepository.updateTransaction(transaction); }
    public void deleteTransaction(Transaction transaction) { mRepository.deleteTransaction(transaction); }
    public void updateAccount(Account account) { mRepository.updateAccount(account); }

    public LiveData<List<String>> getActiveCurrencies() {
        return activeCurrencies;
    }
    public LiveData<List<TransactionItem>> getSelectedTransactionItems() { return transactionItems; }
    public LiveData<User> getUserProfile() { return mRepository.getUserProfile(); }
}