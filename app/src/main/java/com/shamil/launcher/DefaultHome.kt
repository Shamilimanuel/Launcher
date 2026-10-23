package com.shamil.launcher

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings

/** Helpers for "is this app the phone's default Home screen, and how do I ask to become it?" */
object DefaultHome {

    fun isDefault(context: Context): Boolean {
        // Android 10+ has a proper "Home role" we can ask about directly.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roles = context.getSystemService(RoleManager::class.java)
            return roles.isRoleHeld(RoleManager.ROLE_HOME)
        }
        // Older Android: ask which app would open if Home were pressed right now.
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val match = context.packageManager.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)
        return match?.activityInfo?.packageName == context.packageName
    }

    /** The system pop-up that asks "Use Launcher as your Home app?" (Android 10+), else the Home settings page. */
    fun requestIntent(context: Context): Intent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            context.getSystemService(RoleManager::class.java).createRequestRoleIntent(RoleManager.ROLE_HOME)
        } else {
            settingsIntent()
        }

    /** The phone's own "Default apps → Home app" settings page. */
    fun settingsIntent(): Intent = Intent(Settings.ACTION_HOME_SETTINGS)
}
