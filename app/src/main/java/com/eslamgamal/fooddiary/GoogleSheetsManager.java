package com.eslamgamal.fooddiary;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.api.services.sheets.v4.model.AppendValuesResponse;
import com.google.api.services.sheets.v4.model.BatchGetValuesResponse;
import com.google.api.services.sheets.v4.model.BatchUpdateSpreadsheetRequest;
import com.google.api.services.sheets.v4.model.ClearValuesRequest;
import com.google.api.services.sheets.v4.model.Request;
import com.google.api.services.sheets.v4.model.Spreadsheet;
import com.google.api.services.sheets.v4.model.SpreadsheetProperties;
import com.google.api.services.sheets.v4.model.ValueRange;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
                Log.d(TAG, "Creating HTTP transport...");
                final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();

                // Check if service account file exists
                InputStream credentialsStream = null;
                try {
                    credentialsStream = context.getAssets().open("service-account-key.json");
                    Log.d(TAG, "✓ Service account file found and opened");
                } catch (IOException e) {
                    Log.e(TAG, "✗ CRITICAL ERROR: Service account file 'service-account-key.json' not found in assets folder!", e);
                    Log.e(TAG, "Please make sure you have:");
                    Log.e(TAG, "1. Downloaded the service account JSON from Google Cloud Console");
                    Log.e(TAG, "2. Renamed it to 'service-account-key.json'");
                    Log.e(TAG, "3. Placed it in app/src/main/assets/ folder");
                    isInitializing = false;
                    return;
                }

                Log.d(TAG, "Creating Google credentials...");
                // Load service account credentials
                GoogleCredential credential = GoogleCredential.fromStream(credentialsStream)
                        .createScoped(SCOPES);

                Log.d(TAG, "Building Sheets service...");
                sheetsService = new Sheets.Builder(HTTP_TRANSPORT, JSON_FACTORY, credential)
                        .setApplicationName(APPLICATION_NAME)
                        .build();

                Log.d(TAG, "✓ Google Sheets service initialized successfully!");
                isInitialized = true;
                isInitializing = false;

                // Initialize user's spreadsheet if we don't have one
                if (spreadsheetId == null) {
                    Log.d(TAG, "No existing spreadsheet ID, initializing user spreadsheet...");
                    initializeUserSpreadsheet();
                } else {
                    Log.d(TAG, "Using existing spreadsheet ID: " + spreadsheetId);
                }

            } catch (GeneralSecurityException e) {
                Log.e(TAG, "✗ SECURITY ERROR during service initialization", e);
                Log.e(TAG, "This might be due to invalid service account credentials");
                isInitialized = false;
                isInitializing = false;
            } catch (IOException e) {
                Log.e(TAG, "✗ IO ERROR during service initialization", e);
                Log.e(TAG, "This might be due to network issues or file access problems");
                isInitialized = false;
                isInitializing = false;
            } catch (Exception e) {
                Log.e(TAG, "✗ UNEXPECTED ERROR during service initialization", e);
                isInitialized = false;
                isInitializing = false;
            }

            Log.d(TAG, "=== INITIALIZATION COMPLETED. isInitialized: " + isInitialized + " ===");
        });
    }

    private void initializeUserSpreadsheet() {
        Log.d(TAG, "=== STARTING USER SPREADSHEET INITIALIZATION ===");

        // Wait a bit for Firebase user to be fully loaded
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            if (user == null) {
                Log.w(TAG, "No Firebase user available for spreadsheet initialization, retrying in 3 seconds...");
                // Retry after another delay (max 3 retries)
                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                    FirebaseUser retryUser = FirebaseAuth.getInstance().getCurrentUser();
                    if (retryUser == null) {
                        Log.e(TAG, "✗ CRITICAL: Still no Firebase user after retry. Cannot create spreadsheet!");
                        Log.e(TAG, "Please make sure user is logged in before trying to sync.");
                        return;
                    }
                    Log.d(TAG, "✓ Firebase user found on retry: " + retryUser.getEmail());
                    createUserSpreadsheetWithUser(retryUser);
                }, 3000);
                return;
            }

            Log.d(TAG, "✓ Firebase user found: " + user.getEmail());
            createUserSpreadsheetWithUser(user);
        }, 2000); // Wait 2 seconds for Firebase user to be ready
    }

    private void createUserSpreadsheetWithUser(FirebaseUser user) {
        String userEmail = user.getEmail();
        if (userEmail == null) {
            userEmail = user.getUid(); // Fallback to UID if email is null
            Log.d(TAG, "Using UID as fallback: " + userEmail);
        }

        String spreadsheetTitle = "Food Diary - " + userEmail;
        Log.d(TAG, "Creating spreadsheet with title: " + spreadsheetTitle);

        executor.execute(() -> {
            try {
                if (!isInitialized) {
                    Log.e(TAG, "✗ Cannot create spreadsheet: Sheets service not initialized!");
                    return;
                }

                Log.d(TAG, "Creating new spreadsheet...");
                String newSpreadsheetId = createUserSpreadsheet(spreadsheetTitle);
                this.spreadsheetId = newSpreadsheetId;

                // Save spreadsheet ID for future use
                prefs.edit().putString(KEY_SPREADSHEET_ID, spreadsheetId).apply();

                Log.d(TAG, "✓ User spreadsheet created successfully!");
                Log.d(TAG, "✓ Spreadsheet ID: " + spreadsheetId);
                Log.d(TAG, "✓ Spreadsheet URL: https://docs.google.com/spreadsheets/d/" + spreadsheetId);
                Log.d(TAG, "=== USER SPREADSHEET INITIALIZATION COMPLETED ===");

            } catch (IOException e) {
                Log.e(TAG, "✗ Failed to create user spreadsheet", e);
                if (e.getMessage().contains("PERMISSION_DENIED") || e.getMessage().contains("403")) {
                    Log.e(TAG, "PERMISSION ERROR: Check your service account permissions!");
                    Log.e(TAG, "Make sure the service account has access to Google Sheets API");
                } else if (e.getMessage().contains("UNAUTHENTICATED") || e.getMessage().contains("401")) {
                    Log.e(TAG, "AUTHENTICATION ERROR: Check your service account credentials!");
                }
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
                Arrays.asList("Date", "Category", "Meal Name", "Time", "Timestamp")
        );

        ValueRange body = new ValueRange().setValues(values);

        sheetsService.spreadsheets().values()
                .update(spreadsheetId, "Sheet1!A1:E1", body)
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
                                meal.getFormattedTime(),
                                meal.getTimestamp().getTime() // Store timestamp as long for sorting
                        )
                );

                ValueRange body = new ValueRange().setValues(values);

                AppendValuesResponse result = sheetsService.spreadsheets().values()
                        .append(spreadsheetId, "Sheet1!A:E", body)
                        .setValueInputOption("RAW")
                        .setInsertDataOption("INSERT_ROWS")
                        .execute();

                Log.d(TAG, "Meal synced to sheets: " + meal.getName());
                callback.onSuccess("Meal synced successfully");

            } catch (IOException e) {
                Log.e(TAG, "Failed to sync meal to sheets", e);
                callback.onError("Failed to sync meal: " + e.getMessage());
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
                            meal.getFormattedTime(),
                            meal.getTimestamp().getTime()
                    ));
                }

                ValueRange body = new ValueRange().setValues(values);

                AppendValuesResponse result = sheetsService.spreadsheets().values()
                        .append(spreadsheetId, "Sheet1!A:E", body)
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
                        .get(spreadsheetId, "Sheet1!A2:E") // Skip header row
                        .execute();

                List<List<Object>> values = response.getValues();
                List<Meal> meals = new ArrayList<>();

                if (values != null) {
                    for (List<Object> row : values) {
                        if (row.size() >= 5) {
                            String date = row.get(0).toString();
                            String category = row.get(1).toString();
                            String name = row.get(2).toString();
                            String timeStr = row.get(3).toString();
                            long timestamp = Long.parseLong(row.get(4).toString());

                            Date mealTimestamp = new Date(timestamp);
                            Meal meal = new Meal(name, category, mealTimestamp, date);
                            meals.add(meal);
                        }
                    }
                }

                Log.d(TAG, "Loaded " + meals.size() + " meals from sheets");
                callback.onMealsLoaded(meals);

            } catch (IOException | NumberFormatException e) {
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
                // First, get all data to find the row to delete
                ValueRange response = sheetsService.spreadsheets().values()
                        .get(spreadsheetId, "Sheet1!A:E")
                        .execute();

                List<List<Object>> values = response.getValues();
                int rowToDelete = -1;

                // Find the row (skip header at index 0)
                for (int i = 1; i < values.size(); i++) {
                    List<Object> row = values.get(i);
                    if (row.size() >= 5) {
                        String name = row.get(2).toString();
                        String category = row.get(1).toString();
                        String date = row.get(0).toString();
                        long timestamp = Long.parseLong(row.get(4).toString());

                        if (name.equals(mealToDelete.getName()) &&
                                category.equals(mealToDelete.getCategory()) &&
                                date.equals(mealToDelete.getDate()) &&
                                timestamp == mealToDelete.getTimestamp().getTime()) {
                            rowToDelete = i + 1; // +1 because sheets are 1-indexed
                            break;
                        }
                    }
                }

                if (rowToDelete != -1) {
                    // Delete the row
                    List<Request> requests = new ArrayList<>();
                    requests.add(new Request()
                            .setDeleteDimension(new com.google.api.services.sheets.v4.model.DeleteDimensionRequest()
                                    .setRange(new com.google.api.services.sheets.v4.model.DimensionRange()
                                            .setSheetId(0) // Assuming first sheet
                                            .setDimension("ROWS")
                                            .setStartIndex(rowToDelete - 1) // 0-indexed for API
                                            .setEndIndex(rowToDelete))));

                    BatchUpdateSpreadsheetRequest batchRequest = new BatchUpdateSpreadsheetRequest()
                            .setRequests(requests);

                    sheetsService.spreadsheets().batchUpdate(spreadsheetId, batchRequest).execute();

                    Log.d(TAG, "Meal deleted from sheets: " + mealToDelete.getName());
                    callback.onSuccess("Meal deleted successfully");
                } else {
                    callback.onError("Meal not found in spreadsheet");
                }

            } catch (IOException | NumberFormatException e) {
                Log.e(TAG, "Failed to delete meal from sheets", e);
                callback.onError("Failed to delete meal: " + e.getMessage());
            }
        });
    }

    public void clearAllData(SyncCallback callback) {
        if (!checkInitialization(callback)) {
            return;
        }

        executor.execute(() -> {
            try {
                ClearValuesRequest clearRequest = new ClearValuesRequest();
                sheetsService.spreadsheets().values()
                        .clear(spreadsheetId, "Sheet1!A2:E", clearRequest)
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
            if (callback != null) {
                callback.onError(error);
            }
            return false;
        }

        if (spreadsheetId == null) {
            String error = "Spreadsheet not ready";
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
            if (callback != null) {
                callback.onError(error);
            }
            return false;
        }

        if (spreadsheetId == null) {
            String error = "Spreadsheet not ready";
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

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        status.append("Firebase User: ").append(user != null ? "✓ LOGGED IN (" + user.getEmail() + ")" : "✗ NOT LOGGED IN").append("\n");
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