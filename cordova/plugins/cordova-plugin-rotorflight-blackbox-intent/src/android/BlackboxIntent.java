package org.rotorflight.blackboxintent;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import androidx.core.content.FileProvider;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.json.JSONArray;
import org.json.JSONException;

import java.io.File;

public final class BlackboxIntent extends CordovaPlugin {
    private static final String ACTION_OPEN = "open";
    private static final String BLACKBOX_PACKAGE = "org.rotorflight.blackbox";
    private static final String BLACKBOX_MIME_TYPE = "application/x-blackbox-log";

    @Override
    public boolean execute(
        String action,
        JSONArray args,
        CallbackContext callbackContext
    ) throws JSONException {
        if (!ACTION_OPEN.equals(action)) {
            return false;
        }

        String fileUrl = args.optString(0, null);
        if (fileUrl == null || fileUrl.trim().isEmpty()) {
            callbackContext.error("missing_file_url");
            return true;
        }

        cordova.getThreadPool().execute(() -> openBlackbox(fileUrl, callbackContext));
        return true;
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

            Intent intent = new Intent(Intent.ACTION_VIEW)
                .setDataAndType(sharedUri, BLACKBOX_MIME_TYPE)
                .setPackage(BLACKBOX_PACKAGE)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            if (intent.resolveActivity(context.getPackageManager()) == null) {
                callbackContext.error("blackbox_not_installed");
                return;
            }

            context.grantUriPermission(
                BLACKBOX_PACKAGE,
                sharedUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            );
            context.startActivity(intent);
            callbackContext.success();
        } catch (ActivityNotFoundException error) {
            callbackContext.error("blackbox_not_installed");
        } catch (IllegalArgumentException | SecurityException error) {
            callbackContext.error("unable_to_share_log: " + error.getMessage());
        }
    }
}
