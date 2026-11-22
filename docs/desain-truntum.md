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

## Navigasi

`KarsaDock.kt`: Dock pil espresso lima tab (Hari Ini, Jadwal, **Asisten**, Tugas, Pengaturan). Tombol
Asisten di tengah berupa lingkaran oranye yang menonjol dengan cincin kertas. `CampusApp.kt` menutup
aplikasi dengan splash oranye minimal 1,3 detik atau sampai data siap.
