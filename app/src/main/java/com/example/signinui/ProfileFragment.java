package com.example.signinui;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.activity.OnBackPressedCallback;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import com.google.firebase.auth.FirebaseAuth;

public class ProfileFragment extends Fragment {
    private LinearLayout logout;
    private ImageView myRoutesIcon;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_profile, container, false);

        // Initialize the ImageView
        myRoutesIcon = view.findViewById(R.id.my_routes_icon);

        // Set click listener for My Routes icon
        myRoutesIcon.setOnClickListener(v -> {
            Intent intent = new Intent(getActivity(), MyRoutesActivity.class);
            startActivity(intent);
        });

        // logout
        LinearLayout btnLogout = view.findViewById(R.id.logout);
        btnLogout.setOnClickListener(v -> {
            FirebaseAuth.getInstance().signOut();
            startActivity(new Intent(getActivity(), LoginActivity.class));
            getActivity().finish();
        });

        return view;
    }
}