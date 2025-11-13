package com.hpp.daftree.ui;

// CurrencyViewModel.java

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.hpp.daftree.database.Currency;
import com.hpp.daftree.models.DaftreeRepository;

import java.util.List;

public class CurrencyViewModel extends AndroidViewModel {
    private final DaftreeRepository repository;
    private final LiveData<List<Currency>> allCurrencies;
//    private final LiveData<String> firstCurrency;
    public CurrencyViewModel(@NonNull Application application) {
        super(application);
        repository = new DaftreeRepository(application);
        allCurrencies = repository.getAllCurrencies();
    }

    public LiveData<List<Currency>> getAllCurrencies() {
        return allCurrencies;
    }

    public void insert(Currency currency) {
        repository.insertCurrency(currency); // سنضيف هذه الدالة في Repository
    }
    public String getCurrencyFirestoreId(int currencyId ) {
        return repository.getCurrencyFirestoreId(currencyId);
    }
}
