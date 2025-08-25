package com.laila.terastv.ui.about

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.laila.terastv.ui.theme.TerasTVTheme
import com.laila.terastv.ui.theme.getRobotoFontFamily

@Composable
fun AboutScreen(
    modifier: Modifier = Modifier,
    onBackClick: () -> Unit = {}
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Tombol kembali di atas
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBackClick,
                modifier = Modifier
                    .size(32.dp)
                    .background(Color.White, RoundedCornerShape(6.dp))
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Kembali",
                    tint = Color.Red,
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        // Card isi konten "Tentang Aplikasi"
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp)
            ) {
                Text(
                    text = "Tentang Aplikasi",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = getRobotoFontFamily(),
                    color = Color.Black,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "TerasTV (Telaah Rekam Aktivitas Sekolah di Televisi) adalah aplikasi pemantauan penggunaan Smart TV di lingkungan sekolah. " +
                            "Aplikasi ini menampilkan riwayat aktivitas penggunaan Smart TV, durasi penggunaan aplikasi, " +
                            "serta detail lain seperti serial number pada Smart TV dan tautan konten yang diakses. " +
                            "Tujuannya untuk membantu pihak sekolah melakukan pengawasan yang transparan dan rapi pada Smart TV di lingkungan Sekolah.",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = getRobotoFontFamily(),
                    color = Color.DarkGray,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                Text(
                    text = "Fitur Utama",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = getRobotoFontFamily(),
                    color = Color.Black,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Text(
                    text = "• Ringkasan informasi sekolah dan perangkat\n" +
                            "• Riwayat pemakaian Smart TV (aplikasi, URL, durasi)\n" +
                            "• Filter berdasarkan tanggal dan ekspor PDF\n" +
                            "• Tampilan yang ringan dan mudah dibaca",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = getRobotoFontFamily(),
                    color = Color.DarkGray,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                Text(
                    text = "Catatan",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = getRobotoFontFamily(),
                    color = Color.Black,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Text(
                    text = "Pastikan perangkat terhubung ke jaringan yang telah diizinkan. Riwayat penggunaan Smart TV hanya akan " +
                            "diperbarui saat perangkat dalam keadaan menyala. Jika Smart TV mati, tidak ada riwayat baru yang tercatat. ",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = getRobotoFontFamily(),
                    color = Color.DarkGray
                )
            }
        }
    }
}

/* ================= Preview ================= */
@Preview(showBackground = true, device = "id:tv_1080p")
@Composable
fun AboutScreenPreview() {
    TerasTVTheme {
        AboutScreen()
    }
}