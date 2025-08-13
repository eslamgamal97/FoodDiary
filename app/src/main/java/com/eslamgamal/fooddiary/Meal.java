package com.eslamgamal.fooddiary;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class Meal {
    private String name;
    private String category; // breakfast, lunch, dinner, snacks
    private Date timestamp;
    private String date; // format: yyyy-MM-dd

    public Meal(String name, String category) {
        this.name = name;
        this.category = category;
        this.timestamp = new Date();

        // Format date as yyyy-MM-dd for consistency
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        this.date = dateFormat.format(this.timestamp);
    }

    public Meal(String name, String category, Date timestamp, String date) {
        this.name = name;
        this.category = category;
        this.timestamp = timestamp;
        this.date = date;
    }

    // Getters
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

    // Get formatted time string (12:30 PM)
    public String getFormattedTime() {
        SimpleDateFormat timeFormat = new SimpleDateFormat("h:mm a", Locale.getDefault());
        return timeFormat.format(timestamp);
    }

    // Setters
    public void setName(String name) {
        this.name = name;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public void setTimestamp(Date timestamp) {
        this.timestamp = timestamp;
    }

    public void setDate(String date) {
        this.date = date;
    }
}