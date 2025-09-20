package com.example.signinui;

import android.Manifest;
import android.app.Activity;
import android.content.ContentResolver;

import android.content.Intent;
import android.content.pm.PackageManager;

import android.net.Uri;

import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.MimeTypeMap;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.signinui.model.FeedPost;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class FeedFragment extends Fragment {

    private static final String TAG = "FeedFragment";

    private RecyclerView recyclerView;
    private FeedAdapter adapter;
    private List<FeedPost> postList;
    private ProgressBar progressBar;
    private LinearLayout emptyStateView;

    private ExtendedFloatingActionButton fabNewPost;
    private ImageButton btnAddPhoto;

    // Firebase
    private DatabaseReference databaseReference;
    private StorageReference storageReference;
    private FirebaseAuth mAuth;

    // Location
    private FusedLocationProviderClient fusedLocationClient;
    private double currentLatitude = 0.0;
    private double currentLongitude = 0.0;
    private String currentLocationName = "Unknown Location";

    private Uri imageUri;
    private String currentPhotoPath;

    // Activity Result Launchers
    private final ActivityResultLauncher<Intent> galleryLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    imageUri = result.getData().getData();
                    Log.d(TAG, "Gallery image selected: " + imageUri.toString());
                    getCurrentLocation();
                }
            });

    private final ActivityResultLauncher<Intent> cameraLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK) {
                    Log.d(TAG, "Camera photo taken, URI: " + (imageUri != null ? imageUri.toString() : "null"));
                    if (imageUri != null) {
                        getCurrentLocation();
                    } else {
                        Toast.makeText(getContext(), "Error capturing photo", Toast.LENGTH_SHORT).show();
                    }
                }
            });

    // Location permission launcher
    private final ActivityResultLauncher<String[]> locationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(),
                    permissions -> {
                        Boolean fineLocationGranted = permissions.get(Manifest.permission.ACCESS_FINE_LOCATION);
                        Boolean coarseLocationGranted = permissions.get(Manifest.permission.ACCESS_COARSE_LOCATION);

                        if ((fineLocationGranted != null && fineLocationGranted) ||
                                (coarseLocationGranted != null && coarseLocationGranted)) {
                            getCurrentLocation();
                        } else {
                            Toast.makeText(getContext(), "Location permission denied", Toast.LENGTH_SHORT).show();
                            showCreatePostDialog();
                        }
                    });

    // Camera permission launcher
    private final ActivityResultLauncher<String> cameraPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(),
                    isGranted -> {
                        if (isGranted) {
                            Log.d(TAG, "Camera permission granted");
                            dispatchTakePictureIntent();
                        } else {
                            Toast.makeText(getContext(), "Camera permission is required to take photos", Toast.LENGTH_SHORT).show();
                        }
                    });

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_feed, container, false);

        // Initialize Firebase
        mAuth = FirebaseAuth.getInstance();
        databaseReference = FirebaseDatabase.getInstance().getReference("posts");
        storageReference = FirebaseStorage.getInstance().getReference("post_images");

        // Initialize Location
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity());

        // Initialize Views
        recyclerView = view.findViewById(R.id.recycler_feed);
        fabNewPost = view.findViewById(R.id.fab_new_post);
        btnAddPhoto = view.findViewById(R.id.btn_add_photo);
        progressBar = view.findViewById(R.id.progress_bar);
        emptyStateView = view.findViewById(R.id.empty_state_view);

        // Setup RecyclerView
        postList = new ArrayList<>();
        adapter = new FeedAdapter(postList, getContext());
        LinearLayoutManager layoutManager = new LinearLayoutManager(getContext());
        layoutManager.setReverseLayout(true);
        layoutManager.setStackFromEnd(true);
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setAdapter(adapter);

        // Show loading initially
        showLoading(true);

        // Fetch posts from Firebase
        fetchPosts();

        // Set up click listeners
        View.OnClickListener addPostClickListener = v -> selectImage();

        fabNewPost.setOnClickListener(addPostClickListener);
        if (btnAddPhoto != null) {
            btnAddPhoto.setOnClickListener(addPostClickListener);
        }

        return view;
    }

    private void showLoading(boolean isLoading) {
        if (progressBar != null) {
            progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        }
        if (recyclerView != null) {
            recyclerView.setVisibility(isLoading ? View.GONE : View.VISIBLE);
        }
        if (emptyStateView != null) {
            emptyStateView.setVisibility(View.GONE);
        }
    }

    private void showEmptyState() {
        if (progressBar != null) {
            progressBar.setVisibility(View.GONE);
        }
        if (recyclerView != null) {
            recyclerView.setVisibility(View.GONE);
        }
        if (emptyStateView != null) {
            emptyStateView.setVisibility(View.VISIBLE);
        }
    }

    private void showContent() {
        if (progressBar != null) {
            progressBar.setVisibility(View.GONE);
        }
        if (recyclerView != null) {
            recyclerView.setVisibility(View.VISIBLE);
        }
        if (emptyStateView != null) {
            emptyStateView.setVisibility(View.GONE);
        }
    }

    private void dispatchTakePictureIntent() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);

        // Ensure that there's a camera activity to handle the intent
        if (takePictureIntent.resolveActivity(requireContext().getPackageManager()) != null) {
            // Create the File where the photo should go
            File photoFile = null;
            try {
                photoFile = createImageFile();
                Log.d(TAG, "Created image file: " + photoFile.getAbsolutePath());
            } catch (IOException ex) {
                Log.e(TAG, "Error occurred while creating the File", ex);
                Toast.makeText(getContext(), "Error creating image file", Toast.LENGTH_SHORT).show();
                return;
            }

            // Continue only if the File was successfully created
            if (photoFile != null) {
                try {
                    imageUri = FileProvider.getUriForFile(requireContext(),
                            requireContext().getPackageName() + ".provider",
                            photoFile);
                    Log.d(TAG, "FileProvider URI created: " + imageUri.toString());

                    takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, imageUri);
                    takePictureIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    takePictureIntent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

                    cameraLauncher.launch(takePictureIntent);
                } catch (IllegalArgumentException e) {
                    Log.e(TAG, "FileProvider error: ", e);
                    Toast.makeText(getContext(),
                            "Error setting up camera. Please check app configuration.",
                            Toast.LENGTH_LONG).show();
                }
            }
        } else {
            Toast.makeText(getContext(), "No camera app found", Toast.LENGTH_SHORT).show();
        }
    }

    private File createImageFile() throws IOException {
        // Create an image file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";

        // Get the app's external files directory for pictures
        File storageDir = requireContext().getExternalFilesDir(Environment.DIRECTORY_PICTURES);

        // Make sure the Pictures directory exists
        if (storageDir != null && !storageDir.exists()) {
            boolean created = storageDir.mkdirs();
            Log.d(TAG, "Pictures directory created: " + created);
        }

        File image = File.createTempFile(
                imageFileName,  /* prefix */
                ".jpg",         /* suffix */
                storageDir      /* directory */
        );

        // Save a file path for use with ACTION_VIEW intents
        currentPhotoPath = image.getAbsolutePath();
        Log.d(TAG, "Image file path: " + currentPhotoPath);
        return image;
    }

    private void getCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION)
                        != PackageManager.PERMISSION_GRANTED) {

            locationPermissionLauncher.launch(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            });
            return;
        }

        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(requireActivity(), location -> {
                    if (location != null) {
                        currentLatitude = location.getLatitude();
                        currentLongitude = location.getLongitude();
                        currentLocationName = "Custom Location";
                        Log.d(TAG, "Location obtained: " + currentLatitude + ", " + currentLongitude);
                    }
                    showCreatePostDialog();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to get location", e);
                    Toast.makeText(getContext(), "Using default location", Toast.LENGTH_SHORT).show();
                    showCreatePostDialog();
                });
    }

    private void selectImage() {
        final CharSequence[] options = {"Take Photo", "Choose from Gallery", "Cancel"};
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle("Add Photo!");
        builder.setItems(options, (dialog, item) -> {
            if (options[item].equals("Take Photo")) {
                // Check camera permission before launching camera
                if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
                        != PackageManager.PERMISSION_GRANTED) {
                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
                } else {
                    dispatchTakePictureIntent();
                }
            } else if (options[item].equals("Choose from Gallery")) {
                Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                galleryLauncher.launch(intent);
            } else if (options[item].equals("Cancel")) {
                dialog.dismiss();
            }
        });
        builder.show();
    }

    private void showCreatePostDialog() {
        if (imageUri == null) {
            Log.e(TAG, "No image URI available for post creation");
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        LayoutInflater inflater = requireActivity().getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_add_post, null);

        final ImageView imagePreview = dialogView.findViewById(R.id.image_preview);
        final EditText editLocation = dialogView.findViewById(R.id.edit_location);
        final MaterialAutoCompleteTextView autoCompleteActivity = dialogView.findViewById(R.id.auto_complete_activity);
        final EditText editCaption = dialogView.findViewById(R.id.edit_post_caption);
        final ImageButton btnClose = dialogView.findViewById(R.id.btn_close);
        final LinearLayout btnChangePhoto = dialogView.findViewById(R.id.btn_change_photo);
        final MaterialButton btnCancel = dialogView.findViewById(R.id.btn_cancel);
        final MaterialButton btnPost = dialogView.findViewById(R.id.btn_post);

        // Set image preview
        try {
            imagePreview.setImageURI(imageUri);
        } catch (Exception e) {
            Log.e(TAG, "Error loading image preview", e);
            Toast.makeText(getContext(), "Error loading image", Toast.LENGTH_SHORT).show();
            return;
        }

        editLocation.setText(currentLocationName);

        // Setup activity type dropdown
        List<String> activityTypes = Arrays.asList("Hiking", "Cycling", "Running", "Walking", "Swimming", "Gym", "Yoga", "Other");
        ArrayAdapter<String> activityAdapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_dropdown_item_1line,
                activityTypes
        );
        autoCompleteActivity.setAdapter(activityAdapter);
        autoCompleteActivity.setText("Hiking", false);

        // Create the dialog
        AlertDialog dialog = builder.setView(dialogView).create();

        // Set up click listeners
        btnClose.setOnClickListener(v -> dialog.dismiss());

        btnChangePhoto.setOnClickListener(v -> {
            dialog.dismiss();
            selectImage();
        });

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnPost.setOnClickListener(v -> {
            String location = editLocation.getText().toString().trim();
            String activityType = autoCompleteActivity.getText().toString().trim();
            String caption = editCaption.getText().toString().trim();

            if (!location.isEmpty() && !activityType.isEmpty() && !caption.isEmpty()) {
                uploadPost(location, activityType, caption);
                dialog.dismiss();
            } else {
                Toast.makeText(getContext(), "Please fill all fields", Toast.LENGTH_SHORT).show();
            }
        });

        dialog.show();
    }

    private String getFileExtension(Uri uri) {
        try {
            ContentResolver cR = requireContext().getContentResolver();
            MimeTypeMap mime = MimeTypeMap.getSingleton();
            String extension = mime.getExtensionFromMimeType(cR.getType(uri));
            return extension != null ? extension : "jpg"; // default to jpg if extension can't be determined
        } catch (Exception e) {
            Log.e(TAG, "Error getting file extension", e);
            return "jpg"; // default fallback
        }
    }

    private void uploadPost(final String location, final String activityType, final String caption) {
        if (imageUri != null) {
            Toast.makeText(getContext(), "Uploading post...", Toast.LENGTH_SHORT).show();

            final StorageReference fileReference = storageReference.child(System.currentTimeMillis()
                    + "." + getFileExtension(imageUri));

            fileReference.putFile(imageUri)
                    .addOnSuccessListener(taskSnapshot -> {
                        Log.d(TAG, "Image uploaded successfully");
                        fileReference.getDownloadUrl().addOnSuccessListener(uri -> {
                            Log.d(TAG, "Download URL obtained: " + uri.toString());

                            FirebaseUser currentUser = mAuth.getCurrentUser();
                            if (currentUser != null) {
                                String userId = currentUser.getUid();
                                String username = currentUser.getEmail() != null ?
                                        currentUser.getEmail().split("@")[0] : "Anonymous";

                                String postId = databaseReference.push().getKey();
                                long timestamp = System.currentTimeMillis();

                                FeedPost newPost = new FeedPost(
                                        postId,
                                        userId,
                                        username,
                                        location,
                                        currentLatitude,
                                        currentLongitude,
                                        timestamp,
                                        uri.toString(),
                                        activityType,
                                        0, 0,
                                        caption
                                );

                                if (postId != null) {
                                    databaseReference.child(postId).setValue(newPost)
                                            .addOnSuccessListener(aVoid -> {
                                                Toast.makeText(getContext(), "Post shared successfully!", Toast.LENGTH_SHORT).show();
                                                Log.d(TAG, "Post saved to database");
                                            })
                                            .addOnFailureListener(e -> {
                                                Log.e(TAG, "Failed to save post to database", e);
                                                Toast.makeText(getContext(), "Failed to save post", Toast.LENGTH_SHORT).show();
                                            });
                                }
                            }
                        }).addOnFailureListener(e -> {
                            Log.e(TAG, "Failed to get download URL", e);
                            Toast.makeText(getContext(), "Failed to get image URL", Toast.LENGTH_SHORT).show();
                        });
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Image upload failed", e);
                        Toast.makeText(getContext(), "Upload failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
        } else {
            Toast.makeText(getContext(), "No image selected", Toast.LENGTH_SHORT).show();
        }
    }

    private void fetchPosts() {
        databaseReference.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                postList.clear();
                for (DataSnapshot postSnapshot : dataSnapshot.getChildren()) {
                    FeedPost post = postSnapshot.getValue(FeedPost.class);
                    if (post != null) {
                        postList.add(post);
                    }
                }

                if (postList.isEmpty()) {
                    showEmptyState();
                } else {
                    Collections.sort(postList, (p1, p2) -> Long.compare(p2.getTimestamp(), p1.getTimestamp()));
                    showContent();
                }

                adapter.notifyDataSetChanged();
                Log.d(TAG, "Posts loaded: " + postList.size());
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.e(TAG, "Database error: " + databaseError.getMessage());
                showEmptyState();
                Toast.makeText(getContext(), "Failed to load posts", Toast.LENGTH_SHORT).show();
            }
        });
    }
}