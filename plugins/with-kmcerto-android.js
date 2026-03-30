const { withAndroidManifest } = require("@expo/config-plugins");

function withKmCertoManifest(config) {
  return withAndroidManifest(config, (cfg) => {
    const manifest = cfg.modResults;
    const app = manifest.manifest.application[0];

    // Garante namespace tools no elemento raiz
    if (!manifest.manifest.$) manifest.manifest.$ = {};
    manifest.manifest.$["xmlns:tools"] = "http://schemas.android.com/tools";

    if (!manifest.manifest["uses-permission"]) {
      manifest.manifest["uses-permission"] = [];
    }
    const permissions = manifest.manifest["uses-permission"];
    const requiredPermissions = [
      "android.permission.SYSTEM_ALERT_WINDOW",
      "android.permission.FOREGROUND_SERVICE",
      "android.permission.BIND_ACCESSIBILITY_SERVICE",
      "android.permission.QUERY_ALL_PACKAGES"
    ];
    
    requiredPermissions.forEach(perm => {
      if (!permissions.some(p => p.$ && p.$["android:name"] === perm)) {
        permissions.push({ $: { "android:name": perm } });
      }
    });

    if (!app.service) app.service = [];
    const accessibilityServiceName = "expo.modules.kmcertonative.KmCertoAccessibilityService";
    app.service = app.service.filter(s => s.$ && s.$["android:name"] !== accessibilityServiceName);

    app.service.push({
      $: {
        "android:name": accessibilityServiceName,
        "android:permission": "android.permission.BIND_ACCESSIBILITY_SERVICE",
        "android:exported": "true",
        "android:label": "KmCerto Monitor",
        "tools:replace": "android:label"   // <-- linha nova
      },
      "intent-filter": [
        {
          action: [{ $: { "android:name": "android.accessibilityservice.AccessibilityService" } }]
        }
      ],
    });

    return cfg;
  });
}

module.exports = function withKmcertoAndroid(config) {
  return withKmCertoManifest(config);
};
