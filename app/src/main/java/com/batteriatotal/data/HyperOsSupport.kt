package com.batteriatotal.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import java.util.Locale

/**
 * Integración best-effort con HyperOS y MIUI.
 *
 * Xiaomi cambia el nombre de las actividades entre versiones y modelos, así que
 * no se depende de un único ComponentName. El orden es:
 *
 * 1. Componentes conocidos de HyperOS/MIUI.
 * 2. Búsqueda de actividades exportadas cuyo nombre encaje con la función.
 * 3. Acciones públicas o implícitas del sistema.
 * 4. Ficha de la app / Ajustes como último recurso.
 *
 * Todo fallo se ignora: si una pantalla no existe, se abre una alternativa y se
 * avisa al usuario de la ruta manual.
 */
object HyperOsSupport {

    private const val SECURITY_CENTER = "com.miui.securitycenter"
    private const val SECURITY_ADD = "com.miui.securityadd"
    private val securityPackages = listOf(SECURITY_CENTER, SECURITY_ADD)

    private val autostartComponents = listOf(
        ComponentName(
            SECURITY_CENTER,
            "com.miui.permcenter.autostart.AutoStartManagementActivity"
        ),
        ComponentName(
            SECURITY_ADD,
            "com.miui.permcenter.autostart.AutoStartManagementActivity"
        ),
        ComponentName(
            SECURITY_CENTER,
            "com.miui.permcenter.autostart.AutoStartDetailActivity"
        )
    )

    private val chargeComponents = listOf(
        ComponentName(
            SECURITY_CENTER,
            "com.miui.powercenter.charge.ChargeFeatureActivity"
        ),
        ComponentName(
            SECURITY_CENTER,
            "com.miui.powercenter.charge.ChargeFeatureSettingsActivity"
        ),
        ComponentName(
            SECURITY_CENTER,
            "com.miui.powercenter.PowerSettings"
        ),
        ComponentName(
            SECURITY_CENTER,
            "com.miui.powercenter.PowerMainActivity"
        ),
        ComponentName(
            SECURITY_CENTER,
            "com.miui.powercenter.fastcharge.FastChargeActivity"
        )
    )

    private val gameTurboComponents = listOf(
        ComponentName(
            SECURITY_CENTER,
            "com.miui.gamebooster.ui.WelcomActivity"
        ),
        ComponentName(
            SECURITY_CENTER,
            "com.miui.gamebooster.ui.SettingsActivity"
        ),
        ComponentName(
            SECURITY_ADD,
            "com.miui.gamebooster.GameBoosterRichWebActivity"
        ),
        ComponentName(
            SECURITY_ADD,
            "com.miui.gamebooster.ui.WelcomActivity"
        ),
        ComponentName(
            SECURITY_ADD,
            "com.miui.gamebooster.GameBoosterMainActivity"
        )
    )

    /** true si el dispositivo es Xiaomi/Redmi/POCO o ejecuta HyperOS/MIUI. */
    fun isXiaomiFamily(): Boolean {
        val identity = listOf(
            Build.MANUFACTURER,
            Build.BRAND,
            Build.DEVICE,
            Build.PRODUCT
        ).joinToString(" ").lowercase(Locale.ROOT)
        return listOf("xiaomi", "redmi", "poco", "miui").any { it in identity }
    }

    fun openAutoStart(context: Context): Boolean {
        val intent = findFirstLaunchable(
            context = context,
            exact = autostartComponents,
            packages = securityPackages,
            keywords = listOf(
                "AutoStartManagementActivity",
                "AutoStartDetailActivity",
                "autostart"
            ),
            excludes = listOf("service", "receiver", "provider", "widget", "shortcut")
        ) ?: implicitAutoStartIntent(context)
        if (intent != null && launch(context, intent)) return true

        toast(
            context,
            "Abre Ajustes → Apps → Batería Total → Autoinicio en HyperOS/MIUI."
        )
        openAppDetails(context)
        return false
    }

    fun chargeSettingsIntent(context: Context): Intent {
        findFirstLaunchable(
            context = context,
            exact = chargeComponents,
            packages = securityPackages,
            keywords = listOf(
                "ChargeFeatureActivity",
                "ChargeFeatureSettingsActivity",
                "FastChargeActivity",
                "chargefeature",
                "fastcharge",
                "charginglimit",
                "powercenter.charge"
            ),
            excludes = listOf(
                "animation",
                "notification",
                "widget",
                "shortcut",
                "service",
                "receiver",
                "tile"
            )
        )?.let { return it.withNewTask() }

        val implicit = implicitChargeIntent(context)
        if (implicit != null) return implicit
        return Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun openChargeSettings(context: Context): Boolean {
        if (launch(context, chargeSettingsIntent(context))) return true
        toast(context, "Abre Ajustes → Batería → Carga en HyperOS/MIUI.")
        openAppDetails(context)
        return false
    }

    fun gameTurboIntent(context: Context): Intent {
        findFirstLaunchable(
            context = context,
            exact = gameTurboComponents,
            packages = securityPackages,
            keywords = listOf(
                "GameBoosterRichWebActivity",
                "GameBoosterMainActivity",
                "WelcomActivity",
                "gamebooster"
            ),
            excludes = listOf(
                "overlay",
                "service",
                "float",
                "notification",
                "widget",
                "shortcut",
                "tile",
                "key"
            )
        )?.let { return it.withNewTask() }

        val implicit = implicitGameTurboIntent(context)
        if (implicit != null) return implicit
        return Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun openGameTurbo(context: Context): Boolean {
        if (launch(context, gameTurboIntent(context))) return true
        toast(context, "Game Turbo no está disponible en este modelo.")
        openAppDetails(context)
        return false
    }

    private fun implicitAutoStartIntent(context: Context): Intent? {
        val candidates = listOf(
            Intent("miui.intent.action.OP_AUTO_START").addCategory(Intent.CATEGORY_DEFAULT),
            Intent("miui.intent.action.OP_AUTO_START").setPackage(SECURITY_CENTER),
            Intent("miui.intent.action.AUTO_START").addCategory(Intent.CATEGORY_DEFAULT),
            Intent("miui.intent.action.APP_AUTO_START").addCategory(Intent.CATEGORY_DEFAULT)
        )
        return candidates.firstOrNull { it.resolveActivity(context.packageManager) != null }
            ?.withNewTask()
    }

    private fun implicitChargeIntent(context: Context): Intent? {
        val candidates = listOf(
            Intent("miui.intent.action.CHARGE_FEATURE_MANAGER")
                .setPackage(SECURITY_CENTER),
            Intent("com.miui.powercenter.FAST_CHARGE").setPackage(SECURITY_CENTER),
            Intent("miui.intent.action.POWER_SETTINGS").setPackage(SECURITY_CENTER),
            Intent(Intent.ACTION_POWER_USAGE_SUMMARY)
        )
        return candidates.firstOrNull { it.resolveActivity(context.packageManager) != null }
            ?.withNewTask()
    }

    private fun implicitGameTurboIntent(context: Context): Intent? {
        val candidates = listOf(
            Intent("com.miui.gamebooster.action.ACCESS_MAINACTIVITY")
                .setPackage(SECURITY_CENTER)
                .addCategory(Intent.CATEGORY_DEFAULT),
            Intent("com.miui.gamebooster.action.ACCESS_MAINACTIVITY")
                .setPackage(SECURITY_ADD)
                .addCategory(Intent.CATEGORY_DEFAULT),
            Intent("miui.intent.action.GAME_BOOSTER"),
            Intent("miui.intent.action.GAME_TURBO")
        )
        return candidates.firstOrNull { it.resolveActivity(context.packageManager) != null }
            ?.withNewTask()
    }

    /**
     * Devuelve el primer intent que resuelve, probando componentes conocidos y
     * después actividades exportadas cuyo nombre encaje con [keywords].
     */
    private fun findFirstLaunchable(
        context: Context,
        exact: List<ComponentName>,
        packages: List<String>,
        keywords: List<String>,
        excludes: List<String>
    ): Intent? {
        for (component in exact) {
            val intent = Intent().setComponent(component).withNewTask()
            if (intent.resolveActivity(context.packageManager) != null) return intent
        }
        val scanned = scanExportedActivity(context, packages, keywords, excludes)
        return scanned?.let { Intent().setComponent(it).withNewTask() }
    }

    @Suppress("DEPRECATION")
    private fun scanExportedActivity(
        context: Context,
        packages: List<String>,
        keywords: List<String>,
        excludes: List<String>
    ): ComponentName? {
        val pm = context.packageManager
        for (packageName in packages) {
            val info = runCatching {
                pm.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES)
            }.getOrNull() ?: continue
            val activities = info.activities ?: continue
            val best = activities.asSequence()
                .filter { it.exported && it.enabled }
                .filter { activity ->
                    excludes.none { excluded ->
                        activity.name.contains(excluded, ignoreCase = true)
                    }
                }
                .map { activity -> activity to score(activity.name, keywords) }
                .filter { it.second > 0 }
                .maxByOrNull { it.second }
                ?: continue
            return ComponentName(packageName, best.first.name)
        }
        return null
    }

    private fun score(className: String, keywords: List<String>): Int {
        val normalized = className.lowercase(Locale.ROOT)
        var score = 0
        keywords.forEachIndexed { index, keyword ->
            if (normalized.contains(keyword.lowercase(Locale.ROOT))) {
                score += (keywords.size - index) * 10
            }
        }
        return score
    }

    private fun Intent.withNewTask(): Intent =
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private fun launch(context: Context, intent: Intent): Boolean = runCatching {
        if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
            true
        } else {
            false
        }
    }.getOrDefault(false)

    private fun openAppDetails(context: Context) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }

    private fun toast(context: Context, message: String) {
        runCatching {
            Toast.makeText(context.applicationContext, message, Toast.LENGTH_LONG).show()
        }
    }
}
