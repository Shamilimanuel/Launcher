package com.shamil.launcher.apps

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.os.Process

/**
 * One-off import of his Niagara favourites: their order and his names for them (24 Oct). Niagara's backup file is
 * encrypted, so the list is read off Niagara's screen on the computer and sent here over adb:
 *
 *   adb shell "am broadcast -n com.shamil.launcher/.apps.NiagaraImport --es favourites 'com.whatsapp|WhatsApp;...'"
 *
 * Only adb (the shell) can send it: the receiver requires android.permission.DUMP, which normal apps never get.
 * Each entry is "package|name". A name that differs from the app's own name becomes a rename (like Niagara's);
 * the same name clears any rename. Apps that aren't installed are skipped. Seelie restarts afterwards to show it.
 */
class NiagaraImport : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val raw = intent.getStringExtra("favourites") ?: return
        val entries = raw.split(";").mapNotNull { part ->
            val bits = part.split("|", limit = 2)
            val pkg = bits[0].trim()
            if (pkg.isEmpty()) null else pkg to bits.getOrNull(1)?.trim().orEmpty()
        }
        val launcherApps = context.getSystemService(LauncherApps::class.java)
        // The name Seelie shows by default = the app's launcher name.
        val known = entries.mapNotNull { (pkg, name) ->
            val label = runCatching { launcherApps.getActivityList(pkg, Process.myUserHandle()).firstOrNull()?.label?.toString() }.getOrNull()
            label?.let { Triple(pkg, name, it) }
        }
        Favourites(context).setOrder(known.map { it.first })
        val tweaks = AppTweaks(context)
        known.forEach { (pkg, name, label) -> tweaks.rename(pkg, if (name.isNotEmpty() && name != label) name else "") }
        resultData = "Imported ${known.size} of ${entries.size} favourites" +
            (entries.map { it.first } - known.map { it.first }.toSet()).let { if (it.isEmpty()) "" else "; skipped (not installed): ${it.joinToString()}" }
    }
}
