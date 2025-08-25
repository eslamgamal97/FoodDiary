package com.eslamgamal.fooddiary;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.Scope;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;

public class LoginActivity extends AppCompatActivity {

    private static final String TAG = "LoginActivity";
    private static final int RC_SIGN_IN = 9001;
    private FirebaseAuth mAuth;
    private GoogleSignInClient mGoogleSignInClient;
    private ImageView googleSignInButton;
    private boolean isSigningIn = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        mAuth = FirebaseAuth.getInstance();
        setupGoogleSignIn();
        setupUI();
    }

    private void setupGoogleSignIn() {
        // Configure Google Sign In
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .requestScopes(
                        new Scope(SheetsScopes.SPREADSHEETS),
                        new Scope("https://www.googleapis.com/auth/drive.file")
                )
                .build();

        mGoogleSignInClient = GoogleSignIn.getClient(this, gso);
    }

    private void setupUI() {
        googleSignInButton = findViewById(R.id.custom_google_button);
        googleSignInButton.setOnClickListener(v -> {
            if (!isSigningIn) {
                signIn();
            }
        });
    }

    private void signIn() {
        if (isSigningIn) {
            Log.d(TAG, "Sign-in already in progress");
            return;
        }

        // Check network connectivity
        if (!NetworkUtils.isNetworkAvailable(this)) {
            showError("No internet connection", "Please check your internet connection and try again.");
            return;
        }

        isSigningIn = true;
        setSignInButtonEnabled(false);

        Intent signInIntent = mGoogleSignInClient.getSignInIntent();
        startActivityForResult(signInIntent, RC_SIGN_IN);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == RC_SIGN_IN) {
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            handleSignInResult(task);
        }
    }

    private void handleSignInResult(Task<GoogleSignInAccount> task) {
        try {
            GoogleSignInAccount account = task.getResult(ApiException.class);
            Log.d(TAG, "Google sign-in successful for: " + account.getEmail());
            firebaseAuthWithGoogle(account.getIdToken());

        } catch (ApiException e) {
            Log.w(TAG, "Google sign-in failed with code: " + e.getStatusCode(), e);
            handleSignInError(e);
        } finally {
            isSigningIn = false;
            setSignInButtonEnabled(true);
        }
    }

    private void handleSignInError(ApiException e) {
        String title = "Sign In Failed";
        String message;

        switch (e.getStatusCode()) {
            case 12501: // User canceled
                message = "Sign in was cancelled. Please try again.";
                break;
            case 12502: // Sign in currently in progress
                message = "Sign in is already in progress. Please wait.";
                break;
            case 12500: // Sign in failed
                message = "Sign in failed. Please check your Google Play Services and try again.";
                break;
            case 7: // Network error
                message = "Network error. Please check your internet connection and try again.";
                break;
            default:
                message = "Sign in failed with error code: " + e.getStatusCode() +
                        ". Please try again or contact support if the problem persists.";
                break;
        }

        showError(title, message);
    }

    private void firebaseAuthWithGoogle(String idToken) {
        if (idToken == null) {
            showError("Authentication Error", "Failed to get authentication token. Please try again.");
            return;
        }

        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        Log.d(TAG, "Firebase authentication successful");
                        FirebaseUser user = mAuth.getCurrentUser();

                        if (user != null) {
                            String displayName = user.getDisplayName();
                            if (displayName == null || displayName.trim().isEmpty()) {
                                displayName = user.getEmail();
                            }

                            Toast.makeText(LoginActivity.this,
                                    "Welcome " + displayName, Toast.LENGTH_SHORT).show();

                            navigateToMainActivity();
                        } else {
                            showError("Authentication Error", "Failed to get user information. Please try again.");
                        }
                    } else {
                        Log.e(TAG, "Firebase authentication failed", task.getException());
                        Exception exception = task.getException();
                        String errorMessage = "Authentication failed. Please try again.";

                        if (exception != null) {
                            errorMessage = "Authentication failed: " + exception.getMessage();
                        }

                        showError("Authentication Failed", errorMessage);
                    }
                });
    }

    private void navigateToMainActivity() {
        Intent intent = new Intent(LoginActivity.this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void showError(String title, String message) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("OK", null)
                .setNegativeButton("Retry", (dialog, which) -> signIn())
                .show();
    }

    private void setSignInButtonEnabled(boolean enabled) {
        if (googleSignInButton != null) {
            googleSignInButton.setEnabled(enabled);
            googleSignInButton.setAlpha(enabled ? 1.0f : 0.6f);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        isSigningIn = false;
    }
}