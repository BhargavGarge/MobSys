package com.example.signinui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import com.example.signinui.model.TrailDetails;
import java.util.List;
import java.util.Locale;

public class RoutesAdapter extends RecyclerView.Adapter<RoutesAdapter.RouteViewHolder> {

    private final List<TrailDetails> trails;

    public RoutesAdapter(List<TrailDetails> trails) {
        this.trails = trails;
    }

    @NonNull
    @Override
    public RouteViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // INFLATE YOUR NEW CUSTOM LAYOUT
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.list_item_route, parent, false);
        return new RouteViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RouteViewHolder holder, int position) {
        TrailDetails trail = trails.get(position);
        Context context = holder.itemView.getContext();

        // SET THE DATA FOR EACH VIEW
        holder.trailName.setText(trail.name);
        holder.trailDistance.setText(trail.distance);
        holder.trailDifficulty.setText(trail.difficulty);

        // SET THE ICON BASED ON TRAIL TYPE
        String type = trail.type != null ? trail.type.toLowerCase() : "";
        if (type.contains("cycling") || type.contains("bike")) {
            holder.trailIcon.setImageResource(R.drawable.ic_cycling);
        } else if (type.contains("running")) {
            holder.trailIcon.setImageResource(R.drawable.ic_running);
        } else {
            // Default to hiking
            holder.trailIcon.setImageResource(R.drawable.ic_hiking);
        }

        // SET THE DIFFICULTY COLOR
        int difficultyColor = getDifficultyColor(context, trail.difficulty);
        GradientDrawable background = (GradientDrawable) holder.trailDifficulty.getBackground();
        background.setColor(difficultyColor);
    }

    @Override
    public int getItemCount() {
        return trails.size();
    }

    // A HELPER METHOD TO GET COLOR BASED ON DIFFICULTY
    private int getDifficultyColor(Context context, String difficulty) {
        if (difficulty == null) difficulty = "easy";
        switch (difficulty.toLowerCase()) {
            case "easy":
                return ContextCompat.getColor(context, android.R.color.holo_green_dark);
            case "moderate":
                return ContextCompat.getColor(context, android.R.color.holo_orange_dark);
            case "challenging":
            case "difficult":
                return ContextCompat.getColor(context, android.R.color.holo_red_dark);
            default:
                return ContextCompat.getColor(context, android.R.color.darker_gray);
        }
    }

    // UPDATE THE ViewHolder TO HOLD YOUR NEW VIEWS
    static class RouteViewHolder extends RecyclerView.ViewHolder {
        ImageView trailIcon;
        TextView trailName;
        TextView trailDistance;
        TextView trailDifficulty;

        public RouteViewHolder(@NonNull View itemView) {
            super(itemView);
            trailIcon = itemView.findViewById(R.id.icon_trail_type);
            trailName = itemView.findViewById(R.id.text_trail_name);
            trailDistance = itemView.findViewById(R.id.text_trail_distance);
            trailDifficulty = itemView.findViewById(R.id.text_trail_difficulty);
        }
    }
}