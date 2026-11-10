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

## Alat bantu
- `preview/widget-preview.html`: replika widget di browser untuk cek tampilan tanpa emulator
  (bukan bagian APK). Ukuran di dalamnya ilustratif, bukan hasil pengukuran perangkat.
- `.base/` berisi salinan bersih proyek untuk `diff` ketika perlu memastikan tidak ada yang hilang.
