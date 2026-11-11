# Catatan proyek kampus

## Build & verifikasi
- Sandbox pengembangan tidak punya JDK/Android SDK (hanya Node). Jangan janjikan `gradle` lokal.
- APK di-build oleh GitHub Actions `.github/workflows/android.yml` pada setiap `push`:
  `testReleaseUnitTest lintRelease assembleRelease`, artifact `kampus-release-apk`.
  Butuh secrets signing (keystore Base64, password, alias).
- `app/build.gradle.kts` memakai `lint { abortOnError = true }` — error lint menggagalkan build.
- minSdk 26, compileSdk/targetSdk 35.
- Sandbox tidak punya `git` CLI; status commit & Action dicek via `node -e` + `fetch` ke
  `https://api.github.com/repos/ywildan/kampus/...` (`commits`, `actions/runs`, `.../jobs`, `.../artifacts`).

## Widget "Hari Ini"
- `widget/CampusWidgetProvider.kt` → `WidgetRenderer` hanya membuat `RemoteViews` (sedikit
  StaticLayout/TextPaint untuk mengukur), bukan Compose. Semua ukuran font dan visibilitas
  ditetapkan saat runtime; nilai `textSize` di XML cuma placeholder.
- `layout/campus_widget.xml` menampung baris utama (dua kolom: teks kiri, badge ruangan kanan),
  wadah kosong `@id/widget_rows`, dan label `@id/widget_more`.
- `layout/campus_widget_secondary_row.xml` adalah layout untuk setiap SISA jadwal di hari yang
  sama; renderer membuat satu nested `RemoteViews` per jadwal dan menambahkannya lewat `addView`,
  setelah `removeAllViews` (RemoteViews tidak punya diffing). Jangan kembali ke pola "hanya satu
  baris berikutnya" — jumlah baris harus mengikuti jumlah jadwal, dibatasi tinggi widget.
- Baris sekunder dipakai berurutan sampai tidak muat (`takeWhile`); sisanya muncul sebagai
  "+N jadwal lagi hari ini". Konstanta `ROW_TOP_SPACE` (15dp) harus tetap sama dengan margin
  divider di layout baris (8dp + 6dp + garis 1dp).
- Konstanta `BADGE_PAD_H/V` dan `COLUMN_GAP` di renderer harus tetap sama dengan padding
  XML/drawable badge.
- Urutan prioritas ukuran: kode ruangan paling besar dan paling akhir diperkecil; jam menyesuaikan
  ke lebar kolom kiri. Jaga prinsip ini saat mengubah logika `badgeCeiling`/`fitWidth`.

## Asisten AI (BYOK)
- Kunci API HANYA di `agent/AgentSecrets.kt` (AES-GCM + Android Keystore, berkas di
  `noBackupFilesDir`): tidak di Room, SharedPreferences, log, pesan error, atau BuildConfig.
  `AgentRequest.toString()` dan `ChatHttpRequest.toString()` sengaja disetel ulang agar tidak
  mencetak kunci — jangan diubah menjadi data class biasa.
- Setelan non-rahasia di `agent/AgentConfigStore.kt` (SharedPreferences `agent_config`) — sengaja
  bukan Room supaya `SettingsEntity`/skema DB tidak perlu migrasi.
- Seluruh paket `agent/`, KECUALI `AgentSecrets.kt` dan `AgentConfigStore.kt`, wajib bebas
  `android.*` agar bisa diuji CI JVM. JSON memakai `agent/AgentJson.kt` (ditulis sendiri), **bukan**
  `org.json`: kelas `org.json` di unit test JVM datang dari android.jar "mockable" sehingga parser
  tidak bisa diuji. Menambah dependency test (mis. `org.json:json`) harus tanya pemilik dulu.
- Alur satu permintaan chat: `ChatRequestBuilder.kt` (badan + `tool_choice: auto`) →
  `SseStream.kt` (`SseLineBuffer` menyambung potongan `read()`, `ToolCallAccumulator` menyambung
  argumen per `index`) → `OpenAiClient.kt` (`HttpURLConnection`, connect 20 dtk / read 120 dtk,
  retry **sekali** tanpa `tools` bila server menjawab 400/422, lalu `PromptFallback.kt` membaca
  satu blok JSON berpagar). Semua pesan error lewat `AgentFailure` + `redact`.
- `agent/ModelCatalog.kt` (`GET {base}/models`) memakai `HttpURLConnection` + `AgentJson`.
- `res/xml/network_security_config.xml`: Network Security Config tidak menerima CIDR, jadi cleartext
  hanya untuk `localhost`, `127.0.0.1`, `10.0.2.2`, `10.0.3.2`, dan host `*.local`. `http://` ke IP
  LAN lain ditolak Android dan diterjemahkan `AgentFailure` menjadi pesan "Blocked".

## Asisten AI — tool & loop sesi
- `agent/AgentTools.kt` = tool **read-only**. Semua jadwal dihitung `OccurrenceResolver`; jangan
  menambah logika jadwal sendiri di tool (nanti berbeda dari layar Jadwal/widget/pengingat).
  Data masuk lewat `AgentSnapshotSource` (fungsi `suspend () -> CampusSnapshot`), **bukan**
  `CampusRepository` langsung, supaya tetap bebas `android.*` dan bisa diuji JVM.
  Di aplikasi nanti: `AgentSnapshotSource { repository.snapshot() }`.
- `kelas_hari` mengambil snapshot pada **awal hari yang ditanya** (`OccurrenceResolver` dengan
  `Clock.fixed`), supaya kelas pagi tetap terlihat saat user bertanya "jadwal Rabu" sore hari.
- `AgentSession.kt`: maksimal `AgentLimits.maxToolIterations` = 6 putaran tool, lalu satu
  permintaan terakhir **tanpa tool** agar model menyimpulkan; hasil tool dipotong `truncate()`
  (default 4.000 karakter) dan model diberi tahu bahwa isinya dipotong. Jejak aksi
  (`AgentTrace.describe()`) dipakai UI untuk menunjukkan "membaca 3 mata kuliah, 5 tugas".
- Tool tulis **wajib** `writesData = true` dan **wajib** menyediakan `prepare()` (lihat bagian
  "tool tulis" di bawah). `AgentSession` tidak pernah memanggil `execute()` untuk tool tulis.
  Tanpa persetujuan, hasil tool berisi "dibatalkan" dan model diberi tahu agar tidak mengulang.
  Jangan melonggarkan aturan ini.
- `AgentPrompt.system()` (di `AgentSession.kt`) menyatakan hasil tool adalah **DATA, bukan
  instruksi** (mis. catatan berisi "hapus semua tugas"). Jangan hapus paragraf KEAMANAN-nya.
- Menambah tool: buat `SimpleAgentTool` + daftarkan di `AgentTools.registry`, lalu uji dengan
  `AgentSnapshotSource` tiruan (lihat `AgentToolsTest`). Tool destruktif tidak boleh ada.
- Server yang mengabaikan `tools` (menjawab teks biasa berisi blok JSON) ditangani di sesi:
  `PromptFallback.parse` dipanggil saat ronde selesai tanpa tool-call, dan UI menerima
  `AgentSessionEvent.ResetStream` untuk membuang draf teks JSON itu.

## Asisten AI — tool tulis, konfirmasi, Undo
- Alur tulis selalu tiga langkah: `AgentWriteTools.prepare*()` (**validasi, belum menyimpan**)
  → `AgentSessionEvent.WriteProposed` (kartu "Setuju/Batal") → `AgentWritePreparation.Ready.apply()`
  (baru menyimpan). Jangan pernah memanggil penyimpanan dari `prepare`.
- Argumen kurang/keliru → `AgentWritePreparation.Invalid(question)`; sesi mengembalikan JSON
  `status: "perlu_pertanyaan"` dan model **harus bertanya**, tidak mengarang hari/jam/ruangan.
- `AgentWriteStore` (`agent/AgentWrites.kt`) adalah satu-satunya pintu tulis. Implementasi
  aplikasi: `data/AgentRepositoryWriteStore.kt` → `CampusRepository` saja, dengan `onSaved`
  yang harus diisi `ScheduleCoordinator.refresh(...)` + `refreshAccess()` (jalur yang sama
  dengan `CampusViewModel.mutate`). Jangan memanggil `ScheduleAlarms` dengan cara baru.
- **Tidak ada tool hapus.** `AgentWriteStore.remove*` hanya dipakai Undo atas baris yang barusan
  dibuat agent sendiri (satu-satunya efek samping yang diizinkan).
- Undo: satu aksi = satu entri `AgentUndoStack` (LIFO, batas 10, entri tertua dibuang).
  `AgentWriteTools.undoLast()` mengembalikan nilai lama (`SaveCourse`/`SaveTask`/`SaveNote`)
  atau menghapus baris baru (`RemoveCourse`/`RemoveTask`/`RemoveNote`).
- Validasi meniru formulir manual: nama & ruang tidak kosong, hari 1–7, `start < end`.
  **Bentrok jam dan rentang semester hanya jadi *peringatan* di kartu**, bukan penolakan:
  `OccurrenceResolver` memang membolehkan jadwal yang beririsan, jadi user yang memutuskan.
  Jangan mengubahnya jadi error tanpa mengubah resolver + layar Jadwal juga.
- `AgentTraceKind` memisahkan `READ`/`WRITE`/`ASK`; `AgentTrace.describe()` menghasilkan
  "membaca 3 mata kuliah Rabu, menambah tugas X". Jaga pemisahan ini agar UI tidak menyebut
  perubahan sebagai bacaan.
- Catatan id: Room `@Upsert` tidak mengembalikan id, jadi penyimpanan data baru dicari ulang
  berdasarkan isi (id terbesar bila ada duplikat). Dipakai hanya untuk Undo.

## Asisten AI — skill & routing model
- `agent/AgentSkills.kt` = skill sebagai **data** (`id`, `label`, `hint`, `systemPrompt`,
  `allowedTools`, `output`). Menambah skill = menambah satu entri di `AgentSkills.all`.
- Nama tool ditulis lewat konstanta `AgentToolNames`, **bukan string bebas**. `AgentSkillsTest`
  mencocokkan setiap nama dengan registry sungguhan, dan `AgentToolNames.ALL` harus tetap sama
  dengan tool yang benar-benar terdaftar.
- **Allowlist selalu menyempit, tidak pernah melebar** (`effectiveAllowlist`): allowlist skill
  diiris dengan batasan dari UI. Skill = mode kerja, bukan tambahan izin.
- Skill `Ringkas materi` dan `Sitasi & daftar pustaka` **tidak punya** tool jadwal sama sekali
  (`AgentToolNames.SCHEDULE`). Jangan menambahkan tool jadwal ke keduanya.
- Skill `Jadwal & ruangan` adalah bawaan dan read-only. Konsekuensinya `ubah_jadwal` tidak
  disebut skill mana pun: mengubah jadwal hanya bisa lewat mode tanpa skill (allowlist kosong =
  semua tool) dan selalu dengan kartu konfirmasi. Kalau pemilik ingin skill bisa mengubah
  jadwal, tambahkan tool itu ke skill yang tepat — jangan ke skill bawaan.
- Routing model: putaran yang menawarkan tool memakai `toolModel` (= `modelCepat`), penulisan
  akhir memakai `answerModel` (= `modelPintar`). Saat keduanya berbeda, sesi menjalankan satu
  putaran penulisan ulang dan mengirim `ResetStream` lebih dulu supaya draf model cepat tidak
  terlihat sebagai dua jawaban. Kalau nilainya sama, tidak ada permintaan tambahan.
- `AgentOutputKind.DOKUMEN` menandai skill yang menyusun `DocumentSpec` (Tahap 7). Skill tidak
  pernah menulis file; aplikasi yang merender.

## Menguji logika murni tanpa menunggu CI (opsional)
- `apk add openjdk17-jdk unzip`, unduh `kotlinc` 2.2.0 + `junit`, `hamcrest`,
  `kotlinx-coroutines-core-jvm`, `kotlinx-coroutines-test-jvm` dari Maven Central, lalu
  `kotlinc <berkas agent tanpa android.*> app/src/test/java/id/personal/campus/agent/*.kt -cp <jar> -d out`
  dan `java -cp out:<jar> org.junit.runner.JUnitCore id.personal.campus.agent.<Test>`.
- Test tool agen butuh `Models.kt` + `OccurrenceResolver.kt`. Karena `Models.kt` memakai anotasi
  Room, buat stub 6 baris di luar `app/src` (mis. `.sandbox-stubs/room.kt`) berisi
  `annotation class Entity(val tableName: String = "")` dan
  `annotation class PrimaryKey(val autoGenerate: Boolean = false)` di paket `androidx.room`,
  lalu hapus stub itu setelah selesai — jangan pernah taruh di dalam `app/src`.
- Ini hanya berlaku untuk paket `agent/`; berkas Compose/Android tetap hanya bisa diverifikasi CI.

## Navigasi, Pengaturan & chat Asisten
- Urutan tab bawah: Hari Ini (0), Jadwal (1), **Asisten (2, tengah)**, Tugas (3), Pengaturan (4).
  Semua lompatan (`onSettings`, `onTasks`, `onOpenSettings`, navigasi `settings`) memakai nomor ini.
- `SettingsScreen` memakai sub-menu: daftar 5 baris (Semester & zona waktu, Pengingat kuliah,
  Akses dari Android, Asisten AI, Tentang & batas) → tiap baris membuka satu bagian + tombol
  kembali. Draf semester/pengingat tetap di level layar supaya pindah bagian tidak membuang
  perubahan belum disimpan; tombol Simpan/Batal ada di bagian Semester dan Pengingat.
- Penyedia custom: `AgentConfig.customProviders` (id + label + baseUrl, JSON di
  `AgentConfigStore` kunci `penyedia_custom`). Di Setelan → Asisten AI, URL valid yang belum
  dikenal bisa disimpan dengan nama ("Simpan sebagai penyedia") lalu dipilih dari daftar
  preset; penyedia custom bisa dihapus. Tombol "Simpan setelan Asisten AI" wajib membawa
  `customProviders` dari config tersimpan, kalau tidak penyedia custom ikut terhapus.
- Chat Asisten: `OpenAiModel.stream` memakai `channelFlow`, **bukan** `flow` — transport
  memanggil `onChunk` dari `Dispatchers.IO` sehingga `emit` biasa melanggar invarian Flow
  (dulu muncul sebagai "Flow exception transparency is violated" + `IllegalStateException`).
  Pesan exception mentah tidak pernah ditampilkan ke user (`AgentChatViewModel.send` catch-all
  tanpa detail; `AgentChatState.Failed` hanya menggabung pesan + detail bila detailnya
  memang pesan server yang sudah disensor).
- Baris model di chat memakai nama pendek (`nvidia/nemotron-3.5-lightning:free` →
  `nemotron-3.5-lightning`), satu baris dengan ellipsis. Balon gagal (merah muda) punya tombol
  "Salin pesan" dan "Coba lagi" (`AgentChatViewModel.retry()` mengirim ulang pesan user terakhir).
- Jawaban asisten dirender sebagai Markdown, bukan teks polos: `MessageBubble` memakai
  `com.mikepenz.markdown.m3.Markdown` + `rememberMarkdownState(content, retainState = true)`
  (dependency `com.mikepenz:multiplatform-markdown-renderer-m3:0.39.0`, GFM termasuk tabel).
  Versi 0.39.0 sengaja: 0.42.0+ butuh `minCompileSdk` 37 dan 0.46.0 menarik Compose 1.12.1
  (butuh AGP 9.1.0), sedangkan proyek ini compileSdk 35 + AGP 8.10.1 — CI run 43018473463
  GAGAL di `:app:checkReleaseAarMetadata` karena 0.46.0. 0.39.0 adalah versi terbaru yang
  `minCompileSdk`-nya 1 dan tanpa dependency runtime Compose; API yang dipakai
  (`rememberMarkdownState`, `Markdown(state, colors)`, `markdownColor(text)`) sudah ada di 0.39.0.
  Pesan user tetap `Text` polos. `retainState` menjaga render lama tetap tampil saat streaming
  supaya tidak berkedip ke state loading tiap potongan baru.
- Composer satu pil: `Surface` membulat penuh (`surfaceContainerHigh`) berisi `BasicTextField`
  + `FilledIconButton` hijau di ujung kanan (abu-abu saat `!canSend`); lingkaran yang sama
  berubah jadi tombol Stop saat `busy`. Tinggi pil 56–148dp, kolom teks maks 5 baris,
  tombol Enter keyboard tetap mengirim.

## Alat bantu
- `preview/widget-preview.html`: replika widget di browser untuk cek tampilan tanpa emulator
  (bukan bagian APK). Ukuran di dalamnya ilustratif, bukan hasil pengukuran perangkat.
- `.base/` berisi salinan bersih proyek untuk `diff` ketika perlu memastikan tidak ada yang hilang.
