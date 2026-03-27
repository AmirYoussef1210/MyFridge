package com.example.myfridge;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;

import androidx.appcompat.app.AlertDialog;

public class NetworkChangeReceiver extends BroadcastReceiver {

    private static boolean isConnected = false;
    private static AlertDialog networkDialog;

    private final Activity activity;

    public NetworkChangeReceiver(Activity activity) {
        this.activity = activity;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        Network activeNetwork = cm.getActiveNetwork();
        NetworkCapabilities capabilities = cm.getNetworkCapabilities(activeNetwork);
        isConnected = capabilities != null
                && (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET));

        if (!isConnected) {
            showDialog();
        } else {
            dismissDialog();
        }
    }

    private void showDialog() {
        if ((networkDialog == null || !networkDialog.isShowing()) && !activity.isFinishing()) {
            networkDialog = new AlertDialog.Builder(activity)
                    .setTitle("No Internet Connection")
                    .setMessage("MyFridge requires an internet connection. Please reconnect to continue.")
                    .setCancelable(false)
                    .setPositiveButton("Wi-Fi Settings", (dialog, which) ->
                            activity.startActivity(new Intent(android.provider.Settings.ACTION_WIFI_SETTINGS)))
                    .create();
            networkDialog.show();
        }
    }

    private void dismissDialog() {
        if (networkDialog != null && networkDialog.isShowing()) {
            networkDialog.dismiss();
            networkDialog = null;
        }
    }

    public static boolean isConnected() {
        return isConnected;
    }
}
