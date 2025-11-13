package com.hpp.daftree;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.hpp.daftree.database.User;
import com.hpp.daftree.models.DaftreeRepository;

public class ProfileViewModel extends AndroidViewModel {
    private final DaftreeRepository repository;
    private final LiveData<User> userProfile;

    public ProfileViewModel(@NonNull Application application) {
        super(application);
        repository = new DaftreeRepository(application);
        userProfile = repository.getUserProfile();
    }

    public LiveData<User> getUserProfile() {
        return userProfile;
    }

    public void updateUserProfile(User user) {
        repository.updateUserProfile(user);
    }
}