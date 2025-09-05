package com.example.signinui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.text.NumberFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class ProfileFragment extends Fragment {

    private static final String STEP_PREFS = "StepPreferences";
    private static final String TOTAL_STEPS_KEY = "total_steps";
    private static final String CURRENT_LEVEL_KEY = "current_level";
    private static final String LEVEL_THRESHOLDS_KEY = "level_thresholds";

    // Constants for image picking
    private static final int REQUEST_IMAGE_CAPTURE = 1;
    private static final int REQUEST_IMAGE_PICK = 2;

    // Views
    private ProgressBar stepProgressBar;
    private TextView currentStepsText, targetStepsText, stepsToNextLevelText, levelText;
    private TextView stepsTodayText, totalStepsText, daysActiveText, userName, userEmail;
    private LinearLayout myRoutesLayout, logoutLayout, helpLayout, personalInfoLayout;
    private ImageView profileImage;
    private ImageView cameraIcon;
    private ImageButton editProfileBtn;

    // Firebase
    private FirebaseAuth mAuth;
    private DatabaseReference mDatabase;
    private String userId;
    private Uri profileImageUri;

    public ProfileFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_profile, container, false);

        // Initialize Firebase
        mAuth = FirebaseAuth.getInstance();
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            userId = currentUser.getUid();
            mDatabase = FirebaseDatabase.getInstance().getReference().child("users").child(userId);
        }

        // Initialize views
        stepProgressBar = view.findViewById(R.id.step_progress_bar);
        currentStepsText = view.findViewById(R.id.current_steps);
        targetStepsText = view.findViewById(R.id.target_steps);
        stepsToNextLevelText = view.findViewById(R.id.steps_to_next_level);
        levelText = view.findViewById(R.id.level_text);
        stepsTodayText = view.findViewById(R.id.steps_today);
        totalStepsText = view.findViewById(R.id.total_steps);
        daysActiveText = view.findViewById(R.id.days_active);
        userName = view.findViewById(R.id.user_name);
        userEmail = view.findViewById(R.id.user_email);
        profileImage = view.findViewById(R.id.profile_image);
        cameraIcon = view.findViewById(R.id.camera_icon);
        editProfileBtn = view.findViewById(R.id.edit_profile_btn);

        myRoutesLayout = view.findViewById(R.id.notifications_item);
        logoutLayout = view.findViewById(R.id.logout);
        helpLayout = view.findViewById(R.id.help_item);
        personalInfoLayout = view.findViewById(R.id.personal_info_item);

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

        // FIXED: Set up correct click listeners
        // Personal Info opens the Activity
        personalInfoLayout.setOnClickListener(v -> {
            Intent intent = new Intent(getActivity(), PersonalInfoActivity.class);
            startActivity(intent);
        });

        // Edit button opens the dialog
        editProfileBtn.setOnClickListener(v -> showEditProfileDialog());

        // Camera icon opens image picker
        cameraIcon.setOnClickListener(v -> showImagePickerOptions());

        // Load user data from Firebase
        loadUserDataFromFirebase();

        return view;
    }

    private void loadUserDataFromFirebase() {
        if (mDatabase != null) {
            mDatabase.addValueEventListener(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                    if (dataSnapshot.exists()) {
                        // Load user data
                        String name = dataSnapshot.child("name").getValue(String.class);
                        String email = dataSnapshot.child("email").getValue(String.class);
                        String phone = dataSnapshot.child("phone").getValue(String.class);
                        String profileImage = dataSnapshot.child("profileImage").getValue(String.class);

                        // Update UI
                        if (name != null) userName.setText(name);
                        if (email != null) userEmail.setText(email);

                        // Load profile image if exists
                        if (profileImage != null && !profileImage.isEmpty()) {
                            byte[] imageBytes = Base64.decode(profileImage, Base64.DEFAULT);
                            Bitmap bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
                            ProfileFragment.this.profileImage.setImageBitmap(bitmap);
                        }
                    } else {
                        // Set default values if no data exists
                        FirebaseUser user = mAuth.getCurrentUser();
                        if (user != null) {
                            userName.setText(user.getDisplayName() != null ? user.getDisplayName() : "User Name");
                            userEmail.setText(user.getEmail() != null ? user.getEmail() : "user@email.com");
                        }
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError databaseError) {
                    Toast.makeText(getContext(), "Failed to load user data: " + databaseError.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    private void showEditProfileDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        LayoutInflater inflater = requireActivity().getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_edit_profile, null);
        builder.setView(dialogView);

        AlertDialog dialog = builder.create();

        // Initialize dialog views
        ImageView dialogProfileImage = dialogView.findViewById(R.id.dialog_profile_image);
        ImageButton dialogCameraIcon = dialogView.findViewById(R.id.dialog_camera_icon);
        TextInputEditText etName = dialogView.findViewById(R.id.et_name);
        TextInputEditText etEmail = dialogView.findViewById(R.id.et_email);
        TextInputEditText etPhone = dialogView.findViewById(R.id.et_phone);
        ImageButton btnClose = dialogView.findViewById(R.id.btn_close);
        MaterialButton btnCancel = dialogView.findViewById(R.id.btn_cancel);
        MaterialButton btnSave = dialogView.findViewById(R.id.btn_save);

        // Load current data from Firebase
        if (mDatabase != null) {
            mDatabase.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                    if (dataSnapshot.exists()) {
                        String name = dataSnapshot.child("name").getValue(String.class);
                        String email = dataSnapshot.child("email").getValue(String.class);
                        String phone = dataSnapshot.child("phone").getValue(String.class);
                        String profileImage = dataSnapshot.child("profileImage").getValue(String.class);

                        etName.setText(name != null ? name : "");
                        etEmail.setText(email != null ? email : "");
                        etPhone.setText(phone != null ? phone : "");

                        // Load profile image if exists
                        if (profileImage != null && !profileImage.isEmpty()) {
                            byte[] imageBytes = Base64.decode(profileImage, Base64.DEFAULT);
                            Bitmap bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
                            dialogProfileImage.setImageBitmap(bitmap);
                        }
                    } else {
                        // Set default values from Firebase user
                        FirebaseUser user = mAuth.getCurrentUser();
                        if (user != null) {
                            etName.setText(user.getDisplayName() != null ? user.getDisplayName() : "");
                            etEmail.setText(user.getEmail() != null ? user.getEmail() : "");
                        }
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError databaseError) {
                    Toast.makeText(getContext(), "Failed to load user data: " + databaseError.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });
        }

        // Set click listeners
        dialogCameraIcon.setOnClickListener(v -> showImagePickerOptions());
        btnClose.setOnClickListener(v -> dialog.dismiss());
        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnSave.setOnClickListener(v -> {
            String name = etName.getText().toString().trim();
            String email = etEmail.getText().toString().trim();
            String phone = etPhone.getText().toString().trim();

            if (name.isEmpty()) {
                etName.setError("Name is required");
                return;
            }

            if (email.isEmpty()) {
                etEmail.setError("Email is required");
                return;
            }

            // Save data to Firebase
            saveProfileDataToFirebase(name, email, phone);

            dialog.dismiss();
        });

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        dialog.show();
    }

    private void saveProfileDataToFirebase(String name, String email, String phone) {
        if (mDatabase != null) {
            Map<String, Object> userData = new HashMap<>();
            userData.put("name", name);
            userData.put("email", email);
            userData.put("phone", phone);

            // If we have a profile image, convert it to base64 and save it
            if (profileImageUri != null) {
                try {
                    Bitmap bitmap = MediaStore.Images.Media.getBitmap(requireActivity().getContentResolver(), profileImageUri);
                    String imageString = convertBitmapToBase64(bitmap);
                    userData.put("profileImage", imageString);
                } catch (IOException e) {
                    e.printStackTrace();
                    Toast.makeText(getContext(), "Failed to process image", Toast.LENGTH_SHORT).show();
                }
            }

            mDatabase.setValue(userData)
                    .addOnSuccessListener(aVoid -> {
                        Toast.makeText(getContext(), "Profile updated successfully", Toast.LENGTH_SHORT).show();
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(getContext(), "Failed to update profile: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
        }
    }

    private String convertBitmapToBase64(Bitmap bitmap) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, baos);
        byte[] imageBytes = baos.toByteArray();
        return Base64.encodeToString(imageBytes, Base64.DEFAULT);
    }

    private void showImagePickerOptions() {
        String[] options = {"Take Photo", "Choose from Gallery", "Cancel"};

        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle("Choose Profile Picture");
        builder.setItems(options, (dialog, which) -> {
            if (which == 0) {
                takePhoto();
            } else if (which == 1) {
                chooseFromGallery();
            } else {
                dialog.dismiss();
            }
        });
        builder.show();
    }

    private void takePhoto() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(requireActivity().getPackageManager()) != null) {
            startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
        }
    }

    private void chooseFromGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        intent.setType("image/*");
        startActivityForResult(Intent.createChooser(intent, "Select Picture"), REQUEST_IMAGE_PICK);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == requireActivity().RESULT_OK) {
            if (requestCode == REQUEST_IMAGE_CAPTURE && data != null) {
                Bundle extras = data.getExtras();
                if (extras != null) {
                    Bitmap imageBitmap = (Bitmap) extras.get("data");
                    profileImage.setImageBitmap(imageBitmap);

                    // Convert to URI and save to Firebase
                    String imageString = convertBitmapToBase64(imageBitmap);
                    saveImageToFirebase(imageString);
                }
            } else if (requestCode == REQUEST_IMAGE_PICK && data != null) {
                profileImageUri = data.getData();
                try {
                    Bitmap bitmap = MediaStore.Images.Media.getBitmap(requireActivity().getContentResolver(), profileImageUri);
                    profileImage.setImageBitmap(bitmap);

                    // Convert to base64 and save to Firebase
                    String imageString = convertBitmapToBase64(bitmap);
                    saveImageToFirebase(imageString);
                } catch (IOException e) {
                    e.printStackTrace();
                    Toast.makeText(getContext(), "Failed to load image", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    private void saveImageToFirebase(String imageString) {
        if (mDatabase != null) {
            mDatabase.child("profileImage").setValue(imageString)
                    .addOnSuccessListener(aVoid -> {
                        Toast.makeText(getContext(), "Profile picture updated", Toast.LENGTH_SHORT).show();
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(getContext(), "Failed to update profile picture: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
        }
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