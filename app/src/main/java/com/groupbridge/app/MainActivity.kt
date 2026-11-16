package com.groupbridge.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var store: GroupStore
    private lateinit var list: ListView
    private lateinit var empty: TextView
    private var groups: List<Group> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        store = GroupStore(this)
        list = findViewById(R.id.group_list)
        empty = findViewById(R.id.empty_text)

        findViewById<Button>(R.id.new_group).setOnClickListener {
            startActivity(Intent(this, GroupActivity::class.java))
        }
        list.setOnItemClickListener { _, _, position, _ ->
            startActivity(
                Intent(this, GroupActivity::class.java)
                    .putExtra(GroupActivity.EXTRA_GROUP_ID, groups[position].id),
            )
        }

        if (savedInstanceState == null && store.myNumber.isBlank()) {
            showWelcome()
        }
    }

    override fun onResume() {
        super.onResume()
        groups = store.all()
        val rows = groups.map { g ->
            val r = GroupReport.of(g.members)
            val name = g.name.ifBlank { getString(R.string.untitled_group) }
            getString(R.string.group_row, name, g.members.size, r.textable.size)
        }
        list.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, rows)
        empty.visibility = if (groups.isEmpty()) View.VISIBLE else View.GONE
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(0, MENU_SETTINGS, 0, R.string.settings)
        menu.add(0, MENU_HELP, 1, R.string.how_it_works)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        MENU_SETTINGS -> { showSettings(this, store); true }
        MENU_HELP -> { showHelp(this); true }
        else -> super.onOptionsItemSelected(item)
    }

    private fun showWelcome() {
        AlertDialog.Builder(this)
            .setTitle(R.string.welcome_title)
            .setMessage(R.string.help_text)
            .setPositiveButton(R.string.set_my_number) { _, _ -> showSettings(this, store) }
            .setNegativeButton(R.string.later, null)
            .show()
    }

    companion object {
        private const val MENU_SETTINGS = 1
        private const val MENU_HELP = 2

        fun showHelp(context: Context) {
            AlertDialog.Builder(context)
                .setTitle(R.string.how_it_works)
                .setMessage(R.string.help_text)
                .setPositiveButton(android.R.string.ok, null)
                .show()
        }

        fun showSettings(context: Context, store: GroupStore, onSaved: () -> Unit = {}) {
            val pad = (16 * context.resources.displayMetrics.density).toInt()
            val layout = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(pad, pad, pad, 0)
            }
            fun label(res: Int) = TextView(context).apply { setText(res) }

            val number = EditText(context).apply {
                inputType = InputType.TYPE_CLASS_PHONE
                setText(store.myNumber)
                setHint(R.string.my_number_hint)
            }
            val country = EditText(context).apply {
                inputType = InputType.TYPE_CLASS_NUMBER
                setText(store.countryCode)
            }
            val sepGroup = RadioGroup(context)
            val options = listOf(
                "auto" to R.string.sep_auto,
                ";" to R.string.sep_semicolon,
                "," to R.string.sep_comma,
            )
            options.forEach { (value, res) ->
                sepGroup.addView(RadioButton(context).apply {
                    id = View.generateViewId()
                    setText(res)
                    tag = value
                    isChecked = store.separatorSetting == value
                })
            }

            layout.addView(label(R.string.my_number))
            layout.addView(number)
            layout.addView(label(R.string.country_code))
            layout.addView(country)
            layout.addView(label(R.string.separator))
            layout.addView(sepGroup)

            AlertDialog.Builder(context)
                .setTitle(R.string.settings)
                .setView(layout)
                .setPositiveButton(R.string.save) { _, _ ->
                    store.countryCode = country.text.toString()
                    val typed = number.text.toString()
                    store.myNumber = Addresses.normalizePhone(typed, store.countryCode) ?: typed.trim()
                    val checked = sepGroup.findViewById<RadioButton>(sepGroup.checkedRadioButtonId)
                    store.separatorSetting = (checked?.tag as? String) ?: "auto"
                    onSaved()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }
}
