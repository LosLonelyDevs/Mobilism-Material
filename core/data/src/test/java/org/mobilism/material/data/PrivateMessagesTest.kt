package org.mobilism.material.data

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class PrivateMessagesTest {
    @Test fun preservesConversationAndMessageIdsAndDecodesReadState() {
        val messages = PrivateMessages.parse(JSONObject("""{"response":[
            {"id":10,"msg_id":42,"subject":"Question &amp; answer","from_user":"Author","to_user":"Reader",
             "content":"<b>Hello</b>","preview":"Hello &amp; welcome","times":"1700000000","readed":"0"},
            {"id":10,"msg_id":43,"subject":"Question","from_user":"Reader","to_user":"Author","readed":1}
        ]}"""))
        assertEquals(listOf(42L, 43L), messages.map { it.messageId })
        assertEquals(10L, messages.first().id)
        assertEquals("Question & answer", messages.first().subject)
        assertEquals("Hello & welcome", messages.first().preview)
        assertEquals("<b>Hello</b>", messages.first().contentHtml)
        assertFalse(messages.first().read)
        assertTrue(messages.last().read)
        assertEquals("Author", messages.first().recipient("reader"))
        assertEquals("Author", messages.last().recipient("Reader"))
    }

    @Test fun conversationEntriesUseTheirOwnIdWhenMsgIdIsAbsent() {
        val messages = PrivateMessages.parse(JSONObject("""{"response":[{"id":42,"content":null,"preview":null}]}"""))
        assertEquals(42L, messages.single().messageId)
        assertEquals("", messages.single().contentHtml)
        assertEquals("", messages.single().preview)
        assertFalse(messages.single().toString().contains("content"))
    }

    @Test fun decodesEscapedSmileyMarkupInMailboxPreviews() {
        val messages = PrivateMessages.parse(JSONObject("""{"response":[
            {"id":1,"preview":"<b>Premium support added</b> [smile]\\ud83d\\ude0a[/smile]"},
            {"id":2,"preview":"Thanks &amp; welcome [SMILE]&#128522;[/SMILE] ❤️"}
        ]}"""))
        assertEquals("Premium support added 😊", messages[0].preview)
        assertEquals("Thanks & welcome 😊 ❤️", messages[1].preview)
    }

    @Test fun previewDecodingPreservesLiteralEscapesAndPlainEmoji() {
        val message = PrivateMessages.parse(JSONObject("""{"response":[{
            "id":1,"preview":"Code \\u1234 [smile]\\ud83d\\udc4d\\ud83c\\udffd[/smile] ❤️"
        }]}""")).single()
        assertEquals("""Code \u1234 👍🏽 ❤️""", message.preview)
    }

    @Test fun deduplicatesMessagesAndAcceptsEmptyMailboxes() {
        assertTrue(PrivateMessages.parse(JSONObject("""{"response":[]}""")).isEmpty())
        assertEquals(1, PrivateMessages.parse(JSONObject("""{"response":[{"id":1,"msg_id":2},{"id":1,"msg_id":2}]}""")).size)
    }

    @Test fun requiresExplicitActionConfirmation() {
        PrivateMessages.confirm(JSONObject("""{"status":"success"}"""))
        for (body in listOf("{}", """{"status":"error"}""")) {
            assertEquals(FailureKind.InvalidResponse,
                (runCatching { PrivateMessages.confirm(JSONObject(body)) }.exceptionOrNull() as ApiFailure).kind)
        }
    }
}
