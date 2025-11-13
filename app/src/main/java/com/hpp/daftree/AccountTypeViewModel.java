package com.hpp.daftree;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.hpp.daftree.database.AccountType;
import com.hpp.daftree.models.DaftreeRepository;

import java.util.List;

public class AccountTypeViewModel extends AndroidViewModel {
    private final DaftreeRepository repository;
    private final LiveData<List<AccountType>> allAccountTypes;

    public AccountTypeViewModel(@NonNull Application application) {
        super(application);
        repository = new DaftreeRepository(application);
        allAccountTypes = repository.getAllAccountTypes(); // سنضيف هذه الدالة في Repository
    }

    public LiveData<List<AccountType>> getAllAccountTypes() {
        return allAccountTypes;
    }

    public void insert(AccountType accountType) {
        repository.insertAccountType(accountType); // سنضيف هذه الدالة في Repository
    }
}