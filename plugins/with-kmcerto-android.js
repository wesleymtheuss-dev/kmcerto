const { withAndroidManifest } = require("@expo/config-plugins");

function withKmCertoManifest(config) {
  return withAndroidManifest(config, (cfg) => {
    const manifest = cfg.modResults;
    const app = manifest.manifest.application[0];

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
        "android:label": "KmCerto Monitor"
      },
      "intent-filter": [
        {
          action: [{ $: { "android:name": "android.accessibilityservice.AccessibilityService" } }]
        }
      ],
      "meta-data": [
        {
          $: {
            "android:name": "android.accessibilityservice",
            "android:resource": "@xml/kmcerto_accessibility_service_config"
          }
        }
      ]
    });

    return cfg;
  });
}

module.exports = function withKmcertoAndroid(config) {
  return withKmCertoManifest(config);
};
