// FeedAdapter.java
package com.example.signinui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.signinui.model.FeedPost;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class FeedAdapter extends RecyclerView.Adapter<FeedAdapter.ViewHolder> {

    private final List<FeedPost> postList;
    private final Context context;
    private final DatabaseReference postsReference;
    private final FirebaseUser currentUser;

    public FeedAdapter(List<FeedPost> postList, Context context) {
        this.postList = postList;
        this.context = context;
        this.postsReference = FirebaseDatabase.getInstance().getReference("posts");
        this.currentUser = FirebaseAuth.getInstance().getCurrentUser();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_feed_post, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        FeedPost post = postList.get(position);
        String postId = post.getPostId();

        holder.username.setText(post.getUsername());
        holder.location.setText(post.getLocation());
        holder.caption.setText(post.getCaption());
        holder.activityType.setText(post.getActivityType());
        holder.likeCount.setText(String.valueOf(post.getLikeCount()));
        holder.commentCount.setText(String.valueOf(post.getCommentCount()));
        holder.time.setText(formatTimestamp(post.getTimestamp()));
        // Show location if available
        if (post.getLocation() != null && !post.getLocation().isEmpty()) {
            holder.location.setText(post.getLocation());
            holder.location.setVisibility(View.VISIBLE);
        } else {
            holder.location.setVisibility(View.GONE);
        }
        // Load image
        Glide.with(context)
                .load(post.getPostImageUrl())
                .placeholder(R.drawable.placeholder_image)
                .error(R.drawable.error_image)
                .centerCrop()
                .into(holder.postImage);

        // Set like button state
        if (currentUser != null && post.getLikes() != null &&
                post.getLikes().containsKey(currentUser.getUid())) {
            holder.likeIcon.setImageResource(R.drawable.ic_fav_border);
            holder.likeIcon.setColorFilter(context.getResources().getColor(android.R.color.holo_red_dark));
        } else {
            holder.likeIcon.setImageResource(R.drawable.ic_fav);
            holder.likeIcon.setColorFilter(context.getResources().getColor(R.color.green));
        }

        // Like button click listener
        holder.btnLike.setOnClickListener(v -> {
            if (currentUser != null) {
                toggleLike(postId, post);
            } else {
                Toast.makeText(context, "Please sign in to like posts", Toast.LENGTH_SHORT).show();
            }
        });

        // Comment button click listener
        holder.btnComment.setOnClickListener(v -> {
            if (currentUser != null) {
                showCommentDialog(postId);
            } else {
                Toast.makeText(context, "Please sign in to comment", Toast.LENGTH_SHORT).show();
            }
        });

        // View comments click listener
        holder.viewComments.setOnClickListener(v -> {
            if (post.getCommentCount() > 0) {
                showCommentsDialog(post);
            }
        });
    }

    @Override
    public int getItemCount() {
        return postList.size();
    }

    private void toggleLike(String postId, FeedPost post) {
        String userId = currentUser.getUid();
        DatabaseReference postRef = postsReference.child(postId);

        if (post.getLikes() != null && post.getLikes().containsKey(userId)) {
            // Unlike
            postRef.child("likes").child(userId).removeValue();
            postRef.child("likeCount").setValue(post.getLikeCount() - 1);
        } else {
            // Like
            postRef.child("likes").child(userId).setValue(true);
            postRef.child("likeCount").setValue(post.getLikeCount() + 1);
        }
    }

    private void showCommentDialog(String postId) {
        // Implement comment dialog
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(context);
        builder.setTitle("Add Comment");

        final EditText input = new EditText(context);
        input.setHint("Write a comment...");
        builder.setView(input);

        builder.setPositiveButton("Post", (dialog, which) -> {
            String commentText = input.getText().toString().trim();
            if (!commentText.isEmpty()) {
                addComment(postId, commentText);
            }
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
        builder.show();
    }

    private void addComment(String postId, String commentText) {
        String userId = currentUser.getUid();
        String username = currentUser.getDisplayName();
        if (username == null || username.isEmpty()) {
            username = currentUser.getEmail() != null ?
                    currentUser.getEmail().split("@")[0] : "Anonymous";
        }

        DatabaseReference postRef = postsReference.child(postId);
        String commentId = postRef.child("comments").push().getKey();

        FeedPost.Comment comment = new FeedPost.Comment(
                userId, username, commentText, System.currentTimeMillis()
        );

        if (commentId != null) {
            postRef.child("comments").child(commentId).setValue(comment);
            postRef.child("commentCount").setValue(ServerValue.increment(1));
        }
    }

    private void showCommentsDialog(FeedPost post) {
        // Implement comments view dialog
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(context);
        builder.setTitle("Comments");

        if (post.getComments() != null && !post.getComments().isEmpty()) {
            StringBuilder commentsText = new StringBuilder();
            for (FeedPost.Comment comment : post.getComments().values()) {
                commentsText.append(comment.getUsername())
                        .append(": ")
                        .append(comment.getCommentText())
                        .append("\n\n");
            }
            builder.setMessage(commentsText.toString());
        } else {
            builder.setMessage("No comments yet");
        }

        builder.setPositiveButton("OK", (dialog, which) -> dialog.dismiss());
        builder.show();
    }

    private String formatTimestamp(long timestamp) {
        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy 'at' hh:mm a", Locale.getDefault());
        return sdf.format(new Date(timestamp));
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView username, location, time, activityType, likeCount, commentCount, caption, viewComments;
        ImageView postImage, likeIcon;
        View btnLike, btnComment;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            username = itemView.findViewById(R.id.text_post_username);
            location = itemView.findViewById(R.id.text_location);
            time = itemView.findViewById(R.id.text_post_time);
            postImage = itemView.findViewById(R.id.img_post_photo);
            activityType = itemView.findViewById(R.id.text_activity_type);
            likeCount = itemView.findViewById(R.id.text_like_count);
            commentCount = itemView.findViewById(R.id.text_comment_count);
            caption = itemView.findViewById(R.id.text_post_caption);
            viewComments = itemView.findViewById(R.id.text_view_comments);

            // Like button
            btnLike = itemView.findViewById(R.id.btn_like);
            likeIcon = itemView.findViewById(R.id.img_like_icon);

            // Comment button
            btnComment = itemView.findViewById(R.id.btn_comment);
        }
    }
}