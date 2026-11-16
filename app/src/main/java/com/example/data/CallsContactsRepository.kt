package com.example.data

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.CallLog
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import com.example.model.CallDirection
import com.example.model.CallLogEntry
import com.example.model.ContactEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CallsContactsRepository(private val context: Context) {

    fun hasCallLogPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) ==
            PackageManager.PERMISSION_GRANTED

    fun hasContactsPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED

    fun hasCallPhonePermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) ==
            PackageManager.PERMISSION_GRANTED

    suspend fun loadCallLogs(limit: Int = 100): List<CallLogEntry> = withContext(Dispatchers.IO) {
        if (!hasCallLogPermission()) return@withContext emptyList()
        val results = mutableListOf<CallLogEntry>()
        val timeFormatter = SimpleDateFormat("HH:mm", Locale.getDefault())
        val projection = arrayOf(
            CallLog.Calls._ID,
            CallLog.Calls.CACHED_NAME,
            CallLog.Calls.NUMBER,
            CallLog.Calls.TYPE,
            CallLog.Calls.DATE
        )
        runCatching {
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                projection,
                null,
                null,
                "${CallLog.Calls.DATE} DESC"
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndexOrThrow(CallLog.Calls._ID)
                val nameIdx = cursor.getColumnIndexOrThrow(CallLog.Calls.CACHED_NAME)
                val numIdx = cursor.getColumnIndexOrThrow(CallLog.Calls.NUMBER)
                val typeIdx = cursor.getColumnIndexOrThrow(CallLog.Calls.TYPE)
                val dateIdx = cursor.getColumnIndexOrThrow(CallLog.Calls.DATE)

                while (cursor.moveToNext() && results.size < limit) {
                    val id = cursor.getLong(idIdx)
                    val number = cursor.getString(numIdx)?.trim().orEmpty().ifEmpty { "Скрытый номер" }
                    val cachedName = cursor.getString(nameIdx)?.trim().orEmpty()
                    val displayName = cachedName.ifEmpty { number }
                    val timestamp = cursor.getLong(dateIdx)
                    val direction = when (cursor.getInt(typeIdx)) {
                        CallLog.Calls.INCOMING_TYPE -> CallDirection.INCOMING
                        CallLog.Calls.OUTGOING_TYPE -> CallDirection.OUTGOING
                        CallLog.Calls.MISSED_TYPE -> CallDirection.MISSED
                        CallLog.Calls.REJECTED_TYPE -> CallDirection.REJECTED
                        else -> CallDirection.OTHER
                    }
                    results.add(
                        CallLogEntry(
                            id = id,
                            displayName = displayName,
                            number = number,
                            direction = direction,
                            timeFormatted = timeFormatter.format(Date(timestamp)),
                            timestamp = timestamp
                        )
                    )
                }
            }
        }
        results
    }

    suspend fun loadContacts(): List<ContactEntry> = withContext(Dispatchers.IO) {
        if (!hasContactsPermission()) return@withContext emptyList()
        val results = mutableListOf<ContactEntry>()
        val seenKeys = HashSet<String>()
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.STARRED
        )
        runCatching {
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                null,
                null,
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY} COLLATE NOCASE ASC"
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                val nameIdx = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY)
                val numIdx = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val starIdx = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.STARRED)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idIdx)
                    val name = cursor.getString(nameIdx)?.trim().orEmpty()
                    val number = cursor.getString(numIdx)?.trim().orEmpty()
                    if (name.isEmpty() && number.isEmpty()) continue
                    val normalizedNum = number.filter { it.isDigit() || it == '+' }
                    val dedupKey = "$id:$normalizedNum"
                    if (!seenKeys.add(dedupKey)) continue

                    val display = name.ifEmpty { number }
                    results.add(
                        ContactEntry(
                            id = id,
                            name = display,
                            number = number,
                            isStarred = cursor.getInt(starIdx) == 1,
                            initialChar = AppsRepository.computeInitialChar(display)
                        )
                    )
                }
            }
        }
        results
    }

    fun placeCall(number: String): Boolean {
        val cleaned = number.trim()
        if (cleaned.isEmpty()) return false
        val uri = Uri.fromParts("tel", cleaned, null)
        return runCatching {
            val action = if (hasCallPhonePermission()) Intent.ACTION_CALL else Intent.ACTION_DIAL
            val intent = Intent(action, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        }.getOrElse {
            runCatching {
                val dialIntent = Intent(Intent.ACTION_DIAL, uri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(dialIntent)
                true
            }.getOrDefault(false)
        }
    }

    fun openAddContact(prefillNumberOrName: String) {
        runCatching {
            val intent = Intent(ContactsContract.Intents.Insert.ACTION).apply {
                type = ContactsContract.RawContacts.CONTENT_TYPE
                val trimmed = prefillNumberOrName.trim()
                if (trimmed.isNotEmpty()) {
                    if (trimmed.any { it.isDigit() } && trimmed.none { it.isLetter() }) {
                        putExtra(ContactsContract.Intents.Insert.PHONE, trimmed)
                    } else {
                        putExtra(ContactsContract.Intents.Insert.NAME, trimmed)
                    }
                }
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }
}
