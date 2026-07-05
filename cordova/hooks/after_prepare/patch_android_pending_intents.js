/*
 * Cordova hook: modernise PendingIntent calls in legacy USB plugins.
 *
 * Several USB plugins used by the Android build still create broadcast
 * PendingIntents with a flags value of 0. Android 12+ requires an explicit
 * mutability flag. UsbManager adds the permission result and device as extras,
 * so these permission intents must remain mutable. The intent is also scoped
 * to this app's package to satisfy Android 14+ restrictions on mutable,
 * implicit PendingIntents.
 */

const fs = require("node:fs");
const path = require("node:path");

function walkJavaFiles(directory) {
  if (!fs.existsSync(directory)) {
    return [];
  }

  return fs.readdirSync(directory, { withFileTypes: true }).flatMap((entry) => {
    const entryPath = path.join(directory, entry.name);
    if (entry.isDirectory()) {
      return walkJavaFiles(entryPath);
    }
    return entry.isFile() && entry.name.endsWith(".java") ? [entryPath] : [];
  });
}

module.exports = function patchAndroidPendingIntents(context) {
  const projectRoot = context?.opts?.projectRoot || process.cwd();
  const javaRoot = path.join(
    projectRoot,
    "platforms",
    "android",
    "app",
    "src",
    "main",
    "java",
  );

  let patchedCalls = 0;

  for (const filePath of walkJavaFiles(javaRoot)) {
    const original = fs.readFileSync(filePath, "utf8");

    const updated = original.replace(
      /PendingIntent\.getBroadcast\(\s*([^,\n]+)\s*,\s*([^,\n]+)\s*,\s*new Intent\(([^)\n]+)\)\s*,\s*0\s*\)/g,
      (_match, contextExpression, requestCode, actionExpression) => {
        patchedCalls += 1;
        const androidContext = contextExpression.trim();
        return `PendingIntent.getBroadcast(${androidContext}, ${requestCode.trim()}, new Intent(${actionExpression.trim()}).setPackage(${androidContext}.getPackageName()), PendingIntent.FLAG_MUTABLE)`;
      },
    );

    if (updated !== original) {
      fs.writeFileSync(filePath, updated, "utf8");
      console.log(
        `[android-pending-intent] Patched ${path.relative(projectRoot, filePath)}`,
      );
    }
  }

  if (patchedCalls === 0) {
    console.log("[android-pending-intent] No legacy calls required patching.");
  } else {
    console.log(
      `[android-pending-intent] Patched ${patchedCalls} legacy PendingIntent call(s).`,
    );
  }
};
