package com.eslamgamal.fooddiary;

import android.content.Context;
import android.widget.Toast;

public class InputValidator {

    public static final int MIN_MEAL_NAME_LENGTH = 1;
    public static final int MAX_MEAL_NAME_LENGTH = 100;

    // Validation result class
    public static class ValidationResult {
        private boolean isValid;
        private String errorMessage;

        public ValidationResult(boolean isValid, String errorMessage) {
            this.isValid = isValid;
            this.errorMessage = errorMessage;
        }

        public boolean isValid() {
            return isValid;
        }

        public String getErrorMessage() {
            return errorMessage;
        }
    }

    /**
     * Validate meal name input
     * @param mealName The meal name to validate
     * @return ValidationResult with validation status and error message
     */
    public static ValidationResult validateMealName(String mealName) {
        // Check for null or empty
        if (mealName == null || mealName.trim().isEmpty()) {
            return new ValidationResult(false, "Please enter a meal name");
        }

        String trimmed = mealName.trim();

        // Check minimum length
        if (trimmed.length() < MIN_MEAL_NAME_LENGTH) {
            return new ValidationResult(false, "Meal name is too short");
        }

        // Check maximum length
        if (trimmed.length() > MAX_MEAL_NAME_LENGTH) {
            return new ValidationResult(false, "Meal name is too long (max " + MAX_MEAL_NAME_LENGTH + " characters)");
        }

        // Check for invalid characters (optional - adjust based on your needs)
        if (containsInvalidCharacters(trimmed)) {
            return new ValidationResult(false, "Meal name contains invalid characters");
        }

        return new ValidationResult(true, null);
    }

    /**
     * Validate meal category
     * @param category The category to validate
     * @return ValidationResult with validation status and error message
     */
    public static ValidationResult validateMealCategory(String category) {
        if (!Meal.isValidCategory(category)) {
            return new ValidationResult(false, "Invalid meal category. Must be breakfast, lunch, dinner, or snacks");
        }
        return new ValidationResult(true, null);
    }

    /**
     * Check if meal name contains invalid characters
     * @param name The name to check
     * @return true if contains invalid characters, false otherwise
     */
    private static boolean containsInvalidCharacters(String name) {
        // Allow letters (Latin + Arabic), numbers, spaces, and common punctuation
        // \u0600-\u06FF covers Arabic script
        // \u0750-\u077F covers Arabic Supplement
        // \uFB50-\uFDFF covers Arabic Presentation Forms-A
        // \uFE70-\uFEFF covers Arabic Presentation Forms-B
        String validPattern = "^[a-zA-Z0-9\\u0600-\\u06FF\\u0750-\\u077F\\uFB50-\\uFDFF\\uFE70-\\uFEFF\\s\\-_.,'()&!]+$";
        return !name.matches(validPattern);
    }

    /**
     * Validate and show error toast if invalid
     * @param context Context for showing toast
     * @param mealName Meal name to validate
     * @return true if valid, false if invalid (and shows toast)
     */
    public static boolean validateMealNameWithToast(Context context, String mealName) {
        ValidationResult result = validateMealName(mealName);
        if (!result.isValid()) {
            if (context != null) {
                Toast.makeText(context, result.getErrorMessage(), Toast.LENGTH_SHORT).show();
            }
            return false;
        }
        return true;
    }

    /**
     * Validate and show error toast if invalid
     * @param context Context for showing toast
     * @param category Category to validate
     * @return true if valid, false if invalid (and shows toast)
     */
    public static boolean validateMealCategoryWithToast(Context context, String category) {
        ValidationResult result = validateMealCategory(category);
        if (!result.isValid()) {
            if (context != null) {
                Toast.makeText(context, result.getErrorMessage(), Toast.LENGTH_SHORT).show();
            }
            return false;
        }
        return true;
    }

    /**
     * Clean and format meal name input
     * @param mealName Raw meal name input
     * @return Cleaned meal name or null if invalid
     */
    public static String cleanMealName(String mealName) {
        ValidationResult result = validateMealName(mealName);
        if (result.isValid()) {
            return mealName.trim();
        }
        return null;
    }

    /**
     * Sanitize input by removing potentially problematic characters
     * @param input Raw input string
     * @return Sanitized string
     */
    public static String sanitizeInput(String input) {
        if (input == null) {
            return "";
        }

        // Remove leading/trailing whitespace
        String sanitized = input.trim();

        // Remove multiple consecutive spaces
        sanitized = sanitized.replaceAll("\\s+", " ");

        // Remove any control characters
        sanitized = sanitized.replaceAll("[\\p{Cntrl}]", "");

        return sanitized;
    }
}