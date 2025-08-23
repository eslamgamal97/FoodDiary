package com.eslamgamal.fooddiary;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.api.services.sheets.v4.model.AppendValuesResponse;
import com.google.api.services.sheets.v4.model.BatchUpdateSpreadsheetRequest;
import com.google.api.services.sheets.v4.model.ClearValuesRequest;
import com.google.api.services.sheets.v4.model.Request;
import com.google.api.services.sheets.v4.model.Spreadsheet;
import com.google.api.services.sheets.v4.model.SpreadsheetProperties;
import com.google.api.services.sheets.v4.model.ValueRange;
import com.google.api.services.sheets.v4.model.DeleteDimensionRequest;
import com.google.api.services.sheets.v4.model.DimensionRange;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.text.SimpleDateFormat;
import java.text.ParseException;
import java.util.Locale;

public class GoogleSheetsManager {
    private static final String TAG = "GoogleSheetsManager";
    private static final String APPLICATION_NAME = "Food Diary App";
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private static final List<String> SCOPES = Collections.singletonList(SheetsScopes.SPREADSHEETS);

    // SharedPreferences keys
    private static final String PREFS_NAME = "sheets_manager_prefs";
    private static final String KEY_SPREADSHEET_ID = "spreadsheet_id";

    private Sheets sheetsService;
    private Context context;
    private ExecutorService executor;
    private String spreadsheetId;
    private boolean isInitialized = false;
    private boolean isInitializing = false;
    private SharedPreferences prefs;

    // Callback interfaces
    public interface InitializationCallback {
        void onInitializationComplete(boolean success, String message);
    }
    private InitializationCallback initCallback;

    public void setInitializationCallback(InitializationCallback callback) {
        this.initCallback = callback;
    }
    public interface SyncCallback {
        void onSuccess(String message);
        void onError(String error);
    }

    public interface LoadCallback {
        void onMealsLoaded(List<Meal> meals);
        void onError(String error);
    }

    public GoogleSheetsManager(Context context) {
        this.context = context;
        this.executor = Executors.newSingleThreadExecutor();
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        // Load saved spreadsheet ID
        this.spreadsheetId = prefs.getString(KEY_SPREADSHEET_ID, null);

        initializeService();
    }

    private void initializeService() {
        if (isInitializing || isInitialized) {
            Log.d(TAG, "Service initialization already in progress or completed");
            return;
        }

        Log.d(TAG, "=== STARTING GOOGLE SHEETS INITIALIZATION ===");
        isInitializing = true;

        executor.execute(() -> {
            try {
                // Get the signed-in Google account
                GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(context);

                if (account == null) {
                    Log.e(TAG, "No signed-in Google account found");
                    isInitializing = false;
                    return;
                }

                Log.d(TAG, "Using Google account: " + account.getEmail());

                // Create credential using GoogleAccountCredential (modern approach)
                GoogleAccountCredential credential = GoogleAccountCredential.usingOAuth2(
                        context, SCOPES);
                credential.setSelectedAccount(account.getAccount());

                HttpTransport transport = AndroidHttp.newCompatibleTransport();

                Log.d(TAG, "Building Sheets service...");
                sheetsService = new Sheets.Builder(transport, JSON_FACTORY, credential)
                        .setApplicationName(APPLICATION_NAME)
                        .build();

                Log.d(TAG, "✓ Google Sheets service initialized successfully!");
                isInitialized = true;
                isInitializing = false;

                // Initialize user's spreadsheet if we don't have one
                if (spreadsheetId == null) {
                    Log.d(TAG, "No existing spreadsheet ID, creating new spreadsheet...");
                    initializeUserSpreadsheet(account);
                } else {
                    Log.d(TAG, "Using existing spreadsheet ID: " + spreadsheetId);
                }

            } catch (Exception e) {
                Log.e(TAG, "✗ ERROR during service initialization", e);
                isInitialized = false;
                isInitializing = false;
            }

            Log.d(TAG, "=== INITIALIZATION COMPLETED. isInitialized: " + isInitialized + " ===");
        });
    }

    private void initializeUserSpreadsheet(GoogleSignInAccount account) {
        String userEmail = account.getEmail();
        if (userEmail == null) {
            userEmail = "User";
        }
        String spreadsheetTitle = "Food Diary - " + userEmail;

        executor.execute(() -> {
            try {
                if (!isInitialized) {
                    Log.e(TAG, "✗ Cannot create spreadsheet: Sheets service not initialized!");
                    return;
                }

                Log.d(TAG, "Creating new spreadsheet: " + spreadsheetTitle);
                String newSpreadsheetId = createUserSpreadsheet(spreadsheetTitle);
                this.spreadsheetId = newSpreadsheetId;

                // Save spreadsheet ID for future use
                prefs.edit().putString(KEY_SPREADSHEET_ID, spreadsheetId).apply();

                Log.d(TAG, "✓ User spreadsheet created successfully!");
                Log.d(TAG, "✓ Spreadsheet ID: " + spreadsheetId);
                Log.d(TAG, "✓ Spreadsheet URL: https://docs.google.com/spreadsheets/d/" + spreadsheetId);

            } catch (IOException e) {
                Log.e(TAG, "✗ Failed to create user spreadsheet", e);
                Log.e(TAG, "Error details: " + e.getMessage());
            } catch (Exception e) {
                Log.e(TAG, "✗ Unexpected error creating spreadsheet", e);
            }
        });
    }

    private String createUserSpreadsheet(String title) throws IOException {
        if (!isInitialized) {
            throw new IOException("Sheets service not initialized");
        }

        // Create a new spreadsheet
        Spreadsheet spreadsheet = new Spreadsheet()
                .setProperties(new SpreadsheetProperties().setTitle(title));

        spreadsheet = sheetsService.spreadsheets().create(spreadsheet).execute();
        String newSpreadsheetId = spreadsheet.getSpreadsheetId();

        // Set up headers
        setupSpreadsheetHeaders(newSpreadsheetId);

        return newSpreadsheetId;
    }

    private void setupSpreadsheetHeaders(String spreadsheetId) throws IOException {
        List<List<Object>> values = Arrays.asList(
                Arrays.asList("Date", "Category", "Meal Name", "Time") // Removed "Timestamp"
        );

        ValueRange body = new ValueRange().setValues(values);

        sheetsService.spreadsheets().values()
                .update(spreadsheetId, "Sheet1!A1:D1", body) // Changed from A1:E1 to A1:D1
                .setValueInputOption("RAW")
                .execute();

        Log.d(TAG, "Spreadsheet headers set up successfully");
    }

    public void syncMealToSheets(Meal meal, SyncCallback callback) {
        if (!checkInitialization(callback)) {
            return;
        }

        executor.execute(() -> {
            try {
                List<List<Object>> values = Arrays.asList(
                        Arrays.asList(
                                meal.getDate(),
                                meal.getCategory(),
                                meal.getName(),
                                meal.getFormattedTime()
                                // Removed meal.getTimestamp().getTime()
                        )
                );

                ValueRange body = new ValueRange().setValues(values);

                AppendValuesResponse result = sheetsService.spreadsheets().values()
                        .append(spreadsheetId, "Sheet1!A:D", body) // Changed from A:E to A:D
                        .setValueInputOption("RAW")
                        .setInsertDataOption("INSERT_ROWS")
                        .execute();

                Log.d(TAG, "Meal synced to sheets: " + meal.getName());
                callback.onSuccess("Meal synced successfully");

            } catch (IOException e) {
                Log.e(TAG, "Failed to sync meal to sheets", e);
                callback.onError("Failed to sync meal: " + e.getMessage());
            } catch (Exception e) {
                Log.e(TAG, "Unexpected error syncing meal", e);
                callback.onError("Unexpected error: " + e.getMessage());
            }
        });
    }

    public void syncMultipleMealsToSheets(List<Meal> meals, SyncCallback callback) {
        if (!checkInitialization(callback)) {
            return;
        }

        executor.execute(() -> {
            try {
                List<List<Object>> values = new ArrayList<>();

                for (Meal meal : meals) {
                    values.add(Arrays.asList(
                            meal.getDate(),
                            meal.getCategory(),
                            meal.getName(),
                            meal.getFormattedTime()
                            // Removed meal.getTimestamp().getTime()
                    ));
                }

                ValueRange body = new ValueRange().setValues(values);

                AppendValuesResponse result = sheetsService.spreadsheets().values()
                        .append(spreadsheetId, "Sheet1!A:D", body) // Changed from A:E to A:D
                        .setValueInputOption("RAW")
                        .setInsertDataOption("INSERT_ROWS")
                        .execute();

                Log.d(TAG, "Multiple meals synced to sheets: " + meals.size());
                callback.onSuccess(meals.size() + " meals synced successfully");

            } catch (IOException e) {
                Log.e(TAG, "Failed to sync meals to sheets", e);
                callback.onError("Failed to sync meals: " + e.getMessage());
            }
        });
    }

    public void loadMealsFromSheets(LoadCallback callback) {
        if (!checkInitialization(callback)) {
            return;
        }

        executor.execute(() -> {
            try {
                ValueRange response = sheetsService.spreadsheets().values()
                        .get(spreadsheetId, "Sheet1!A2:D")
                        .execute();

                List<List<Object>> values = response.getValues();
                List<Meal> meals = new ArrayList<>();

                if (values != null) {
                    for (List<Object> row : values) {
                        if (row.size() >= 4) {
                            String date = row.get(0).toString();
                            String category = row.get(1).toString();
                            String name = row.get(2).toString();
                            String timeStr = row.get(3).toString();

                            // Reconstruct timestamp from date and time
                            java.util.Date mealTimestamp = reconstructTimestamp(date, timeStr);

                            Meal meal = new Meal(name, category, mealTimestamp, date);
                            meals.add(meal);
                        }
                    }
                }

                Log.d(TAG, "Loaded " + meals.size() + " meals from sheets");
                callback.onMealsLoaded(meals);

            } catch (IOException e) {
                Log.e(TAG, "Failed to load meals from sheets", e);
                callback.onError("Failed to load meals: " + e.getMessage());
            }
        });
    }

    public void loadMealsForDate(String date, LoadCallback callback) {
        loadMealsFromSheets(new LoadCallback() {
            @Override
            public void onMealsLoaded(List<Meal> allMeals) {
                List<Meal> mealsForDate = new ArrayList<>();
                for (Meal meal : allMeals) {
                    if (meal.getDate().equals(date)) {
                        mealsForDate.add(meal);
                    }
                }
                callback.onMealsLoaded(mealsForDate);
            }

            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        });
    }

    public void deleteMealFromSheets(Meal mealToDelete, SyncCallback callback) {
        if (!checkInitialization(callback)) {
            return;
        }

        executor.execute(() -> {
            try {
                // Get all data to find the row to delete
                ValueRange response = sheetsService.spreadsheets().values()
                        .get(spreadsheetId, "Sheet1!A:D")
                        .execute();

                List<List<Object>> values = response.getValues();
                int rowToDelete = -1;

                // Create unique identifier for the meal to delete
                String targetSignature = createSheetMealSignature(
                        mealToDelete.getDate(),
                        mealToDelete.getFormattedTime(),
                        mealToDelete.getName(),
                        mealToDelete.getCategory()
                );

                // Find the row (skip header at index 0)
                for (int i = 1; i < values.size(); i++) {
                    List<Object> row = values.get(i);
                    if (row.size() >= 4) {
                        String date = row.get(0).toString();
                        String category = row.get(1).toString();
                        String name = row.get(2).toString();
                        String time = row.get(3).toString();

                        String rowSignature = createSheetMealSignature(date, time, name, category);

                        if (targetSignature.equals(rowSignature)) {
                            rowToDelete = i + 1; // +1 because sheets are 1-indexed
                            Log.d(TAG, "Found meal to delete at row " + rowToDelete + ": " + targetSignature);
                            break;
                        }
                    }
                }

                if (rowToDelete != -1) {
                    // Delete the row
                    List<Request> requests = new ArrayList<>();
                    requests.add(new Request()
                            .setDeleteDimension(new DeleteDimensionRequest()
                                    .setRange(new DimensionRange()
                                            .setSheetId(0)
                                            .setDimension("ROWS")
                                            .setStartIndex(rowToDelete - 1)
                                            .setEndIndex(rowToDelete))));

                    BatchUpdateSpreadsheetRequest batchRequest = new BatchUpdateSpreadsheetRequest()
                            .setRequests(requests);

                    sheetsService.spreadsheets().batchUpdate(spreadsheetId, batchRequest).execute();

                    Log.d(TAG, "Meal deleted from sheets: " + mealToDelete.getName() + " at " + mealToDelete.getFormattedTime());
                    callback.onSuccess("Meal deleted successfully");
                } else {
                    Log.w(TAG, "Meal not found in spreadsheet: " + targetSignature);
                    callback.onError("Meal not found in spreadsheet");
                }

            } catch (IOException e) {
                Log.e(TAG, "Failed to delete meal from sheets", e);
                callback.onError("Failed to delete meal: " + e.getMessage());
            }
        });
    }

    //Helper method to create sheet-based meal signature
    private String createSheetMealSignature(String date, String time, String name, String category) {
        return date + "|" + time + "|" + name + "|" + category;
    }

    // 6. Helper method to reconstruct timestamp from date and time strings
    private java.util.Date reconstructTimestamp(String dateStr, String timeStr) {
        try {
            // Assuming date format is "yyyy-MM-dd" and time format is "HH:mm"
            SimpleDateFormat dateTimeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
            return dateTimeFormat.parse(dateStr + " " + timeStr);
        } catch (ParseException e) {
            Log.w(TAG, "Failed to parse date/time: " + dateStr + " " + timeStr + ", using current time");
            return new java.util.Date(); // Fallback to current time
        }
    }

    public void clearAllData(SyncCallback callback) {
        if (!checkInitialization(callback)) {
            return;
        }

        executor.execute(() -> {
            try {
                ClearValuesRequest clearRequest = new ClearValuesRequest();
                sheetsService.spreadsheets().values()
                        .clear(spreadsheetId, "Sheet1!A2:D", clearRequest) // Changed from A2:E to A2:D
                        .execute();

                Log.d(TAG, "All meal data cleared from sheets");
                callback.onSuccess("All data cleared successfully");

            } catch (IOException e) {
                Log.e(TAG, "Failed to clear data from sheets", e);
                callback.onError("Failed to clear data: " + e.getMessage());
            }
        });
    }

    // Helper method to check if service is initialized
    private boolean checkInitialization(SyncCallback callback) {
        if (!isInitialized) {
            String error = "Sheets service not initialized";
            Log.w(TAG, error);
            if (callback != null) {
                callback.onError(error);
            }
            return false;
        }

        if (spreadsheetId == null) {
            String error = "Spreadsheet not ready";
            Log.w(TAG, error);
            if (callback != null) {
                callback.onError(error);
            }
            return false;
        }

        return true;
    }

    // Helper method for LoadCallback
    private boolean checkInitialization(LoadCallback callback) {
        if (!isInitialized) {
            String error = "Sheets service not initialized";
            Log.w(TAG, error);
            if (callback != null) {
                callback.onError(error);
            }
            return false;
        }

        if (spreadsheetId == null) {
            String error = "Spreadsheet not ready";
            Log.w(TAG, error);
            if (callback != null) {
                callback.onError(error);
            }
            return false;
        }

        return true;
    }

    public String getSpreadsheetUrl() {
        if (spreadsheetId != null) {
            return "https://docs.google.com/spreadsheets/d/" + spreadsheetId;
        }
        return null;
    }

    public boolean isReady() {
        boolean ready = isInitialized && spreadsheetId != null;
        Log.d(TAG, "isReady() check: initialized=" + isInitialized + ", spreadsheetId=" + (spreadsheetId != null ? "exists" : "null") + ", result=" + ready);
        return ready;
    }

    public String getDetailedStatus() {
        StringBuilder status = new StringBuilder();
        status.append("=== GOOGLE SHEETS SERVICE STATUS ===\n");
        status.append("Service Initialized: ").append(isInitialized ? "✓ YES" : "✗ NO").append("\n");
        status.append("Is Initializing: ").append(isInitializing ? "YES" : "NO").append("\n");
        status.append("Spreadsheet ID: ").append(spreadsheetId != null ? "✓ EXISTS" : "✗ NULL").append("\n");
        status.append("Service Ready: ").append(isReady() ? "✓ READY" : "✗ NOT READY").append("\n");

        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(context);
        status.append("Google Sign-In Account: ").append(account != null ? "✓ SIGNED IN (" + account.getEmail() + ")" : "✗ NOT SIGNED IN").append("\n");
        status.append("====================================");
        return status.toString();
    }

    public void forceReinitialize() {
        isInitialized = false;
        isInitializing = false;
        spreadsheetId = null;
        prefs.edit().remove(KEY_SPREADSHEET_ID).apply();
        initializeService();
    }

    public void shutdown() {
        if (executor != null) {
            executor.shutdown();
        }
    }
}