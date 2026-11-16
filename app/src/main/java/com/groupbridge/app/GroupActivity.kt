package com.groupbridge.app

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.text.InputType
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/** Build a group from phone numbers, Gmail, iCloud and other emails, then start the chat. */
class GroupActivity : Activity() {

    private lateinit var store: GroupStore
    private lateinit var group: Group

    private lateinit var nameInput: EditText
    private lateinit var addressInput: EditText
    private lateinit var membersContainer: LinearLayout
    private lateinit var summary: TextView
    private lateinit var includeEmails: CheckBox

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_group)
        actionBar?.setDisplayHomeAsUpEnabled(true)
        store = GroupStore(this)

        val id = savedInstanceState?.getString(EXTRA_GROUP_ID) ?: intent.getStringExtra(EXTRA_GROUP_ID)
        group = id?.let { store.get(it) } ?: Group()

        nameInput = findViewById(R.id.group_name)
        addressInput = findViewById(R.id.address_input)
        membersContainer = findViewById(R.id.members)
        summary = findViewById(R.id.summary)
        includeEmails = findViewById(R.id.include_emails)

        nameInput.setText(group.name)
        includeEmails.isChecked = store.includeEmailsInText
        includeEmails.setOnCheckedChangeListener { _, checked -> store.includeEmailsInText = checked }

        findViewById<Button>(R.id.add_button).setOnClickListener { addTyped() }
        addressInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) { addTyped(); true } else false
        }
        findViewById<Button>(R.id.pick_phone).setOnClickListener {
            pickContact(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, REQ_PICK_PHONE)
        }
        findViewById<Button>(R.id.pick_email).setOnClickListener {
            pickContact(ContactsContract.CommonDataKinds.Email.CONTENT_URI, REQ_PICK_EMAIL)
        }
        findViewById<Button>(R.id.paste_list).setOnClickListener { showPasteDialog() }

        findViewById<Button>(R.id.action_group_text).setOnClickListener { startGroupText() }
        findViewById<Button>(R.id.action_email).setOnClickListener { emailGroup() }
        findViewById<Button>(R.id.action_invite).setOnClickListener { askToBeAdded() }
        findViewById<Button>(R.id.action_copy).setOnClickListener {
            Launch.copy(this, getString(R.string.app_name), Messages.pasteableList(group.members, store.myNumber))
        }

        // Text shared into the app (e.g. a contact card or a list of addresses).
        if (savedInstanceState == null && intent.action == Intent.ACTION_SEND) {
            intent.getStringExtra(Intent.EXTRA_TEXT)?.let { addFromText(it) }
        }

        render()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        persist()
        outState.putString(EXTRA_GROUP_ID, group.id)
    }

    override fun onPause() {
        super.onPause()
        persist()
    }

    private fun persist() {
        group.name = nameInput.text.toString().trim()
        if (group.members.isNotEmpty() || group.name.isNotEmpty()) store.save(group)
    }

    // ---- Adding members -------------------------------------------------------------------

    private fun addTyped() {
        val text = addressInput.text.toString()
        if (text.isBlank()) return
        val added = Addresses.extractAll(text, store.countryCode)
            .ifEmpty { listOfNotNull(Addresses.parse(text, store.countryCode)) }
        if (added.isEmpty()) {
            Toast.makeText(this, R.string.not_recognised, Toast.LENGTH_LONG).show()
            return
        }
        addMembers(added)
        addressInput.setText("")
    }

    private fun addFromText(text: String) {
        val added = Addresses.extractAll(text, store.countryCode)
        if (added.isEmpty()) {
            Toast.makeText(this, R.string.nothing_found, Toast.LENGTH_LONG).show()
        } else {
            addMembers(added)
            Toast.makeText(this, getString(R.string.added_n, added.size), Toast.LENGTH_SHORT).show()
        }
    }

    private fun addMembers(added: List<Member>) {
        group.members = Addresses.merge(group.members, added)
        persist()
        render()
    }

    private fun pickContact(uri: Uri, requestCode: Int) {
        try {
            @Suppress("DEPRECATION")
            startActivityForResult(Intent(Intent.ACTION_PICK, uri), requestCode)
        } catch (e: Exception) {
            Toast.makeText(this, R.string.no_contacts_app, Toast.LENGTH_LONG).show()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data ?: return
        if (resultCode != RESULT_OK) return
        val column = when (requestCode) {
            REQ_PICK_PHONE -> ContactsContract.CommonDataKinds.Phone.NUMBER
            REQ_PICK_EMAIL -> ContactsContract.CommonDataKinds.Email.ADDRESS
            else -> return
        }
        // The picker grants temporary read access to just this row, so no contacts permission is needed.
        val value = try {
            contentResolver.query(uri, arrayOf(column), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        } catch (e: Exception) {
            null
        }
        val member = value?.let { Addresses.parse(it, store.countryCode) }
        if (member == null) {
            Toast.makeText(this, R.string.not_recognised, Toast.LENGTH_LONG).show()
        } else {
            addMembers(listOf(member))
        }
    }

    private fun showPasteDialog() {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 4
            setHint(R.string.paste_hint)
            val clip = (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip
            val clipText = clip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this@GroupActivity)
            if (!clipText.isNullOrBlank()) setText(clipText)
        }
        val pad = (16 * resources.displayMetrics.density).toInt()
        val frame = LinearLayout(this).apply {
            setPadding(pad, pad / 2, pad, 0)
            addView(input, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.paste_list)
            .setMessage(R.string.paste_explain)
            .setView(frame)
            .setPositiveButton(R.string.add) { _, _ -> addFromText(input.text.toString()) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    // ---- Rendering ------------------------------------------------------------------------

    private fun render() {
        membersContainer.removeAllViews()
        val inflater = LayoutInflater.from(this)
        group.members.forEach { member ->
            val row = inflater.inflate(R.layout.item_member, membersContainer, false)
            row.findViewById<TextView>(R.id.member_address).text = member.address
            val (labelRes, colorRes) = when (member.kind) {
                AddressKind.PHONE -> R.string.kind_phone to R.color.ok
                AddressKind.ICLOUD_EMAIL -> R.string.kind_icloud to R.color.warn
                AddressKind.GMAIL -> R.string.kind_gmail to R.color.info
                AddressKind.OTHER_EMAIL -> R.string.kind_email to R.color.info
            }
            row.findViewById<TextView>(R.id.member_kind).apply {
                setText(labelRes)
                setTextColor(getColor(colorRes))
            }
            row.findViewById<ImageButton>(R.id.member_remove).setOnClickListener {
                group.members = group.members - member
                persist()
                render()
            }
            membersContainer.addView(row)
        }

        val r = GroupReport.of(group.members)
        summary.text = if (group.members.isEmpty()) {
            getString(R.string.summary_empty)
        } else buildString {
            append(getString(R.string.summary_text, r.textable.size))
            if (r.icloudOnly.isNotEmpty()) append("\n").append(getString(R.string.summary_icloud, r.icloudOnly.size))
            if (r.otherEmails.isNotEmpty()) append("\n").append(getString(R.string.summary_email, r.otherEmails.size))
        }
    }

    // ---- Actions --------------------------------------------------------------------------

    private fun startGroupText() {
        val r = GroupReport.of(group.members)
        val addresses = r.textable.map { it.address } +
            if (includeEmails.isChecked) r.emails.map { it.address } else emptyList()
        if (r.icloudOnly.isNotEmpty() && !includeEmails.isChecked) {
            Toast.makeText(this, getString(R.string.icloud_left_out, r.icloudOnly.size), Toast.LENGTH_LONG).show()
        }
        Launch.groupText(this, addresses, store.separator)
    }

    private fun emailGroup() {
        val r = GroupReport.of(group.members)
        Launch.groupEmail(this, r.emails.map { it.address }, nameInput.text.toString().ifBlank { getString(R.string.app_name) })
    }

    private fun askToBeAdded() {
        if (store.myNumber.isBlank()) {
            MainActivity.showSettings(this, store) { if (store.myNumber.isNotBlank()) askToBeAdded() }
            return
        }
        val text = Messages.inviteText(nameInput.text.toString(), store.myNumber, group.members)
        AlertDialog.Builder(this)
            .setTitle(R.string.invite_title)
            .setMessage(text)
            .setPositiveButton(R.string.send) { _, _ -> Launch.share(this, text, getString(R.string.invite_title)) }
            .setNeutralButton(R.string.copy) { _, _ -> Launch.copy(this, getString(R.string.invite_title), text) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    // ---- Menu -----------------------------------------------------------------------------

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(0, MENU_HELP, 0, R.string.how_it_works)
        menu.add(0, MENU_SETTINGS, 1, R.string.settings)
        menu.add(0, MENU_DELETE, 2, R.string.delete_group)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        MENU_HELP -> { MainActivity.showHelp(this); true }
        MENU_SETTINGS -> { MainActivity.showSettings(this, store); true }
        MENU_DELETE -> {
            AlertDialog.Builder(this)
                .setMessage(R.string.delete_confirm)
                .setPositiveButton(R.string.delete_group) { _, _ ->
                    store.delete(group.id)
                    group = Group() // so onPause doesn't save it again
                    nameInput.setText("")
                    finish()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    companion object {
        const val EXTRA_GROUP_ID = "group_id"
        private const val REQ_PICK_PHONE = 1
        private const val REQ_PICK_EMAIL = 2
        private const val MENU_HELP = 1
        private const val MENU_SETTINGS = 2
        private const val MENU_DELETE = 3
    }
}
