package com.eslamgamal.fooddiary;

import android.app.DatePickerDialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.Scope;
import com.google.android.material.navigation.NavigationView;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.firebase.auth.FirebaseAuth;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements MealAdapter.OnMealDeleteListener {

    private DrawerLayout drawerLayout;
    private NavigationView navigationView;
    private ImageButton menuButton;
    private FirebaseAuth mAuth;
    private GoogleSignInClient mGoogleSignInClient;

    // UI components
    private TextView selectedDateText;
    private ImageButton changeDateButton;
    private Button addBreakfastButton, addLunchButton, addDinnerButton, addSnacksButton;
    private RecyclerView breakfastRecycler, lunchRecycler, dinnerRecycler, snacksRecycler;

    // Data
    private List<Meal> allMeals;
    private MealAdapter breakfastAdapter, lunchAdapter, dinnerAdapter, snacksAdapter;
    private Calendar selectedDate;

    // Sync components
    private MealSyncManager syncManager;
    private ProgressDialog syncProgressDialog;
    private ProgressDialog initializationDialog;
    private Handler mainHandler;

    // Initialization state
    private boolean isInitializationComplete = false;
    private boolean initializationSuccess = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize Firebase Auth
        mAuth = FirebaseAuth.getInstance();

        // Configure Google Sign In (same as LoginActivity)
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .requestScopes(
                        new Scope(SheetsScopes.SPREADSHEETS),
                        new Scope("https://www.googleapis.com/auth/drive.file")
                )
                .build();
        mGoogleSignInClient = GoogleSignIn.getClient(this, gso);

        // Setup Toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // Remove default title text
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }

        // Drawer & Navigation
        drawerLayout = findViewById(R.id.drawer_layout);
        navigationView = findViewById(R.id.navigation_view);
        menuButton = findViewById(R.id.menu_button);

        // Initialize sync components
        syncManager = new MealSyncManager(this);
        mainHandler = new Handler(Looper.getMainLooper());

        // Initialize data
        allMeals = new ArrayList<>();
        selectedDate = Calendar.getInstance();

        // Setup UI
        setupMealLoggingInterface();
        setupNavigationDrawer();
        setupBackPressHandling();

        // Update date display
        updateDateDisplay();
        loadMealsForSelectedDate();

        // Start initialization check
        checkInitializationStatus();
    }

    private void checkInitializationStatus() {
        // Show initialization progress
        showInitializationProgress("Setting up sync...");

        // Check initialization status periodically
        Handler checkHandler = new Handler(Looper.getMainLooper());
        Runnable checkRunnable = new Runnable() {
            int attempts = 0;
            final int maxAttempts = 20; // 10 seconds max (20 * 500ms)

            @Override
            public void run() {
                attempts++;

                if (syncManager.isReady()) {
                    // Success - initialization complete
                    hideInitializationProgress();
                    isInitializationComplete = true;
                    initializationSuccess = true;

                    Toast.makeText(MainActivity.this, "Sync ready ✓", Toast.LENGTH_SHORT).show();
                    loadMealsFromCloud();

                } else if (attempts >= maxAttempts) {
                    // Timeout - initialization failed
                    hideInitializationProgress();
                    isInitializationComplete = true;
                    initializationSuccess = false;

                    Toast.makeText(MainActivity.this, "Sync unavailable - running in offline mode", Toast.LENGTH_LONG).show();
                    showSyncStatus("⚠ Offline mode", true);

                } else {
                    // Still waiting - check again
                    checkHandler.postDelayed(this, 500);
                }
            }
        };

        // Start checking after a short delay
        checkHandler.postDelayed(checkRunnable, 1000);
    }

    private void setupMealLoggingInterface() {
        // Inflate the meal logging layout into content_frame
        LayoutInflater.from(this).inflate(R.layout.fragment_meal_logging,
                findViewById(R.id.content_frame), true);

        // Initialize UI components
        selectedDateText = findViewById(R.id.selected_date);
        changeDateButton = findViewById(R.id.change_date_button);

        addBreakfastButton = findViewById(R.id.add_breakfast_button);
        addLunchButton = findViewById(R.id.add_lunch_button);
        addDinnerButton = findViewById(R.id.add_dinner_button);
        addSnacksButton = findViewById(R.id.add_snacks_button);

        breakfastRecycler = findViewById(R.id.breakfast_recycler);
        lunchRecycler = findViewById(R.id.lunch_recycler);
        dinnerRecycler = findViewById(R.id.dinner_recycler);
        snacksRecycler = findViewById(R.id.snacks_recycler);

        // Setup RecyclerViews
        setupRecyclerViews();

        // Setup click listeners
        changeDateButton.setOnClickListener(v -> showDatePicker());
        addBreakfastButton.setOnClickListener(v -> showAddMealDialog("breakfast"));
        addLunchButton.setOnClickListener(v -> showAddMealDialog("lunch"));
        addDinnerButton.setOnClickListener(v -> showAddMealDialog("dinner"));
        addSnacksButton.setOnClickListener(v -> showAddMealDialog("snacks"));
    }

    private void setupRecyclerViews() {
        // Initialize adapters
        breakfastAdapter = new MealAdapter(new ArrayList<>(), this);
        lunchAdapter = new MealAdapter(new ArrayList<>(), this);
        dinnerAdapter = new MealAdapter(new ArrayList<>(), this);
        snacksAdapter = new MealAdapter(new ArrayList<>(), this);

        // Setup RecyclerViews
        breakfastRecycler.setLayoutManager(new LinearLayoutManager(this));
        breakfastRecycler.setAdapter(breakfastAdapter);

        lunchRecycler.setLayoutManager(new LinearLayoutManager(this));
        lunchRecycler.setAdapter(lunchAdapter);

        dinnerRecycler.setLayoutManager(new LinearLayoutManager(this));
        dinnerRecycler.setAdapter(dinnerAdapter);

        snacksRecycler.setLayoutManager(new LinearLayoutManager(this));
        snacksRecycler.setAdapter(snacksAdapter);
    }

    private void setupNavigationDrawer() {
        // Open drawer when menu button clicked
        menuButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    drawerLayout.openDrawer(GravityCompat.START);
                } else {
                    drawerLayout.closeDrawer(GravityCompat.START);
                }
            }
        });

        // Handle navigation menu item clicks
        navigationView.setNavigationItemSelectedListener(new NavigationView.OnNavigationItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(@NonNull android.view.MenuItem item) {
                int id = item.getItemId();
                if (id == R.id.nav_logout) {
                    performLogout();
                } else if (id == R.id.nav_sync_now) {
                    performManualSync();
                } else if (id == R.id.nav_view_spreadsheet) {
                    viewSpreadsheet();
                } else if (id == R.id.nav_sync_settings) {
                    showSyncSettings();
                } else if (id == R.id.nav_home) {
                    // Already on home screen
                    Toast.makeText(MainActivity.this, "You're on the home screen", Toast.LENGTH_SHORT).show();
                } else if (id == R.id.nav_calendar) {
                    showDatePicker();
                }
                drawerLayout.closeDrawer(GravityCompat.START);
                return true;
            }
        });
    }

    private void setupBackPressHandling() {
        // Modern back press handling
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    drawerLayout.closeDrawer(GravityCompat.START);
                } else {
                    // Allow normal back press behavior
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });
    }

    private void showDatePicker() {
        DatePickerDialog datePickerDialog = new DatePickerDialog(
                this,
                (view, year, month, dayOfMonth) -> {
                    selectedDate.set(year, month, dayOfMonth);
                    updateDateDisplay();
                    loadMealsForSelectedDate();
                },
                selectedDate.get(Calendar.YEAR),
                selectedDate.get(Calendar.MONTH),
                selectedDate.get(Calendar.DAY_OF_MONTH)
        );
        datePickerDialog.show();
    }

    private void updateDateDisplay() {
        SimpleDateFormat dateFormat = new SimpleDateFormat("EEEE - MMMM d, yyyy", Locale.getDefault());
        String dateString = dateFormat.format(selectedDate.getTime());

        // Check if it's today
        Calendar today = Calendar.getInstance();
        if (selectedDate.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
                selectedDate.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)) {
            selectedDateText.setText("Today - " + dateFormat.format(selectedDate.getTime()));
        } else {
            selectedDateText.setText(dateString);
        }
    }

    private void showAddMealDialog(String category) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Add " + category.substring(0, 1).toUpperCase() + category.substring(1));

        // Create input field
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setHint("Enter meal name");
        builder.setView(input);

        builder.setPositiveButton("Add", (dialog, which) -> {
            String mealName = input.getText().toString().trim();
            if (!mealName.isEmpty()) {
                addMeal(mealName, category);
            } else {
                Toast.makeText(MainActivity.this, "Please enter a meal name", Toast.LENGTH_SHORT).show();
            }
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
        builder.show();
    }

    private void addMeal(String name, String category) {
        // Create meal with selected date
        Meal meal = createMealWithSelectedDate(name, category);
        allMeals.add(meal);
        loadMealsForSelectedDate();

        Toast.makeText(this, name + " added to " + category, Toast.LENGTH_SHORT).show();

        // Only sync to cloud if initialization is complete and successful
        if (isInitializationComplete && initializationSuccess) {
            syncMealToCloud(meal);
        } else if (!isInitializationComplete) {
            Toast.makeText(this, "Sync still initializing, meal saved locally", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Offline mode - meal saved locally", Toast.LENGTH_SHORT).show();
        }
    }

    private Meal createMealWithSelectedDate(String name, String category) {
        // Create timestamp for the selected date with current time
        Calendar mealTime = Calendar.getInstance();
        mealTime.set(selectedDate.get(Calendar.YEAR),
                selectedDate.get(Calendar.MONTH),
                selectedDate.get(Calendar.DAY_OF_MONTH));

        Date timestamp = mealTime.getTime();
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        String dateString = dateFormat.format(selectedDate.getTime());

        return new Meal(name, category, timestamp, dateString);
    }

    private void syncMealToCloud(Meal meal) {
        syncManager.syncMeal(meal, new MealSyncManager.SyncStatusListener() {
            @Override
            public void onSyncStarted() {
                // Optional: Show sync indicator
            }

            @Override
            public void onSyncCompleted(boolean success, String message) {
                mainHandler.post(() -> {
                    if (success) {
                        // Show subtle success indication
                        showSyncStatus("✓ Synced", false);
                    } else {
                        // Show error but don't be intrusive
                        showSyncStatus("⚠ Sync pending", true);
                    }
                });
            }

            @Override
            public void onSyncProgress(int completed, int total) {
                // Not used for single meal sync
            }
        });
    }

    private void loadMealsFromCloud() {
        // Only attempt if initialization is complete and successful
        if (!isInitializationComplete || !initializationSuccess) {
            return;
        }

        syncManager.loadMealsFromCloud(new GoogleSheetsManager.LoadCallback() {
            @Override
            public void onMealsLoaded(List<Meal> cloudMeals) {
                mainHandler.post(() -> {
                    // Merge with local meals (avoid duplicates)
                    mergeCloudMeals(cloudMeals);
                    loadMealsForSelectedDate();
                    showSyncStatus("✓ Data loaded", false);
                });
            }

            @Override
            public void onError(String error) {
                mainHandler.post(() -> {
                    // Don't show error on startup - user might be offline
                    showSyncStatus("⚠ Offline mode", true);
                });
            }
        });
    }

    private void mergeCloudMeals(List<Meal> cloudMeals) {
        // Simple merge - in production you might want more sophisticated conflict resolution
        for (Meal cloudMeal : cloudMeals) {
            boolean exists = false;
            for (Meal localMeal : allMeals) {
                if (mealsAreEqual(localMeal, cloudMeal)) {
                    exists = true;
                    break;
                }
            }
            if (!exists) {
                allMeals.add(cloudMeal);
            }
        }
    }

    private boolean mealsAreEqual(Meal meal1, Meal meal2) {
        return meal1.getName().equals(meal2.getName()) &&
                meal1.getCategory().equals(meal2.getCategory()) &&
                meal1.getDate().equals(meal2.getDate()) &&
                Math.abs(meal1.getTimestamp().getTime() - meal2.getTimestamp().getTime()) < 60000; // Within 1 minute
    }

    private void loadMealsForSelectedDate() {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        String selectedDateString = dateFormat.format(selectedDate.getTime());

        List<Meal> breakfastMeals = new ArrayList<>();
        List<Meal> lunchMeals = new ArrayList<>();
        List<Meal> dinnerMeals = new ArrayList<>();
        List<Meal> snacksMeals = new ArrayList<>();

        for (Meal meal : allMeals) {
            if (meal.getDate().equals(selectedDateString)) {
                switch (meal.getCategory()) {
                    case "breakfast":
                        breakfastMeals.add(meal);
                        break;
                    case "lunch":
                        lunchMeals.add(meal);
                        break;
                    case "dinner":
                        dinnerMeals.add(meal);
                        break;
                    case "snacks":
                        snacksMeals.add(meal);
                        break;
                }
            }
        }

        // Update adapters
        breakfastAdapter.updateMeals(breakfastMeals);
        lunchAdapter.updateMeals(lunchMeals);
        dinnerAdapter.updateMeals(dinnerMeals);
        snacksAdapter.updateMeals(snacksMeals);
    }

    @Override
    public void onMealDelete(int position) {
        // Find which adapter called this and get the meal
        Meal mealToDelete = findMealToDelete(position);
        if (mealToDelete != null) {
            showDeleteConfirmDialog(mealToDelete);
        }
    }

    private Meal findMealToDelete(int position) {
        // This is a simplified approach - you might want to improve this
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        String selectedDateString = dateFormat.format(selectedDate.getTime());

        List<Meal> mealsForDate = new ArrayList<>();
        for (Meal meal : allMeals) {
            if (meal.getDate().equals(selectedDateString)) {
                mealsForDate.add(meal);
            }
        }

        // This is a simple approach - in production you'd want to pass more context
        // to identify which specific meal was clicked
        if (position < mealsForDate.size()) {
            return mealsForDate.get(position);
        }
        return null;
    }

    private void showDeleteConfirmDialog(Meal meal) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Meal")
                .setMessage("Are you sure you want to delete \"" + meal.getName() + "\"?")
                .setPositiveButton("Delete", (dialog, which) -> deleteMeal(meal))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteMeal(Meal meal) {
        // Remove from local list
        allMeals.remove(meal);
        loadMealsForSelectedDate();

        // Only delete from cloud if sync is available
        if (isInitializationComplete && initializationSuccess) {
            // Delete from cloud
            syncManager.deleteMeal(meal, new MealSyncManager.SyncStatusListener() {
                @Override
                public void onSyncStarted() {
                    // Show deleting indicator
                }

                @Override
                public void onSyncCompleted(boolean success, String message) {
                    mainHandler.post(() -> {
                        if (success) {
                            Toast.makeText(MainActivity.this, "Meal deleted", Toast.LENGTH_SHORT).show();
                            showSyncStatus("✓ Deleted", false);
                        } else {
                            Toast.makeText(MainActivity.this, "Failed to delete from cloud", Toast.LENGTH_SHORT).show();
                            showSyncStatus("⚠ Delete failed", true);
                        }
                    });
                }

                @Override
                public void onSyncProgress(int completed, int total) {
                    // Not used
                }
            });
        } else {
            Toast.makeText(this, "Meal deleted locally (offline mode)", Toast.LENGTH_SHORT).show();
        }
    }

    private void performManualSync() {
        if (!isInitializationComplete) {
            Toast.makeText(this, "Sync is still initializing, please wait...", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!initializationSuccess) {
            Toast.makeText(this, "Sync service unavailable. Please check your connection and restart the app.", Toast.LENGTH_LONG).show();
            return;
        }

        showSyncProgress("Syncing data...");

        syncManager.performFullSync(allMeals, new MealSyncManager.SyncStatusListener() {
            @Override
            public void onSyncStarted() {
                // Already showing progress
            }

            @Override
            public void onSyncCompleted(boolean success, String message) {
                mainHandler.post(() -> {
                    hideSyncProgress();
                    if (success) {
                        Toast.makeText(MainActivity.this, "Sync completed successfully", Toast.LENGTH_SHORT).show();
                        // Reload data from cloud
                        loadMealsFromCloud();
                    } else {
                        Toast.makeText(MainActivity.this, "Sync failed: " + message, Toast.LENGTH_LONG).show();
                    }
                });
            }

            @Override
            public void onSyncProgress(int completed, int total) {
                mainHandler.post(() -> {
                    if (syncProgressDialog != null) {
                        syncProgressDialog.setMessage("Syncing " + completed + "/" + total + " meals...");
                    }
                });
            }
        });
    }

    private void viewSpreadsheet() {
        if (!isInitializationComplete || !initializationSuccess) {
            Toast.makeText(this, "Spreadsheet not available (sync not ready)", Toast.LENGTH_SHORT).show();
            return;
        }

        String url = syncManager.getSpreadsheetUrl();
        if (url != null) {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(android.net.Uri.parse(url));
            try {
                startActivity(intent);
            } catch (Exception e) {
                Toast.makeText(this, "Cannot open spreadsheet. URL copied to clipboard", Toast.LENGTH_LONG).show();
                // You could implement clipboard copy here if needed
            }
        } else {
            Toast.makeText(this, "Spreadsheet not ready yet. Try syncing first.", Toast.LENGTH_SHORT).show();
        }
    }

    private void showSyncSettings() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Sync Settings");

        String message = "Sync Status:\n\n";

        if (!isInitializationComplete) {
            message += "Status: Initializing...\n\n";
        } else if (initializationSuccess) {
            message += "Status: Ready ✓\n\n";
            message += "Last sync: " + getLastSyncTimeString() + "\n\n";

            if (syncManager.hasPendingSync()) {
                message += "Pending items: " + syncManager.getPendingSyncCount() + "\n\n";
                message += "Some items couldn't be synced. Try manual sync when you have internet connection.";
            } else {
                message += "All data is synced ✓";
            }
        } else {
            message += "Status: Unavailable ⚠\n\n";
            message += "Sync service is not available. App is running in offline mode.";
        }

        builder.setMessage(message);

        if (isInitializationComplete && initializationSuccess && syncManager.hasPendingSync()) {
            builder.setPositiveButton("Retry Sync", (dialog, which) -> retryPendingSync());
        }

        if (isInitializationComplete && initializationSuccess) {
            builder.setNeutralButton("Manual Sync", (dialog, which) -> performManualSync());
        }

        builder.setNegativeButton("Close", null);
        builder.show();
    }

    private void retryPendingSync() {
        if (!isInitializationComplete || !initializationSuccess) {
            Toast.makeText(this, "Sync service not available", Toast.LENGTH_SHORT).show();
            return;
        }

        showSyncProgress("Retrying sync...");

        syncManager.retryPendingSync(new MealSyncManager.SyncStatusListener() {
            @Override
            public void onSyncStarted() {
                // Already showing progress
            }

            @Override
            public void onSyncCompleted(boolean success, String message) {
                mainHandler.post(() -> {
                    hideSyncProgress();
                    Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onSyncProgress(int completed, int total) {
                // Update progress if needed
            }
        });
    }

    private String getLastSyncTimeString() {
        long lastSync = syncManager.getLastSyncTime();
        if (lastSync == 0) {
            return "Never";
        }

        long timeDiff = System.currentTimeMillis() - lastSync;
        if (timeDiff < 60000) { // Less than 1 minute
            return "Just now";
        } else if (timeDiff < 3600000) { // Less than 1 hour
            return (timeDiff / 60000) + " minutes ago";
        } else if (timeDiff < 86400000) { // Less than 1 day
            return (timeDiff / 3600000) + " hours ago";
        } else {
            return new SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(new Date(lastSync));
        }
    }

    private void showInitializationProgress(String message) {
        if (initializationDialog == null) {
            initializationDialog = new ProgressDialog(this);
            initializationDialog.setCancelable(false);
        }
        initializationDialog.setMessage(message);
        if (!initializationDialog.isShowing()) {
            initializationDialog.show();
        }
    }

    private void hideInitializationProgress() {
        if (initializationDialog != null && initializationDialog.isShowing()) {
            initializationDialog.dismiss();
        }
    }

    private void showSyncProgress(String message) {
        if (syncProgressDialog == null) {
            syncProgressDialog = new ProgressDialog(this);
            syncProgressDialog.setCancelable(false);
        }
        syncProgressDialog.setMessage(message);
        syncProgressDialog.show();
    }

    private void hideSyncProgress() {
        if (syncProgressDialog != null && syncProgressDialog.isShowing()) {
            syncProgressDialog.dismiss();
        }
    }

    private void showSyncStatus(String message, boolean isWarning) {
        // You can implement a subtle status indicator here
        // For now, just use toast for important messages
        if (isWarning) {
            // Only show warning toasts, not success messages to avoid spam
            // Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        }
    }

    private void performLogout() {
        // Sign out from Firebase
        mAuth.signOut();

        // Sign out from Google
        mGoogleSignInClient.signOut().addOnCompleteListener(this, task -> {
            // Cleanup sync manager
            if (syncManager != null) {
                syncManager.shutdown();
            }

            // Show logout message
            Toast.makeText(MainActivity.this, "Logged out successfully", Toast.LENGTH_SHORT).show();

            // Redirect to LoginActivity
            Intent intent = new Intent(MainActivity.this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (syncManager != null) {
            syncManager.shutdown();
        }
        if (syncProgressDialog != null && syncProgressDialog.isShowing()) {
            syncProgressDialog.dismiss();
        }
        if (initializationDialog != null && initializationDialog.isShowing()) {
            initializationDialog.dismiss();
        }
    }
}