package com.example.signinui;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.signinui.model.TrailDetails;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SavedRoutesFragment extends Fragment {

    private RecyclerView recyclerView;
    private RoutesAdapter adapter;
    private final Gson gson = new Gson();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_routes_list, container, false);

        recyclerView = view.findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        loadSavedRoutes();

        return view;
    }

    private void loadSavedRoutes() {
        SharedPreferences prefs = requireContext().getSharedPreferences("saved_trails", Context.MODE_PRIVATE);
        List<TrailDetails> trails = new ArrayList<>();

        Map<String, ?> allEntries = prefs.getAll();
        for (Map.Entry<String, ?> entry : allEntries.entrySet()) {
            // --- FIX STARTS HERE ---
            // Check if the value is actually a String before trying to use it.
            if (entry.getValue() instanceof String) {
                String json = (String) entry.getValue();
                TrailDetails trail = gson.fromJson(json, TrailDetails.class);
                trails.add(trail);
            }
            // This will now safely ignore any non-String entries.
            // --- FIX ENDS HERE ---
        }

        adapter = new RoutesAdapter(trails);
        recyclerView.setAdapter(adapter);
    }
}