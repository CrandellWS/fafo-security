package ws.crandell.fafo.mail

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import ws.crandell.fafo.camera.IncidentInfo
import ws.crandell.fafo.camera.PhotoInfo
import ws.crandell.fafo.camera.PrunePolicy
import java.io.File
import java.util.Base64

class MimeAndOutboxTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun mimeHasHeadersAttachmentsAndCrlf() {
        val jpeg = ByteArray(300) { it.toByte() }
        val m = String(MimeBuilder.build("f@example.invalid", listOf("a@example.invalid", "b@example.invalid"), "FAFO alarm: Kitchen", "Body", listOf(Attachment("p1.jpg", jpeg))))
        assertThat(m).contains("To: a@example.invalid, b@example.invalid\r\n")
        assertThat(m).contains("Content-Type: multipart/mixed; boundary=")
        assertThat(m).contains("filename=\"p1.jpg\"")
        assertThat(m.lines().all { it.length <= 998 }).isTrue()
        assertThat(m.replace("\r\n", "").contains("\n")).isFalse() // only CRLF line ends
        val b64 = m.substringAfter("filename=\"p1.jpg\"\r\n\r\n").substringBefore("\r\n--").replace("\r\n", "")
        assertThat(Base64.getDecoder().decode(b64)).isEqualTo(jpeg)
        assertThat(m.substringAfter("filename=\"p1.jpg\"\r\n\r\n").lines().first().length).isEqualTo(76)
    }

    @Test fun nonAsciiSubjectIsEncoded() {
        assertThat(MimeBuilder.encodeHeader("Alarm: Küche")).startsWith("=?UTF-8?B?")
        assertThat(MimeBuilder.encodeHeader("plain")).isEqualTo("plain")
    }

    @Test fun splitKeepsMessagesUnderTheCap() {
        val parts = MimeBuilder.split(listOf(10L, 10L, 10L, 25L), { it }, max = 25)
        assertThat(parts).containsExactly(listOf(10L, 10L), listOf(10L), listOf(25L)).inOrder()
    }

    @Test fun emailParsing() {
        val (ok, bad) = Email.parseList("a@example.org, nope, B@example.org; a@example.org")
        assertThat(ok).containsExactly("a@example.org", "B@example.org")
        assertThat(bad).containsExactly("nope")
        assertThat(Email.parseList((1..8).joinToString(",") { "u$it@example.org" }).first).hasSize(5)
    }

    @Test fun firstPhotoAloneThenBatches() {
        val o = Outbox(tmp.newFolder())
        o.enqueuePhoto("inc", "p1.jpg", 1_000)
        assertThat(o.due(1_000).single().photos).containsExactly("p1.jpg")
        o.enqueuePhoto("inc", "p2.jpg", 2_000)
        o.enqueuePhoto("inc", "p3.jpg", 3_000)
        assertThat(o.due(3_000).map { it.photos }).containsExactly(listOf("p1.jpg")) // batch not due yet
        assertThat(o.due(2_000 + Outbox.BATCH_MS).map { it.photos }).contains(listOf("p2.jpg", "p3.jpg"))
    }

    @Test fun fullBatchIsDueAtOnce() {
        val o = Outbox(tmp.newFolder())
        o.enqueuePhoto("inc", "p0.jpg", 0)
        (1..Outbox.BATCH_MAX).forEach { o.enqueuePhoto("inc", "p$it.jpg", 1_000L + it) }
        assertThat(o.due(1_010).map { it.photos.size }).contains(Outbox.BATCH_MAX)
    }

    @Test fun backoffThenPermanentFailure() {
        val o = Outbox(tmp.newFolder())
        o.enqueuePhoto("inc", "p1.jpg", 0)
        o.markFailed(o.due(0).single(), MailError.Network(), 0)
        assertThat(o.due(29_999)).isEmpty()
        assertThat(o.due(30_000)).hasSize(1)
        o.markFailed(o.due(30_000).single(), MailError.Network(), 30_000)
        assertThat(o.due(30_000 + 59_999)).isEmpty()
        o.markFailed(o.pending().single(), MailError.Auth(), 100_000)
        assertThat(o.pending()).isEmpty()
        assertThat(o.all().single().state).isEqualTo(OutboxState.FAILED_PERMANENT)
        assertThat(Outbox.backoffMs(9)).isEqualTo(900_000)
    }

    @Test fun networkBackResetsBackoff() {
        val o = Outbox(tmp.newFolder())
        o.enqueuePhoto("inc", "p1.jpg", 0)
        o.markFailed(o.due(0).single(), MailError.Timeout(), 0)
        o.resetBackoff(5)
        assertThat(o.due(5)).hasSize(1)
    }

    @Test fun capPerIncident() {
        val o = Outbox(tmp.newFolder())
        var t = 0L
        repeat(200) { o.enqueuePhoto("inc", "p$it.jpg", t); t += Outbox.BATCH_MS + 1; o.due(t).forEach { e -> if (e.open) Unit } }
        assertThat(o.all().size).isAtMost(Outbox.MAX_PER_INCIDENT)
    }

    @Test fun capPerIncidentHoldsWhenMailIsSent() {
        // A long alarm with a working connection: every due message is sent at once. The cap must still hold.
        val o = Outbox(tmp.newFolder())
        var t = 0L
        repeat(500) {
            o.enqueuePhoto("inc", "p$it.jpg", t)
            o.due(t).forEach(o::markSent)
            t += 10_000L
        }
        o.due(t + Outbox.BATCH_MS).forEach(o::markSent)
        assertThat(o.all().size).isAtMost(Outbox.MAX_PER_INCIDENT)
        assertThat(o.pending()).isEmpty()
        assertThat(o.all().map { it.seq }.toSet()).hasSize(o.all().size)
    }

    @Test fun sentRecordsExpire() {
        val o = Outbox(tmp.newFolder())
        o.enqueuePhoto("old", "p1.jpg", 0)
        o.due(0).forEach(o::markSent)
        assertThat(o.all().single().state).isEqualTo(OutboxState.SENT)
        assertThat(o.pinned()).isEmpty()
        o.enqueuePhoto("new", "p1.jpg", Outbox.SENT_KEEP_MS + 1)
        assertThat(o.all().map { it.incidentId }).containsExactly("new")
    }

    @Test fun pendingPhotosArePinnedAgainstPruning() {
        val o = Outbox(tmp.newFolder())
        o.enqueuePhoto("inc", "p2.jpg", 0)
        val inc = IncidentInfo("inc", 0, false, (1..4).map { PhotoInfo("inc", "p$it.jpg", 100, it.toLong()) })
        val plan = PrunePolicy.plan(listOf(inc), capBytes = 100, incomingBytes = 100, pinned = o.pinned())
        assertThat(plan.deletes.map { it.name }).doesNotContain("p2.jpg")
        assertThat(PrunePolicy.protectedNames(inc, o.pinned())).contains("p2.jpg")
    }

    @Test fun entriesSurviveARestart() {
        val dir = tmp.newFolder()
        Outbox(dir).enqueuePhoto("inc", "p1.jpg", 0)
        assertThat(Outbox(dir).pending().single().photos).containsExactly("p1.jpg")
        assertThat(dir.listFiles()!!.none { it.name.endsWith(".tmp") }).isTrue()
    }

    @Test fun drainSendsDueMailAndKeepsTheGap() = runTest {
        val dir = tmp.newFolder()
        val photos = tmp.newFolder()
        File(photos, "p1.jpg").writeBytes(ByteArray(10))
        File(photos, "p2.jpg").writeBytes(ByteArray(10))
        val o = Outbox(dir)
        o.enqueuePhoto("inc", "p1.jpg", 0)
        var now = 0L
        val sent = mutableListOf<String>()
        val cfg = MailConfig(true, listOf("a@example.invalid"), host = "h.invalid", user = "u@example.invalid")
        val drain = MailDrain(o, { cfg }, { MailSender { _, to, m -> sent += String(m); SendResult.Sent } }, { _, n -> File(photos, n) }, { "Kitchen" }, { now })
        drain.drainOnce()
        assertThat(sent).hasSize(1)
        assertThat(sent[0]).contains("Subject: FAFO alarm: Kitchen")
        assertThat(o.pending()).isEmpty()
        o.enqueuePhoto("inc", "p2.jpg", 1_000)
        now = 1_000 + Outbox.BATCH_MS
        drain.drainOnce()
        assertThat(sent).hasSize(2)
        assertThat(drain.sentPhotos).isEqualTo(2)
    }

    @Test fun drainRecordsFailuresForRetry() = runTest {
        val photos = tmp.newFolder(); File(photos, "p1.jpg").writeBytes(ByteArray(10))
        val o = Outbox(tmp.newFolder()); o.enqueuePhoto("inc", "p1.jpg", 0)
        val cfg = MailConfig(true, listOf("a@example.invalid"), host = "h.invalid", user = "u@example.invalid")
        val drain = MailDrain(o, { cfg }, { MailSender { _, _, _ -> SendResult.Failed(MailError.Network()) } }, { _, n -> File(photos, n) }, { "K" }, { 0 })
        val next = drain.drainOnce()
        assertThat(next).isEqualTo(30_000)
        assertThat(drain.status().pending).isEqualTo(1)
        assertThat(drain.status().lastError).isNotNull()
    }

    @Test fun drainDoesNothingWhenMailIsOff() = runTest {
        val o = Outbox(tmp.newFolder()); o.enqueuePhoto("inc", "p1.jpg", 0)
        var called = false
        MailDrain(o, { MailConfig(enabled = false) }, { called = true; null }, { _, n -> File(n) }, { "K" }, { 0 }).drainOnce()
        assertThat(called).isFalse()
        assertThat(o.pending()).hasSize(1)
    }

    @Test fun secretBoxRoundTripsAndNeverStoresPlainText() {
        val fake = object : SecretCipher {
            override fun encrypt(plain: ByteArray) = plain.map { (it.toInt() xor 0x5A).toByte() }.toByteArray()
            override fun decrypt(blob: ByteArray) = encrypt(blob)
        }
        val sealed = SecretBox.seal(fake, "hunter2-app-pass")
        assertThat(sealed).doesNotContain("hunter2")
        assertThat(SecretBox.open(fake, sealed)).isEqualTo("hunter2-app-pass")
        assertThat(SecretBox.open(fake, "")).isNull()
    }
}
