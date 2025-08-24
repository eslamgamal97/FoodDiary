package com.eslamgamal.fooddiary;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;

public class Meal {
    private String id;
    private String name;
    private String category; // breakfast, lunch, dinner, snacks
    private Date timestamp;
    private String date; // format: yyyy-MM-dd

    // Constants for validation
    public static final int MAX_NAME_LENGTH = 100;
    public static final String[] VALID_CATEGORIES = {"breakfast", "lunch", "dinner", "snacks"};

    public Meal(String name, String category) {
        this.id = UUID.randomUUID().toString();
        this.name = validateAndTrimName(name);
        this.category = validateCategory(category);
        this.timestamp = new Date();

        // Format date as yyyy-MM-dd for consistency
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        this.date = dateFormat.format(this.timestamp);
    }

    public Meal(String name, String category, Date timestamp, String date) {
        this.id = UUID.randomUUID().toString();
        this.name = validateAndTrimName(name);
        this.category = validateCategory(category);
        this.timestamp = timestamp != null ? timestamp : new Date();
        this.date = date != null ? date : formatDate(this.timestamp);
    }

    // Constructor with ID (for loading from storage/cloud)
    public Meal(String id, String name, String category, Date timestamp, String date) {
        this.id = id != null ? id : UUID.randomUUID().toString();
        this.name = validateAndTrimName(name);
        this.category = validateCategory(category);
        this.timestamp = timestamp != null ? timestamp : new Date();
        this.date = date != null ? date : formatDate(this.timestamp);
    }

    // Validation methods
    private String validateAndTrimName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Meal name cannot be empty");
        }

        String trimmed = name.trim();
        if (trimmed.length() > MAX_NAME_LENGTH) {
            return trimmed.substring(0, MAX_NAME_LENGTH);
        }

        return trimmed;
    }

    private String validateCategory(String category) {
        if (category == null || category.trim().isEmpty()) {
            throw new IllegalArgumentException("Meal category cannot be empty");
        }

        String lowerCategory = category.toLowerCase().trim();
        for (String validCategory : VALID_CATEGORIES) {
            if (validCategory.equals(lowerCategory)) {
                return lowerCategory;
            }
        }

        throw new IllegalArgumentException("Invalid meal category: " + category);
    }

    private String formatDate(Date date) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        return dateFormat.format(date);
    }

    // Getters
    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getCategory() {
        return category;
    }

    public Date getTimestamp() {
        return timestamp;
    }

    public String getDate() {
        return date;
    }

    // Get formatted time string (HH:mm)
    public String getFormattedTime() {
        SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
        return timeFormat.format(this.timestamp);
    }

    // Get formatted time string for display (12:30 PM format)
    public String getFormattedTimeDisplay() {
        SimpleDateFormat timeFormat = new SimpleDateFormat("hh:mm a", Locale.getDefault());
        return timeFormat.format(this.timestamp);
    }

    // Get display name for category
    public String getCategoryDisplayName() {
        switch (category) {
            case "breakfast":
                return "Breakfast";
            case "lunch":
                return "Lunch";
            case "dinner":
                return "Dinner";
            case "snacks":
                return "Snacks";
            default:
                return category.substring(0, 1).toUpperCase() + category.substring(1);
        }
    }

    // Setters with validation
    public void setName(String name) {
        this.name = validateAndTrimName(name);
    }

    public void setCategory(String category) {
        this.category = validateCategory(category);
    }

    public void setTimestamp(Date timestamp) {
        if (timestamp == null) {
            throw new IllegalArgumentException("Timestamp cannot be null");
        }
        this.timestamp = timestamp;
        // Update date when timestamp changes
        this.date = formatDate(timestamp);
    }

    public void setDate(String date) {
        if (date == null || date.trim().isEmpty()) {
            throw new IllegalArgumentException("Date cannot be empty");
        }
        this.date = date.trim();
    }

    // Utility methods
    public boolean isToday() {
        String today = formatDate(new Date());
        return today.equals(this.date);
    }

    public long getTimestampMillis() {
        return timestamp.getTime();
    }

    // Create a unique signature for this meal (useful for comparison)
    public String getSignature() {
        return date + "|" + getFormattedTime() + "|" + name + "|" + category;
    }

    // Static validation methods
    public static boolean isValidMealName(String name) {
        return name != null && !name.trim().isEmpty() && name.trim().length() <= MAX_NAME_LENGTH;
    }

    public static boolean isValidCategory(String category) {
        if (category == null || category.trim().isEmpty()) {
            return false;
        }

        String lowerCategory = category.toLowerCase().trim();
        for (String validCategory : VALID_CATEGORIES) {
            if (validCategory.equals(lowerCategory)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;

        Meal meal = (Meal) obj;
        return id.equals(meal.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "Meal{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", category='" + category + '\'' +
                ", date='" + date + '\'' +
                ", time='" + getFormattedTime() + '\'' +
                '}';
    }
}