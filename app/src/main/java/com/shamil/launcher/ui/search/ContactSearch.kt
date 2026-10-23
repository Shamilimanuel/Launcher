package com.shamil.launcher.ui.search

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Settings
import androidx.core.content.ContextCompat

/** A person from the phone book that matches the search. */
class ContactHit(val id: Long, val lookupKey: String, val name: String)

/**
 * Finds people in the phone's contacts by name. Only works once he said yes to "Allow Seelie to access
 * your contacts?" (the READ_CONTACTS permission); before that, search shows an "allow access" row instead.
 * Nothing is copied or kept: Seelie asks the phone book each time and forgets it again.
 */
object ContactSearch {

    fun allowed(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    /**
     * Up to [limit] contacts whose name matches [query]. Reads the phone's database,
     * so call it OFF the main thread (Dispatchers.IO). Empty when access isn't allowed.
     */
    fun find(context: Context, query: String, limit: Int = 3): List<ContactHit> {
        val q = query.trim()
        if (q.isEmpty() || !allowed(context)) return emptyList()
        // CONTENT_FILTER_URI = Android's own "contacts whose name matches this" search (fast, uses its index).
        val uri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_FILTER_URI, Uri.encode(q))
        val columns = arrayOf(
            ContactsContract.Contacts._ID,
            ContactsContract.Contacts.LOOKUP_KEY,
            ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
        )
        return try {
            context.contentResolver.query(uri, columns, null, null, null)?.use { rows ->
                val found = mutableListOf<ContactHit>()
                while (found.size < limit && rows.moveToNext()) {
                    val name = rows.getString(2) ?: continue
                    found += ContactHit(id = rows.getLong(0), lookupKey = rows.getString(1) ?: "", name = name)
                }
                found
            } ?: emptyList()
        } catch (e: Exception) {
            // Access taken away a moment ago, or a phone-book app that misbehaves: just show no contacts.
            emptyList()
        }
    }

    /** Opens the person's card in the phone's Contacts app (call, message, edit from there). */
    fun open(context: Context, contact: ContactHit) {
        val uri = ContactsContract.Contacts.getLookupUri(contact.id, contact.lookupKey)
        if (uri == null || !startSafely(context, Intent(Intent.ACTION_VIEW, uri))) toast(context, "Couldn't open ${contact.name}")
    }

    /** Seelie's own App info page, where a "Don't allow" can be changed into "Allow" later. */
    fun openPermissionSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
        startSafely(context, intent)
    }
}
