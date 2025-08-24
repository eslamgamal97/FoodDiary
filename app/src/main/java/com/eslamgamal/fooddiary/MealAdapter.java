package com.eslamgamal.fooddiary;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class MealAdapter extends RecyclerView.Adapter<MealAdapter.MealViewHolder> {

    private List<Meal> meals;
    private OnMealDeleteListener deleteListener;

    public interface OnMealDeleteListener {
        void onMealDelete(Meal meal, int position);
    }

    public MealAdapter(List<Meal> meals, OnMealDeleteListener deleteListener) {
        this.meals = meals != null ? new ArrayList<>(meals) : new ArrayList<>();
        this.deleteListener = deleteListener;
    }

    @NonNull
    @Override
    public MealViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_meal, parent, false);
        return new MealViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MealViewHolder holder, int position) {
        if (position < 0 || position >= meals.size()) {
            return; // Safety check
        }

        Meal meal = meals.get(position);
        if (meal == null) {
            return; // Safety check
        }

        // Bind data safely
        holder.mealName.setText(meal.getName());
        holder.mealTime.setText(meal.getFormattedTimeDisplay()); // Use 12-hour format for display

        // Set delete button click listener
        holder.deleteButton.setOnClickListener(v -> {
            int currentPosition = holder.getAdapterPosition();
            if (currentPosition != RecyclerView.NO_POSITION &&
                    currentPosition < meals.size() &&
                    deleteListener != null) {

                Meal currentMeal = meals.get(currentPosition);
                deleteListener.onMealDelete(currentMeal, currentPosition);
            }
        });

        // Add accessibility support
        holder.deleteButton.setContentDescription("Delete " + meal.getName());

        // Optional: Add long click for meal editing (future feature)
        holder.itemView.setOnLongClickListener(v -> {
            // TODO: Implement meal editing functionality
            return false;
        });
    }

    @Override
    public int getItemCount() {
        return meals.size();
    }

    public void updateMeals(List<Meal> newMeals) {
        if (newMeals == null) {
            this.meals.clear();
        } else {
            this.meals.clear();
            this.meals.addAll(newMeals);
        }
        notifyDataSetChanged();
    }

    public void addMeal(Meal meal) {
        if (meal != null) {
            meals.add(meal);
            notifyItemInserted(meals.size() - 1);
        }
    }

    public void removeMeal(int position) {
        if (position >= 0 && position < meals.size()) {
            meals.remove(position);
            notifyItemRemoved(position);

            // Notify about range changes to update positions
            if (position < meals.size()) {
                notifyItemRangeChanged(position, meals.size() - position);
            }
        }
    }

    public void removeMeal(Meal meal) {
        if (meal != null) {
            int position = findMealPosition(meal);
            if (position != -1) {
                removeMeal(position);
            }
        }
    }

    private int findMealPosition(Meal meal) {
        for (int i = 0; i < meals.size(); i++) {
            if (meals.get(i).getId().equals(meal.getId())) {
                return i;
            }
        }
        return -1;
    }

    public Meal getMeal(int position) {
        if (position >= 0 && position < meals.size()) {
            return meals.get(position);
        }
        return null;
    }

    public List<Meal> getAllMeals() {
        return new ArrayList<>(meals);
    }

    public boolean isEmpty() {
        return meals.isEmpty();
    }

    // ViewHolder with null safety
    static class MealViewHolder extends RecyclerView.ViewHolder {
        TextView mealName;
        TextView mealTime;
        ImageButton deleteButton;

        public MealViewHolder(@NonNull View itemView) {
            super(itemView);

            // Find views with null checks
            mealName = itemView.findViewById(R.id.meal_name);
            mealTime = itemView.findViewById(R.id.meal_time);
            deleteButton = itemView.findViewById(R.id.delete_meal_button);

            // Set default values if views not found
            if (mealName == null) {
                throw new IllegalStateException("meal_name TextView not found in item_meal layout");
            }
            if (mealTime == null) {
                throw new IllegalStateException("meal_time TextView not found in item_meal layout");
            }
            if (deleteButton == null) {
                throw new IllegalStateException("delete_meal_button ImageButton not found in item_meal layout");
            }
        }
    }
}