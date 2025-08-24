package com.eslamgamal.fooddiary;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

public class NetworkUtils {

    /**
     * Check if device has network connectivity
     * @param context Application context
     * @return true if network is available, false otherwise
     */
    public static boolean isNetworkAvailable(Context context) {
        if (context == null) {
            return false;
        }

        try {
            ConnectivityManager connectivityManager =
                    (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);

            if (connectivityManager == null) {
                return false;
            }

            NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
            return activeNetworkInfo != null && activeNetworkInfo.isConnected();

        } catch (SecurityException e) {
            // In case ACCESS_NETWORK_STATE permission is missing
            return true; // Assume network is available
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Check if device has WiFi connectivity
     * @param context Application context
     * @return true if WiFi is connected, false otherwise
     */
    public static boolean isWiFiConnected(Context context) {
        if (context == null) {
            return false;
        }

        try {
            ConnectivityManager connectivityManager =
                    (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);

            if (connectivityManager == null) {
                return false;
            }

            NetworkInfo wifiInfo = connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
            return wifiInfo != null && wifiInfo.isConnected();

        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Get network type name for debugging/logging
     * @param context Application context
     * @return Network type as string (WiFi, Mobile, etc.)
     */
    public static String getNetworkType(Context context) {
        if (context == null) {
            return "Unknown";
        }

        try {
            ConnectivityManager connectivityManager =
                    (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);

            if (connectivityManager == null) {
                return "Unknown";
            }

            NetworkInfo activeNetwork = connectivityManager.getActiveNetworkInfo();
            if (activeNetwork == null) {
                return "No Connection";
            }

            return activeNetwork.getTypeName();

        } catch (Exception e) {
            return "Unknown";
        }
    }
}