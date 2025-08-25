package com.eslamgamal.fooddiary;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.widget.ProgressBar;
import android.widget.TextView;

/**
 * Modern replacement for the deprecated ProgressDialog
 * This creates a custom dialog with material design styling
 */
public class ModernProgressDialog {

    private Dialog dialog;
    private TextView messageText;
    private ProgressBar progressBar;
    private boolean isShowing = false;

    public ModernProgressDialog(Context context) {
        createDialog(context);
    }

    private void createDialog(Context context) {
        dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        // Create the layout programmatically to avoid dependency on XML
        View dialogView = createDialogView(context);
        dialog.setContentView(dialogView);

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        dialog.setCancelable(false);
        dialog.setCanceledOnTouchOutside(false);
    }

    private View createDialogView(Context context) {
        // Create the main container
        android.widget.LinearLayout container = new android.widget.LinearLayout(context);
        container.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        container.setPadding(48, 32, 48, 32);
        container.setBackgroundColor(Color.WHITE);

        // Set corner radius programmatically
        android.graphics.drawable.GradientDrawable background = new android.graphics.drawable.GradientDrawable();
        background.setColor(Color.WHITE);
        background.setCornerRadius(16);
        container.setBackground(background);

        // Create progress bar
        progressBar = new ProgressBar(context);
        android.widget.LinearLayout.LayoutParams progressParams =
                new android.widget.LinearLayout.LayoutParams(72, 72);
        progressParams.setMargins(0, 0, 24, 0);
        progressBar.setLayoutParams(progressParams);

        // Create message text
        messageText = new TextView(context);
        messageText.setText("Loading...");
        messageText.setTextSize(16);
        messageText.setTextColor(Color.parseColor("#333333"));
        android.widget.LinearLayout.LayoutParams textParams =
                new android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                );
        textParams.gravity = android.view.Gravity.CENTER_VERTICAL;
        messageText.setLayoutParams(textParams);

        container.addView(progressBar);
        container.addView(messageText);

        return container;
    }

    public void setMessage(String message) {
        if (messageText != null) {
            messageText.setText(message);
        }
    }

    public void show() {
        if (dialog != null && !isShowing) {
            try {
                dialog.show();
                isShowing = true;
            } catch (Exception e) {
                // Handle case where activity is finishing
                isShowing = false;
            }
        }
    }

    public void dismiss() {
        if (dialog != null && isShowing) {
            try {
                dialog.dismiss();
                isShowing = false;
            } catch (Exception e) {
                // Handle case where activity is finishing
                isShowing = false;
            }
        }
    }

    public boolean isShowing() {
        return isShowing && dialog != null && dialog.isShowing();
    }

    public void setCancelable(boolean cancelable) {
        if (dialog != null) {
            dialog.setCancelable(cancelable);
            dialog.setCanceledOnTouchOutside(cancelable);
        }
    }

    // Static convenience methods
    public static ModernProgressDialog show(Context context, String message) {
        ModernProgressDialog progressDialog = new ModernProgressDialog(context);
        progressDialog.setMessage(message);
        progressDialog.show();
        return progressDialog;
    }

    public static ModernProgressDialog show(Context context, String message, boolean cancelable) {
        ModernProgressDialog progressDialog = new ModernProgressDialog(context);
        progressDialog.setMessage(message);
        progressDialog.setCancelable(cancelable);
        progressDialog.show();
        return progressDialog;
    }
}