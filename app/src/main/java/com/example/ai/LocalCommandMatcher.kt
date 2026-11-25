package com.example.ai

import com.example.device.DeviceActionExecutor
import com.example.device.InstalledAppInfo

sealed interface LocalRouteResult {
    data class MatchedPlan(val plan: JarvisPlan) : LocalRouteResult
    data object SleepCommand : LocalRouteResult
    data object WakeCommandOnly : LocalRouteResult
}

object LocalCommandMatcher {

    fun tryMatch(
        input: String,
        installedApps: List<InstalledAppInfo>,
        actionExecutor: DeviceActionExecutor,
        preferBn: Boolean
    ): LocalRouteResult? {
        val cleaned = input.trim().trimEnd('?', '.', '!', '।')
        val lower = cleaned.lowercase()

        // 0. Sleep / Turn Off Jarvis
        if (lower in setOf(
                "বন্ধ হও", "জার্ভিস বন্ধ হও", "জার্ভিস বন্ধ করো", "অফ হও", "ঘুমাও",
                "বাই জার্ভিস", "টাটা জার্ভিস", "sleep", "turn off", "go to sleep",
                "bye jarvis", "stop jarvis", "jarvis off"
            )
        ) {
            return LocalRouteResult.SleepCommand
        }

        // 1. Wake phrase only ("হ্যালো জার্ভিস" / "Hello Jarvis")
        if (lower in setOf(
                "হ্যালো জার্ভিস", "হালো জার্ভিস", "হেলো জার্ভিস", "হ্যালো জারভিস",
                "hello jarvis", "helo jarvis", "halo jarvis", "hey jarvis"
            )
        ) {
            return LocalRouteResult.WakeCommandOnly
        }

        // 2. Instant small-talk & identity (0ms local response)
        when (lower) {
            "হ্যালো", "হাই", "ওহে", "hello", "hi", "hey" -> {
                return LocalRouteResult.MatchedPlan(
                    JarvisPlan(
                        intentType = JarvisIntentType.CHAT,
                        spokenReply = if (preferBn) "হ্যালো! আমি জার্ভিস। বলুন, কী সাহায্য করতে পারি?" else "Hello! I'm Jarvis. How can I help?",
                        englishFallbackReply = "Hello! I'm Jarvis. How can I help?",
                        suggestions = if (preferBn) {
                            listOf("এখন কয়টা বাজে?", "YouTube খোলো")
                        } else {
                            listOf("What time is it?", "Open YouTube")
                        }
                    )
                )
            }
            "কেমন আছো", "কেমন আছেন", "how are you" -> {
                return LocalRouteResult.MatchedPlan(
                    JarvisPlan(
                        intentType = JarvisIntentType.CHAT,
                        spokenReply = if (preferBn) "আমি একদম ভালো আছি এবং আপনার কাজের জন্য প্রস্তুত! আপনি কেমন আছেন?" else "I'm doing great and ready to help! How are you?",
                        englishFallbackReply = "I'm doing great and ready to help!",
                        suggestions = if (preferBn) {
                            listOf("আজকের কাজের রুটিন দাও", "একটা মজার তথ্য বলো")
                        } else {
                            listOf("Suggest a daily routine", "Tell me a quick fact")
                        }
                    )
                )
            }
            "তুমি কে", "তোমার নাম কি", "তোমার নাম কী", "who are you", "what is your name" -> {
                return LocalRouteResult.MatchedPlan(
                    JarvisPlan(
                        intentType = JarvisIntentType.CHAT,
                        spokenReply = if (preferBn) "আমি জার্ভিস, আপনার পার্সোনাল AI অ্যাসিস্ট্যান্ট। কল, মেসেজ, অ্যাপ খোলা-বন্ধ করা, কোড লেখা—সবকিছুতেই সাহায্য করতে পারি।" else "I am Jarvis, your personal AI assistant for calls, apps, coding, and daily tasks.",
                        englishFallbackReply = "I am Jarvis, your personal AI assistant.",
                        suggestions = if (preferBn) {
                            listOf("এখন কয়টা বাজে?", "Python কোড লিখে দাও")
                        } else {
                            listOf("What time is it?", "Write Python code")
                        }
                    )
                )
            }
            "ধন্যবাদ", "থ্যাংকস", "thanks", "thank you" -> {
                return LocalRouteResult.MatchedPlan(
                    JarvisPlan(
                        intentType = JarvisIntentType.CHAT,
                        spokenReply = if (preferBn) "স্বাগতম! আর কোনো সাহায্য লাগলে বলবেন।" else "You're welcome! Let me know if you need anything else.",
                        englishFallbackReply = "You're welcome!"
                    )
                )
            }
        }

        // 3. Time & Date
        if (lower in setOf(
                "এখন কয়টা বাজে", "এখন কয়টা বাজে", "কয়টা বাজে", "কয়টা বাজে",
                "সময় কত", "সময় কত", "সময় বলো", "সময় বলো", "আজকের তারিখ", "আজ কি বার",
                "what time is it", "what is the time", "tell me the time", "time", "date"
            )
        ) {
            return LocalRouteResult.MatchedPlan(
                JarvisPlan(
                    intentType = JarvisIntentType.TELL_TIME,
                    spokenReply = actionExecutor.getCurrentTimeText(preferBn),
                    englishFallbackReply = actionExecutor.getCurrentTimeText(false),
                    suggestions = if (preferBn) {
                        listOf("আজকের রুটিনের সাজেসন দাও", "অ্যালার্ম সেট করো")
                    } else {
                        listOf("Suggest a daily routine", "Set an alarm")
                    }
                )
            )
        }

        // 4. Flashlight ON / OFF
        if (lower in setOf(
                "ফ্ল্যাশলাইট জ্বালাও", "ফ্ল্যাশলাইট অন", "ফ্ল্যাশলাইট অন করো",
                "লাইট জ্বালাও", "টর্চ জ্বালাও", "flashlight on", "turn on flashlight", "torch on"
            )
        ) {
            return LocalRouteResult.MatchedPlan(
                JarvisPlan(
                    intentType = JarvisIntentType.FLASHLIGHT_ON,
                    spokenReply = if (preferBn) "ফ্ল্যাশলাইট জ্বালিয়ে দিয়েছি।" else "Flashlight turned on.",
                    englishFallbackReply = "Flashlight turned on."
                )
            )
        }
        if (lower in setOf(
                "ফ্ল্যাশলাইট বন্ধ করো", "ফ্ল্যাশলাইট অফ", "ফ্ল্যাশলাইট অফ করো",
                "লাইট বন্ধ করো", "টর্চ বন্ধ করো", "flashlight off", "turn off flashlight", "torch off"
            )
        ) {
            return LocalRouteResult.MatchedPlan(
                JarvisPlan(
                    intentType = JarvisIntentType.FLASHLIGHT_OFF,
                    spokenReply = if (preferBn) "ফ্ল্যাশলাইট বন্ধ করে দিয়েছি।" else "Flashlight turned off.",
                    englishFallbackReply = "Flashlight turned off."
                )
            )
        }

        // 5. Volume controls
        if (lower in setOf("ভলিউম বাড়াও", "ভলিউম বাড়াও", "সাউন্ড বাড়াও", "সাউন্ড বাড়াও", "volume up", "increase volume")) {
            return LocalRouteResult.MatchedPlan(
                JarvisPlan(
                    intentType = JarvisIntentType.VOLUME_UP,
                    spokenReply = if (preferBn) "ভলিউম বাড়িয়ে দিয়েছি।" else "Volume increased.",
                    englishFallbackReply = "Volume increased."
                )
            )
        }
        if (lower in setOf("ভলিউম কমাও", "সাউন্ড কমাও", "volume down", "decrease volume")) {
            return LocalRouteResult.MatchedPlan(
                JarvisPlan(
                    intentType = JarvisIntentType.VOLUME_DOWN,
                    spokenReply = if (preferBn) "ভলিউম কমিয়ে দিয়েছি।" else "Volume lowered.",
                    englishFallbackReply = "Volume lowered."
                )
            )
        }
        if (lower in setOf("মিউট করো", "সাউন্ড বন্ধ করো", "ভলিউম মিউট", "mute", "mute volume")) {
            return LocalRouteResult.MatchedPlan(
                JarvisPlan(
                    intentType = JarvisIntentType.VOLUME_MUTE,
                    spokenReply = if (preferBn) "সাউন্ড মিউট করে দিয়েছি।" else "Volume muted.",
                    englishFallbackReply = "Volume muted."
                )
            )
        }

        // 6. Accessibility & System Settings
        if (lower in setOf("হোম স্ক্রিনে যাও", "হোমে যাও", "go home", "home screen")) {
            return LocalRouteResult.MatchedPlan(
                JarvisPlan(
                    intentType = JarvisIntentType.GO_HOME,
                    spokenReply = if (preferBn) "হোম স্ক্রিনে ফিরে যাচ্ছি।" else "Going home.",
                    englishFallbackReply = "Going home."
                )
            )
        }
        if (lower in setOf("ব্যাক করো", "পিছনে যাও", "go back", "back")) {
            return LocalRouteResult.MatchedPlan(
                JarvisPlan(
                    intentType = JarvisIntentType.GO_BACK,
                    spokenReply = if (preferBn) "পিছনে ফিরে যাচ্ছি।" else "Going back.",
                    englishFallbackReply = "Going back."
                )
            )
        }
        if (lower in setOf("নোটিফিকেশন খোলো", "নোটিফিকেশন দেখাও", "open notifications", "show notifications")) {
            return LocalRouteResult.MatchedPlan(
                JarvisPlan(
                    intentType = JarvisIntentType.OPEN_NOTIFICATIONS,
                    spokenReply = if (preferBn) "নোটিফিকেশন খুলে দিয়েছি।" else "Opened notifications.",
                    englishFallbackReply = "Opened notifications."
                )
            )
        }
        if (lower in setOf("রিসেন্ট অ্যাপ", "recent apps", "open recents")) {
            return LocalRouteResult.MatchedPlan(
                JarvisPlan(
                    intentType = JarvisIntentType.OPEN_RECENTS,
                    spokenReply = if (preferBn) "রিসেন্ট অ্যাপ দেখাচ্ছি।" else "Opened recent apps.",
                    englishFallbackReply = "Opened recent apps."
                )
            )
        }
        if (lower in setOf("স্ক্রিনশট নাও", "স্ক্রিনশট তোলো", "take screenshot", "screenshot")) {
            return LocalRouteResult.MatchedPlan(
                JarvisPlan(
                    intentType = JarvisIntentType.TAKE_SCREENSHOT,
                    spokenReply = if (preferBn) "স্ক্রিনশট নিচ্ছি।" else "Taking screenshot.",
                    englishFallbackReply = "Taking screenshot."
                )
            )
        }
        if (lower in setOf("অ্যাপ বন্ধ করো", "close app")) {
            return LocalRouteResult.MatchedPlan(
                JarvisPlan(
                    intentType = JarvisIntentType.CLOSE_APP,
                    spokenReply = if (preferBn) "অ্যাপ বন্ধ করছি।" else "Closing app.",
                    englishFallbackReply = "Closing app."
                )
            )
        }
        if (lower in setOf("ওয়াইফাই খোলো", "ওয়াইফাই খোলো", "wifi settings", "open wifi")) {
            return LocalRouteResult.MatchedPlan(
                JarvisPlan(
                    intentType = JarvisIntentType.OPEN_SETTINGS,
                    target = "wifi",
                    spokenReply = if (preferBn) "Wi-Fi সেটিংস খুলে দিয়েছি।" else "Opening Wi-Fi settings.",
                    englishFallbackReply = "Opening Wi-Fi settings."
                )
            )
        }

        // 7. Direct Open App
        val openTarget = extractOpenAppTarget(cleaned, lower, installedApps)
        if (openTarget != null) {
            return LocalRouteResult.MatchedPlan(
                JarvisPlan(
                    intentType = JarvisIntentType.OPEN_APP,
                    target = openTarget,
                    spokenReply = if (preferBn) "$openTarget চালু করছি।" else "Opening $openTarget.",
                    englishFallbackReply = "Opening $openTarget."
                )
            )
        }

        // 8. Direct Call
        val callTarget = extractCallTarget(cleaned, lower)
        if (callTarget != null) {
            return LocalRouteResult.MatchedPlan(
                JarvisPlan(
                    intentType = JarvisIntentType.CALL,
                    target = callTarget,
                    spokenReply = if (preferBn) "$callTarget-কে কল দিচ্ছি।" else "Calling $callTarget.",
                    englishFallbackReply = "Calling $callTarget."
                )
            )
        }

        return null
    }

    private fun extractOpenAppTarget(
        cleaned: String,
        lower: String,
        installedApps: List<InstalledAppInfo>
    ): String? {
        val candidate = when {
            lower.startsWith("open ") -> cleaned.drop(5).trim()
            lower.startsWith("launch ") -> cleaned.drop(7).trim()
            lower.startsWith("start ") -> cleaned.drop(6).trim()
            lower.endsWith(" খোলো") -> cleaned.removeSuffix(" খোলো").trim()
            lower.endsWith(" খুলো") -> cleaned.removeSuffix(" খুলো").trim()
            lower.endsWith(" চালু করো") -> cleaned.removeSuffix(" চালু করো").trim()
            lower.endsWith(" ওপেন করো") -> cleaned.removeSuffix(" ওপেন করো").trim()
            else -> return null
        }.trim()

        if (candidate.isEmpty() || candidate.length > 36) return null
        val bnKnownAliases = setOf(
            "ইউটিউব", "হোয়াটসঅ্যাপ", "হোয়াটসঅ্যাপ", "ফেসবুক", "মেসেঞ্জার",
            "ইনস্টাগ্রাম", "ক্রোম", "গুগল", "ক্যামেরা", "সেটিংস", "ফোন",
            "মেসেজ", "জিমেইল", "ইমেল", "ম্যাপ", "গ্যালারি", "ফটো",
            "ক্যালকুলেটর", "ঘড়ি", "অ্যালার্ম", "প্লে স্টোর", "টেলিগ্রাম", "স্পটিফাই", "টিকটক"
        )
        val candidateLower = candidate.lowercase()
        val matchesKnownAlias = bnKnownAliases.any { candidateLower.contains(it) }
        val matchesInstalled = installedApps.any {
            it.label.equals(candidate, ignoreCase = true) ||
                it.label.contains(candidate, ignoreCase = true) ||
                candidateLower.contains(it.label.lowercase())
        }
        return if (matchesKnownAlias || matchesInstalled) candidate else null
    }

    private fun extractCallTarget(cleaned: String, lower: String): String? {
        val candidate = when {
            lower.startsWith("call ") -> cleaned.drop(5).trim()
            lower.startsWith("কল দাও ") -> cleaned.drop(7).trim()
            lower.endsWith("-কে কল দাও") -> cleaned.removeSuffix("-কে কল দাও").trim()
            lower.endsWith(" কে কল দাও") -> cleaned.removeSuffix(" কে কল দাও").trim()
            lower.endsWith(" কল দাও") -> cleaned.removeSuffix(" কল দাও").trim()
            else -> return null
        }.trim()
        return candidate.takeIf { it.isNotEmpty() && it.length <= 40 }
    }
}
