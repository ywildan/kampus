package id.personal.campus.agent

import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentFailureTest {
    @Test fun statusCodesBecomeActionableIndonesianMessages() {
        assertEquals(AgentFailureKind.Unauthorized, failureForStatus(401).kind)
        assertEquals(AgentFailureKind.Forbidden, failureForStatus(403).kind)
        assertEquals(AgentFailureKind.NotFound, failureForStatus(404).kind)
        assertEquals(AgentFailureKind.PaymentRequired, failureForStatus(402).kind)
        assertEquals(AgentFailureKind.RateLimited, failureForStatus(429).kind)
        assertEquals(AgentFailureKind.BadRequest, failureForStatus(422).kind)
        assertEquals(AgentFailureKind.ServerError, failureForStatus(503).kind)
        assertTrue(failureForStatus(401).message.contains("401"))
        assertTrue(failureForStatus(404).message.contains("/v1"))
        assertTrue(failureForStatus(429).message.contains("429"))
        assertEquals("pesan server", failureForStatus(500, "pesan server").detail)
    }

    @Test fun networkExceptionsBecomeSpecificKind() {
        assertEquals(AgentFailureKind.Timeout, failureForException(SocketTimeoutException("connect timed out")).kind)
        assertEquals(AgentFailureKind.Dns, failureForException(UnknownHostException("contoh.invalid")).kind)
        assertEquals(AgentFailureKind.Offline, failureForException(ConnectException("failed to connect")).kind)
        assertEquals(AgentFailureKind.Unknown, failureForException(IllegalStateException("tidak terduga")).kind)
        assertTrue(failureForException(UnknownHostException("contoh.invalid")).message.contains("DNS"))
    }

    @Test fun redactHidesTheStoredKeyAndItsPatterns() {
        val key = "sk-proj-AbCdEf0123456789"
        val safe = redact("Authorization gagal untuk $key (Bearer $key)", listOf(key))
        assertFalse(safe.contains("AbCdEf0123456789"))
        assertTrue(safe.contains(AGENT_REDACTION_MASK))
    }

    @Test fun redactCatchesKeyPatternsItHasNeverSeen() {
        val safe = redact("""{"error":{"message":"api_key: gsk_1234567890abcdef tidak valid"}}""")
        assertFalse(safe.contains("gsk_1234567890abcdef"))
        assertTrue(safe.contains(AGENT_REDACTION_MASK))
    }

    @Test fun redactLeavesOrdinaryTextAlone() {
        val text = "Jadwal Rabu: 3 mata kuliah, ruang A.3b.5"
        assertEquals(text, redact(text))
    }
}
