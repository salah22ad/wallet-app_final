package com.hpp.daftree;

import android.app.Application;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import com.google.firebase.auth.FirebaseAuth;
import com.hpp.daftree.database.Account;
import com.hpp.daftree.database.Currency;
import com.hpp.daftree.models.AccountWithBalance;
import com.hpp.daftree.models.DaftreeRepository;
import com.hpp.daftree.syncmanagers.SyncPreferences;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

public class MainViewModel extends AndroidViewModel {

    private final DaftreeRepository mRepository;
    private final MutableLiveData<String> currentCurrencyName;
    private final MutableLiveData<String> searchQuery = new MutableLiveData<>("");
    private final MutableLiveData<String> accountTypeFirestoreIdFilter = new MutableLiveData<>(null);
    private final MutableLiveData<Boolean> refreshTrigger = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> forceRefresh = new MutableLiveData<>(false);

    private final MediatorLiveData<List<AccountWithBalance>> accounts = new MediatorLiveData<>();
    private LiveData<List<AccountWithBalance>> currentSource = null;

    private final LiveData<List<Currency>> allCurrencies;
    private final Map<String, Integer> currencyNameToIdMap = new HashMap<>();
    private final LiveData<List<String>> currenciesWithTransactions;

    private AtomicBoolean isInitialLoad = new AtomicBoolean(true);

    public MainViewModel(@NonNull Application application) {
        super(application);
        mRepository = new DaftreeRepository(application);
        SyncPreferences syncPreferences = new SyncPreferences(application);

        currentCurrencyName = new MutableLiveData<>(syncPreferences.getLocalCurrency(SyncPreferences.DEFAULT_CURRENCY));
        allCurrencies = mRepository.getAllCurrencies();
        currenciesWithTransactions = mRepository.getCurrenciesWithTransactions();

        setupDataSources();
        updateDataSource();
    }

    private void setupDataSources() {
        // إزالة جميع المصادر القديمة أولاً
        accounts.removeSource(refreshTrigger);
        accounts.removeSource(allCurrencies);
        accounts.removeSource(currentCurrencyName);
        accounts.removeSource(searchQuery);
        accounts.removeSource(accountTypeFirestoreIdFilter);
        accounts.removeSource(forceRefresh);

        // إضافة المصادر مجدداً
        accounts.addSource(refreshTrigger, s -> updateDataSource());
        accounts.addSource(forceRefresh, s -> forceUpdateDataSource());

        accounts.addSource(allCurrencies, currencies -> {
            currencyNameToIdMap.clear();
            if (currencies != null) {
                for (Currency c : currencies) {
                    currencyNameToIdMap.put(c.name, c.id);
                }
            }
            updateDataSource();
        });

        accounts.addSource(currentCurrencyName, s -> updateDataSource());
        accounts.addSource(searchQuery, s -> updateDataSource());
        accounts.addSource(accountTypeFirestoreIdFilter, s -> updateDataSource());
    }

    private void updateDataSource() {
        if (currentSource != null) {
            accounts.removeSource(currentSource);
        }

        String currencyName = currentCurrencyName.getValue();
        String search = searchQuery.getValue();
        String filterId = accountTypeFirestoreIdFilter.getValue();

        Integer currencyId = currencyNameToIdMap.get(currencyName);
        if (currencyId == null) {
            currencyId = 1;
        }

        currentSource = mRepository.getAccountsWithBalances(
                currencyId,
                filterId,
                search != null ? search : ""
        );

        accounts.addSource(currentSource, accounts::setValue);

        if (isInitialLoad.get()) {
            isInitialLoad.set(false);
        }
    }

    /**
     * ✅ تحديث قسري للبيانات - يستخدم بعد المزامنة
     */
    private void forceUpdateDataSource() {
        Log.d("MainViewModel", "بدء التحديث القسري للبيانات");

        if (currentSource != null) {
            accounts.removeSource(currentSource);
        }

        String currencyName = currentCurrencyName.getValue();
        String search = searchQuery.getValue();
        String filterId = accountTypeFirestoreIdFilter.getValue();

        Integer currencyId = currencyNameToIdMap.get(currencyName);
        if (currencyId == null) {
            currencyId = 1;
        }
        // إنشاء مصدر جديد تماماً للبيانات
        currentSource = mRepository.getAccountsWithBalancesForceRefresh(
                currencyId,
                filterId,
                search != null ? search : ""
        );

        accounts.addSource(currentSource, data -> {
            Log.d("MainViewModel", "تم استلام البيانات بعد التحديث القسري، عدد الحسابات: " + (data != null ? data.size() : 0));
            accounts.setValue(data);
        });
    }

    public void refreshData() {
        Log.d("MainViewModel", "تنفيذ refreshData()");
        refreshTrigger.setValue(!Boolean.TRUE.equals(refreshTrigger.getValue()));
    }

    /**
     * ✅ تحديث قسري بعد المزامنة
     */
    public void forceRefreshAfterSync() {
        Log.d("MainViewModel", "تنفيذ forceRefreshAfterSync()");
        forceRefresh.setValue(!Boolean.TRUE.equals(forceRefresh.getValue()));
    }

    // --- Getters and Setters for UI ---
    public LiveData<List<AccountWithBalance>> getAccounts() {
        Log.d("MainViewModel", "استدعاء getAccounts()");
        return accounts;
    }

    public LiveData<String> getCurrency() { return currentCurrencyName; }

    public void setCurrency(String currencyName) {
        Log.d("MainViewModel", "تعيين العملة: " + currencyName);
        this.currentCurrencyName.setValue(currencyName);
    }

    public LiveData<List<String>> getCurrenciesWithTransactions() { return currenciesWithTransactions; }

    public void setFilter(String firestoreId) {
        Log.d("MainViewModel", "تعيين الفلتر: " + firestoreId);
        if (!Objects.equals(this.accountTypeFirestoreIdFilter.getValue(), firestoreId)) {
            this.accountTypeFirestoreIdFilter.setValue(firestoreId);
        }
    }
    public LiveData<String> getFilter() {
        return accountTypeFirestoreIdFilter;
    }
    public void setSearchQuery(String query) {
        this.searchQuery.setValue(query);
    }

    public void deleteAccount(Account account) {
        mRepository.deleteAccount(account);
        refreshData(); // تحديث البيانات بعد الحذف
    }

    public void updateAccount(Account account) {
        mRepository.updateAccount(account);
        refreshData(); // تحديث البيانات بعد التعديل
    }
}