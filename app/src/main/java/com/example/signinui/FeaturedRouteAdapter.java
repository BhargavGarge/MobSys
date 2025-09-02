package com.example.signinui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.example.signinui.model.Route;

import java.util.List;

public class FeaturedRouteAdapter extends RecyclerView.Adapter<FeaturedRouteAdapter.RouteViewHolder> {

    private final List<Route> routes;
    private final Context context;

    public FeaturedRouteAdapter(List<Route> routes, Context context) {
        this.routes = routes;
        this.context = context;
    }

    @NonNull
    @Override
    public RouteViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_featured_route, parent, false);
        return new RouteViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RouteViewHolder holder, int position) {
        Route route = routes.get(position);

        // SET THE DATA FOR EACH VIEW
        holder.routeName.setText(route.getName());
        holder.routeInfo.setText(route.getDetails());
        holder.routeType.setText(route.getType().toUpperCase());

        // LOAD IMAGE USING GLIDE
        Glide.with(context)
                .load(route.getImageUrl())
                .transition(DrawableTransitionOptions.withCrossFade())
                .placeholder(R.drawable.placeholder_image)
                .error(R.drawable.error_image)
                .centerCrop()
                .into(holder.routeImage);

        // SET BACKGROUND COLOR BASED ON ROUTE TYPE
        int typeColor = getTypeColor(context, route.getType());
        holder.routeType.setBackgroundColor(typeColor);
    }

    @Override
    public int getItemCount() {
        return routes.size();
    }

    // A HELPER METHOD TO GET COLOR BASED ON ROUTE TYPE
    private int getTypeColor(Context context, String type) {
        if (type == null) type = "hiking";
        switch (type.toLowerCase()) {
            case "cycling":
            case "bike":
                return context.getResources().getColor(R.color.cycling_color);
            case "running":
                return context.getResources().getColor(R.color.running_color);
            case "hiking":
            default:
                return context.getResources().getColor(R.color.hiking_color);
        }
    }

    // VIEWHOLDER CLASS
    static class RouteViewHolder extends RecyclerView.ViewHolder {
        ImageView routeImage;
        TextView routeType;
        TextView routeName;
        TextView routeInfo;
        CardView cardView;

        public RouteViewHolder(@NonNull View itemView) {
            super(itemView);
            routeImage = itemView.findViewById(R.id.route_image);
            routeType = itemView.findViewById(R.id.route_type);
            routeName = itemView.findViewById(R.id.route_name);
            routeInfo = itemView.findViewById(R.id.route_info);
            cardView = (CardView) itemView;
        }
    }
}