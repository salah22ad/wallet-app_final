

package com.hpp.daftree.ui;

import android.app.Application;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import com.hpp.daftree.database.AppDatabase;
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

public class AddTransactionViewModel extends AndroidViewModel {
    private final DaftreeRepository mRepository;
    private final LiveData<List<Account>> allAccounts;
    private final MutableLiveData<Integer> selectedAccountId = new MutableLiveData<>(-1);

    private final LiveData<Account> selectedAccountDetails;
    private final MutableLiveData<String> selectedCurrency = new MutableLiveData<>(MyApplication.defaultCurrencyName);

    private final MediatorLiveData<List<TransactionItem>> transactionItems = new MediatorLiveData<>();
    private final MediatorLiveData<List<String>> detailSuggestions = new MediatorLiveData<>();

    // متغيرات لتتبع المصادر الحالية وتجنب التسريب
    private LiveData<List<TransactionItem>> currentTransactionSource = null;
    private LiveData<List<String>> currentSuggestionSource = null;
    private final MutableLiveData<String> detailsQuery = new MutableLiveData<>();

    private final Map<String, Integer> currencyNameToIdMap = new HashMap<>();
    private final MutableLiveData<Boolean> _accountCreationStatus1 = new MutableLiveData<>();
    private final LiveData<List<Currency>> allCurrencies;

    public AddTransactionViewModel(@NonNull Application application) {
        super(application);
        mRepository = new DaftreeRepository(application);
        allAccounts = mRepository.getAllAccounts();
        allCurrencies = mRepository.getAllCurrencies();
        transactionItems.addSource(allCurrencies, currencies -> {
            currencyNameToIdMap.clear();
            if (currencies != null) {
                for (Currency c : currencies) {
                    currencyNameToIdMap.put(c.name, c.id);
                }
            }
            updateTransactionSource();
        });

        detailSuggestions.addSource(selectedAccountId, id -> updateDetailSuggestionsSource());
        detailSuggestions.addSource(detailsQuery, query -> updateDetailSuggestionsSource());

        selectedAccountDetails = Transformations.switchMap(selectedAccountId, id -> {
            if (id == null || id == -1) return new MutableLiveData<>(null);
            return mRepository.getAccountById(id);
        });

        transactionItems.addSource(selectedAccountId, id -> updateTransactionSource());
        transactionItems.addSource(selectedCurrency, currency -> updateTransactionSource());
    }

    // دالة جديدة لتهيئة خريطة العملات
    public void initCurrencyMap(List<Currency> currencies) {
        currencyNameToIdMap.clear();
        if (currencies != null) {
            for (Currency c : currencies) {
                currencyNameToIdMap.put(c.name, c.id);
            }
        }
        updateTransactionSource();
    }

    // جعل الدالة عامة ليتم استدعاؤها من Activity
    public void updateTransactionSource() {
        //Log.d("TransactionDebug", "updateTransactionSource called");

        if (currentTransactionSource != null) {
            transactionItems.removeSource(currentTransactionSource);
        }

        Integer accountId = selectedAccountId.getValue();
        String currencyName = selectedCurrency.getValue();

        //Log.d("TransactionDebug", "AccountID: " + accountId + ", CurrencyName: " + currencyName);

        if (currencyName == null) {
            transactionItems.setValue(new ArrayList<>());
            return;
        }

        Integer currencyId = currencyNameToIdMap.get(currencyName);

        //Log.d("TransactionDebug", "CurrencyID: " + currencyId);

        if (currencyId == null) {
            //Log.e("TransactionDebug", "Currency ID not found for: " + currencyName);
            transactionItems.setValue(new ArrayList<>());
            return;
        }

        if (accountId == null || accountId == -1) {
            transactionItems.setValue(new ArrayList<>());
            return;
        }

        currentTransactionSource = Transformations.map(
                mRepository.getLiveTransactionsForAccount(accountId, currencyId),
                transactions -> {
                    //Log.d("TransactionDebug", "Transformations.map: transactions size=" + transactions.size());
                    List<TransactionItem> itemsWithBalance = new ArrayList<>();
                    double runningBalance = 0.0;
                    for (Transaction tx : transactions) {
                        runningBalance += (tx.getAmount() * tx.getType());
                        itemsWithBalance.add(new TransactionItem(tx, runningBalance));
                    }
                    Collections.reverse(itemsWithBalance);
                    return itemsWithBalance;
                });

        transactionItems.addSource(currentTransactionSource,
                items -> transactionItems.setValue(items));
    }

    private void updateDetailSuggestionsSource() {
        if (currentSuggestionSource != null) {
            detailSuggestions.removeSource(currentSuggestionSource);
        }

        Integer accountId = selectedAccountId.getValue();
        String query = detailsQuery.getValue();

        if (accountId == null || accountId == -1 || query == null || query.isEmpty() || query.contains(" ")) {
            detailSuggestions.setValue(new ArrayList<>()); // إفراغ القائمة
            return;
        }

        currentSuggestionSource = mRepository.getDetailSuggestions(accountId, query);
        detailSuggestions.addSource(currentSuggestionSource, suggestions -> detailSuggestions.setValue(suggestions));
    }

    // --- دوال عامة ---
    public LiveData<List<Account>> getAllAccounts() { return allAccounts; }
    public LiveData<User> getUserProfile() { return mRepository.getUserProfile(); }
    public LiveData<List<TransactionItem>> getSelectedTransactionItems() { return transactionItems; }

    public void setSelectedAccount(int accountId) {
        selectedAccountId.setValue(accountId);
    }

    public Integer getSelectedAccountId() {
        return selectedAccountId.getValue();
    }

    public LiveData<Integer> getSelectedAccount() {
        return selectedAccountId;
    }

    public void setSelectedCurrency(String currency) {
        selectedCurrency.setValue(currency);
    }

    public String getSelectedCurrency() {
        return selectedCurrency.getValue();
    }

    public void addTransaction(Transaction transaction) { mRepository.insertTransaction(transaction); }
    public void addTransactionInvoice(Transaction transaction) { mRepository.insertTransactionInvoice(transaction); }

    public void createAccount(Account account) {
        mRepository.insertNeAccount(account);
    }
//    private final MutableLiveData<Long> _newAccountId = new MutableLiveData<>();
//
//    public LiveData<Long> getNewAccountId() {
//        return _newAccountId;
//    }
//    public void createAccount2(Account account) {
//        AppDatabase.databaseWriteExecutor.execute(() -> {
//            long newId = mRepository.insertNewAccount(account);
//            _newAccountId.postValue(newId);
//        });
//    }
    public LiveData<List<String>> getDetailSuggestions() {
        return detailSuggestions;
    }

    public void setDetailsQuery(String query) {
        if (query != null && !query.equals(detailsQuery.getValue())) {
            detailsQuery.setValue(query);
        } else if (query == null && detailsQuery.getValue() != null) {
            detailsQuery.setValue(null);
        }
    }
    public LiveData<Account> getSelectedAccountDetails() {
        return selectedAccountDetails;
    }

    // جديد: دالة لتحديث الحساب (سنحتاجها عند إضافة رقم الهاتف)
    public void updateAccount(Account account) {
        mRepository.updateAccount(account);
    }
}