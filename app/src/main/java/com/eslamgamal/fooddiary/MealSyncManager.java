package com.eslamgamal.fooddiary;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MealSyncManager {
    private static final String TAG = "MealSyncManager";
    private static final String PREFS_NAME = "meal_sync_prefs";
    private static final String KEY_PENDING_SYNC = "pending_sync_meals";
    private static final String KEY_LAST_SYNC = "last_sync_timestamp";
    private static final String KEY_SPREADSHEET_ID = "spreadsheet_id";
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final int RETRY_DELAY_MS = 2000;

    private Context context;
    private GoogleSheetsManager sheetsManager;
    private SharedPreferences prefs;
    private Handler mainHandler;

    // Callbacks
    public interface InitializationCallback {
        void onInitializationComplete(boolean success, String message);
    }

    // Add this field
    private InitializationCallback initCallback;

    // Add this method
    public void setInitializationCallback(InitializationCallback callback) {
        this.initCallback = callback;
        // Forward the callback to GoogleSheetsManager
        if (sheetsManager != null) {
            sheetsManager.setInitializationCallback((GoogleSheetsManager.InitializationCallback) callback);
        }
    }
    public interface SyncStatusListener {
        void onSyncStarted();
        void onSyncCompleted(boolean success, String message);
        void onSyncProgress(int completed, int total);
    }

    public MealSyncManager(Context context) {
        this.context = context;
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.sheetsManager = new GoogleSheetsManager(context);
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    public void syncMeal(Meal meal, SyncStatusListener listener) {
        if (listener != null) {
            listener.onSyncStarted();
        }

        syncMealWithRetry(meal, listener, 0);
    }

    private void syncMealWithRetry(Meal meal, SyncStatusListener listener, int retryAttempt) {
        if (!sheetsManager.isReady()) {
            if (retryAttempt < MAX_RETRY_ATTEMPTS) {
                Log.d(TAG, "Sheets service not ready, retrying in " + RETRY_DELAY_MS + "ms (attempt " + (retryAttempt + 1) + "/" + MAX_RETRY_ATTEMPTS + ")");
                mainHandler.postDelayed(() -> {
                    syncMealWithRetry(meal, listener, retryAttempt + 1);
                }, RETRY_DELAY_MS);
                return;
            } else {
                // Max retries reached, add to pending sync
                Log.w(TAG, "Max retry attempts reached, adding meal to pending sync");
                addToPendingSync(meal);
                if (listener != null) {
                    listener.onSyncCompleted(false, "Service not ready after multiple attempts. Meal saved for later sync.");
                }
                return;
            }
        }

        // Service is ready, proceed with sync
        sheetsManager.syncMealToSheets(meal, new GoogleSheetsManager.SyncCallback() {
            @Override
            public void onSuccess(String message) {
                updateLastSyncTime();
                if (listener != null) {
                    listener.onSyncCompleted(true, message);
                }
                Log.d(TAG, "Meal synced successfully: " + meal.getName());
            }

            @Override
            public void onError(String error) {
                // Add to pending sync for retry later
                addToPendingSync(meal);
                if (listener != null) {
                    listener.onSyncCompleted(false, error);
                }
                Log.e(TAG, "Failed to sync meal: " + error);
            }
        });
    }

    public void syncMultipleMeals(List<Meal> meals, SyncStatusListener listener) {
        if (meals.isEmpty()) return;

        if (listener != null) {
            listener.onSyncStarted();
        }

        syncMultipleMealsWithRetry(meals, listener, 0);
    }

    private void syncMultipleMealsWithRetry(List<Meal> meals, SyncStatusListener listener, int retryAttempt) {
        if (!sheetsManager.isReady()) {
            if (retryAttempt < MAX_RETRY_ATTEMPTS) {
                Log.d(TAG, "Sheets service not ready for multiple sync, retrying in " + RETRY_DELAY_MS + "ms (attempt " + (retryAttempt + 1) + "/" + MAX_RETRY_ATTEMPTS + ")");
                mainHandler.postDelayed(() -> {
                    syncMultipleMealsWithRetry(meals, listener, retryAttempt + 1);
                }, RETRY_DELAY_MS);
                return;
            } else {
                // Max retries reached
                addMultipleToPendingSync(meals);
                if (listener != null) {
                    listener.onSyncCompleted(false, "Service not ready after multiple attempts. Meals saved for later sync.");
                }
                return;
            }
        }

        sheetsManager.syncMultipleMealsToSheets(meals, new GoogleSheetsManager.SyncCallback() {
            @Override
            public void onSuccess(String message) {
                updateLastSyncTime();
                if (listener != null) {
                    listener.onSyncCompleted(true, message);
                }
                Log.d(TAG, "Multiple meals synced successfully");
            }

            @Override
            public void onError(String error) {
                // Add all meals to pending sync for retry later
                addMultipleToPendingSync(meals);
                if (listener != null) {
                    listener.onSyncCompleted(false, error);
                }
                Log.e(TAG, "Failed to sync multiple meals: " + error);
            }
        });
    }

    public void loadMealsFromCloud(GoogleSheetsManager.LoadCallback callback) {
        loadMealsFromCloudWithRetry(callback, 0);
    }

    private void loadMealsFromCloudWithRetry(GoogleSheetsManager.LoadCallback callback, int retryAttempt) {
        if (!sheetsManager.isReady()) {
            if (retryAttempt < MAX_RETRY_ATTEMPTS) {
                Log.d(TAG, "Sheets service not ready for loading, retrying in " + RETRY_DELAY_MS + "ms (attempt " + (retryAttempt + 1) + "/" + MAX_RETRY_ATTEMPTS + ")");
                mainHandler.postDelayed(() -> {
                    loadMealsFromCloudWithRetry(callback, retryAttempt + 1);
                }, RETRY_DELAY_MS);
                return;
            } else {
                // Max retries reached
                if (callback != null) {
                    callback.onError("Service not ready after multiple attempts. Please try again later.");
                }
                return;
            }
        }

        sheetsManager.loadMealsFromSheets(callback);
    }

    public void loadMealsForDate(String date, GoogleSheetsManager.LoadCallback callback) {
        loadMealsForDateWithRetry(date, callback, 0);
    }

    private void loadMealsForDateWithRetry(String date, GoogleSheetsManager.LoadCallback callback, int retryAttempt) {
        if (!sheetsManager.isReady()) {
            if (retryAttempt < MAX_RETRY_ATTEMPTS) {
                Log.d(TAG, "Sheets service not ready for date loading, retrying in " + RETRY_DELAY_MS + "ms");
                mainHandler.postDelayed(() -> {
                    loadMealsForDateWithRetry(date, callback, retryAttempt + 1);
                }, RETRY_DELAY_MS);
                return;
            } else {
                if (callback != null) {
                    callback.onError("Service not ready after multiple attempts.");
                }
                return;
            }
        }

        sheetsManager.loadMealsForDate(date, callback);
    }

    public void deleteMeal(Meal meal, SyncStatusListener listener) {
        if (listener != null) {
            listener.onSyncStarted();
        }

        deleteMealWithRetry(meal, listener, 0);
    }

    private void deleteMealWithRetry(Meal meal, SyncStatusListener listener, int retryAttempt) {
        if (!sheetsManager.isReady()) {
            if (retryAttempt < MAX_RETRY_ATTEMPTS) {
                Log.d(TAG, "Sheets service not ready for deletion, retrying in " + RETRY_DELAY_MS + "ms");
                mainHandler.postDelayed(() -> {
                    deleteMealWithRetry(meal, listener, retryAttempt + 1);
                }, RETRY_DELAY_MS);
                return;
            } else {
                if (listener != null) {
                    listener.onSyncCompleted(false, "Service not ready. Please try again later.");
                }
                return;
            }
        }

        sheetsManager.deleteMealFromSheets(meal, new GoogleSheetsManager.SyncCallback() {
            @Override
            public void onSuccess(String message) {
                updateLastSyncTime();
                if (listener != null) {
                    listener.onSyncCompleted(true, message);
                }
                Log.d(TAG, "Meal deleted successfully: " + meal.getName());
            }

            @Override
            public void onError(String error) {
                if (listener != null) {
                    listener.onSyncCompleted(false, error);
                }
                Log.e(TAG, "Failed to delete meal: " + error);
            }
        });
    }

    public void retryPendingSync(SyncStatusListener listener) {
        Set<String> pendingMeals = prefs.getStringSet(KEY_PENDING_SYNC, new HashSet<>());
        if (pendingMeals.isEmpty()) {
            if (listener != null) {
                listener.onSyncCompleted(true, "No pending meals to sync");
            }
            return;
        }

        if (listener != null) {
            listener.onSyncStarted();
        }

        // Convert stored meal data back to Meal objects and sync
        List<Meal> mealsToSync = new ArrayList<>();
        for (String mealData : pendingMeals) {
            Meal meal = parseMealFromString(mealData);
            if (meal != null) {
                mealsToSync.add(meal);
            }
        }

        if (!mealsToSync.isEmpty()) {
            syncMultipleMeals(mealsToSync, new SyncStatusListener() {
                @Override
                public void onSyncStarted() {
                    // Already notified
                }

                @Override
                public void onSyncCompleted(boolean success, String message) {
                    if (success) {
                        clearPendingSync();
                    }
                    if (listener != null) {
                        listener.onSyncCompleted(success, message);
                    }
                }

                @Override
                public void onSyncProgress(int completed, int total) {
                    if (listener != null) {
                        listener.onSyncProgress(completed, total);
                    }
                }
            });
        }
    }

    public void performFullSync(List<Meal> localMeals, SyncStatusListener listener) {
        if (listener != null) {
            listener.onSyncStarted();
        }

        performFullSyncWithRetry(localMeals, listener, 0);
    }

    private void performFullSyncWithRetry(List<Meal> localMeals, SyncStatusListener listener, int retryAttempt) {
        if (!sheetsManager.isReady()) {
            if (retryAttempt < MAX_RETRY_ATTEMPTS) {
                Log.d(TAG, "Sheets service not ready for full sync, retrying in " + RETRY_DELAY_MS + "ms");
                mainHandler.postDelayed(() -> {
                    performFullSyncWithRetry(localMeals, listener, retryAttempt + 1);
                }, RETRY_DELAY_MS);
                return;
            } else {
                if (listener != null) {
                    listener.onSyncCompleted(false, "Service not ready after multiple attempts. Please check your internet connection and try again.");
                }
                return;
            }
        }

        // First, load all meals from cloud
        sheetsManager.loadMealsFromSheets(new GoogleSheetsManager.LoadCallback() {
            @Override
            public void onMealsLoaded(List<Meal> cloudMeals) {
                // Compare local and cloud meals
                List<Meal> mealsToUpload = findMealsToUpload(localMeals, cloudMeals);

                if (mealsToUpload.isEmpty()) {
                    if (listener != null) {
                        listener.onSyncCompleted(true, "All meals are already synced");
                    }
                    return;
                }

                // Upload missing meals
                syncMultipleMeals(mealsToUpload, listener);
            }

            @Override
            public void onError(String error) {
                if (listener != null) {
                    listener.onSyncCompleted(false, "Failed to load cloud data: " + error);
                }
            }
        });
    }

    private List<Meal> findMealsToUpload(List<Meal> localMeals, List<Meal> cloudMeals) {
        List<Meal> mealsToUpload = new ArrayList<>();

        // Create a set of cloud meal signatures for quick lookup
        Set<String> cloudMealSignatures = new HashSet<>();
        for (Meal cloudMeal : cloudMeals) {
            cloudMealSignatures.add(createMealSignature(cloudMeal));
        }

        // Find local meals that aren't in cloud
        for (Meal localMeal : localMeals) {
            if (!cloudMealSignatures.contains(createMealSignature(localMeal))) {
                mealsToUpload.add(localMeal);
            }
        }

        Log.d(TAG, "Found " + mealsToUpload.size() + " meals to upload out of " + localMeals.size() + " local meals");
        return mealsToUpload;
    }

    private String createMealSignature(Meal meal) {
        // Create a unique signature for comparing meals
        return meal.getDate() + "|" + meal.getFormattedTime() + "|" + meal.getName() + "|" + meal.getCategory();
    }

    private void addToPendingSync(Meal meal) {
        Set<String> pendingMeals = new HashSet<>(prefs.getStringSet(KEY_PENDING_SYNC, new HashSet<>()));
        pendingMeals.add(serializeMeal(meal));
        prefs.edit().putStringSet(KEY_PENDING_SYNC, pendingMeals).apply();
    }

    private void addMultipleToPendingSync(List<Meal> meals) {
        Set<String> pendingMeals = new HashSet<>(prefs.getStringSet(KEY_PENDING_SYNC, new HashSet<>()));
        for (Meal meal : meals) {
            pendingMeals.add(serializeMeal(meal));
        }
        prefs.edit().putStringSet(KEY_PENDING_SYNC, pendingMeals).apply();
    }

    private void clearPendingSync() {
        prefs.edit().remove(KEY_PENDING_SYNC).apply();
    }

    private void updateLastSyncTime() {
        prefs.edit().putLong(KEY_LAST_SYNC, System.currentTimeMillis()).apply();
    }

    public long getLastSyncTime() {
        return prefs.getLong(KEY_LAST_SYNC, 0);
    }

    public boolean hasPendingSync() {
        Set<String> pendingMeals = prefs.getStringSet(KEY_PENDING_SYNC, new HashSet<>());
        return !pendingMeals.isEmpty();
    }

    public int getPendingSyncCount() {
        Set<String> pendingMeals = prefs.getStringSet(KEY_PENDING_SYNC, new HashSet<>());
        return pendingMeals.size();
    }

    public boolean isReady() {
        return sheetsManager.isReady();
    }

    //serialization methods
    private String serializeMeal(Meal meal) {
        return meal.getName() + ";" + meal.getCategory() + ";" + meal.getDate() + ";" + meal.getFormattedTime();
    }

    private Meal parseMealFromString(String mealData) {
        try {
            String[] parts = mealData.split(";");
            if (parts.length >= 4) {
                String name = parts[0];
                String category = parts[1];
                String date = parts[2];
                String timeStr = parts[3];

                // Reconstruct timestamp from date and time
                java.util.Date mealTimestamp = reconstructTimestampFromDateTime(date, timeStr);

                return new Meal(name, category, mealTimestamp, date);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to parse meal data: " + mealData, e);
        }
        return null;
    }

    private java.util.Date reconstructTimestampFromDateTime(String dateStr, String timeStr) {
        try {
            // Assuming date format is "yyyy-MM-dd" and time format is "HH:mm"
            java.text.SimpleDateFormat dateTimeFormat = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault());
            return dateTimeFormat.parse(dateStr + " " + timeStr);
        } catch (java.text.ParseException e) {
            Log.w(TAG, "Failed to parse date/time: " + dateStr + " " + timeStr + ", using current time");
            return new java.util.Date(); // Fallback to current time
        }
    }
    public String getSpreadsheetUrl() {
        return sheetsManager.getSpreadsheetUrl();
    }

    public void forceReinitialize() {
        sheetsManager.forceReinitialize();
    }

    public void shutdown() {
        if (sheetsManager != null) {
            sheetsManager.shutdown();
        }
    }
}