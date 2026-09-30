package com.tubenime.app

/**
 * Info pembayaran premium sideload. Ganti TODO dengan data asli pemilik:
 * - QRIS untuk pembeli lokal (string yang ditampilkan + instruksi).
 * - Tautan PayPal untuk pembeli luar negeri.
 * - Harga yang ditampilkan di layar Premium.
 */
object LicenseConfig {

    /** Harga bulanan lokal, mis. "Rp 15.000 / bulan". */
    const val PRICE_LOCAL = "Rp 15.000 / bulan" // TODO: sesuaikan harga

    /** Harga bulanan internasional, mis. "$2 / month". */
    const val PRICE_INTL = "$2 / month" // TODO: sesuaikan harga

    /** Instruksi bayar lokal (ditampilkan di layar Premium). */
    const val PAY_LOCAL = "Scan QRIS di bawah, lalu kirim bukti transfer via chat" // TODO

    /** Tautan PayPal untuk luar negeri (tombol di layar Premium). */
    const val PAYPAL_LINK = "https://paypal.me/tubenime" // TODO: ganti link asli

    /** Kontak penjual untuk kirim bukti bayar (WhatsApp/Telegram/Email). */
    const val SELLER_CONTACT = "Chat penjual untuk kode aktivasi" // TODO: nomor/link
}
