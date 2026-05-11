package com.example.myfridge;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.DialogInterface;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;

import androidx.appcompat.app.AlertDialog;

/**
 * {@link BroadcastReceiver} that monitors network connectivity changes and
 * shows a non-cancellable "No Internet" dialog whenever the device loses all
 * network access, dismissing it automatically once connectivity is restored.
 * <p>
 * Register an instance in {@code onResume} and unregister it in {@code onPause}
 * of every Activity or Fragment that needs to respond to connectivity changes.
 * </p>
 */
public class NetworkChangeReceiver extends BroadcastReceiver {

    /** Whether the device currently has an active internet connection. */
    private static boolean isConnected = false;
    /** The currently displayed "No Internet" dialog, or {@code null} if none is showing. */
    private static AlertDialog networkDialog;

    /** The Activity used as the dialog context. */
    private final Activity activity;

    /**
     * Creates a new receiver tied to the given Activity.
     *
     * @param activity the Activity in whose context the dialog will be shown
     */
    public NetworkChangeReceiver(Activity activity) {
        this.activity = activity;
    }

    /**
     * Called by the system when a connectivity change is detected. Checks
     * whether Wi-Fi, cellular, or Ethernet is available and either shows the
     * "No Internet" dialog or dismisses it.
     *
     * @param context the context in which the receiver is running
     * @param intent  the broadcast intent (action {@link ConnectivityManager#CONNECTIVITY_ACTION})
     */
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

    /**
     * Creates and shows a non-cancellable "No Internet" {@link AlertDialog} if one is
     * not already visible and the host activity is still running.
     * Tapping the positive button opens the system Wi-Fi settings screen.
     */
    private void showDialog() {
        if ((networkDialog == null || !networkDialog.isShowing()) && !activity.isFinishing()) {
            networkDialog = new AlertDialog.Builder(activity)
                    .setTitle("No Internet Connection")
                    .setMessage("MyFridge requires an internet connection. Please reconnect to continue.")
                    .setCancelable(false)
                    .setPositiveButton("Wi-Fi Settings", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            activity.startActivity(new Intent(android.provider.Settings.ACTION_WIFI_SETTINGS));
                        }
                    })
                    .create();
            networkDialog.show();
        }
    }

    /**
     * Dismisses the currently displayed "No Internet" dialog, if one is showing,
     * and clears the {@link #networkDialog} reference so a new dialog can be
     * shown when connectivity is lost again.
     */
    private void dismissDialog() {
        if (networkDialog != null && networkDialog.isShowing()) {
            networkDialog.dismiss();
            networkDialog = null;
        }
    }

    /**
     * Returns whether the device currently has an active internet connection,
     * as determined by the most recent {@link #onReceive} callback.
     *
     * @return {@code true} if Wi-Fi, cellular, or Ethernet transport is available;
     *         {@code false} otherwise
     */
    public static boolean isConnected() {
        return isConnected;
    }
}
