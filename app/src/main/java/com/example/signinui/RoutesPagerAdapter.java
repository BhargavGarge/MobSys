package com.example.signinui;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

public class RoutesPagerAdapter extends FragmentStateAdapter {

    public RoutesPagerAdapter(@NonNull FragmentActivity fragmentActivity) {
        super(fragmentActivity);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        if (position == 0) {
            return new SavedRoutesFragment();  // Tab 0 → Saved
        } else {
            return new LikedRoutesFragment();  // Tab 1 → Liked
        }
    }

    @Override
    public int getItemCount() {
        return 2; // number of tabs
    }
}