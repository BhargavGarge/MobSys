package com.example.signinui;

import android.Manifest;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.MimeTypeMap;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.signinui.model.FeedPost;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class FeedFragment extends Fragment {

    private RecyclerView recyclerView;
    private FeedAdapter adapter;
    private List<FeedPost> postList;

    private FloatingActionButton fabNewPost;
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

    // Activity Result Launchers
    private final ActivityResultLauncher<Intent> galleryLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    imageUri = result.getData().getData();
                    getCurrentLocation();
                }
            });

    private final ActivityResultLauncher<Intent> cameraLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    imageUri = result.getData().getData();
                    if (imageUri == null) {
                        Bundle extras = result.getData().getExtras();
                        if (extras != null && extras.containsKey("data")) {
                            Toast.makeText(getContext(), "Please use gallery for better quality", Toast.LENGTH_SHORT).show();
                            return;
                        }
                    }
                    getCurrentLocation();
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

        // Setup RecyclerView
        postList = new ArrayList<>();
        adapter = new FeedAdapter(postList, getContext());
        LinearLayoutManager layoutManager = new LinearLayoutManager(getContext());
        layoutManager.setReverseLayout(true);
        layoutManager.setStackFromEnd(true);
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setAdapter(adapter);

        // Fetch posts from Firebase
        fetchPosts();

        // Set up click listeners
        View.OnClickListener addPostClickListener = v -> selectImage();

        fabNewPost.setOnClickListener(addPostClickListener);
        btnAddPhoto.setOnClickListener(addPostClickListener);

        return view;
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
                        // We'll just get coordinates but not the location name
                        currentLocationName = "Custom Location"; // Default name
                    }
                    showCreatePostDialog();
                })
                .addOnFailureListener(e -> {
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
                Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                cameraLauncher.launch(takePictureIntent);
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
        if (imageUri == null) return;

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

        imagePreview.setImageURI(imageUri);
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

        // Set up click listeners for custom buttons
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
        ContentResolver cR = requireContext().getContentResolver();
        MimeTypeMap mime = MimeTypeMap.getSingleton();
        return mime.getExtensionFromMimeType(cR.getType(uri));
    }

    private void uploadPost(final String location, final String activityType, final String caption) {
        if (imageUri != null) {
            Toast.makeText(getContext(), "Uploading...", Toast.LENGTH_LONG).show();
            final StorageReference fileReference = storageReference.child(System.currentTimeMillis()
                    + "." + getFileExtension(imageUri));

            fileReference.putFile(imageUri)
                    .addOnSuccessListener(taskSnapshot -> fileReference.getDownloadUrl().addOnSuccessListener(uri -> {
                        Toast.makeText(getContext(), "Post Uploaded!", Toast.LENGTH_SHORT).show();

                        FirebaseUser currentUser = mAuth.getCurrentUser();
                        if (currentUser != null) {
                            String userId = currentUser.getUid();
                            String username = currentUser.getEmail() != null ? currentUser.getEmail().split("@")[0] : "Anonymous";

                            String postId = databaseReference.push().getKey();
                            long timestamp = System.currentTimeMillis();

                            FeedPost newPost = new FeedPost(
                                    postId,
                                    userId,
                                    username,
                                    location, // Use the manually entered location
                                    currentLatitude,
                                    currentLongitude,
                                    timestamp,
                                    uri.toString(),
                                    activityType, // Use the selected activity type
                                    0, 0,
                                    caption
                            );

                            if (postId != null) {
                                databaseReference.child(postId).setValue(newPost);
                            }
                        }
                    }))
                    .addOnFailureListener(e -> Toast.makeText(getContext(), e.getMessage(), Toast.LENGTH_SHORT).show());
        } else {
            Toast.makeText(getContext(), "No file selected", Toast.LENGTH_SHORT).show();
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
                Collections.sort(postList, (p1, p2) -> Long.compare(p2.getTimestamp(), p1.getTimestamp()));
                adapter.notifyDataSetChanged();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Toast.makeText(getContext(), databaseError.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }
}