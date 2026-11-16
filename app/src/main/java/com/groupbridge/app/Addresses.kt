package com.groupbridge.app

/**
 * Pure-Kotlin logic (no Android dependencies) for recognising the kinds of
 * addresses that appear in an Apple Messages group, and working out what an
 * Android phone can actually do with each one.
 */

enum class AddressKind {
    /** A phone number: can be part of an SMS/MMS/RCS group text with iPhones and Androids. */
    PHONE,

    /** @icloud.com / @me.com / @mac.com: almost always an Apple ID used only for iMessage. */
    ICLOUD_EMAIL,

    /** @gmail.com / @googlemail.com. */
    GMAIL,

    /** Any other email address (Outlook, Yahoo, work email...). */
    OTHER_EMAIL,
}

data class Member(val address: String, val kind: AddressKind) {
    val isPhone get() = kind == AddressKind.PHONE
    val isEmail get() = kind != AddressKind.PHONE
}

object Addresses {

    private val ICLOUD_DOMAINS = setOf("icloud.com", "me.com", "mac.com")
    private val GMAIL_DOMAINS = setOf("gmail.com", "googlemail.com")

    private val EMAIL_REGEX =
        Regex("""[A-Za-z0-9._%+\-]+@[A-Za-z0-9.\-]+\.[A-Za-z]{2,}""")

    // A run of characters that looks like a phone number: optional +, then digits
    // with common separators. Validated further in normalizePhone().
    private val PHONE_REGEX =
        Regex("""\+?\(?\d[\d\s().\-]{5,20}\d""")

    fun isEmail(raw: String): Boolean = EMAIL_REGEX.matches(raw.trim())

    fun classifyEmail(email: String): AddressKind {
        val domain = email.substringAfterLast('@').lowercase()
        return when (domain) {
            in ICLOUD_DOMAINS -> AddressKind.ICLOUD_EMAIL
            in GMAIL_DOMAINS -> AddressKind.GMAIL
            else -> AddressKind.OTHER_EMAIL
        }
    }

    /**
     * Normalises a phone number to a "+<country><number>" style string where possible.
     *
     * @param defaultCountryCode digits of the country calling code used when the
     *   number has no leading "+" (e.g. "1" for US/Canada, "44" for UK).
     * @return the normalised number, or null if it doesn't look like a phone number.
     */
    fun normalizePhone(raw: String, defaultCountryCode: String = "1"): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty() || trimmed.any { it.isLetter() }) return null
        val hasPlus = trimmed.startsWith("+") || trimmed.startsWith("00")
        var digits = trimmed.filter { it.isDigit() }
        if (trimmed.startsWith("00")) digits = digits.removePrefix("00")
        if (digits.length < 7 || digits.length > 15) return null

        if (hasPlus) return "+$digits"

        val cc = defaultCountryCode.filter { it.isDigit() }
        if (cc == "1") {
            // North American Numbering Plan.
            return when {
                digits.length == 10 -> "+1$digits"
                digits.length == 11 && digits.startsWith("1") -> "+$digits"
                else -> digits // local/short form: leave as typed digits
            }
        }
        if (cc.isNotEmpty() && digits.startsWith("0") && digits.length >= 9) {
            // National format with trunk prefix 0 (UK, EU, AU, ...).
            return "+$cc${digits.drop(1)}"
        }
        if (cc.isNotEmpty() && digits.startsWith(cc) && digits.length >= 10) {
            return "+$digits"
        }
        return digits
    }

    /** Parses a single typed/pasted address into a Member, or null if unrecognised. */
    fun parse(raw: String, defaultCountryCode: String = "1"): Member? {
        val s = raw.trim().removePrefix("mailto:").removePrefix("tel:").removePrefix("sms:")
        if (s.isEmpty()) return null
        if (isEmail(s)) {
            val email = s.lowercase()
            return Member(email, classifyEmail(email))
        }
        val phone = normalizePhone(s, defaultCountryCode) ?: return null
        return Member(phone, AddressKind.PHONE)
    }

    /**
     * Pulls every email address and phone number out of free text, e.g. a list
     * copied from an iPhone group's details, a contact card or a message.
     * Duplicates are removed; order of first appearance is kept.
     */
    fun extractAll(text: String, defaultCountryCode: String = "1"): List<Member> {
        val hits = mutableListOf<Pair<Int, Member>>()
        for (m in EMAIL_REGEX.findAll(text)) {
            parse(m.value, defaultCountryCode)?.let { hits += m.range.first to it }
        }
        // Blank out emails (keeping offsets) so digits inside them aren't read as phone numbers.
        val withoutEmails = EMAIL_REGEX.replace(text) { " ".repeat(it.value.length) }
        for (m in PHONE_REGEX.findAll(withoutEmails)) {
            parse(m.value, defaultCountryCode)?.let { hits += m.range.first to it }
        }
        val found = LinkedHashMap<String, Member>()
        hits.sortedBy { it.first }.forEach { (_, member) -> found.putIfAbsent(member.address, member) }
        return found.values.toList()
    }

    /** Merges new members into an existing list without duplicates. */
    fun merge(existing: List<Member>, added: List<Member>): List<Member> {
        val map = LinkedHashMap<String, Member>()
        (existing + added).forEach { map.putIfAbsent(it.address, it) }
        return map.values.toList()
    }
}

/** A short human summary of what Android can do for each member of the group. */
data class GroupReport(
    val textable: List<Member>,
    val icloudOnly: List<Member>,
    val otherEmails: List<Member>,
) {
    val emails get() = icloudOnly + otherEmails

    companion object {
        fun of(members: List<Member>) = GroupReport(
            textable = members.filter { it.isPhone },
            icloudOnly = members.filter { it.kind == AddressKind.ICLOUD_EMAIL },
            otherEmails = members.filter { it.kind == AddressKind.GMAIL || it.kind == AddressKind.OTHER_EMAIL },
        )
    }
}

object Messages {

    /** Text an Android user sends to an iPhone member so they can re-create the group with them in it. */
    fun inviteText(groupName: String, myNumber: String, members: List<Member>): String {
        val report = GroupReport.of(members)
        val everyone = (members.map { it.address } + myNumber).filter { it.isNotBlank() }.distinct()
        val name = groupName.ifBlank { "our group chat" }
        return buildString {
            appendLine("Hi! I'm on Android, so iMessage can't add me to \"$name\" as it is.")
            appendLine("To include me, please start a NEW group in Messages with everyone plus my number ${myNumber.ifBlank { "(my number)" }}:")
            appendLine()
            appendLine("1. iPhone Settings > Apps > Messages: turn ON \"MMS Messaging\" (and \"RCS Messaging\" on iOS 18 or later).")
            appendLine("2. In Messages tap the compose button and add these people:")
            everyone.forEach { appendLine("   • $it") }
            appendLine("3. Send a message. It will be a group text that iPhones and Androids can all reply to.")
            if (report.icloudOnly.isNotEmpty()) {
                appendLine()
                appendLine("Note: people added only by an iCloud email (${report.icloudOnly.joinToString { it.address }}) can't get group texts. Use their phone number instead.")
            }
        }.trimEnd()
    }

    /** Addresses in a form that can be pasted into the iPhone "To:" field. */
    fun pasteableList(members: List<Member>, myNumber: String = ""): String =
        (members.map { it.address } + myNumber).filter { it.isNotBlank() }.distinct().joinToString(", ")
}
