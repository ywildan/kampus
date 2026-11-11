package id.personal.campus.agent

import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.net.UnknownServiceException
import javax.net.ssl.SSLException

/**
 * Pemetaan kegagalan jaringan ke pesan Indonesia yang bisa ditindaklanjuti.
 *
 * Berkas ini **bebas `android.*`** supaya bisa diuji di JVM/CI. Semua teks yang berasal
 * dari server harus dilewatkan [redact] lebih dulu — jangan pernah menampilkan kunci API.
 */
enum class AgentFailureKind {
    Unauthorized, Forbidden, PaymentRequired, NotFound, BadRequest, RateLimited,
    ServerError, Timeout, Dns, Offline, Blocked, Malformed, Unknown,
}

data class AgentFailure(
    val kind: AgentFailureKind,
    val message: String,
    /** Cuplikan pesan server (sudah disensor). Kosong bila tidak ada. */
    val detail: String = "",
)

sealed interface AgentResult<out T> {
    data class Success<T>(val value: T) : AgentResult<T>
    data class Failure(val failure: AgentFailure) : AgentResult<Nothing>
}

const val AGENT_REDACTION_MASK = "[kunci disamarkan]"

private val SecretPattern = Regex(
    "(sk-[A-Za-z0-9_-]{4,}|gsk_[A-Za-z0-9_-]{4,}|Bearer\\s+[A-Za-z0-9._-]{8,}|" +
        "(?:api[-_ ]?key|apikey|authorization)\"?\\s*[:=]\\s*\"?[A-Za-z0-9._-]{8,})",
    RegexOption.IGNORE_CASE,
)

/**
 * Buang kunci API dari teks apa pun sebelum ditampilkan/di-log.
 * [secrets] adalah kunci yang kita tahu; pola umum juga disensor sebagai jaring kedua.
 */
fun redact(text: String, secrets: List<String> = emptyList()): String {
    if (text.isEmpty()) return text
    var safe = text
    for (secret in secrets) {
        if (secret.length >= 6) safe = safe.replace(secret, AGENT_REDACTION_MASK)
    }
    return safe.replace(SecretPattern) { match -> match.value.take(4) + AGENT_REDACTION_MASK }
}

fun failureForStatus(code: Int, detail: String = ""): AgentFailure = when (code) {
    401 -> AgentFailure(
        AgentFailureKind.Unauthorized,
        "Kunci API ditolak (401). Periksa kembali kunci pada Setelan → Asisten AI.",
        detail,
    )
    403 -> AgentFailure(
        AgentFailureKind.Forbidden,
        "Akses ditolak (403). Kunci mungkin tidak punya izin untuk model atau endpoint ini.",
        detail,
    )
    402 -> AgentFailure(
        AgentFailureKind.PaymentRequired,
        "Kredit atau kuota akun habis (402). Isi ulang di situs penyedia layanan.",
        detail,
    )
    404 -> AgentFailure(
        AgentFailureKind.NotFound,
        "Alamat tidak ditemukan (404). Periksa base URL — beberapa penyedia memerlukan akhiran /v1.",
        detail,
    )
    422 -> AgentFailure(
        AgentFailureKind.BadRequest,
        "Permintaan ditolak server (422). Periksa nama model yang dipilih.",
        detail,
    )
    429 -> AgentFailure(
        AgentFailureKind.RateLimited,
        "Terlalu banyak permintaan (429). Tunggu sebentar, lalu coba lagi.",
        detail,
    )
    in 400..499 -> AgentFailure(
        AgentFailureKind.BadRequest,
        "Permintaan ditolak server ($code). Periksa nama model dan panjang konteks.",
        detail,
    )
    in 500..599 -> AgentFailure(
        AgentFailureKind.ServerError,
        "Server penyedia sedang bermasalah ($code). Coba lagi beberapa saat lagi.",
        detail,
    )
    else -> AgentFailure(AgentFailureKind.Unknown, "Server menjawab dengan kode tak terduga ($code).", detail)
}

fun failureForException(error: Throwable): AgentFailure = when (error) {
    is SocketTimeoutException -> AgentFailure(
        AgentFailureKind.Timeout,
        "Waktu koneksi habis. Server tidak menjawab — periksa jaringan atau alamat server.",
    )
    is UnknownHostException -> AgentFailure(
        AgentFailureKind.Dns,
        "Alamat server tidak ditemukan (DNS gagal). Periksa penulisan URL dan koneksi internet.",
    )
    is ConnectException, is NoRouteToHostException, is SocketException -> AgentFailure(
        AgentFailureKind.Offline,
        "Tidak dapat terhubung ke server. Periksa jaringan, atau pastikan server lokal (Ollama/LM Studio) sedang berjalan.",
    )
    is UnknownServiceException -> AgentFailure(
        AgentFailureKind.Blocked,
        "Koneksi http:// ke alamat ini diblokir Android. Gunakan https://, atau alamat lokal " +
            "(localhost, 10.0.2.2, atau nama host berakhiran .local).",
    )
    is SSLException -> AgentFailure(
        AgentFailureKind.Blocked,
        "Koneksi aman (TLS) gagal. Periksa sertifikat server atau gunakan https:// yang benar.",
    )
    else -> AgentFailure(
        AgentFailureKind.Unknown,
        "Gagal menghubungi server. Periksa alamat server dan jaringan, lalu coba lagi.",
    )
}
