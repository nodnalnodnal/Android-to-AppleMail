package com.groupbridge.app

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

/** Hands the group off to the phone's own texting / email apps. */
object Launch {

    /**
     * Opens the default messaging app (Google Messages, Samsung Messages...) with every
     * address pre-filled, so sending creates one group text (MMS, or RCS where supported)
     * that iPhones and Androids can all reply to.
     */
    fun groupText(activity: Activity, addresses: List<String>, separator: String, body: String = "") {
        if (addresses.isEmpty()) {
            toast(activity, R.string.no_textable)
            return
        }
        val uri = Uri.parse("smsto:" + addresses.joinToString(separator))
        val intent = Intent(Intent.ACTION_SENDTO, uri).apply {
            if (body.isNotEmpty()) putExtra("sms_body", body)
        }
        start(activity, intent, R.string.no_sms_app)
    }

    /** Opens the email app addressed to every email member (Gmail, iCloud, Outlook...). */
    fun groupEmail(activity: Activity, emails: List<String>, subject: String, body: String = "") {
        if (emails.isEmpty()) {
            toast(activity, R.string.no_emails)
            return
        }
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).apply {
            putExtra(Intent.EXTRA_EMAIL, emails.toTypedArray())
            putExtra(Intent.EXTRA_SUBJECT, subject)
            if (body.isNotEmpty()) putExtra(Intent.EXTRA_TEXT, body)
        }
        start(activity, intent, R.string.no_email_app)
    }

    /** Lets the user send text through any app (Messages, WhatsApp, email...). */
    fun share(activity: Activity, text: String, title: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        start(activity, Intent.createChooser(intent, title), R.string.no_share_app)
    }

    fun copy(context: Context, label: String, text: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(label, text))
        toast(context, R.string.copied)
    }

    private fun start(activity: Activity, intent: Intent, errorRes: Int) {
        try {
            activity.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            toast(activity, errorRes)
        }
    }

    private fun toast(context: Context, res: Int) =
        Toast.makeText(context, res, Toast.LENGTH_LONG).show()
}
