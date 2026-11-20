/*
 * FLauncher
 * Copyright (C) 2021  Étienne Fesser
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package me.efesser.flauncher

import android.content.Intent
import android.content.Intent.*
import android.content.pm.ActivityInfo
import android.content.pm.LauncherApps
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.UserHandle
import android.provider.Settings
import android.view.KeyEvent
import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.NonNull
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.EventChannel.EventSink
import io.flutter.plugin.common.EventChannel.StreamHandler
import io.flutter.plugin.common.MethodChannel
import java.io.ByteArrayOutputStream
import java.io.Serializable

private const val METHOD_CHANNEL = "me.efesser.flauncher/method"
private const val EVENT_CHANNEL = "me.efesser.flauncher/event"
private const val REMOTE_MAPPINGS_PREFS = "flauncher_remote_mappings"
private const val REMOTE_MAPPING_PREFIX = "key_"

class MainActivity : FlutterActivity() {
    val launcherAppsCallbacks = ArrayList<LauncherApps.Callback>()
    private var buttonCaptureEnabled = false
    private var eventSink: EventSink? = null
    private val remoteMappings: SharedPreferences
        get() = getSharedPreferences(REMOTE_MAPPINGS_PREFS, Context.MODE_PRIVATE)

    override fun configureFlutterEngine(@NonNull flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, METHOD_CHANNEL).setMethodCallHandler { call, result ->
            when (call.method) {
                "getApplications" -> result.success(getApplications())
                "launchApp" -> result.success(launchApp(call.arguments as String))
                "openSettings" -> result.success(openSettings())
                "openAppInfo" -> result.success(openAppInfo(call.arguments as String))
                "uninstallApp" -> result.success(uninstallApp(call.arguments as String))
                "isDefaultLauncher" -> result.success(isDefaultLauncher())
                "checkForGetContentAvailability" -> result.success(checkForGetContentAvailability())
                "getRemoteButtonMappings" -> result.success(getRemoteButtonMappings())
                "setRemoteButtonMapping" -> {
                    val arguments = call.arguments as Map<*, *>
                    val keyCode = (arguments["keyCode"] as Number).toInt()
                    val packageName = arguments["packageName"] as String
                    remoteMappings.edit()
                        .putString("$REMOTE_MAPPING_PREFIX$keyCode", packageName)
                        .apply()
                    result.success(true)
                }
                "clearRemoteButtonMapping" -> {
                    val keyCode = (call.arguments as Number).toInt()
                    remoteMappings.edit()
                        .remove("$REMOTE_MAPPING_PREFIX$keyCode")
                        .apply()
                    result.success(true)
                }
                "startRemoteButtonCapture" -> {
                    buttonCaptureEnabled = true
                    result.success(true)
                }
                "stopRemoteButtonCapture" -> {
                    buttonCaptureEnabled = false
                    result.success(true)
                }
                else -> throw IllegalArgumentException()
            }
        }

        EventChannel(flutterEngine.dartExecutor.binaryMessenger, EVENT_CHANNEL).setStreamHandler(object : StreamHandler {
            lateinit var launcherAppsCallback: LauncherApps.Callback
            val launcherApps = getSystemService(LAUNCHER_APPS_SERVICE) as LauncherApps
            override fun onListen(arguments: Any?, events: EventSink) {
                eventSink = events
                launcherAppsCallback = object : LauncherApps.Callback() {
                    override fun onPackageRemoved(packageName: String, user: UserHandle) {
                        events.success(mapOf("action" to "PACKAGE_REMOVED", "packageName" to packageName))
                    }

                    override fun onPackageAdded(packageName: String, user: UserHandle) {
                        val applications = getApplication(packageName)
                        if (applications.isNotEmpty()) {
                            events.success(mapOf("action" to "PACKAGE_ADDED", "activitiesInfo" to applications))
                        }
                    }

                    override fun onPackageChanged(packageName: String, user: UserHandle) {
                        val applications = getApplication(packageName)
                        if (applications.isNotEmpty()) {
                            events.success(mapOf("action" to "PACKAGE_CHANGED", "activitiesInfo" to applications))
                        }
                    }

                    override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) {}
                    override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) {}
                }

                launcherAppsCallbacks.add(launcherAppsCallback)
                launcherApps.registerCallback(launcherAppsCallback)
            }

            override fun onCancel(arguments: Any?) {
                eventSink = null
                launcherApps.unregisterCallback(launcherAppsCallback)
                launcherAppsCallbacks.remove(launcherAppsCallback)
            }
        })
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            val keyCode = event.keyCode
            if (buttonCaptureEnabled) {
                buttonCaptureEnabled = false
                eventSink?.success(mapOf(
                    "action" to "REMOTE_BUTTON_CAPTURED",
                    "keyCode" to keyCode,
                    "keyLabel" to remoteButtonLabel(keyCode)
                ))
                return true
            }

            if (event.repeatCount == 0) {
                val packageName = remoteMappings.getString("$REMOTE_MAPPING_PREFIX$keyCode", null)
                if (!packageName.isNullOrBlank()) {
                    launchApp(packageName)
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onDestroy() {
        val launcherApps = getSystemService(LAUNCHER_APPS_SERVICE) as LauncherApps
        launcherAppsCallbacks.forEach(launcherApps::unregisterCallback)
        super.onDestroy()
    }

    private fun getApplications(): List<Map<String, Serializable?>> {
        val tvActivitiesInfo = queryIntentActivities(false)
        val nonTvActivitiesInfo = queryIntentActivities(true)
                .filter { nonTvActivityInfo -> !tvActivitiesInfo.any { tvActivityInfo -> tvActivityInfo.packageName == nonTvActivityInfo.packageName } }
        return tvActivitiesInfo.map { buildAppMap(it, false) } + nonTvActivitiesInfo.map { buildAppMap(it, true) }
    }

    fun getApplication(packageName: String): List<Map<String, Serializable?>> {
        val tvActivitiesInfo = queryIntentActivities(false)
                .filter { it.packageName == packageName }
                .map { buildAppMap(it, false) }
        return if (tvActivitiesInfo.isNotEmpty()) {
            tvActivitiesInfo
        } else {
            queryIntentActivities(true)
                    .filter { it.packageName == packageName }
                    .map { buildAppMap(it, true) }
        }
    }

    private fun queryIntentActivities(sideloaded: Boolean) = packageManager
            .queryIntentActivities(Intent(ACTION_MAIN, null)
                    .addCategory(if (sideloaded) CATEGORY_LAUNCHER else CATEGORY_LEANBACK_LAUNCHER), 0)
            .map(ResolveInfo::activityInfo)

    private fun buildAppMap(activityInfo: ActivityInfo, sideloaded: Boolean) = mapOf(
            "name" to activityInfo.loadLabel(packageManager).toString(),
            "packageName" to activityInfo.packageName,
            "banner" to activityInfo.loadBanner(packageManager)?.let(::drawableToByteArray),
            "icon" to activityInfo.loadIcon(packageManager)?.let(::drawableToByteArray),
            "version" to packageManager.getPackageInfo(activityInfo.packageName, 0).versionName,
            "sideloaded" to sideloaded,
    )

    private fun launchApp(packageName: String) = try {
        val intent = packageManager.getLeanbackLaunchIntentForPackage(packageName)
                ?: packageManager.getLaunchIntentForPackage(packageName)
        startActivity(intent)
        true
    } catch (e: Exception) {
        false
    }

    private fun openSettings() = try {
        startActivity(Intent(Settings.ACTION_SETTINGS))
        true
    } catch (e: Exception) {
        false
    }

    private fun openAppInfo(packageName: String) = try {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.fromParts("package", packageName, null))
                .let(::startActivity)
        true
    } catch (e: Exception) {
        false
    }

    private fun uninstallApp(packageName: String) = try {
        Intent(ACTION_DELETE)
                .setData(Uri.fromParts("package", packageName, null))
                .let(::startActivity)
        true
    } catch (e: Exception) {
        false
    }

    private fun checkForGetContentAvailability() = try {
        val intentActivities = packageManager.queryIntentActivities(Intent(ACTION_GET_CONTENT, null).setTypeAndNormalize("image/*"), 0)
        intentActivities.isNotEmpty()
    } catch (e: Exception) {
        false
    }

    private fun isDefaultLauncher() = try {
        val defaultLauncher = packageManager.resolveActivity(Intent(ACTION_MAIN).addCategory(CATEGORY_HOME), 0)
        defaultLauncher?.activityInfo?.packageName == packageName
    } catch (e: Exception) {
        false
    }

    private fun getRemoteButtonMappings(): List<Map<String, Any>> =
        remoteMappings.all
            .filterKeys { it.startsWith(REMOTE_MAPPING_PREFIX) }
            .mapNotNull { (key, packageName) ->
                val keyCode = key.removePrefix(REMOTE_MAPPING_PREFIX).toIntOrNull()
                val packageNameString = packageName as? String
                if (keyCode != null && packageNameString != null) {
                    mapOf(
                        "keyCode" to keyCode,
                        "keyLabel" to remoteButtonLabel(keyCode),
                        "packageName" to packageNameString
                    )
                } else {
                    null
                }
            }
            .sortedBy { it["keyCode"] as Int }

    private fun remoteButtonLabel(keyCode: Int): String =
        KeyEvent.keyCodeToString(keyCode)
            .removePrefix("KEYCODE_")
            .replace('_', ' ')
            .lowercase()
            .replaceFirstChar { it.uppercase() }

    private fun drawableToByteArray(drawable: Drawable): ByteArray {
        fun drawableToBitmap(drawable: Drawable): Bitmap {
            val bitmap = Bitmap.createBitmap(drawable.intrinsicWidth, drawable.intrinsicHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            return bitmap
        }

        val bitmap = drawableToBitmap(drawable)
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        return stream.toByteArray()
    }
}
