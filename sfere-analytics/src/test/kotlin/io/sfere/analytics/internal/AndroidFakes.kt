package io.sfere.analytics.internal

import android.content.ContextWrapper
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo

/**
 * The few Android pieces the JVM tests need. The unit tests run against Android's stub jar with
 * default return values (`isReturnDefaultValues`), so a ContextWrapper with no base is safe to use.
 */
internal class FakeContext(
    private val prefs: SharedPreferences = FakeSharedPreferences(),
    private val appInfo: ApplicationInfo? = ApplicationInfo(),
) : ContextWrapper(null) {
    override fun getApplicationInfo(): ApplicationInfo? = appInfo

    override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences {
        (prefs as? FakeSharedPreferences)?.name = name
        return prefs
    }
}

/** SharedPreferences over a map. With [failing], every read and write throws, like a corrupt preferences file. */
internal class FakeSharedPreferences(private val failing: Boolean = false) : SharedPreferences {
    var name: String? = null
    private val values = HashMap<String, Any?>()

    private fun checkWorking() = check(!failing) { "storage failure" }

    override fun getAll(): Map<String, *> = values.toMap()

    override fun getString(key: String?, defValue: String?): String? {
        checkWorking()
        return values[key] as? String ?: defValue
    }

    override fun getStringSet(key: String?, defValues: Set<String>?): Set<String>? = defValues

    override fun getInt(key: String?, defValue: Int): Int = defValue

    override fun getLong(key: String?, defValue: Long): Long = defValue

    override fun getFloat(key: String?, defValue: Float): Float = defValue

    override fun getBoolean(key: String?, defValue: Boolean): Boolean = defValue

    override fun contains(key: String?): Boolean = key in values

    override fun edit(): SharedPreferences.Editor {
        checkWorking()
        return FakeEditor()
    }

    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

    private inner class FakeEditor : SharedPreferences.Editor {
        private val changes = LinkedHashMap<String, Any?>()

        override fun putString(key: String, value: String?): SharedPreferences.Editor = also { changes[key] = value }

        override fun putStringSet(key: String, values: Set<String>?): SharedPreferences.Editor = also { changes[key] = values }

        override fun putInt(key: String, value: Int): SharedPreferences.Editor = also { changes[key] = value }

        override fun putLong(key: String, value: Long): SharedPreferences.Editor = also { changes[key] = value }

        override fun putFloat(key: String, value: Float): SharedPreferences.Editor = also { changes[key] = value }

        override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor = also { changes[key] = value }

        override fun remove(key: String): SharedPreferences.Editor = also { changes[key] = null }

        override fun clear(): SharedPreferences.Editor = also { values.clear() }

        override fun commit(): Boolean {
            apply()
            return true
        }

        override fun apply() {
            for ((key, value) in changes) if (value == null) values.remove(key) else values[key] = value
        }
    }
}
