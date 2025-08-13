package com.eslamgamal.fooddiary;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class MealAdapter extends RecyclerView.Adapter<MealAdapter.MealViewHolder> {

    private List<Meal> meals;
    private OnMealDeleteListener deleteListener;

    public interface OnMealDeleteListener {
        void onMealDelete(int position);
    }

    public MealAdapter(List<Meal> meals, OnMealDeleteListener deleteListener) {
        this.meals = meals;
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
        Meal meal = meals.get(position);
        holder.mealName.setText(meal.getName());
        holder.mealTime.setText(meal.getFormattedTime());

        holder.deleteButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (deleteListener != null) {
                    deleteListener.onMealDelete(holder.getAdapterPosition());
                }
            }
        });
    }

    @Override
    public int getItemCount() {
        return meals.size();
    }

    public void updateMeals(List<Meal> newMeals) {
        this.meals = newMeals;
        notifyDataSetChanged();
    }

    static class MealViewHolder extends RecyclerView.ViewHolder {
        TextView mealName;
        TextView mealTime;
        ImageButton deleteButton;

        public MealViewHolder(@NonNull View itemView) {
            super(itemView);
            mealName = itemView.findViewById(R.id.meal_name);
            mealTime = itemView.findViewById(R.id.meal_time);
            deleteButton = itemView.findViewById(R.id.delete_meal_button);
        }
    }
}