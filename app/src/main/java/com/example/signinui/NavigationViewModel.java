package com.example.signinui;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

public class NavigationViewModel extends ViewModel {

    private final MutableLiveData<Boolean> isNavigating = new MutableLiveData<>(false);

    public LiveData<Boolean> getIsNavigating() {
        return isNavigating;
    }

    public void setNavigating(boolean navigating) {
        isNavigating.setValue(navigating);
    }
}