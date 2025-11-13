package com.hpp.daftree;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.hpp.daftree.database.Account;
import com.hpp.daftree.database.AccountType;
import com.hpp.daftree.database.Currency;
import com.hpp.daftree.database.User;
import com.hpp.daftree.models.DaftreeRepository;

import java.util.List;

public class ReportsViewModel extends AndroidViewModel {
    private final DaftreeRepository repository;
    public ReportsViewModel(@NonNull Application application) {
        super(application);
        repository = new DaftreeRepository(application);
    }

    public LiveData<User> getUserProfile() { return repository.getUserProfile(); }
    public LiveData<List<AccountType>> getAllAccountTypes() { return repository.getAllAccountTypes(); }
    public LiveData<List<Currency>> getAllCurrencies() { return repository.getAllCurrencies(); }
    public LiveData<List<Account>> getAllAccounts() { return repository.getAllAccounts(); }

}