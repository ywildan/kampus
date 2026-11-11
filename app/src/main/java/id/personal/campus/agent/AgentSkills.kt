package id.personal.campus.agent

/**
 * Nama tool agen dalam satu tempat.
 *
 * Skill menyebut tool lewat konstanta ini — bukan string bebas — supaya salah ketik nama tool
 * ketahuan oleh unit test (`AgentSkillsTest` mencocokkannya dengan registry sungguhan).
 */
object AgentToolNames {
    // Read-only (Tahap 3)
    const val KELAS_HARI = "kelas_hari"
    const val KELAS_BERIKUTNYA = "kelas_berikutnya"
    const val JADWAL_SEMINGGU = "jadwal_seminggu"
    const val CARI_RUANGAN = "cari_ruangan"
    const val DAFTAR_TUGAS = "daftar_tugas"
    const val CARI_CATATAN = "cari_catatan"

    // Tulis (Tahap 4) — selalu lewat kartu konfirmasi + Undo.
    const val TAMBAH_TUGAS = "tambah_tugas"
    const val UBAH_JADWAL = "ubah_jadwal"
    const val TAMBAH_CATATAN = "tambah_catatan"
    const val TANDAI_TUGAS_SELESAI = "tandai_tugas_selesai"

    val READ_ONLY: Set<String> = setOf(
        KELAS_HARI,
        KELAS_BERIKUTNYA,
        JADWAL_SEMINGGU,
        CARI_RUANGAN,
        DAFTAR_TUGAS,
        CARI_CATATAN,
    )

    val WRITE: Set<String> = setOf(
        TAMBAH_TUGAS,
        UBAH_JADWAL,
        TAMBAH_CATATAN,
        TANDAI_TUGAS_SELESAI,
    )

    /** Tool yang menyentuh jadwal/ruangan. Dipakai untuk membuktikan sebuah skill tidak punya akses jadwal. */
    val SCHEDULE: Set<String> = setOf(
        KELAS_HARI,
        KELAS_BERIKUTNYA,
        JADWAL_SEMINGGU,
        CARI_RUANGAN,
    )

    val ALL: Set<String> get() = READ_ONLY + WRITE
}

/** Bentuk keluaran yang diharapkan dari sebuah skill. */
enum class AgentOutputKind {
    /** Jawaban teks di layar Asisten. */
    TEKS,

    /**
     * Model menyusun isi dokumen (Tahap 7: `DocumentSpec`) yang bisa diekspor ke CSV/PDF.
     * Skill ini **tidak** menulis file sendiri — aplikasi yang merender.
     */
    DOKUMEN,
}

/**
 * Skill = data, bukan kode.
 *
 * Menambah skill cukup menambah satu entri di [AgentSkills.all] — loop sesi, UI, dan pengujian
 * tidak perlu diubah. `allowedTools` adalah **allowlist**, jadi skill yang tidak menyebut tool
 * tidak bisa memakainya: "Ringkas materi" misalnya tidak punya akses jadwal sama sekali.
 */
data class AgentSkill(
    val id: String,
    val label: String,
    /** Satu kalimat untuk chip/pemilih di UI. */
    val hint: String,
    val systemPrompt: String,
    val allowedTools: Set<String>,
    val output: AgentOutputKind = AgentOutputKind.TEKS,
) {
    /** Skill tanpa tool tulis: agen hanya membaca. */
    val isReadOnly: Boolean get() = allowedTools.none { it in AgentToolNames.WRITE }

    val hasScheduleAccess: Boolean get() = allowedTools.any { it in AgentToolNames.SCHEDULE }

    /** Teks yang ditempelkan ke prompt sistem. Aturan keamanan dasar tetap di depan. */
    fun promptBlock(): String = buildString {
        append("PERAN SAAT INI: ")
        append(label)
        append('\n')
        append(systemPrompt.trim())
        append('\n')
        if (allowedTools.isEmpty()) {
            append("Tidak ada tool untuk peran ini. Jawab dari percakapan saja.\n")
        } else {
            append("Tool yang boleh kamu pakai hanya: ")
            append(allowedTools.sorted().joinToString(", "))
            append('.')
        }
    }
}

object AgentSkills {

    val JadwalRuangan = AgentSkill(
        id = "jadwal-ruangan",
        label = "Jadwal & ruangan",
        hint = "Tanya jadwal, jam, dan ruangan kuliah.",
        systemPrompt = """
            Fokus pada jadwal kuliah: hari, jam, ruangan, dan mata kuliah berikutnya.
            Sebutkan jam dalam format 24 jam dan selalu sertakan ruangan bila ada.
            Bila user bertanya tentang tugas atau catatan, jelaskan bahwa peran ini hanya
            membaca jadwal dan sarankan memilih skill lain.
        """,
        allowedTools = setOf(
            AgentToolNames.KELAS_HARI,
            AgentToolNames.KELAS_BERIKUTNYA,
            AgentToolNames.JADWAL_SEMINGGU,
            AgentToolNames.CARI_RUANGAN,
        ),
    )

    val RencanaBelajar = AgentSkill(
        id = "rencana-belajar",
        label = "Rencana belajar",
        hint = "Susun rencana belajar dari tugas dan jadwal.",
        systemPrompt = """
            Susun rencana belajar yang realistis: bagi materi ke beberapa sesi, urutkan dari
            tenggat terdekat, dan sisipkan waktu istirahat. Dasarkan rencana pada tugas dan
            jadwal yang benar-benar ada di data. Bila user menyetujui, simpan rencananya sebagai
            catatan lewat tool tambah_catatan (tetap menunggu persetujuan user).
        """,
        allowedTools = setOf(
            AgentToolNames.JADWAL_SEMINGGU,
            AgentToolNames.DAFTAR_TUGAS,
            AgentToolNames.CARI_CATATAN,
            AgentToolNames.TAMBAH_CATATAN,
            AgentToolNames.TAMBAH_TUGAS,
        ),
    )

    /** Sengaja TANPA tool jadwal: merangkum materi tidak butuh dan tidak boleh melihat jadwal. */
    val RingkasMateri = AgentSkill(
        id = "ringkas-materi",
        label = "Ringkas materi",
        hint = "Rangkum isi catatan kuliah menjadi poin-poin.",
        systemPrompt = """
            Rangkum catatan materi yang diberikan menjadi poin-poin padat, pertahankan istilah asli,
            dan tandai bagian yang tidak jelas sebagai "perlu dicek". Jangan menambah fakta yang
            tidak ada di catatan. Bila rangkuman ingin disimpan, gunakan tambah_catatan setelah
            user menyetujui.
        """,
        allowedTools = setOf(
            AgentToolNames.CARI_CATATAN,
            AgentToolNames.TAMBAH_CATATAN,
        ),
    )

    val DeadlineGuard = AgentSkill(
        id = "deadline-guard",
        label = "Deadline guard",
        hint = "Awasi tugas yang mendekati tenggat.",
        systemPrompt = """
            Fokus pada tenggat: sebutkan tugas yang terlambat lebih dulu, lalu yang paling dekat.
            Selalu sertakan tanggal dan mata kuliahnya. Jangan mengarang tenggat yang tidak ada
            di data. Tawarkan menandai tugas selesai hanya bila user menyetujuinya.
        """,
        allowedTools = setOf(
            AgentToolNames.DAFTAR_TUGAS,
            AgentToolNames.TAMBAH_TUGAS,
            AgentToolNames.TANDAI_TUGAS_SELESAI,
        ),
    )

    val SitasiDaftarPustaka = AgentSkill(
        id = "sitasi-daftar-pustaka",
        label = "Sitasi & daftar pustaka",
        hint = "Rapikan sitasi dari catatan kuliah.",
        systemPrompt = """
            Bantu menyusun sitasi dan daftar pustaka dari catatan yang ada. Pakai gaya APA 7 bila
            user tidak menentukan gaya lain, sebutkan gaya yang dipakai, dan tandai entri yang
            datanya tidak lengkap (mis. tanpa tahun) alih-alih mengarangnya.
        """,
        allowedTools = setOf(
            AgentToolNames.CARI_CATATAN,
            AgentToolNames.TAMBAH_CATATAN,
        ),
    )

    val EksporDokumen = AgentSkill(
        id = "ekspor-dokumen",
        label = "Ekspor dokumen",
        hint = "Susun isi dokumen untuk diekspor ke CSV/PDF.",
        systemPrompt = """
            Susun isi dokumen yang siap diekspor: judul, subjudul, beberapa bagian berisi paragraf,
            dan tabel dua kolom atau lebih bila ada data tabular. Ambil isinya hanya dari hasil tool.
            Jangan menulis file: aplikasi yang merender CSV/PDF dan user memilih lokasinya.
        """,
        allowedTools = setOf(
            AgentToolNames.JADWAL_SEMINGGU,
            AgentToolNames.DAFTAR_TUGAS,
            AgentToolNames.CARI_CATATAN,
        ),
        output = AgentOutputKind.DOKUMEN,
    )

    val RencanaMingguan = AgentSkill(
        id = "rencana-mingguan",
        label = "Rencana mingguan",
        hint = "Susun rencana tujuh hari ke depan.",
        systemPrompt = """
            Susun rencana mingguan hari per hari: kuliah yang harus dihadiri, tugas yang perlu
            dikerjakan, dan waktu belajar yang disarankan. Sebutkan hari dan tanggal. Dasarkan
            semua isi pada jadwal dan tugas yang ada di data.
        """,
        allowedTools = setOf(
            AgentToolNames.JADWAL_SEMINGGU,
            AgentToolNames.KELAS_BERIKUTNYA,
            AgentToolNames.DAFTAR_TUGAS,
            AgentToolNames.TAMBAH_TUGAS,
            AgentToolNames.TAMBAH_CATATAN,
        ),
    )

    val all: List<AgentSkill> = listOf(
        JadwalRuangan,
        RencanaBelajar,
        RingkasMateri,
        DeadlineGuard,
        SitasiDaftarPustaka,
        EksporDokumen,
        RencanaMingguan,
    )

    /** Skill bawaan: read-only, dipakai bila user belum memilih apa pun. */
    val default: AgentSkill get() = JadwalRuangan

    fun byId(id: String?): AgentSkill? = all.firstOrNull { it.id == id?.trim() }
}

/**
 * Allowlist efektif untuk satu permintaan.
 *
 * - Tanpa skill dan tanpa batasan → semua tool di registry.
 * - Hanya skill → persis allowlist skill (skill menentukan batasnya sendiri).
 * - Hanya `requestAllowed` (mis. UI membatasi) → himpunan itu.
 * - Keduanya → **irisan**, karena batasan yang lebih sempit selalu menang. Ini yang mencegah
 *   permintaan yang menyebut "Ringkas materi" tapi mengirim allowlist berisi tool jadwal.
 */
fun effectiveAllowlist(requestAllowed: Set<String>, skill: AgentSkill?): Set<String> = when {
    skill == null -> requestAllowed
    requestAllowed.isEmpty() -> skill.allowedTools
    else -> requestAllowed intersect skill.allowedTools
}