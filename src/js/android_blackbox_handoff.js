let savedLog = null;
let massStoragePickerPending = false;

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

  if (mscBox) {
    mscBox.style.removeProperty("display");

    if (!mscBox.querySelector(".android-blackbox-msc-note")) {
      const note = document.createElement("p");
      note.className = "android-blackbox-msc-note note";
      note.textContent =
        "Recommended on Android: activate Mass Storage Device. The controller will disconnect briefly, then Android Files will open. Select the .BBL log to open it in Blackbox.";
      mscBox.querySelector(".spacer_box")?.appendChild(note);
    }
  }

  const dataflashButtons = document.querySelector(".dataflash-buttons");
  const oldNote = dataflashButtons?.querySelector(
    ".android-blackbox-download-note",
  );
  if (oldNote) {
    oldNote.remove();
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

function showHandoffError(error) {
  const code = typeof error === "string" ? error : error?.message;

  if (code === "selection_cancelled" || code === "picker_cancelled") {
    return;
  }

  if (code === "blackbox_not_installed") {
    window.alert("Rotorflight Blackbox Android is not installed.");
    return;
  }

  if (code === "no_document_picker") {
    window.alert("Android Files is unavailable on this device.");
    return;
  }

  window.alert(
    `Unable to open the log in Rotorflight Blackbox: ${code || "unknown error"}`,
  );
}

function startMassStoragePicker(mscButton) {
  const bridge = window.cordova?.plugins?.blackboxIntent;
  if (!bridge?.pickAndOpen) {
    window.alert("The Android Mass Storage handoff bridge is unavailable.");
    return false;
  }

  massStoragePickerPending = true;
  const originalText = mscButton.textContent;
  mscButton.textContent = "Switching to USB storage…";
  mscButton.setAttribute("aria-disabled", "true");

  // The normal Configurator click handler sends the MSC reboot command after
  // this capture listener returns. Give Android time to enumerate and mount the
  // controller as a USB drive, then open the system document picker.
  window.setTimeout(() => {
    bridge.pickAndOpen(
      () => {
        massStoragePickerPending = false;
        mscButton.textContent = originalText;
        mscButton.removeAttribute("aria-disabled");
      },
      (error) => {
        massStoragePickerPending = false;
        mscButton.textContent = originalText;
        mscButton.removeAttribute("aria-disabled");
        showHandoffError(error);
      },
    );
  }, 4500);

  return true;
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

      if (massStoragePickerPending) {
        event.stopImmediatePropagation();
        return;
      }

      if (!startMassStoragePicker(mscButton)) {
        event.stopImmediatePropagation();
      }
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
      showHandoffError,
    );
  },
  true,
);

installDialogObserver();
