# Desain Karsa + batik Truntum

Port tampilan dari replika web (hanya referensi visual) ke Jetpack Compose. Logika data, agen/AI,
pengingat, dan widget tidak berubah.

## Token (`ui/Theme.kt`)

| Token | Nilai |
| --- | --- |
| `Paper` | `#FBF5EC` (juga `Paper2` `#F5ECE0`, `Paper3` `#EBDFCD`, `Line` `#EBE0D1`) |
| `Ink` | `#26180F` (`Ink2` `#5A483B`, `Muted` `#8B7B6D`) |
| `Espresso` | `#2A1A10` (`Espresso2` `#3B261A`) |
| `Brand50`–`Brand800` | `#FFF5EC`, `#FFE6D0`, `#FFCB9C`, `#FFA762`, `#F88A2E`, `#E36B0F`, `#C75A08`, `#8F4208`, `#5F2C06` |

`CampusGreen` dihapus. Warna aksi/teks pakai `Brand500`/`Brand600`.

## Motif Truntum (`ui/BatikPattern.kt`)

Acuan: `asset-karsa/src/lib/brand.ts`.

- Bintang 8 sudut: 16 titik, jari-jari 10 (sumbu), 6.4, dan 2.6 (diagonal) — `truntumStarPoints`.
- Posisi bintang dalam ubin 44×44: `[22,22] [0,0] [44,0] [0,44] [44,44]`, ditambah 8 titik pengisi.
- `BatikPattern(modifier, color, alpha, tileSize = 44.dp)` menggambar ubin berulang di Compose `Canvas`.
  Pakai di dalam `Box` dengan `Modifier.matchParentSize()` di bawah konten.
- `TruntumMark` adalah logo bintang (menggantikan logo 4 kelopak Kawung).

Dipakai di: Splash (`SplashScreen.kt`), Home (kartu hero + ajakan Asisten), Pengaturan (kartu merek),
Asisten (latar layar + kartu intro), dan header editor.

## Tipografi brand

`ui/Theme.kt` memuat keluarga font asli asset-karsa sebagai resource (`res/font/`):

| Peran | Font | Berkas |
| --- | --- | --- |
| Judul (`display*`, `headline*`, `titleLarge`) | Bricolage Grotesque Bold/ExtraBold | `bricolage_grotesque_*.ttf` |
| Isi, label, tombol (`titleMedium` ke bawah) | Plus Jakarta Sans 400–800 | `plus_jakarta_sans_*.ttf` |

Berkas sudah di-instance (satu ketebalan per berkas) dan disubset ke Latin + tanda baca Indonesia,
masing-masing ±35–52 KB, jadi tidak perlu unduh font saat aplikasi berjalan. `TimeStyle` memakai
Plus Jakarta Sans ExtraBold dengan `tnum` supaya angka jam tidak bergetar.

## Bahasa gerak (`ui/Motion.kt`)

Satu set durasi dan kurva dipakai seluruh aplikasi:

| Nama | Nilai | Dipakai untuk |
| --- | --- | --- |
| `KarsaMotion.Press` | 130 ms | skala tekan tombol (0,96), ikon (0,92) |
| `KarsaMotion.Quick` | 200 ms | perubahan warna pil Dock, chip, border fokus |
| `KarsaMotion.Medium` | 320 ms | konten masuk (fade + naik 10–12 dp), perpindahan tab |
| `KarsaMotion.Long` | 440 ms | splash keluar, sheet |
| `Standard` | cubic-bezier(.2,.9,.2,1) | kurva default (sama dengan `rise` di asset-karsa) |
| `Emphasized` | cubic-bezier(.05,.7,.1,1) | elemen yang datang |
| `Exit` | cubic-bezier(.4,0,1,1) | elemen yang pergi |

Helper: `rememberEnterProgress()` (progres masuk sekali), `pressScale()`/`Modifier.pressFeedback()`,
`isPressed()`, dan `riseProgress()`.

## Tombol & interaksi (`ui/KarsaButtons.kt`)

`KarsaButton` menggantikan `Button`/`OutlinedButton`/`TextButton` Material di seluruh layar.
Varian: `Primary`, `Dark`, `Soft`, `Outline`, `Ghost`, `Danger`, `DangerSoft`.
Ukuran: `Sm` 36 dp pil · `Md` 48 dp r16 · `Lg` 56 dp r20.
Semua varian: skala tekan 0,96 + pegas, warna latar beranimasi 200 ms, keadaan nonaktif memudar,
cincin pemuat (`LoadingRing`) yang berputar, dan getaran ringan saat ditekan.

Pelengkap: `KarsaIconButton` (plain/ghost/dark/brand), `DangerButton`, `KarsaTextButton`,
`KarsaChip` (pil 36 dp dengan centang yang menyusut masuk), `KarsaSheetHandle`.

## Navigasi

`KarsaDock.kt`: Dock pil espresso lima tab (Hari Ini, Jadwal, **Asisten**, Tugas, Pengaturan).
Tombol Asisten di tengah berupa lingkaran oranye yang menonjol dengan cincin kertas dan pendar
radial yang menguat saat aktif. Tab biasa memakai pil oranye yang **mekar dari 30 → 56 dp** dengan
`spring` (damping 0,72), ikonnya terangkat 1 dp dan membesar 1,06×, dan label berubah putih dengan
animasi warna. Setiap ketukan memberi getaran `TextHandleMove`.

`CampusApp.kt`:
- Splash oranye minimal 1,3 detik, lalu memudar sambil mengecil 1,06× (`scaleOut`) ke arah konten.
- Isi tiap tab berpindah lewat `AnimatedContent`: layar baru menggeser masuk 1/14 lebar dari arah
  tab tujuan sambil memudar, layar lama menggeser keluar 1/20 lebar — arah geser mengikuti urutan
  tab sehingga ruang terasa punya satu sumbu.
- Layar Pengaturan juga memakai `AnimatedContent`: masuk ke sub-menu dari kanan, kembali ke menu dari kiri.
- Splash masuk bertahap: logo tumbuh dari 0,86 sambil naik, lalu judul dan keterangan menyusul.

## Permukaan lain yang dipoles

- `KarsaTextField`: latar `Paper2` → putih saat fokus, guratan `Brand400` yang menyala dengan animasi warna.
- `FieldSurface` (tanggal/jam) & `ChoiceField`: latar memutih + guratan oranye saat ditekan, panah
  `ExpandMore` berputar 180° saat daftar terbuka.
- `CheckCircle`: latar, guratan, dan ikon centang beranimasi (skala 0,4 → 1).
- `ValidationText` masuk dengan fade + naik 6 dp.
- `Badge`/`ToggleField`: transisi warna `Quick`; `OccurrenceHero` menekan 0,985× dan bilah progres
  waktu beranimasi; baris tugas memakai `animateItem()` saat filter berubah.
- `Toast`/snackbar: tetap memakai `SnackbarHost` M3 dengan tema Karsa.

## Widget layar utama (`widget/CampusWidgetProvider.kt`)

Widget memakai palet Karsa, tapi **tipografinya terpaksa face sistem**. Widget di-inflate di proses
launcher, dan `RemoteViews` tidak punya `setTypeface()` — `@font/...` di layout diabaikan dan jatuh ke
font sistem. Jadi permintaan "font brand juga di widget" tidak bisa dipenuhi tanpa mengganti widget
menjadi bitmap, yang akan mematikan Chronometer/TextClock live. Yang dipakai:
`sans-serif` (isi), `sans-serif-medium` (label/eyebrow), `sans-serif-black` (jam, ruangan, angka).

Warna hidup di resource (RemoteViews hanya menulis teks/ukuran/visibilitas):
- `drawable/widget_background.xml` — `Paper #FBF5EC`, garis `Line #EBE0D1`, radius **28 dp**.
- `drawable/widget_room_badge.xml` — blok **espresso `#2A1A10`** radius 20 dp, sepadan
  `RoomBadge(large)`; label `#CBB6A3`, angka putih.
- `values/colors.xml` — token `karsa_*`.

Tata letak yang membuatnya terbaca sekilas:
- Eyebrow status **selebar kartu** di baris sendiri (tanggal panjang tidak boleh berebut ruang
  dengan badge), lalu baris utama: jam + matkul di kiri, badge ruangan di kanan.
- Ukuran: eyebrow 12 sp · jam 28 sp · ruangan 32 sp (maks 40 sp di kartu lebar) · matkul 16 sp ·
  meta 12 sp · footer 11,5 sp · padding 12 dp (8 dp pada kartu pendek).
- Spacer `layout_weight=1` mendorong footer ke tepi bawah kartu, jadi tidak ada ruang mati di
  bawah jadwal yang pendek.
- `WidgetRenderer` mengukur dengan face yang sama dengan XML (`Typeface.SANS_SERIF`,
  `"sans-serif-medium"`, `"sans-serif-black"`), sehingga pemilihan ukuran cocok dengan hasil gambar.
- Tinggi badge dihitung terpisah dari eyebrow, karena eyebrow kini di luar baris utama.

Pratinjau: `preview/widget-preview.html` (dilayani `python3 -m http.server` di folder `preview/`).
