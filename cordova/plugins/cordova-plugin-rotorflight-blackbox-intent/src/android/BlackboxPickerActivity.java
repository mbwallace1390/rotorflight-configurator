package org.rotorflight.blackboxintent;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

public final class BlackboxPickerActivity extends Activity {
    private static final int PICK_BLACKBOX_LOG_REQUEST = 4702;
    private static final String BLACKBOX_PACKAGE = "org.rotorflight.blackbox";
    private static final String BLACKBOX_ACTIVITY =
        "org.rotorflight.blackbox.MainActivity";
    private static final String BLACKBOX_MIME_TYPE = "application/x-blackbox-log";
    private static final String STATE_PICKER_LAUNCHED = "picker_launched";

    private boolean pickerLaunched;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        pickerLaunched = savedInstanceState != null
            && savedInstanceState.getBoolean(STATE_PICKER_LAUNCHED, false);

        if (!pickerLaunched) {
            launchDocumentPicker();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putBoolean(STATE_PICKER_LAUNCHED, pickerLaunched);
        super.onSaveInstanceState(outState);
    }

    private void launchDocumentPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType("*/*")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);

        try {
            pickerLaunched = true;
            startActivityForResult(intent, PICK_BLACKBOX_LOG_REQUEST);
        } catch (ActivityNotFoundException error) {
            showError("Android Files could not be opened.");
            finish();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode != PICK_BLACKBOX_LOG_REQUEST) {
            return;
        }

        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            showError("No Blackbox log was selected.");
            finish();
            return;
        }

        Uri selectedUri = data.getData();
        Toast.makeText(
            this,
            "Blackbox log selected. Opening viewer...",
            Toast.LENGTH_SHORT
        ).show();

        int takeFlags = data.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION;
        if (takeFlags != 0) {
            try {
                getContentResolver().takePersistableUriPermission(selectedUri, takeFlags);
            } catch (SecurityException ignored) {
                // USB document providers commonly grant only temporary access.
            }
        }

        openBlackbox(selectedUri);
    }

    private void openBlackbox(Uri sharedUri) {
        try {
            grantUriPermission(
                BLACKBOX_PACKAGE,
                sharedUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            );

            try {
                startActivity(createViewIntent(sharedUri));
                finish();
                return;
            } catch (ActivityNotFoundException viewError) {
                // Blackbox also accepts ACTION_SEND as a fallback.
            }

            startActivity(createSendIntent(sharedUri));
            finish();
        } catch (ActivityNotFoundException error) {
            showError(
                "Rotorflight Blackbox could not be found. Open it once from the app drawer, then retry."
            );
            finish();
        } catch (IllegalArgumentException | SecurityException error) {
            showError("Android blocked the Blackbox handoff: " + safeMessage(error));
            finish();
        } catch (RuntimeException error) {
            showError("Rotorflight Blackbox could not be opened: " + safeMessage(error));
            finish();
        }
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

    private void showError(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }
}
