package org.rotorflight.blackboxintent;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.json.JSONArray;
import org.json.JSONException;

import java.io.File;

public final class BlackboxIntent extends CordovaPlugin {
    private static final String ACTION_OPEN = "open";
    private static final String ACTION_PICK_AND_OPEN = "pickAndOpen";
    private static final String BLACKBOX_PACKAGE = "org.rotorflight.blackbox";
    private static final String BLACKBOX_ACTIVITY =
        "org.rotorflight.blackbox.MainActivity";
    private static final String BLACKBOX_MIME_TYPE = "application/x-blackbox-log";

    @Override
    public boolean execute(
        String action,
        JSONArray args,
        CallbackContext callbackContext
    ) throws JSONException {
        if (ACTION_OPEN.equals(action)) {
            String fileUrl = args.optString(0, null);
            if (fileUrl == null || fileUrl.trim().isEmpty()) {
                callbackContext.error("missing_file_url");
                return true;
            }

            cordova.getThreadPool().execute(() -> openBlackbox(fileUrl, callbackContext));
            return true;
        }

        if (ACTION_PICK_AND_OPEN.equals(action)) {
            launchNativePicker(callbackContext);
            return true;
        }

        return false;
    }

    private void launchNativePicker(CallbackContext callbackContext) {
        Activity activity = cordova.getActivity();
        activity.runOnUiThread(() -> {
            try {
                Intent intent = new Intent(activity, BlackboxPickerActivity.class);
                activity.startActivity(intent);
                callbackContext.success();
            } catch (RuntimeException error) {
                showNativeError(
                    "Android could not open the Blackbox file picker: " + safeMessage(error)
                );
                callbackContext.error("unable_to_open_picker: " + safeMessage(error));
            }
        });
    }

    private void openBlackbox(String fileUrl, CallbackContext callbackContext) {
        Context context = cordova.getActivity();

        try {
            Uri sourceUri = Uri.parse(fileUrl);
            Uri sharedUri;

            if ("content".equalsIgnoreCase(sourceUri.getScheme())) {
                sharedUri = sourceUri;
            } else {
                String filePath = "file".equalsIgnoreCase(sourceUri.getScheme())
                    ? sourceUri.getPath()
                    : fileUrl;

                if (filePath == null) {
                    showNativeError("The selected Blackbox file path is invalid.");
                    callbackContext.error("invalid_file_url");
                    return;
                }

                File file = new File(filePath);
                if (!file.isFile()) {
                    showNativeError("The selected Blackbox file could not be found.");
                    callbackContext.error("file_not_found");
                    return;
                }

                sharedUri = FileProvider.getUriForFile(
                    context,
                    context.getPackageName() + ".blackboxfiles",
                    file
                );
            }

            openBlackbox(sharedUri, callbackContext);
        } catch (IllegalArgumentException | SecurityException error) {
            showNativeError(
                "Android could not share the selected Blackbox log: "
                    + safeMessage(error)
            );
            callbackContext.error("unable_to_share_log: " + safeMessage(error));
        }
    }

    private void openBlackbox(Uri sharedUri, CallbackContext callbackContext) {
        Activity activity = cordova.getActivity();

        activity.runOnUiThread(() -> {
            try {
                activity.grantUriPermission(
                    BLACKBOX_PACKAGE,
                    sharedUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                );

                try {
                    activity.startActivity(createViewIntent(sharedUri));
                    callbackContext.success();
                    return;
                } catch (ActivityNotFoundException viewError) {
                    // Blackbox also accepts ACTION_SEND.
                }

                activity.startActivity(createSendIntent(sharedUri));
                callbackContext.success();
            } catch (ActivityNotFoundException error) {
                showNativeError(
                    "Rotorflight Blackbox could not be found. Open it once from the app drawer, then retry."
                );
                callbackContext.error("blackbox_not_installed");
            } catch (IllegalArgumentException | SecurityException error) {
                showNativeError(
                    "Android blocked the Blackbox handoff: " + safeMessage(error)
                );
                callbackContext.error("unable_to_share_log: " + safeMessage(error));
            } catch (RuntimeException error) {
                showNativeError(
                    "Rotorflight Blackbox could not be opened: " + safeMessage(error)
                );
                callbackContext.error("unable_to_open_blackbox: " + safeMessage(error));
            }
        });
    }

    private Intent createViewIntent(Uri sharedUri) {
        Intent intent = new Intent(Intent.ACTION_VIEW)
            .setComponent(new ComponentName(BLACKBOX_PACKAGE, BLACKBOX_ACTIVITY))
            .setDataAndType(sharedUri, BLACKBOX_MIME_TYPE);
        intent.setClipData(
            ClipData.newRawUri("Rotorflight Blackbox log", sharedUri)
        );
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return intent;
    }

    private Intent createSendIntent(Uri sharedUri) {
        Intent intent = new Intent(Intent.ACTION_SEND)
            .setPackage(BLACKBOX_PACKAGE)
            .setType(BLACKBOX_MIME_TYPE)
            .putExtra(Intent.EXTRA_STREAM, sharedUri);
        intent.setClipData(
            ClipData.newRawUri("Rotorflight Blackbox log", sharedUri)
        );
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return intent;
    }

    private static String safeMessage(Throwable error) {
        String message = error.getMessage();
        return message == null || message.trim().isEmpty()
            ? error.getClass().getSimpleName()
            : message;
    }

    private void showNativeError(String message) {
        Activity activity = cordova.getActivity();
        activity.runOnUiThread(() ->
            Toast.makeText(activity, message, Toast.LENGTH_LONG).show()
        );
    }
}
