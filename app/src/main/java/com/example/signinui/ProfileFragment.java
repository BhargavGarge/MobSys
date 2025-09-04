package com.example.signinui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;

import java.text.NumberFormat;
import java.util.Locale;

public class ProfileFragment extends Fragment {

    private static final String STEP_PREFS = "StepPreferences";
    private static final String TOTAL_STEPS_KEY = "total_steps";
    private static final String CURRENT_LEVEL_KEY = "current_level";
    private static final String LEVEL_THRESHOLDS_KEY = "level_thresholds";

    // Views
    private ProgressBar stepProgressBar;
    private TextView currentStepsText, targetStepsText, stepsToNextLevelText, levelText;
    private TextView stepsTodayText, totalStepsText, daysActiveText;
    private LinearLayout myRoutesLayout, logoutLayout, helpLayout;

    public ProfileFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_profile, container, false);

        // Initialize views
        stepProgressBar = view.findViewById(R.id.step_progress_bar);
        currentStepsText = view.findViewById(R.id.current_steps);
        targetStepsText = view.findViewById(R.id.target_steps);
        stepsToNextLevelText = view.findViewById(R.id.steps_to_next_level);
        levelText = view.findViewById(R.id.level_text);
        stepsTodayText = view.findViewById(R.id.steps_today);
        totalStepsText = view.findViewById(R.id.total_steps);
        daysActiveText = view.findViewById(R.id.days_active);

        myRoutesLayout = view.findViewById(R.id.notifications_item);
        logoutLayout = view.findViewById(R.id.logout);
        helpLayout = view.findViewById(R.id.help_item);

        // Set up navigation click listeners
        myRoutesLayout.setOnClickListener(v -> {
            Intent intent = new Intent(getActivity(), MyRoutesActivity.class);
            startActivity(intent);
        });

        logoutLayout.setOnClickListener(v -> {
            FirebaseAuth.getInstance().signOut();
            Intent intent = new Intent(getActivity(), LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            if (getActivity() != null) {
                getActivity().finish();
            }
        });

        helpLayout.setOnClickListener(v -> {
            showHelpDialog();
        });

        return view;
    }

    /**
     * **UPDATED**: This method now inflates the custom dialog_help_center.xml layout.
     */
    private void showHelpDialog() {
        // Create a builder for the alert dialog
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());

        // Inflate the custom layout
        LayoutInflater inflater = requireActivity().getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_help_center, null);

        // Set the custom view to the builder
        builder.setView(dialogView);

        // Create the dialog
        final AlertDialog dialog = builder.create();

        // Find the buttons inside the custom layout
        MaterialButton btnGotIt = dialogView.findViewById(R.id.btn_got_it);
        MaterialButton btnEmailSupport = dialogView.findViewById(R.id.btn_email_support);

        // Set click listener for the "Got it" button
        btnGotIt.setOnClickListener(v -> dialog.dismiss());

        // Set click listener for the "Email Us" button to open an email client
        btnEmailSupport.setOnClickListener(v -> {
            Intent emailIntent = new Intent(Intent.ACTION_SENDTO);
            emailIntent.setData(Uri.parse("mailto:TrailHead@gmail.com"));
            emailIntent.putExtra(Intent.EXTRA_SUBJECT, "TrailHead App Support");
            startActivity(Intent.createChooser(emailIntent, "Send email via..."));
            dialog.dismiss();
        });

        // Set the background of the dialog window to transparent to show the custom layout's rounded corners
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        // Show the dialog
        dialog.show();
    }

    @Override
    public void onResume() {
        super.onResume();
        loadStepData();
    }

    private void loadStepData() {
        if (getContext() == null) return;

        SharedPreferences prefs = getContext().getSharedPreferences(STEP_PREFS, Context.MODE_PRIVATE);

        int totalSteps = prefs.getInt(TOTAL_STEPS_KEY, 0);
        int currentLevel = prefs.getInt(CURRENT_LEVEL_KEY, 1);

        String thresholdsStr = prefs.getString(LEVEL_THRESHOLDS_KEY, "");
        if (!thresholdsStr.isEmpty()) {
            String[] thresholdsArray = thresholdsStr.split(",");
            int[] thresholds = new int[thresholdsArray.length];
            for (int i = 0; i < thresholdsArray.length; i++) {
                try {
                    thresholds[i] = Integer.parseInt(thresholdsArray[i]);
                } catch (NumberFormatException e) {
                    return;
                }
            }

            if (currentLevel < 1 || currentLevel > thresholds.length) {
                currentLevel = 1;
            }

            int currentLevelThreshold = thresholds[currentLevel - 1];
            int nextLevelThreshold = (currentLevel < thresholds.length) ? thresholds[currentLevel] : currentLevelThreshold;

            int stepsInCurrentLevel = totalSteps - currentLevelThreshold;
            int stepsForNextLevel = nextLevelThreshold - currentLevelThreshold;
            int stepsRemaining = Math.max(0, nextLevelThreshold - totalSteps);
            int progress = (stepsForNextLevel > 0) ? (int) ((stepsInCurrentLevel * 100.0f) / stepsForNextLevel) : 100;

            NumberFormat numberFormat = NumberFormat.getNumberInstance(Locale.US);

            levelText.setText("Level " + currentLevel);
            currentStepsText.setText(numberFormat.format(totalSteps));
            targetStepsText.setText(numberFormat.format(nextLevelThreshold));
            stepsToNextLevelText.setText(numberFormat.format(stepsRemaining) + " steps");

            stepProgressBar.setProgress(Math.min(100, progress));

            totalStepsText.setText(numberFormat.format(totalSteps));

            stepsTodayText.setText(numberFormat.format(calculateStepsToday(totalSteps)));
            daysActiveText.setText(String.valueOf(calculateDaysActive(totalSteps)));
        }
    }

    private int calculateStepsToday(int totalSteps) {
        return (int) (totalSteps * (new java.util.Random().nextFloat() * 0.05));
    }

    private int calculateDaysActive(int totalSteps) {
        if (totalSteps < 100) return 1;
        return Math.max(1, totalSteps / 5000);
    }
}