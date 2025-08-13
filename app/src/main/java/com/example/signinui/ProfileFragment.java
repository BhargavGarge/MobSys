package com.example.signinui;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.activity.OnBackPressedCallback;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import com.google.firebase.auth.FirebaseAuth;

public class ProfileFragment extends Fragment {
    private LinearLayout logout;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_profile, container, false);
        // Handle back button press
//        view.findViewById(R.id.btnBack).setOnClickListener(v -> {
//            handleBackPressed();
//        });
//
//        // Handle system back button press
//        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(),
//                new OnBackPressedCallback(true) {
//                    @Override
//                    public void handleOnBackPressed() {
//                        handleBackPressed();
//                    }
//                });
        //logout
        LinearLayout btnLogout = view.findViewById(R.id.logout); // Note: using logout_item not btnLogout
        btnLogout.setOnClickListener(v -> {
            FirebaseAuth.getInstance().signOut();
            startActivity(new Intent(getActivity(), LoginActivity.class));
            getActivity().finish();
        });

        return view;
    }
//    private void handleBackPressed() {
//        Navigation.findNavController(requireView()).navigate(R.id.nav_home);
//    }
}