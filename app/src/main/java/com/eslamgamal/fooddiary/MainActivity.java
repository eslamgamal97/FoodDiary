package com.eslamgamal.fooddiary;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.os.Bundle;
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
import com.google.android.material.navigation.NavigationView;
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

        // Initialize data
        allMeals = new ArrayList<>();
        selectedDate = Calendar.getInstance();

        // Setup UI
        setupMealLoggingInterface();
        setupNavigationDrawer();
        setupBackPressHandling();

        // Load meals for today
        updateDateDisplay();
        loadMealsForSelectedDate();
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
                    MainActivity.super.onBackPressed();
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
        Meal meal = new Meal(name, category);
        allMeals.add(meal);
        loadMealsForSelectedDate();
        Toast.makeText(this, name + " added to " + category, Toast.LENGTH_SHORT).show();

        // TODO: Sync with Google Sheets
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
        // TODO: Implement meal deletion
        Toast.makeText(this, "Delete functionality coming soon", Toast.LENGTH_SHORT).show();
    }

    private void performLogout() {
        // Sign out from Firebase
        mAuth.signOut();

        // Sign out from Google
        mGoogleSignInClient.signOut().addOnCompleteListener(this, task -> {
            // Show logout message
            Toast.makeText(MainActivity.this, "Logged out successfully", Toast.LENGTH_SHORT).show();

            // Redirect to LoginActivity
            Intent intent = new Intent(MainActivity.this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });
    }
}