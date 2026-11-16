package com.groupbridge.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AddressesTest {

    @Test
    fun classifiesEmails() {
        assertEquals(AddressKind.ICLOUD_EMAIL, Addresses.parse("Jane@iCloud.com")!!.kind)
        assertEquals(AddressKind.ICLOUD_EMAIL, Addresses.parse("bob@me.com")!!.kind)
        assertEquals(AddressKind.ICLOUD_EMAIL, Addresses.parse("old@mac.com")!!.kind)
        assertEquals(AddressKind.GMAIL, Addresses.parse("someone@gmail.com")!!.kind)
        assertEquals(AddressKind.OTHER_EMAIL, Addresses.parse("x@outlook.com")!!.kind)
        assertEquals("jane@icloud.com", Addresses.parse("mailto:Jane@iCloud.com")!!.address)
    }

    @Test
    fun normalizesUsPhones() {
        assertEquals("+15551234567", Addresses.normalizePhone("(555) 123-4567"))
        assertEquals("+15551234567", Addresses.normalizePhone("1-555-123-4567"))
        assertEquals("+15551234567", Addresses.normalizePhone("+1 555.123.4567"))
        assertEquals("+15551234567", Addresses.parse("tel:5551234567")!!.address)
    }

    @Test
    fun normalizesInternationalPhones() {
        assertEquals("+447700900123", Addresses.normalizePhone("07700 900123", "44"))
        assertEquals("+447700900123", Addresses.normalizePhone("0044 7700 900123", "1"))
        assertEquals("+61412345678", Addresses.normalizePhone("+61 412 345 678", "1"))
    }

    @Test
    fun rejectsJunk() {
        assertNull(Addresses.parse("hello"))
        assertNull(Addresses.parse("12345"))
        assertNull(Addresses.parse(""))
        assertNull(Addresses.normalizePhone("555-CALL-NOW"))
    }

    @Test
    fun extractsFromPastedText() {
        val text = """
            Family Chat
            Mom (555) 111-2222, dad@icloud.com
            Sis: +1 555 333 4444; cousin2024@gmail.com
            Grandpa 555.111.2222
        """.trimIndent()
        val members = Addresses.extractAll(text)
        assertEquals(
            listOf("+15551112222", "dad@icloud.com", "+15553334444", "cousin2024@gmail.com"),
            members.map { it.address },
        )
    }

    @Test
    fun reportSplitsMembers() {
        val members = Addresses.extractAll("+15551112222 a@icloud.com b@gmail.com c@work.org")
        val r = GroupReport.of(members)
        assertEquals(1, r.textable.size)
        assertEquals(1, r.icloudOnly.size)
        assertEquals(2, r.otherEmails.size)
        assertEquals(3, r.emails.size)
    }

    @Test
    fun inviteMentionsEveryoneAndIcloudWarning() {
        val members = Addresses.extractAll("+15551112222 a@icloud.com")
        val msg = Messages.inviteText("Family", "+15559998888", members)
        assertTrue(msg.contains("+15559998888"))
        assertTrue(msg.contains("+15551112222"))
        assertTrue(msg.contains("a@icloud.com"))
        assertTrue(msg.contains("MMS Messaging"))
        assertEquals("+15551112222, a@icloud.com, +15559998888", Messages.pasteableList(members, "+15559998888"))
    }
}
