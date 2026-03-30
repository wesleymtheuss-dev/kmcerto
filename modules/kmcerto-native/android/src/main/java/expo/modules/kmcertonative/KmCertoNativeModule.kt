package expo.modules.kmcertonative

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition
import org.json.JSONObject
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.absoluteValue

class KmCertoNativeModule : Module() {
  override fun definition() = ModuleDefinition {
    Name("KmCertoNative")
    Events("KmCertoOverlayData")

    AsyncFunction("isOverlayPermissionGranted") {
      val ctx = appContext.reactContext ?: return@AsyncFunction false
      KmCertoRuntime.bindContext(ctx)
      Settings.canDrawOverlays(ctx)
    }

    AsyncFunction("isAccessibilityServiceEnabled") {
      val ctx = appContext.reactContext ?: return@AsyncFunction false
      KmCertoRuntime.bindContext(ctx)
      KmCertoAccessibilityService.isEnabled(ctx)
    }

    AsyncFunction("openOverlaySettings") {
      val ctx = appContext.reactContext ?: return@AsyncFunction false
      KmCertoRuntime.bindContext(ctx)
      try {
        val intent = Intent(
          Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
          Uri.parse("package:${ctx.packageName}"),
        ).apply {
          addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        ctx.startActivity(intent)
        true
      } catch (_: Throwable) {
        false
      }
    }

    AsyncFunction("openAccessibilitySettings") {
      val ctx = appContext.reactContext ?: return@AsyncFunction false
      KmCertoRuntime.bindContext(ctx)
      try {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
          addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        ctx.startActivity(intent)
        true
      } catch (_: Throwable) {
        false
      }
    }

    AsyncFunction("startMonitoring") {
      val ctx = appContext.reactContext ?: return@AsyncFunction false
      KmCertoRuntime.bindContext(ctx)
      KmCertoRuntime.setMonitoringEnabled(ctx, true)
      true
    }

    AsyncFunction("stopMonitoring") {
      val ctx = appContext.reactContext ?: return@AsyncFunction false
      KmCertoRuntime.bindContext(ctx)
      KmCertoRuntime.setMonitoringEnabled(ctx, false)
      KmCertoOverlayService.stop(ctx)
      true
    }

    AsyncFunction("hideOverlay") {
      val ctx = appContext.reactContext ?: return@AsyncFunction false
      KmCertoRuntime.bindContext(ctx)
      KmCertoOverlayService.stop(ctx)
      true
    }

    AsyncFunction("setMinimumPerKm") { value: Double ->
      val ctx = appContext.reactContext ?: return@AsyncFunction false
      KmCertoRuntime.bindContext(ctx)
      KmCertoRuntime.setMinimumPerKm(ctx, value)
      true
    }

    AsyncFunction("getMinimumPerKm") {
      val ctx = appContext.reactContext ?: return@AsyncFunction KmCertoRuntime.DEFAULT_MINIMUM_PER_KM
      KmCertoRuntime.bindContext(ctx)
      KmCertoRuntime.getMinimumPerKm(ctx)
    }

    AsyncFunction("showTestOverlay") { payload: String? ->
      val ctx = appContext.reactContext ?: return@AsyncFunction false
      KmCertoRuntime.bindContext(ctx)
      val parsed = KmCertoOfferParser.fromJsonPayload(
        payload = payload,
        minimumPerKm = KmCertoRuntime.getMinimumPerKm(ctx),
      ) ?: return@AsyncFunction false

      sendEvent("KmCertoOverlayData", mapOf(
        "totalFare" to parsed.totalFare,
        "totalFareLabel" to parsed.totalFareLabel,
        "status" to parsed.status,
        "statusColor" to parsed.statusColor,
        "perKm" to parsed.perKm,
        "perHour" to (parsed.perHour as Any?),
        "perMinute" to (parsed.perMinute as Any?),
        "minimumPerKm" to parsed.minimumPerKm,
        "sourceApp" to parsed.sourceApp,
        "rawText" to parsed.rawText,
      ))
      KmCertoOverlayService.show(ctx, parsed)
      true
    }
  }
}

object KmCertoRuntime {
  const val DEFAULT_MINIMUM_PER_KM = 1.5
  private const val PREFERENCES_NAME = "kmcerto_native_preferences"
  private const val KEY_MINIMUM_PER_KM = "minimum_per_km"
  private const val KEY_MONITORING_ENABLED = "monitoring_enabled"

  private var appContext: Context? = null

  val supportedPackages: Map<String, String> = mapOf(
    "br.com.ifood.driver.app" to "iFood",
    "com.app99.driver" to "99Food",
    "com.ubercab.driver" to "Uber",
  )

  fun bindContext(context: Context) {
    appContext = context.applicationContext
  }

  fun setMinimumPerKm(context: Context, value: Double) {
    context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
      .edit()
      .putFloat(KEY_MINIMUM_PER_KM, value.toFloat())
      .apply()
  }

  fun getMinimumPerKm(context: Context): Double {
    val stored = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
      .getFloat(KEY_MINIMUM_PER_KM, DEFAULT_MINIMUM_PER_KM.toFloat())
    return stored.toDouble()
  }

  fun setMonitoringEnabled(context: Context, enabled: Boolean) {
    context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
      .edit()
      .putBoolean(KEY_MONITORING_ENABLED, enabled)
      .apply()
  }

  fun isMonitoringEnabled(context: Context): Boolean {
    return context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
      .getBoolean(KEY_MONITORING_ENABLED, true)
  }

  fun supportsPackage(packageName: String): Boolean {
    return supportedPackages.keys.any { key -> packageName == key || packageName.startsWith("$key:") }
  }

  fun sourceLabel(packageName: String): String {
    return supportedPackages.entries.firstOrNull { packageName == it.key || packageName.startsWith("${it.key}:") }
      ?.value
      ?: packageName.substringAfterLast('.')
  }
}

data class OfferDecisionData(
  val totalFare: Double,
  val totalFareLabel: String,
  val status: String,
  val statusColor: String,
  val perKm: Double,
  val perHour: Double?,
  val perMinute: Double?,
  val minimumPerKm: Double,
  val sourceApp: String,
  val rawText: String,
) {
  fun toJson(): String {
    return JSONObject().apply {
      put("totalFare", totalFare)
      put("totalFareLabel", totalFareLabel)
      put("status", status)
      put("statusColor", statusColor)
      put("perKm", perKm)
      put("perHour", perHour)
      put("perMinute", perMinute)
      put("minimumPerKm", minimumPerKm)
      put("sourceApp", sourceApp)
      put("rawText", rawText)
    }.toString()
  }

  companion object {
    fun fromJson(json: String?): OfferDecisionData? {
      if (json.isNullOrBlank()) return null
      return try {
        val payload = JSONObject(json)
        OfferDecisionData(
          totalFare = payload.optDouble("totalFare", Double.NaN),
          totalFareLabel = payload.optString("totalFareLabel", ""),
          status = payload.optString("status", "RECUSAR"),
          statusColor = payload.optString("statusColor", "#DC2626"),
          perKm = payload.optDouble("perKm", Double.NaN),
          perHour = if (payload.has("perHour") && !payload.isNull("perHour")) payload.optDouble("perHour") else null,
          perMinute = if (payload.has("perMinute") && !payload.isNull("perMinute")) payload.optDouble("perMinute") else null,
          minimumPerKm = payload.optDouble("minimumPerKm", KmCertoRuntime.DEFAULT_MINIMUM_PER_KM),
          sourceApp = payload.optString("sourceApp", "KmCerto"),
          rawText = payload.optString("rawText", ""),
        )
      } catch (_: Throwable) {
        null
      }
    }
  }
}

object KmCertoOfferParser {
  private const val TAG = "KmCerto"
  private val locale = Locale("pt", "BR")

  // === REGEX PRINCIPAL: R$ seguido de valor ===
  // Aceita: R$ 16,20 | R$16,20 | R$ 32.79 | R$11,66
  // Também aceita separadores como " | ", espaços, tabs, etc entre R$ e o número
  private val currencyRegex = Regex(
    """R\$\s*[|\s]*(\d{1,4}(?:\.\d{3})*,\d{2}|\d+[.,]\d{1,2})"""
  )

  // === REGEX FALLBACK: valor monetário sem R$ ===
  // Para quando o "R$" está em um nó separado do número
  // Captura números no formato XX,XX (2 casas decimais com vírgula) que parecem valores monetários
  private val bareMoneyRegex = Regex(
    """(?<!\d)(\d{1,4},\d{2})(?!\d)"""
  )

  // Regex para capturar distância em km: 16,9km ou 13.9 km ou 2,2km ou 29.3 km
  private val kmRegex = Regex("""(\d{1,4}[.,]\d{1,2})\s*km""", RegexOption.IGNORE_CASE)

  // Regex para capturar minutos: 28min ou 30 min ou 7min ou 48 min
  private val minuteRegex = Regex("""(\d{1,3})\s*min""", RegexOption.IGNORE_CASE)

  fun parse(context: Context?, rawText: String, minimumPerKm: Double, sourcePackage: String): OfferDecisionData? {
    // Normalizar: remover quebras de linha, múltiplos espaços
    val normalizedText = rawText.replace("\n", " ").replace(Regex("\\s+"), " ").trim()
    if (normalizedText.isBlank()) return null

    Log.d(TAG, "=== PARSE START ===")
    Log.d(TAG, "Package: $sourcePackage")
    Log.d(TAG, "Text (first 500): ${normalizedText.take(500)}")

    // ========================================================
    // PASSO 1: Extrair distância em KM (precisa ter km para ser oferta)
    // ========================================================
    val kmMatches = kmRegex.findAll(normalizedText).toList()
    Log.d(TAG, "KM matches: ${kmMatches.map { it.value }}")

    val distances = kmMatches.mapNotNull { it.groupValues.getOrNull(1)?.let(::parsePtBrNumber) }
      .filter { it > 0 && it.isFinite() }
    Log.d(TAG, "Parsed distances: $distances")

    // Se não tem KM, não é uma oferta de corrida - sair silenciosamente
    if (distances.isEmpty()) {
      Log.d(TAG, "No KM found - not a ride offer, skipping")
      return null
    }

    // Usar a MAIOR distância (distância total)
    val distance = distances.maxOrNull()!!

    // ========================================================
    // PASSO 2: Extrair valor em R$
    // ========================================================
    var fare: Double? = null
    var fareSource = ""

    // Tentativa 1: Regex principal com R$
    val fareMatch = currencyRegex.find(normalizedText)
    if (fareMatch != null) {
      fare = fareMatch.groupValues.getOrNull(1)?.let(::parsePtBrNumber)
      fareSource = "regex R$"
      Log.d(TAG, "Fare (R$ regex): '${fareMatch.value}' -> $fare")
    }

    // Tentativa 2: Se não achou com R$, procurar "R$" e número em nós separados
    // O texto vem como "nó1 | nó2 | nó3", então R$ pode estar em um nó e o valor em outro
    if (fare == null || fare <= 0 || !fare.isFinite()) {
      // Verificar se existe "R$" em algum lugar do texto
      val hasRealSign = normalizedText.contains("R$") || normalizedText.contains("R\$")
      if (hasRealSign) {
        // Procurar o primeiro número com formato monetário DEPOIS do R$
        val rIdx = normalizedText.indexOf("R$")
        if (rIdx >= 0) {
          val afterR = normalizedText.substring(rIdx + 2)
          val bareMatch = bareMoneyRegex.find(afterR)
          if (bareMatch != null) {
            fare = parsePtBrNumber(bareMatch.groupValues[1])
            fareSource = "R$ separado"
            Log.d(TAG, "Fare (R$ separado): '${bareMatch.value}' -> $fare")
          }
        }
      }
    }

    // Tentativa 3: Fallback total - procurar qualquer valor XX,XX no texto
    // Filtrar valores que fazem sentido como preço de corrida (entre 3 e 500)
    if (fare == null || fare <= 0 || !fare.isFinite()) {
      val allBareValues = bareMoneyRegex.findAll(normalizedText)
        .mapNotNull { it.groupValues.getOrNull(1)?.let(::parsePtBrNumber) }
        .filter { it in 3.0..500.0 }
        .toList()
      Log.d(TAG, "Bare money values found: $allBareValues")

      if (allBareValues.isNotEmpty()) {
        // Pegar o primeiro valor razoável (geralmente o valor da corrida aparece primeiro)
        fare = allBareValues.first()
        fareSource = "fallback bare"
        Log.d(TAG, "Fare (fallback): $fare")
      }
    }

    // Se mesmo assim não achou, mostrar debug
    if (fare == null || fare <= 0 || !fare.isFinite()) {
      Log.d(TAG, "FAIL: No fare found at all")
      if (context != null) {
        val preview = normalizedText.take(150)
        Handler(Looper.getMainLooper()).post {
          Toast.makeText(context, "KmCerto debug: $preview", Toast.LENGTH_LONG).show()
        }
      }
      return null
    }

    // ========================================================
    // PASSO 3: Extrair minutos
    // ========================================================
    val minMatches = minuteRegex.findAll(normalizedText).toList()
    val minutes = minMatches.mapNotNull { it.groupValues.getOrNull(1)?.toDoubleOrNull() }
      .filter { it > 0 }
      .let { values ->
        if (values.isEmpty()) null
        else if (values.size == 1) values.first()
        else values.sum() // Somar pickup + dropoff
      }

    Log.d(TAG, "Minutes: $minutes")

    // ========================================================
    // PASSO 4: Calcular métricas
    // ========================================================
    val perKm = fare / distance
    val perMinute = if (minutes != null && minutes > 0) fare / minutes else null
    val perHour = if (minutes != null && minutes > 0) fare / (minutes / 60.0) else null
    val shouldAccept = perKm + 0.0001 >= minimumPerKm

    Log.d(TAG, "RESULT: fare=$fare dist=$distance perKm=$perKm accept=$shouldAccept fareSource=$fareSource")

    return OfferDecisionData(
      totalFare = fare,
      totalFareLabel = formatCurrency(fare),
      status = if (shouldAccept) "ACEITAR" else "RECUSAR",
      statusColor = if (shouldAccept) "#16A34A" else "#DC2626",
      perKm = round2(perKm),
      perHour = perHour?.let(::round2),
      perMinute = perMinute?.let(::round2),
      minimumPerKm = round2(minimumPerKm),
      sourceApp = KmCertoRuntime.sourceLabel(sourcePackage),
      rawText = normalizedText.take(300),
    )
  }

  fun fromJsonPayload(payload: String?, minimumPerKm: Double): OfferDecisionData? {
    if (payload.isNullOrBlank()) return null
    return try {
      val json = JSONObject(payload)
      val totalFare = json.optDouble("totalFare", Double.NaN)
      val perKm = json.optDouble("perKm", Double.NaN)
      val perHour = if (json.has("perHour") && !json.isNull("perHour")) json.optDouble("perHour") else null
      val perMinute = if (json.has("perMinute") && !json.isNull("perMinute")) json.optDouble("perMinute") else null
      if (!totalFare.isFinite() || !perKm.isFinite()) return null

      OfferDecisionData(
        totalFare = totalFare,
        totalFareLabel = json.optString("totalFareLabel", formatCurrency(totalFare)),
        status = json.optString("status", if (perKm >= minimumPerKm) "ACEITAR" else "RECUSAR"),
        statusColor = json.optString("statusColor", if (perKm >= minimumPerKm) "#16A34A" else "#DC2626"),
        perKm = round2(perKm),
        perHour = perHour?.let(::round2),
        perMinute = perMinute?.let(::round2),
        minimumPerKm = json.optDouble("minimumPerKm", minimumPerKm),
        sourceApp = json.optString("sourceApp", "Teste manual"),
        rawText = json.optString("rawText", ""),
      )
    } catch (_: Throwable) {
      null
    }
  }

  /**
   * Converte número no formato brasileiro para Double.
   * "16,20" -> 16.20
   * "12,20" -> 12.20
   * "16,9" -> 16.9
   * "13.9" -> 13.9 (sem vírgula, trata ponto como decimal)
   * "1.234,56" -> 1234.56 (milhar com ponto)
   */
  private fun parsePtBrNumber(raw: String): Double {
    val s = raw.trim()
    if (s.isEmpty()) return Double.NaN

    // Se tem vírgula, é formato BR: ponto é milhar, vírgula é decimal
    if (s.contains(",")) {
      return s.replace(".", "").replace(",", ".").toDoubleOrNull() ?: Double.NaN
    }

    // Se não tem vírgula, o ponto é decimal (ex: 13.9, 2.2)
    return s.toDoubleOrNull() ?: Double.NaN
  }

  private fun formatCurrency(value: Double): String {
    return NumberFormat.getCurrencyInstance(locale).format(value)
  }

  private fun round2(value: Double): Double {
    return kotlin.math.round(value * 100.0) / 100.0
  }
}

class KmCertoAccessibilityService : AccessibilityService() {
  private val TAG = "KmCerto"
  private var lastSignature: String? = null
  private var lastEmissionAt: Long = 0
  private var lastDebugToastAt: Long = 0

  override fun onServiceConnected() {
    super.onServiceConnected()
    Log.d(TAG, ">>> AccessibilityService CONNECTED <<<")

    serviceInfo = AccessibilityServiceInfo().apply {
      eventTypes = AccessibilityEvent.TYPES_ALL_MASK
      feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
      flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
        AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
        AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
      notificationTimeout = 100
      packageNames = arrayOf(
        "br.com.ifood.driver.app",
        "com.app99.driver",
        "com.ubercab.driver"
      )
    }

    Handler(Looper.getMainLooper()).post {
      Toast.makeText(this, "KmCerto: Serviço de acessibilidade ATIVO!", Toast.LENGTH_LONG).show()
    }
  }

  override fun onAccessibilityEvent(event: AccessibilityEvent?) {
    if (event == null) return

    val packageName = event.packageName?.toString() ?: return

    // Verificar se é um dos apps monitorados
    if (!KmCertoRuntime.supportsPackage(packageName)) return
    if (!KmCertoRuntime.isMonitoringEnabled(this)) return

    Log.d(TAG, "EVENT from $packageName type=${event.eventType}")

    // ========================================================
    // COLETA DE TEXTO: Múltiplas estratégias
    // ========================================================
    val allTexts = mutableListOf<String>()

    // Estratégia 1: rootInActiveWindow - percorre toda a árvore de views
    val root = try { rootInActiveWindow } catch (_: Throwable) { null }
    if (root != null) {
      val windowTexts = collectAllText(root)
      if (windowTexts.isNotBlank()) {
        allTexts.add(windowTexts)
      }
      try { root.recycle() } catch (_: Throwable) { }
    }

    // Estratégia 2: Texto do evento
    event.text?.forEach { t ->
      val s = t?.toString()?.trim()
      if (!s.isNullOrBlank() && s !in allTexts) allTexts.add(s)
    }
    val cd = event.contentDescription?.toString()?.trim()
    if (!cd.isNullOrBlank() && cd !in allTexts) allTexts.add(cd)

    // Estratégia 3: source do evento
    val source = try { event.source } catch (_: Throwable) { null }
    if (source != null) {
      val sourceText = collectAllText(source)
      if (sourceText.isNotBlank() && sourceText !in allTexts) {
        allTexts.add(sourceText)
      }
      try { source.recycle() } catch (_: Throwable) { }
    }

    val combinedText = allTexts.joinToString(" | ")

    if (combinedText.isBlank()) {
      Log.d(TAG, "EMPTY text from $packageName")
      return
    }

    Log.d(TAG, "CAPTURED (${combinedText.length} chars): ${combinedText.take(400)}")

    // ========================================================
    // DEBUG TOAST: Mostrar texto capturado a cada 8 segundos
    // ========================================================
    val now = System.currentTimeMillis()
    if (now - lastDebugToastAt > 8000) {
      lastDebugToastAt = now
      val preview = combinedText.take(120).replace("\n", " ")
      Handler(Looper.getMainLooper()).post {
        Toast.makeText(this, "KmCerto [$packageName]:\n$preview", Toast.LENGTH_LONG).show()
      }
    }

    // ========================================================
    // PARSE: Tentar extrair dados da oferta
    // ========================================================
    val minimumPerKm = KmCertoRuntime.getMinimumPerKm(this)
    val parsed = KmCertoOfferParser.parse(
      context = this,
      rawText = combinedText,
      minimumPerKm = minimumPerKm,
      sourcePackage = packageName,
    )

    if (parsed == null) {
      Log.d(TAG, "PARSE returned null for $packageName")
      return
    }

    // Deduplicação: não mostrar overlay repetido
    val signature = listOf(
      packageName,
      parsed.totalFareLabel,
      parsed.status,
      parsed.perKm.toString(),
    ).joinToString("|")

    if (signature == lastSignature && now - lastEmissionAt < 3500) return

    lastSignature = signature
    lastEmissionAt = now

    Log.d(TAG, ">>> SHOWING OVERLAY: ${parsed.totalFareLabel} ${parsed.perKm}/km ${parsed.status} <<<")
    KmCertoOverlayService.show(this, parsed)
  }

  override fun onInterrupt() {
    Log.d(TAG, "AccessibilityService INTERRUPTED")
  }

  /**
   * Coleta TODO o texto visível da árvore de nós, incluindo text e contentDescription.
   * Junta com espaço simples (não com " | ") para manter R$ e valor juntos quando possível.
   */
  private fun collectAllText(root: AccessibilityNodeInfo): String {
    val parts = mutableListOf<String>()

    fun visit(node: AccessibilityNodeInfo?) {
      if (node == null) return

      val text = node.text?.toString()?.trim()
      if (!text.isNullOrBlank()) {
        parts.add(text)
      }

      val cd = node.contentDescription?.toString()?.trim()
      if (!cd.isNullOrBlank() && cd != text) {
        parts.add(cd)
      }

      for (i in 0 until node.childCount) {
        try {
          val child = node.getChild(i)
          if (child != null) {
            visit(child)
            try { child.recycle() } catch (_: Throwable) { }
          }
        } catch (_: Throwable) { }
      }
    }

    visit(root)

    // IMPORTANTE: Juntar com espaço simples " " em vez de " | "
    // Isso mantém "R$" e "32,79" juntos como "R$ 32,79" quando estão em nós adjacentes
    return parts.joinToString(" ")
  }

  companion object {
    fun isEnabled(context: Context): Boolean {
      val enabledServices = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
      ) ?: return false
      val expected = "${context.packageName}/${KmCertoAccessibilityService::class.java.name}"
      return TextUtils.SimpleStringSplitter(':').run {
        setString(enabledServices)
        any { it.equals(expected, ignoreCase = true) }
      }
    }
  }
}

class KmCertoOverlayService : Service() {
  private val handler = Handler(Looper.getMainLooper())
  private var windowManager: WindowManager? = null
  private var overlayView: LinearLayout? = null
  private val dismissRunnable = Runnable {
    hideOverlayInternal()
    stopForegroundCompat()
    stopSelf()
  }

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    when (intent?.action) {
      ACTION_HIDE -> {
        hideOverlayInternal()
        stopForegroundCompat()
        stopSelf()
        return START_NOT_STICKY
      }
      ACTION_SHOW -> {
        if (!Settings.canDrawOverlays(this)) {
          stopSelf()
          return START_NOT_STICKY
        }
        val payload = OfferDecisionData.fromJson(intent.getStringExtra(EXTRA_PAYLOAD)) ?: return START_NOT_STICKY
        startForegroundInternal()
        showOverlayInternal(payload)
        return START_NOT_STICKY
      }
    }
    return START_NOT_STICKY
  }

  override fun onDestroy() {
    handler.removeCallbacks(dismissRunnable)
    hideOverlayInternal()
    super.onDestroy()
  }

  private fun startForegroundInternal() {
    val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val channel = NotificationChannel(CHANNEL_ID, "KmCerto Overlay", NotificationManager.IMPORTANCE_LOW)
      channel.description = "Canal do overlay automático do KmCerto."
      manager.createNotificationChannel(channel)
    }
    val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      Notification.Builder(this, CHANNEL_ID)
        .setContentTitle("KmCerto ativo")
        .setContentText("Analisando ofertas e exibindo o overlay temporário.")
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setOngoing(true)
        .build()
    } else {
      @Suppress("DEPRECATION")
      Notification.Builder(this)
        .setContentTitle("KmCerto ativo")
        .setContentText("Analisando ofertas e exibindo o overlay temporário.")
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setOngoing(true)
        .build()
    }
    startForeground(NOTIFICATION_ID, notification)
  }

  private fun stopForegroundCompat() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
      stopForeground(STOP_FOREGROUND_REMOVE)
    } else {
      @Suppress("DEPRECATION")
      stopForeground(true)
    }
  }

  private fun showOverlayInternal(data: OfferDecisionData) {
    hideOverlayInternal()
    val manager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
    windowManager = manager
    val container = LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      gravity = Gravity.CENTER_HORIZONTAL
      setPadding(dp(20), dp(18), dp(20), dp(18))
      background = GradientDrawable().apply {
        setColor(Color.parseColor("#CC000000"))
        cornerRadius = dp(24).toFloat()
      }
    }
    val fareText = TextView(this).apply {
      text = data.totalFareLabel
      setTextColor(Color.WHITE)
      setTextSize(TypedValue.COMPLEX_UNIT_SP, 32f)
      setTypeface(typeface, Typeface.BOLD)
      gravity = Gravity.CENTER_HORIZONTAL
    }
    val statusText = TextView(this).apply {
      text = data.status
      setTextColor(Color.WHITE)
      setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
      setTypeface(typeface, Typeface.BOLD)
      gravity = Gravity.CENTER
      setPadding(dp(14), dp(8), dp(14), dp(8))
      background = GradientDrawable().apply {
        setColor(Color.parseColor(data.statusColor))
        cornerRadius = dp(999).toFloat()
      }
    }
    val sourceText = TextView(this).apply {
      text = data.sourceApp
      setTextColor(Color.parseColor("#CFCFD4"))
      setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
      gravity = Gravity.CENTER_HORIZONTAL
    }
    val metricRow = LinearLayout(this).apply {
      orientation = LinearLayout.HORIZONTAL
      gravity = Gravity.CENTER
      val gap = dp(10)
      showDividers = LinearLayout.SHOW_DIVIDER_MIDDLE
      dividerDrawable = GradientDrawable().apply {
        setSize(gap, 1)
        setColor(Color.TRANSPARENT)
      }
    }
    metricRow.addView(createMetricText("R$/km", data.perKm))
    data.perHour?.let { metricRow.addView(createMetricText("R$/hr", it)) }
    data.perMinute?.let { metricRow.addView(createMetricText("R$/min", it)) }
    container.addView(statusText)
    container.addView(spaceView(dp(10)))
    container.addView(fareText)
    container.addView(spaceView(dp(6)))
    container.addView(sourceText)
    container.addView(spaceView(dp(14)))
    container.addView(metricRow)
    val layoutParams = WindowManager.LayoutParams(
      WindowManager.LayoutParams.MATCH_PARENT,
      WindowManager.LayoutParams.WRAP_CONTENT,
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
      } else {
        @Suppress("DEPRECATION")
        WindowManager.LayoutParams.TYPE_PHONE
      },
      WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
      PixelFormat.TRANSLUCENT,
    ).apply {
      gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
      x = 0
      y = dp(72)
      width = WindowManager.LayoutParams.MATCH_PARENT
      horizontalMargin = 0f
    }
    try {
      manager.addView(container, layoutParams)
      overlayView = container
      handler.removeCallbacks(dismissRunnable)
      handler.postDelayed(dismissRunnable, AUTO_DISMISS_MS)
    } catch (_: Throwable) {
      hideOverlayInternal()
      stopForegroundCompat()
      stopSelf()
    }
  }

  private fun hideOverlayInternal() {
    handler.removeCallbacks(dismissRunnable)
    overlayView?.let { view ->
      try { windowManager?.removeView(view) } catch (_: Throwable) { }
    }
    overlayView = null
  }

  private fun createMetricText(label: String, value: Double): LinearLayout {
    return LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      gravity = Gravity.CENTER
      addView(TextView(this@KmCertoOverlayService).apply {
        text = KmCertoFormatters.decimal(value)
        setTextColor(Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
        setTypeface(typeface, Typeface.BOLD)
        gravity = Gravity.CENTER_HORIZONTAL
      })
      addView(TextView(this@KmCertoOverlayService).apply {
        text = label
        setTextColor(Color.parseColor("#CFCFD4"))
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
        gravity = Gravity.CENTER_HORIZONTAL
      })
    }
  }

  private fun spaceView(height: Int): TextView {
    return TextView(this).apply { minHeight = height }
  }

  private fun dp(value: Int): Int {
    return (value * resources.displayMetrics.density).toInt()
  }

  companion object {
    private const val ACTION_SHOW = "expo.modules.kmcertonative.action.SHOW_OVERLAY"
    private const val ACTION_HIDE = "expo.modules.kmcertonative.action.HIDE_OVERLAY"
    private const val EXTRA_PAYLOAD = "expo.modules.kmcertonative.extra.PAYLOAD"
    private const val AUTO_DISMISS_MS = 8_000L
    private const val CHANNEL_ID = "kmcerto_overlay"
    private const val NOTIFICATION_ID = 7071

    fun show(context: Context, payload: OfferDecisionData) {
      val intent = Intent(context, KmCertoOverlayService::class.java).apply {
        action = ACTION_SHOW
        putExtra(EXTRA_PAYLOAD, payload.toJson())
      }
      try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
          context.startForegroundService(intent)
        } else {
          context.startService(intent)
        }
      } catch (_: Throwable) { }
    }

    fun stop(context: Context) {
      val intent = Intent(context, KmCertoOverlayService::class.java).apply {
        action = ACTION_HIDE
      }
      try { context.startService(intent) } catch (_: Throwable) { }
    }
  }
}

object KmCertoFormatters {
  private val locale = Locale("pt", "BR")

  fun decimal(value: Double): String {
    return String.format(locale, "%.2f", value)
  }
}
