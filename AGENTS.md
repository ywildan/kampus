# Catatan proyek kampus

## Build & verifikasi
- Sandbox pengembangan tidak punya JDK/Android SDK (hanya Node). Jangan janjikan `gradle` lokal.
- APK di-build oleh GitHub Actions `.github/workflows/android.yml` pada setiap `push`:
  `testReleaseUnitTest lintRelease assembleRelease`, artifact `kampus-release-apk`.
  Butuh secrets signing (keystore Base64, password, alias).
- `app/build.gradle.kts` memakai `lint { abortOnError = true }` — error lint menggagalkan build.
- minSdk 26, compileSdk/targetSdk 35.

## Widget "Hari Ini"
- `widget/CampusWidgetProvider.kt` → `WidgetRenderer` hanya membuat `RemoteViews` (sedikit
  StaticLayout/TextPaint untuk mengukur), bukan Compose. Semua ukuran font dan visibilitas
  ditetapkan saat runtime; nilai `textSize` di XML cuma placeholder.
- `layout/campus_widget.xml` adalah satu-satunya layout: dua kolom (teks di kiri, badge ruangan di
  kanan) untuk baris utama dan baris berikutnya. Konstanta `BADGE_PAD_H/V` dan `COLUMN_GAP` di
  renderer harus tetap sama dengan padding XML/drawable badge.
- Urutan prioritas ukuran: kode ruangan paling besar dan paling akhir diperkecil; jam menyesuaikan
  ke lebar kolom kiri. Jaga prinsip ini saat mengubah logika `badgeCeiling`/`fitWidth`.

## Alat bantu
- `preview/widget-preview.html`: replika widget di browser untuk cek tampilan tanpa emulator
  (bukan bagian APK). Ukuran di dalamnya ilustratif, bukan hasil pengukuran perangkat.
- `.base/` berisi salinan bersih proyek untuk `diff` ketika perlu memastikan tidak ada yang hilang.
