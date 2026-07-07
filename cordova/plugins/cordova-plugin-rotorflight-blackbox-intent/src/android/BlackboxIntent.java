package org.rotorflight.blackboxintent;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;

import java.io.File;

public final class BlackboxIntent extends CordovaPlugin {
    private static final String ACTION_OPEN = "open";
    private static final String ACTION_PICK_AND_OPEN = "pickAndOpen";
    private static final int PICK_BLACKBOX_LOG_REQUEST = 4701;
    private static final String BLACKBOX_PACKAGE = "org.rotorflight.blackbox";
    private static final String BLACKBOX_ACTIVITY =
        "org.rotorflight.blackbox.MainActivity";
    private static final String BLACKBOX_MIME_TYPE = "application/x-blackbox-log";
    private static final String STATE_PICKER_ACTIVE = "picker_active";

    private CallbackContext pendingPickerCallback;
    private boolean pickerActive;

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
            launchDocumentPicker(callbackContext);
            return true;
        }

        return false;
    }

    private void launchDocumentPicker(CallbackContext callbackContext) {
        if (pickerActive) {
            callbackContext.error("picker_busy");
            return;
        }

        pickerActive = true;
        pendingPickerCallback = callbackContext;
        PluginResult pendingResult = new PluginResult(PluginResult.Status.NO_RESULT);
        pendingResult.setKeepCallback(true);
        callbackContext.sendPluginResult(pendingResult);

        cordova.getActivity().runOnUiThread(() -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("*/*")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                .addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);

            try {
                cordova.startActivityForResult(
                    this,
                    intent,
                    PICK_BLACKBOX_LOG_REQUEST
                );
            } catch (ActivityNotFoundException error) {
                showNativeError("Android Files could not be opened.");
                finishPickerWithError("no_document_picker");
            }
        });
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode != PICK_BLACKBOX_LOG_REQUEST) {
            return;
        }

        CallbackContext callbackContext = pendingPickerCallback;
        pendingPickerCallback = null;
        pickerActive = false;

        if (resultCode != Activity.RESULT_OK || data == null || data.getData() == null) {
            showNativeError("No Blackbox log was selected.");
            sendError(callbackContext, "selection_cancelled");
            return;
        }

        Uri selectedUri = data.getData();
        showNativeStatus("Blackbox log selected. Opening viewer...");

        int takeFlags = data.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION;
        if (takeFlags != 0) {
            try {
                cordova.getActivity()
                    .getContentResolver()
                    .takePersistableUriPermission(selectedUri, takeFlags);
            } catch (SecurityException ignored) {
                // Some USB document providers grant temporary read access only.
            }
        }

        // Switching the flight controller from serial to USB mass storage can
        // reset Cordova while Android Files is open. The JavaScript callback may
        // be gone, but the selected content URI is still valid and must be sent
        // directly to the Blackbox app.
        openBlackbox(selectedUri, callbackContext);
    }

    @Override
    public Bundle onSaveInstanceState() {
        Bundle state = new Bundle();
        state.putBoolean(STATE_PICKER_ACTIVE, pickerActive);
        return state;
    }

    @Override
    public void onRestoreStateForActivityResult(
        Bundle state,
        CallbackContext callbackContext
    ) {
        pickerActive = state != null && state.getBoolean(STATE_PICKER_ACTIVE, false);
        pendingPickerCallback = pickerActive ? callbackContext : null;
    }

    private void finishPickerWithError(String message) {
        CallbackContext callbackContext = pendingPickerCallback;
        pendingPickerCallback = null;
        pickerActive = false;
        sendError(callbackContext, message);
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
                    sendError(callbackContext, "invalid_file_url");
                    return;
                }

                File file = new File(filePath);
                if (!file.isFile()) {
                    showNativeError("The selected Blackbox file could not be found.");
                    sendError(callbackContext, "file_not_found");
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
            sendError(callbackContext, "unable_to_share_log: " + safeMessage(error));
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

                Intent viewIntent = createViewIntent(sharedUri);
                try {
                    activity.startActivity(viewIntent);
                    sendSuccess(callbackContext);
                    return;
                } catch (ActivityNotFoundException viewError) {
                    // Some Android builds are stricter about VIEW resolution.
                    // Blackbox also declares ACTION_SEND for the same log types.
                }

                Intent sendIntent = createSendIntent(sharedUri);
                activity.startActivity(sendIntent);
                sendSuccess(callbackContext);
            } catch (ActivityNotFoundException error) {
                showNativeError(
                    "Rotorflight Blackbox could not be found. Open it once from the app drawer, then retry."
                );
                sendError(callbackContext, "blackbox_not_installed");
            } catch (IllegalArgumentException | SecurityException error) {
                showNativeError(
                    "Android blocked the Blackbox handoff: " + safeMessage(error)
                );
                sendError(callbackContext, "unable_to_share_log: " + safeMessage(error));
            } catch (RuntimeException error) {
                showNativeError(
                    "Rotorflight Blackbox could not be opened: " + safeMessage(error)
                );
                sendError(callbackContext, "unable_to_open_blackbox: " + safeMessage(error));
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

    private void sendSuccess(CallbackContext callbackContext) {
        if (callbackContext != null) {
            callbackContext.success();
        }
    }

    private void sendError(CallbackContext callbackContext, String message) {
        if (callbackContext != null) {
            callbackContext.error(message);
        }
    }

    private void showNativeStatus(String message) {
        Activity activity = cordova.getActivity();
        activity.runOnUiThread(() ->
            Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
        );
    }

    private void showNativeError(String message) {
        Activity activity = cordova.getActivity();
        activity.runOnUiThread(() ->
            Toast.makeText(activity, message, Toast.LENGTH_LONG).show()
        );
    }

    @Override
    public void onReset() {
        // Keep pickerActive true so an Android activity recreation can restore
        // the result route. The JavaScript callback itself may be replaced.
        pendingPickerCallback = null;
    }

    @Override
    public void onDestroy() {
        pendingPickerCallback = null;
    }
}
