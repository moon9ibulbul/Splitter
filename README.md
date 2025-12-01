# AstralSplitter

AstralSplitter adalah aplikasi Android berbasis Kotlin untuk memotong gambar dengan tinggi sangat panjang menjadi beberapa bagian yang lebih kecil. Pengguna dapat menentukan tinggi tiap potongan atau jumlah potongan, lalu menyempurnakan posisi garis potong melalui slider sebelum menyimpan hasilnya ke folder **Pictures/AstralSplitter**.

## Fitur utama
- Pilih gambar dengan Photo Picker bawaan Android.
- Tentukan tinggi per potongan atau jumlah potongan akhir.
- Atur ulang posisi potongan menggunakan slider interaktif di atas pratinjau gambar.
- Simpan hasil ke penyimpanan eksternal (folder Pictures/AstralSplitter).

## Build
Proyek menggunakan Gradle Wrapper tanpa menyimpan berkas biner. Script `gradlew` akan otomatis mengunduh `gradle-wrapper.jar` dari Maven Central ketika dibutuhkan. Pastikan jaringan mengizinkan akses tersebut.

### Menjalankan build lokal
```bash
./gradlew assembleDebug
```

### GitHub Actions
Workflow `Build Debug APK` akan berjalan pada setiap push/pull request dan menghasilkan artefak APK debug di GitHub Actions.
