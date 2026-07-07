let savedLog = null;

function getOpenButton() {
  return document.querySelector(".save-flash-open-blackbox");
}

function updateButtonVisibility() {
  const button = getOpenButton();
  if (!button) return;

  const dialog = button.closest(".dataflash-saving");
  const saveCompleted = dialog?.classList.contains("done");
  button.style.display = savedLog && saveCompleted ? "inline-block" : "none";
}

function updateAndroidBlackboxUi() {
  const mscButton = document.querySelector(".blackboxRebootMsc");
  const mscBox = mscButton?.closest(".gui_box");

  // Rebooting the flight controller into USB Mass Storage removes the serial
  // device that the Android Cordova app is currently using. Android cannot use
  // that desktop-style workflow, so keep it unavailable and use direct flash
  // download instead.
  if (mscBox) {
    mscBox.style.display = "none";
  }

  const dataflashButtons = document.querySelector(".dataflash-buttons");
  if (
    dataflashButtons &&
    !dataflashButtons.querySelector(".android-blackbox-download-note")
  ) {
    const note = document.createElement("p");
    note.className = "android-blackbox-download-note note";
    note.textContent =
      "Android: use Save flash to file. When the download finishes, tap Open in Blackbox.";
    dataflashButtons.appendChild(note);
  }

  updateButtonVisibility();
}

function installDialogObserver() {
  const observer = new MutationObserver(updateAndroidBlackboxUi);
  observer.observe(document.body, {
    attributes: true,
    attributeFilter: ["class"],
    childList: true,
    subtree: true,
  });

  updateAndroidBlackboxUi();
}

window.addEventListener("rotorflight:blackbox-log-closed", (event) => {
  savedLog = event.detail;
  updateButtonVisibility();
});

document.addEventListener(
  "click",
  (event) => {
    const mscButton = event.target.closest(".blackboxRebootMsc");
    if (mscButton) {
      event.preventDefault();
      event.stopImmediatePropagation();
      window.alert(
        "USB Mass Storage mode is not supported in the Android Configurator. Use Save flash to file, then tap Open in Blackbox when the download completes.",
      );
      return;
    }

    const button = event.target.closest(".save-flash-open-blackbox");
    if (!button) return;

    event.preventDefault();
    if (!savedLog) return;

    const bridge = window.cordova?.plugins?.blackboxIntent;
    if (!bridge) {
      window.alert("The Android Blackbox handoff bridge is unavailable.");
      return;
    }

    bridge.open(
      savedLog.url,
      () => {
        button.closest("dialog")?.close();
      },
      (error) => {
        const code = typeof error === "string" ? error : error?.message;
        if (code === "blackbox_not_installed") {
          window.alert("Rotorflight Blackbox Android is not installed.");
        } else {
          window.alert(
            `Unable to open the log in Rotorflight Blackbox: ${code || "unknown error"}`,
          );
        }
      },
    );
  },
  true,
);

installDialogObserver();
