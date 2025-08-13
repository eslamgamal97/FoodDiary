package com.eslamgamal.fooddiary;

import android.content.Context;
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

    private Sheets sheetsService;
    private Context context;
    private ExecutorService executor;
    private String spreadsheetId;

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
        initializeService();
    }

    private void initializeService() {
        executor.execute(() -> {
            try {
                final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();

                // Load service account credentials from assets folder
                InputStream credentialsStream = context.getAssets().open("service-account-key.json");
                GoogleCredential credential = GoogleCredential.fromStream(credentialsStream)
                        .createScoped(SCOPES);

                sheetsService = new Sheets.Builder(HTTP_TRANSPORT, JSON_FACTORY, credential)
                        .setApplicationName(APPLICATION_NAME)
                        .build();

                Log.d(TAG, "Google Sheets service initialized successfully");

                // Initialize user's spreadsheet
                initializeUserSpreadsheet();

            } catch (GeneralSecurityException | IOException e) {
                Log.e(TAG, "Failed to initialize Google Sheets service", e);
            }
        });
    }

    private void initializeUserSpreadsheet() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        String userEmail = user.getEmail();
        String spreadsheetTitle = "Food Diary - " + userEmail;

        executor.execute(() -> {
            try {
                // Check if spreadsheet already exists (you might want to store this in SharedPreferences)
                // For now, we'll create a new one each time or find existing one
                spreadsheetId = getOrCreateUserSpreadsheet(spreadsheetTitle);
                Log.d(TAG, "User spreadsheet ID: " + spreadsheetId);

            } catch (IOException e) {
                Log.e(TAG, "Failed to initialize user spreadsheet", e);
            }
        });
    }

    private String getOrCreateUserSpreadsheet(String title) throws IOException {
        // Create a new spreadsheet
        Spreadsheet spreadsheet = new Spreadsheet()
                .setProperties(new SpreadsheetProperties().setTitle(title));

        spreadsheet = sheetsService.spreadsheets().create(spreadsheet).execute();
        String spreadsheetId = spreadsheet.getSpreadsheetId();

        // Set up headers
        setupSpreadsheetHeaders(spreadsheetId);

        return spreadsheetId;
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
        if (sheetsService == null || spreadsheetId == null) {
            callback.onError("Sheets service not initialized");
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
        if (sheetsService == null || spreadsheetId == null) {
            callback.onError("Sheets service not initialized");
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
        if (sheetsService == null || spreadsheetId == null) {
            callback.onError("Sheets service not initialized");
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
        if (sheetsService == null || spreadsheetId == null) {
            callback.onError("Sheets service not initialized");
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
        if (sheetsService == null || spreadsheetId == null) {
            callback.onError("Sheets service not initialized");
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

    public String getSpreadsheetUrl() {
        if (spreadsheetId != null) {
            return "https://docs.google.com/spreadsheets/d/" + spreadsheetId;
        }
        return null;
    }

    public void shutdown() {
        if (executor != null) {
            executor.shutdown();
        }
    }
}