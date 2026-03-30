const { withAndroidManifest } = require("@expo/config-plugins");

function withKmCertoManifest(config) {
  return withAndroidManifest(config, (cfg) => {
    const manifest = cfg.modResults;
    const app = manifest.manifest.application[0];

    // Ensure permissions
    if (!manifest.manifest["uses-permission"]) {
      manifest.manifest["uses-permission"] = [];
    }
    const permissions = manifest.manifest["uses-permission"];
    const requiredPermissions = [
      "android.permission.SYSTEM_ALERT_WINDOW",
      "android.permission.FOREGROUND_SERVICE",
      "android.permission.FOREGROUND_SERVICE_SPECIAL_USE",
    ];
    for (const perm of requiredPermissions) {
      const exists = permissions.some(
        (p) => p.$ && p.$["android:name"] === perm,
      );
      if (!exists) {
        permissions.push({ $: { "android:name": perm } });
      }
    }

    // Ensure service array exists
    if (!app.service) app.service = [];

    // Remove any existing declarations to avoid duplicates, then re-add
    const overlayServiceName =
      "expo.modules.kmcertonative.KmCertoOverlayService";
    const accessibilityServiceName =
      "expo.modules.kmcertonative.KmCertoAccessibilityService";

    app.service = app.service.filter(
      (s) =>
        s.$ &&
        s.$["android:name"] !== overlayServiceName &&
        s.$["android:name"] !== accessibilityServiceName,
    );

    // Add overlay service
    app.service.push({
      $: {
        "android:name": overlayServiceName,
        "android:exported": "false",
        "android:foregroundServiceType": "specialUse",
      },
      property: [
        {
          $: {
            "android:name": "android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE",
            "android:value": "overlay",
          },
        },
      ],
    });

    // Add accessibility service
    app.service.push({
      $: {
        "android:name": accessibilityServiceName,
        "android:exported": "true",
        "android:label": "KmCerto",
        "android:permission": "android.permission.BIND_ACCESSIBILITY_SERVICE",
      },
      "intent-filter": [
        {
          action: [
            {
              $: {
                "android:name":
                  "android.accessibilityservice.AccessibilityService",
              },
            },
          ],
        },
      ],
      "meta-data": [
        {
          $: {
            "android:name": "android.accessibilityservice",
            "android:resource": "@xml/kmcerto_accessibility_service_config",
          },
        },
      ],
    });

    return cfg;
  });
}

module.exports = function withKmcertoAndroid(config) {
  return withKmCertoManifest(config);
};
