package com.example.device

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.service.JarvisAccessibilityService
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

data class InstalledAppInfo(
    val label: String,
    val packageName: String
)

data class ActionExecutionResult(
    val success: Boolean,
    val actionLabel: String,
    val spokenFeedback: String? = null
)

class DeviceActionExecutor(private val context: Context) {

    private var torchEnabled = false

    fun getCurrentTimeText(preferBengali: Boolean): String {
        val cal = Calendar.getInstance()
        return if (preferBengali) {
            val hour = cal.get(Calendar.HOUR)
            val displayHour = if (hour == 0) 12 else hour
            val minute = cal.get(Calendar.MINUTE)
            val hour24 = cal.get(Calendar.HOUR_OF_DAY)
            val period = when (hour24) {
                in 4..11 -> "সকাল"
                in 12..15 -> "দুপুর"
                in 16..18 -> "বিকাল"
                in 19..20 -> "সন্ধ্যা"
                else -> "রাত"
            }
            val dateFmt = SimpleDateFormat("EEEE, d MMMM yyyy", Locale("bn", "BD"))
            val dateStr = dateFmt.format(cal.time)
            val minutePart = if (minute == 0) "" else " বেজে $minute মিনিট"
            "এখন সময় $period $displayHour টা$minutePart। আজ $dateStr।"
        } else {
            val timeFmt = SimpleDateFormat("h:mm a", Locale.ENGLISH)
            val dateFmt = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.ENGLISH)
            "It is currently ${timeFmt.format(cal.time)} on ${dateFmt.format(cal.time)}."
        }
    }

    fun getInstalledLauncherApps(): List<InstalledAppInfo> {
        val pm = context.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfos = pm.queryIntentActivities(mainIntent, 0)
        return resolveInfos
            .mapNotNull { info ->
                val pkg = info.activityInfo?.packageName ?: return@mapNotNull null
                if (pkg == context.packageName) return@mapNotNull null
                val label = info.loadLabel(pm)?.toString()?.trim().orEmpty()
                if (label.isEmpty()) return@mapNotNull null
                InstalledAppInfo(label = label, packageName = pkg)
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase(Locale.ROOT) }
    }

    fun openApp(query: String, preferBengali: Boolean): ActionExecutionResult {
        val normalized = normalizeAppQuery(query)
        val apps = getInstalledLauncherApps()

        val matched = apps.firstOrNull {
            it.label.equals(normalized, ignoreCase = true)
        } ?: apps.firstOrNull {
            it.label.contains(normalized, ignoreCase = true) ||
                normalized.contains(it.label.lowercase(Locale.ROOT))
        } ?: apps.firstOrNull {
            it.packageName.contains(normalized, ignoreCase = true)
        }

        if (matched == null) {
            return ActionExecutionResult(
                success = false,
                actionLabel = "App not found: $query",
                spokenFeedback = if (preferBengali) {
                    "দুঃখিত, ফোনে $query অ্যাপটি খুঁজে পাইনি।"
                } else {
                    "Sorry, I couldn't find $query installed on your phone."
                }
            )
        }

        val launchIntent = context.packageManager.getLaunchIntentForPackage(matched.packageName)
        return if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
            ActionExecutionResult(
                success = true,
                actionLabel = "Opened ${matched.label}",
                spokenFeedback = if (preferBengali) {
                    "${matched.label} চালু করছি।"
                } else {
                    "Opening ${matched.label}."
                }
            )
        } else {
            ActionExecutionResult(
                success = false,
                actionLabel = "Cannot launch ${matched.label}",
                spokenFeedback = if (preferBengali) {
                    "${matched.label} চালু করা যাচ্ছে না।"
                } else {
                    "Unable to launch ${matched.label}."
                }
            )
        }
    }

    fun closeAppOrGoHome(targetApp: String?, preferBengali: Boolean): ActionExecutionResult {
        val cleaned = targetApp?.trim().orEmpty()
        if (cleaned.isNotEmpty()) {
            val normalized = normalizeAppQuery(cleaned)
            val matched = getInstalledLauncherApps().firstOrNull {
                it.label.contains(normalized, ignoreCase = true) ||
                    it.packageName.contains(normalized, ignoreCase = true)
            }
            if (matched != null) {
                val settingsIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", matched.packageName, null)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(settingsIntent)
                return ActionExecutionResult(
                    success = true,
                    actionLabel = "App controls opened for ${matched.label}",
                    spokenFeedback = if (preferBengali) {
                        "${matched.label} বন্ধ করার জন্য অ্যাপ সেটিংস খুলে দিয়েছি, ফোর্স স্টপ করতে পারেন।"
                    } else {
                        "Opened app info for ${matched.label} so you can force stop it."
                    }
                )
            }
        }

        return if (JarvisAccessibilityService.goHome()) {
            ActionExecutionResult(
                success = true,
                actionLabel = "Returned to Home screen",
                spokenFeedback = if (preferBengali) {
                    "অ্যাপ বন্ধ করে হোম স্ক্রিনে ফিরে এসেছি।"
                } else {
                    "Closed the current view and returned home."
                }
            )
        } else {
            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(homeIntent)
            ActionExecutionResult(
                success = true,
                actionLabel = "Switched to Home screen",
                spokenFeedback = if (preferBengali) {
                    "হোম স্ক্রিনে নিয়ে যাচ্ছি। সম্পূর্ণ অ্যাপ কন্ট্রোলের জন্য এক্সেস ট্যাব থেকে Accessibility চালু করুন।"
                } else {
                    "Returning home. Enable Jarvis Accessibility in Access settings for full app control."
                }
            )
        }
    }

    fun openAppInfoSettings(packageName: String) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun makePhoneCall(target: String, preferBengali: Boolean): ActionExecutionResult {
        val resolvedNumber = resolvePhoneNumber(target)
        if (resolvedNumber == null || resolvedNumber.number.isBlank()) {
            val dialIntent = Intent(Intent.ACTION_DIAL).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(dialIntent)
            return ActionExecutionResult(
                success = false,
                actionLabel = "Contact number not found for $target",
                spokenFeedback = if (preferBengali) {
                    "$target-এর নম্বর কনট্যাক্টে খুঁজে পাইনি, ডায়ালার খুলে দিয়েছি।"
                } else {
                    "Couldn't find a phone number for $target. Opened the dialer."
                }
            )
        }

        val hasCallPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED

        val action = if (hasCallPermission) Intent.ACTION_CALL else Intent.ACTION_DIAL
        val intent = Intent(action, Uri.parse("tel:${Uri.encode(resolvedNumber.number)}")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)

        val displayTarget = resolvedNumber.displayName ?: resolvedNumber.number
        return ActionExecutionResult(
            success = true,
            actionLabel = if (hasCallPermission) "Calling $displayTarget" else "Dialing $displayTarget",
            spokenFeedback = if (preferBengali) {
                if (hasCallPermission) "$displayTarget-কে কল দিচ্ছি।" else "$displayTarget-এর নম্বর ডায়ালারে তুলে দিয়েছি।"
            } else {
                if (hasCallPermission) "Calling $displayTarget now." else "Dialing $displayTarget."
            }
        )
    }

    fun sendMessage(
        target: String,
        messageBody: String,
        viaWhatsApp: Boolean,
        preferBengali: Boolean
    ): ActionExecutionResult {
        val resolved = resolvePhoneNumber(target)
        val number = resolved?.number.orEmpty()
        val displayTarget = resolved?.displayName ?: target.ifBlank { number }

        if (viaWhatsApp && number.isNotBlank()) {
            val digitsOnly = number.filter { it.isDigit() }
            val url = "https://wa.me/$digitsOnly?text=${Uri.encode(messageBody)}"
            val waIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            runCatching {
                context.startActivity(waIntent)
                return ActionExecutionResult(
                    success = true,
                    actionLabel = "WhatsApp message ready for $displayTarget",
                    spokenFeedback = if (preferBengali) {
                        "$displayTarget-কে হোয়াটসঅ্যাপ মেসেজ পাঠানোর জন্য প্রস্তুত করেছি।"
                    } else {
                        "Prepared WhatsApp message for $displayTarget."
                    }
                )
            }
        }

        val smsUri = if (number.isNotBlank()) Uri.parse("smsto:$number") else Uri.parse("smsto:")
        val smsIntent = Intent(Intent.ACTION_SENDTO, smsUri).apply {
            putExtra("sms_body", messageBody)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(smsIntent)
            ActionExecutionResult(
                success = true,
                actionLabel = "Message drafted for $displayTarget",
                spokenFeedback = if (preferBengali) {
                    "$displayTarget-এর জন্য মেসেজ লিখে দিয়েছি।"
                } else {
                    "Drafted message for $displayTarget."
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch SMS intent", e)
            ActionExecutionResult(
                success = false,
                actionLabel = "No messaging app found",
                spokenFeedback = if (preferBengali) {
                    "মেসেজ পাঠানোর অ্যাপ খোলা যায়নি।"
                } else {
                    "Could not open a messaging app."
                }
            )
        }
    }

    fun sendEmail(
        target: String,
        subject: String,
        body: String,
        preferBengali: Boolean
    ): ActionExecutionResult {
        val emailAddress = resolveEmailAddress(target)
        val mailIntent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:")
            if (!emailAddress.isNullOrBlank()) {
                putExtra(Intent.EXTRA_EMAIL, arrayOf(emailAddress))
            }
            putExtra(Intent.EXTRA_SUBJECT, subject.ifBlank { "Message from Jarvis" })
            putExtra(Intent.EXTRA_TEXT, body)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        return try {
            context.startActivity(mailIntent)
            val labelTarget = emailAddress ?: target.ifBlank { "recipient" }
            ActionExecutionResult(
                success = true,
                actionLabel = "Email drafted for $labelTarget",
                spokenFeedback = if (preferBengali) {
                    "$labelTarget-কে ইমেল করার জন্য ইমেল অ্যাপে ড্রাফট তৈরি করে দিয়েছি।"
                } else {
                    "Drafted an email to $labelTarget in your mail app."
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch email intent", e)
            ActionExecutionResult(
                success = false,
                actionLabel = "No email app available",
                spokenFeedback = if (preferBengali) {
                    "ফোনে কোনো ইমেল অ্যাপ পাওয়া যায়নি।"
                } else {
                    "No email app was found on this device."
                }
            )
        }
    }

    fun setFlashlight(enable: Boolean, preferBengali: Boolean): ActionExecutionResult {
        return try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
                cameraManager.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
            if (cameraId != null) {
                cameraManager.setTorchMode(cameraId, enable)
                torchEnabled = enable
                ActionExecutionResult(
                    success = true,
                    actionLabel = if (enable) "Flashlight ON" else "Flashlight OFF",
                    spokenFeedback = if (preferBengali) {
                        if (enable) "ফ্ল্যাশলাইট জ্বালিয়ে দিয়েছি।" else "ফ্ল্যাশলাইট বন্ধ করে দিয়েছি।"
                    } else {
                        if (enable) "Flashlight turned on." else "Flashlight turned off."
                    }
                )
            } else {
                ActionExecutionResult(
                    success = false,
                    actionLabel = "Flashlight unavailable",
                    spokenFeedback = if (preferBengali) "এই ডিভাইসে ফ্ল্যাশলাইট পাওয়া যায়নি।" else "No flashlight found on this device."
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Flashlight control failed", e)
            ActionExecutionResult(
                success = false,
                actionLabel = "Flashlight error",
                spokenFeedback = if (preferBengali) "ফ্ল্যাশলাইট চালু করা যায়নি।" else "Unable to toggle flashlight."
            )
        }
    }

    fun isTorchOn(): Boolean = torchEnabled

    fun adjustVolume(mode: String, preferBengali: Boolean): ActionExecutionResult {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        when (mode) {
            "UP" -> audio.adjustStreamVolume(
                AudioManager.STREAM_MUSIC,
                AudioManager.ADJUST_RAISE,
                AudioManager.FLAG_SHOW_UI
            )
            "DOWN" -> audio.adjustStreamVolume(
                AudioManager.STREAM_MUSIC,
                AudioManager.ADJUST_LOWER,
                AudioManager.FLAG_SHOW_UI
            )
            "MUTE" -> audio.adjustStreamVolume(
                AudioManager.STREAM_MUSIC,
                AudioManager.ADJUST_MUTE,
                AudioManager.FLAG_SHOW_UI
            )
            else -> audio.adjustStreamVolume(
                AudioManager.STREAM_MUSIC,
                AudioManager.ADJUST_UNMUTE,
                AudioManager.FLAG_SHOW_UI
            )
        }
        return ActionExecutionResult(
            success = true,
            actionLabel = "Volume: $mode",
            spokenFeedback = if (preferBengali) {
                when (mode) {
                    "UP" -> "ভলিউম বাড়িয়ে দিয়েছি।"
                    "DOWN" -> "ভলিউম কমিয়ে দিয়েছি।"
                    "MUTE" -> "সাউন্ড মিউট করে দিয়েছি।"
                    else -> "সাউন্ড আনমিউট করেছি।"
                }
            } else {
                when (mode) {
                    "UP" -> "Volume increased."
                    "DOWN" -> "Volume lowered."
                    "MUTE" -> "Volume muted."
                    else -> "Volume unmuted."
                }
            }
        )
    }

    fun setAlarm(hour: Int, minute: Int, label: String, preferBengali: Boolean): ActionExecutionResult {
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour.coerceIn(0, 23))
            putExtra(AlarmClock.EXTRA_MINUTES, minute.coerceIn(0, 59))
            putExtra(AlarmClock.EXTRA_MESSAGE, label.ifBlank { "Jarvis Alarm" })
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            ActionExecutionResult(
                success = true,
                actionLabel = String.format(Locale.ENGLISH, "Alarm %02d:%02d", hour, minute),
                spokenFeedback = if (preferBengali) {
                    "$hour টা $minute মিনিটের অ্যালার্ম সেট করছি।"
                } else {
                    "Setting alarm for $hour:${minute.toString().padStart(2, '0')}."
                }
            )
        } catch (e: Exception) {
            ActionExecutionResult(false, "Unable to set alarm")
        }
    }

    fun setTimer(seconds: Int, label: String, preferBengali: Boolean): ActionExecutionResult {
        val safeSeconds = seconds.coerceAtLeast(5)
        val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
            putExtra(AlarmClock.EXTRA_LENGTH, safeSeconds)
            putExtra(AlarmClock.EXTRA_MESSAGE, label.ifBlank { "Jarvis Timer" })
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            ActionExecutionResult(
                success = true,
                actionLabel = "Timer ${safeSeconds}s",
                spokenFeedback = if (preferBengali) {
                    "$safeSeconds সেকেন্ডের টাইমার সেট করছি।"
                } else {
                    "Setting a timer for $safeSeconds seconds."
                }
            )
        } catch (e: Exception) {
            ActionExecutionResult(false, "Unable to set timer")
        }
    }

    fun openWebSearch(query: String, preferBengali: Boolean): ActionExecutionResult {
        val url = "https://www.google.com/search?q=${Uri.encode(query)}"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return ActionExecutionResult(
            success = true,
            actionLabel = "Searched: $query",
            spokenFeedback = if (preferBengali) {
                "$query সম্পর্কে ওয়েবে সার্চ করছি।"
            } else {
                "Searching the web for $query."
            }
        )
    }

    fun openSystemSettings(panel: String, preferBengali: Boolean): ActionExecutionResult {
        val action = when (panel.lowercase(Locale.ROOT)) {
            "wifi", "internet", "ওয়াইফাই" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    Settings.Panel.ACTION_INTERNET_CONNECTIVITY
                } else {
                    Settings.ACTION_WIFI_SETTINGS
                }
            }
            "bluetooth", "ব্লুটুথ" -> Settings.ACTION_BLUETOOTH_SETTINGS
            "volume", "sound", "সাউন্ড" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    Settings.Panel.ACTION_VOLUME
                } else {
                    Settings.ACTION_SOUND_SETTINGS
                }
            }
            else -> Settings.ACTION_SETTINGS
        }
        val intent = Intent(action).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return ActionExecutionResult(
            success = true,
            actionLabel = "Opened Settings ($panel)",
            spokenFeedback = if (preferBengali) {
                "সেটিংস খুলে দিয়েছি।"
            } else {
                "Opening settings."
            }
        )
    }

    fun executeAccessibilityAction(type: String, preferBengali: Boolean): ActionExecutionResult {
        val executed = when (type) {
            "GO_HOME" -> JarvisAccessibilityService.goHome()
            "GO_BACK" -> JarvisAccessibilityService.goBack()
            "OPEN_RECENTS" -> JarvisAccessibilityService.openRecents()
            "OPEN_NOTIFICATIONS" -> JarvisAccessibilityService.openNotifications()
            "OPEN_QUICK_SETTINGS" -> JarvisAccessibilityService.openQuickSettings()
            "TAKE_SCREENSHOT" -> JarvisAccessibilityService.takeScreenshot()
            else -> false
        }
        if (executed) {
            return ActionExecutionResult(
                success = true,
                actionLabel = "Executed $type"
            )
        }
        if (type == "GO_HOME") {
            return closeAppOrGoHome(null, preferBengali)
        }
        return ActionExecutionResult(
            success = false,
            actionLabel = "Accessibility required for $type",
            spokenFeedback = if (preferBengali) {
                "এই কাজের জন্য এক্সেস ট্যাব থেকে Jarvis Accessibility চালু করুন।"
            } else {
                "Please enable Jarvis Accessibility in the Access tab for this action."
            }
        )
    }

    private data class ResolvedContact(val displayName: String?, val number: String)

    private fun resolvePhoneNumber(target: String): ResolvedContact? {
        val cleaned = target.trim()
        if (cleaned.isEmpty()) return null

        val digitCandidate = cleaned.filter { it.isDigit() || it == '+' }
        if (digitCandidate.count { it.isDigit() } >= 3 && digitCandidate.length >= cleaned.length / 2) {
            return ResolvedContact(displayName = null, number = digitCandidate)
        }

        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_CONTACTS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return null
        }

        val searchTerms = buildSearchVariants(cleaned)
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection,
            null,
            null,
            null
        )?.use { cursor ->
            val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            while (cursor.moveToNext()) {
                val name = cursor.getString(nameIdx).orEmpty()
                val num = cursor.getString(numIdx).orEmpty()
                if (name.isNotBlank() && num.isNotBlank()) {
                    val lowerName = name.lowercase(Locale.ROOT)
                    if (searchTerms.any { lowerName.contains(it) }) {
                        return ResolvedContact(displayName = name, number = num)
                    }
                }
            }
        }
        return null
    }

    private fun resolveEmailAddress(target: String): String? {
        val cleaned = target.trim()
        if (cleaned.contains("@") && cleaned.contains(".")) {
            return cleaned
        }
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_CONTACTS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return null
        }
        val searchTerms = buildSearchVariants(cleaned)
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Email.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Email.ADDRESS
        )
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Email.CONTENT_URI,
            projection,
            null,
            null,
            null
        )?.use { cursor ->
            val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Email.DISPLAY_NAME)
            val emailIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Email.ADDRESS)
            while (cursor.moveToNext()) {
                val name = cursor.getString(nameIdx).orEmpty()
                val email = cursor.getString(emailIdx).orEmpty()
                if (email.isNotBlank()) {
                    val lowerName = name.lowercase(Locale.ROOT)
                    if (searchTerms.any { lowerName.contains(it) || email.lowercase(Locale.ROOT).contains(it) }) {
                        return email
                    }
                }
            }
        }
        return null
    }

    private fun buildSearchVariants(query: String): List<String> {
        val lower = query.trim().lowercase(Locale.ROOT)
        val variants = mutableListOf(lower)
        // Map common Bengali honorific/transliterated contact words
        val map = mapOf(
            "আম্মু" to "ammu",
            "মা" to "ma",
            "আব্বু" to "abbu",
            "বাবা" to "baba",
            "ভাইয়া" to "vai",
            "ভাই" to "bhai",
            "বোন" to "sister",
            "রহিম" to "rahim",
            "করিম" to "karim"
        )
        map[lower]?.let { variants.add(it) }
        return variants.distinct()
    }

    private fun normalizeAppQuery(query: String): String {
        val lower = query.trim().lowercase(Locale.ROOT)
        val aliases = mapOf(
            "ইউটিউব" to "youtube",
            "হোয়াটসঅ্যাপ" to "whatsapp",
            "হোয়াটসঅ্যাপ" to "whatsapp",
            "ফেসবুক" to "facebook",
            "মেসেঞ্জার" to "messenger",
            "ইনস্টাগ্রাম" to "instagram",
            "ক্রোম" to "chrome",
            "গুগল" to "google",
            "ক্যামেরা" to "camera",
            "সেটিংস" to "settings",
            "ফোন" to "phone",
            "কল" to "phone",
            "মেসেজ" to "messages",
            "জিমেইল" to "gmail",
            "ইমেল" to "gmail",
            "ইমেইল" to "gmail",
            "ম্যাপ" to "maps",
            "গ্যালারি" to "photos",
            "ফটো" to "photos",
            "ক্যালকুলেটর" to "calculator",
            "ঘড়ি" to "clock",
            "অ্যালার্ম" to "clock",
            "প্লে স্টোর" to "play store",
            "টেলিগ্রাম" to "telegram",
            "স্পটিফাই" to "spotify",
            "টিকটক" to "tiktok"
        )
        for ((bn, en) in aliases) {
            if (lower.contains(bn)) return en
        }
        return lower
    }

    companion object {
        private const val TAG = "DeviceActionExecutor"
    }
}
