package org.rotorflight.blackboxintent;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

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
    private static final String BLACKBOX_MIME_TYPE = "application/x-blackbox-log";

    private CallbackContext pendingPickerCallback;

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
        if (pendingPickerCallback != null) {
            callbackContext.error("picker_busy");
            return;
        }

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

        if (callbackContext == null) {
            return;
        }

        if (resultCode != Activity.RESULT_OK || data == null || data.getData() == null) {
            callbackContext.error("selection_cancelled");
            return;
        }

        Uri selectedUri = data.getData();
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

        openBlackbox(selectedUri, callbackContext);
    }

    private void finishPickerWithError(String message) {
        CallbackContext callbackContext = pendingPickerCallback;
        pendingPickerCallback = null;
        if (callbackContext != null) {
            callbackContext.error(message);
        }
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
                    callbackContext.error("invalid_file_url");
                    return;
                }

                File file = new File(filePath);
                if (!file.isFile()) {
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
            callbackContext.error("unable_to_share_log: " + error.getMessage());
        }
    }

    private void openBlackbox(Uri sharedUri, CallbackContext callbackContext) {
        Activity activity = cordova.getActivity();

        activity.runOnUiThread(() -> {
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW)
                    .setDataAndType(sharedUri, BLACKBOX_MIME_TYPE)
                    .setPackage(BLACKBOX_PACKAGE)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

                activity.grantUriPermission(
                    BLACKBOX_PACKAGE,
                    sharedUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                );
                activity.startActivity(intent);
                callbackContext.success();
            } catch (ActivityNotFoundException error) {
                callbackContext.error("blackbox_not_installed");
            } catch (IllegalArgumentException | SecurityException error) {
                callbackContext.error("unable_to_share_log: " + error.getMessage());
            }
        });
    }

    @Override
    public void onReset() {
        finishPickerWithError("picker_cancelled");
    }

    @Override
    public void onDestroy() {
        finishPickerWithError("picker_cancelled");
    }
}
