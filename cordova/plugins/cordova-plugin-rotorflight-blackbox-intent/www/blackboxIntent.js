const cordovaExec = require("cordova/exec");

exports.open = function (fileUrl, onSuccess, onError) {
  cordovaExec(onSuccess, onError, "BlackboxIntent", "open", [fileUrl]);
};

exports.pickAndOpen = function (onSuccess, onError) {
  cordovaExec(onSuccess, onError, "BlackboxIntent", "pickAndOpen", []);
};
