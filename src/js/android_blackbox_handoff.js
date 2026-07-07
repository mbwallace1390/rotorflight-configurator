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

function installDialogObserver() {
  const observer = new MutationObserver(updateButtonVisibility);
  observer.observe(document.body, {
    attributes: true,
    attributeFilter: ["class"],
    childList: true,
    subtree: true,
  });
}

window.addEventListener("rotorflight:blackbox-log-closed", (event) => {
  savedLog = event.detail;
  updateButtonVisibility();
});

document.addEventListener("click", (event) => {
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
        window.alert(`Unable to open the log in Rotorflight Blackbox: ${code || "unknown error"}`);
      }
    },
  );
});

installDialogObserver();
