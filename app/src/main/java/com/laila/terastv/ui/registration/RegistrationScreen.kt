package com.laila.terastv.ui.registration

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.laila.terastv.R
import com.laila.terastv.network.RegisterApi
import com.laila.terastv.network.SchoolReferenceApi
import com.laila.terastv.ui.theme.TerasTVTheme
import com.laila.terastv.ui.theme.getRobotoFontFamily
import kotlinx.coroutines.launch
import java.net.URLEncoder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegistrationScreen(
    navController: NavController,
    modifier: Modifier = Modifier
) {
    var npsn by remember { mutableStateOf("") }
    var namaSekolah by remember { mutableStateOf("") }
    var serialNumberTV by remember { mutableStateOf("") }

    val scrollState = rememberScrollState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // Auto-fetch school name when NPSN changes
    LaunchedEffect(npsn) {
        if (npsn.length == 8) {
            SchoolReferenceApi.lookupSchool(npsn) { success, response ->
                if (success && response != null) {
                    val name = extractSchoolName(response)
                    if (name.isNotBlank()) namaSekolah = name
                }
            }
        }
    }

    // Matikan host default (yang di bawah), karena kita pakai host custom di kanan-atas
    Scaffold(snackbarHost = {}) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // ===== Konten utama (form) =====
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 48.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Image(
                    painter = painterResource(id = R.drawable.logo_terastv),
                    contentDescription = "TerasTV Logo",
                    modifier = Modifier
                        .widthIn(max = 120.dp)
                        .heightIn(max = 80.dp)
                        .padding(bottom = 16.dp),
                    contentScale = ContentScale.Fit
                )

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(horizontal = 48.dp, vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Registrasi",
                            fontSize = 22.sp,
                            fontFamily = getRobotoFontFamily(),
                            fontWeight = FontWeight.Bold,
                            color = Color.Black,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )

                        Text(
                            text = "Silahkan Registrasi TV Sekolah Anda di Sini!",
                            fontSize = 12.sp,
                            fontFamily = getRobotoFontFamily(),
                            fontWeight = FontWeight.Medium,
                            color = Color.Gray,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )

                        // NPSN
                        Column(Modifier.fillMaxWidth()) {
                            Text(
                                text = "NPSN",
                                fontSize = 14.sp,
                                fontFamily = getRobotoFontFamily(),
                                fontWeight = FontWeight.SemiBold,
                                color = Color.Black,
                                modifier = Modifier.padding(bottom = 6.dp)
                            )
                            OutlinedTextField(
                                value = npsn,
                                onValueChange = { npsn = it },
                                placeholder = {
                                    Text(
                                        text = "Masukkan NPSN Sekolah Anda",
                                        color = Color.Gray,
                                        fontSize = 14.sp,
                                        fontFamily = getRobotoFontFamily(),
                                        fontWeight = FontWeight.Normal
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.LocationOn,
                                        contentDescription = null,
                                        tint = Color.Gray,
                                        modifier = Modifier.size(20.dp)
                                    )
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFF777CF4),
                                    unfocusedBorderColor = Color.LightGray
                                ),
                                singleLine = true
                            )
                        }

                        Spacer(Modifier.height(12.dp))

                        // Nama Sekolah
                        Column(Modifier.fillMaxWidth()) {
                            Text(
                                text = "Nama Sekolah",
                                fontSize = 14.sp,
                                fontFamily = getRobotoFontFamily(),
                                fontWeight = FontWeight.SemiBold,
                                color = Color.Black,
                                modifier = Modifier.padding(bottom = 6.dp)
                            )
                            OutlinedTextField(
                                value = namaSekolah,
                                onValueChange = { namaSekolah = it },
                                placeholder = {
                                    Text(
                                        text = "Masukkan Nama Sekolah Anda",
                                        color = Color.Gray,
                                        fontSize = 14.sp,
                                        fontFamily = getRobotoFontFamily(),
                                        fontWeight = FontWeight.Normal
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Home,
                                        contentDescription = null,
                                        tint = Color.Gray,
                                        modifier = Modifier.size(20.dp)
                                    )
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFF777CF4),
                                    unfocusedBorderColor = Color.LightGray
                                ),
                                singleLine = true
                            )
                        }

                        Spacer(Modifier.height(12.dp))

                        // Serial Number
                        Column(Modifier.fillMaxWidth()) {
                            Text(
                                text = "Serial Number TV",
                                fontSize = 14.sp,
                                fontFamily = getRobotoFontFamily(),
                                fontWeight = FontWeight.SemiBold,
                                color = Color.Black,
                                modifier = Modifier.padding(bottom = 6.dp)
                            )
                            OutlinedTextField(
                                value = serialNumberTV,
                                onValueChange = { serialNumberTV = it },
                                placeholder = {
                                    Text(
                                        text = "Masukkan Serial Number TV Anda",
                                        color = Color.Gray,
                                        fontSize = 14.sp,
                                        fontFamily = getRobotoFontFamily(),
                                        fontWeight = FontWeight.Normal
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Settings,
                                        contentDescription = null,
                                        tint = Color.Gray,
                                        modifier = Modifier.size(20.dp)
                                    )
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFF777CF4),
                                    unfocusedBorderColor = Color.LightGray
                                ),
                                singleLine = true
                            )
                        }

                        Spacer(Modifier.height(20.dp))

                        var isLoading by remember { mutableStateOf(false) }

                        Button(
                            onClick = {
                                if (isLoading) return@Button

                                // trim biar spasi gak bikin gagal validasi/POST
                                val npsnTrim = npsn.trim()
                                val namaTrim = namaSekolah.trim()
                                val snTrim = serialNumberTV.trim()

                                if (npsnTrim.isBlank() || namaTrim.isBlank() || snTrim.isBlank()) {
                                    scope.launch {
                                        snackbarHostState.showSnackbar(
                                            message = "Semua field harus diisi",
                                            withDismissAction = false,
                                            duration = SnackbarDuration.Short
                                        )
                                    }
                                    return@Button
                                }

                                isLoading = true

                                RegisterApi.registerSchoolOnce(
                                    context = context,
                                    npsn = npsnTrim,
                                    schoolName = namaTrim,
                                    snTv = snTrim
                                ) { success, messageFromApi ->
                                    // pastikan UI update di main thread
                                    Handler(Looper.getMainLooper()).post {
                                        isLoading = false

                                        if (success) {
                                            // tampilkan banner (fire-and-forget)
                                            scope.launch {
                                                snackbarHostState.showSnackbar(
                                                    message = "Anda Berhasil Registrasi",
                                                    withDismissAction = false,
                                                    duration = SnackbarDuration.Short
                                                )
                                            }

                                            // persist & langsung navigate
                                            val prefs = context.getSharedPreferences("tv_prefs", Context.MODE_PRIVATE)
                                            prefs.edit()
                                                .putBoolean("is_registered", true)
                                                .putString("npsn", npsnTrim)
                                                .putString("school_name", namaTrim)
                                                .putString("sn_tv", snTrim)
                                                .putLong("tv_timer_start_ms", System.currentTimeMillis())
                                                .apply()

                                            Log.d("RegisterScreen",
                                                "Saved to prefs: npsn=$npsnTrim, school_name=$namaTrim, sn_tv=$snTrim")

                                            val encodedSchoolName = URLEncoder.encode(namaTrim, "UTF-8")
                                            navController.navigate(
                                                "dashboard?npsn=$npsnTrim&nama_sekolah=$encodedSchoolName&serial=$snTrim"
                                            ) {
                                                launchSingleTop = true
                                                popUpTo("registration") { inclusive = true }
                                            }
                                        } else {
                                            scope.launch {
                                                snackbarHostState.showSnackbar(
                                                    message = if (messageFromApi.isNullOrBlank())
                                                        "Gagal melakukan Registrasi"
                                                    else
                                                        messageFromApi,
                                                    withDismissAction = false,
                                                    duration = SnackbarDuration.Short
                                                )
                                            }
                                        }
                                    }
                                }
                            },
                            enabled = !isLoading,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(42.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF474EF0)
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = if (isLoading) "Loading..." else "Daftar",
                                fontSize = 16.sp,
                                fontFamily = getRobotoFontFamily(),
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
            }

            // ===== Popup kanan-atas, selalu di depan =====
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 36.dp, end = 46.dp)
                    .zIndex(100f)
            ) { data ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF474EF0),
                    shadowElevation = 8.dp
                ) {
                    Box(
                        modifier = Modifier
                            .widthIn(max = 240.dp)
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = data.visuals.message,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, device = "id:tv_1080p", fontScale = 1.0f)
@Composable
fun RegistrationPreview() {
    val dummyNavController = rememberNavController()
    TerasTVTheme {
        RegistrationScreen(navController = dummyNavController)
    }
}

fun extractSchoolName(json: String): String {
    return try {
        val root = org.json.JSONObject(json)
        val data = root.optJSONObject("data")
        data?.optString("nama", "") ?: ""
    } catch (e: Exception) {
        e.printStackTrace()
        ""
    }
}
