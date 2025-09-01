package com.laila.terastv.ui.dashboard

import android.content.Context
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.laila.terastv.R
import com.laila.terastv.RetrofitClient
import com.laila.terastv.ui.theme.TerasTVTheme
import com.laila.terastv.ui.theme.getRobotoFontFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.Calendar

import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import com.laila.terastv.ui.ForegroundAppService

data class AppUsageData(
    val no: Int,
    val snTV: String,
    val tanggal: String,
    val thumbnail: String,
    val namaApp: String,
    val urlApp: String,
    val durasiApp: String,
    val durasiTV: String
)

@Composable
fun DashboardScreen(
    npsn: String,
    namaSekolah: String,
    serial: String,
    modifier: Modifier = Modifier,
    onAboutClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val appUsageList = remember { mutableStateListOf<AppUsageData>() }

    LaunchedEffect(Unit) { Log.d("Dashboard", "Dashboard loaded successfully") }

    val prefs = remember { context.getSharedPreferences("tv_prefs", Context.MODE_PRIVATE) }
    var startMs by remember { mutableStateOf(prefs.getLong("tv_timer_start_ms", 0L)) }

    // ----- Filter preset state -----
    // All | Today | Last 7 Days | Last 30 Days
    var filterExpanded by remember { mutableStateOf(false) }
    var filterLabel by remember { mutableStateOf("All") }
    var dateFrom by remember { mutableStateOf<String?>(null) }
    var dateTo by remember { mutableStateOf<String?>(null) }

    fun setPreset(preset: String) {
        filterLabel = preset
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val cal = Calendar.getInstance()

        when (preset) {
            "All" -> {
                dateFrom = null
                dateTo = null
            }
            "Today" -> {
                val end = fmt.format(cal.time)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                val start = fmt.format(cal.time)
                dateFrom = "$start 00:00:00"
                dateTo = "$end 23:59:59"
            }
            "Last 7 Days" -> {
                val end = fmt.format(cal.time)
                cal.add(Calendar.DAY_OF_YEAR, -6)
                val start = fmt.format(cal.time)
                dateFrom = "$start 00:00:00"
                dateTo = "$end 23:59:59"
            }
            "Last 30 Days" -> {
                val end = fmt.format(cal.time)
                cal.add(Calendar.DAY_OF_YEAR, -29)
                val start = fmt.format(cal.time)
                dateFrom = "$start 00:00:00"
                dateTo = "$end 23:59:59"
            }
        }
    }

    // small tick to force reloads
    var refreshTick by remember { mutableStateOf(0) }
    fun refreshHistoryNow() { refreshTick++ }

    // Load history with filters
    LaunchedEffect(serial, npsn, refreshTick, dateFrom, dateTo) {
        try {
            val resp = withContext(Dispatchers.IO) {
                RetrofitClient.api.getUsageHistory(
                    npsn = npsn,
                    serial = serial,
                    dateFrom = dateFrom,
                    dateTo = dateTo
                ).execute()
            }
            val dtoList = if (resp.isSuccessful) resp.body()?.data.orEmpty() else emptyList()

            val uiList = dtoList
                .filter { it.snTv == serial }
                .mapIndexed { idx, d ->
                    AppUsageData(
                        no = idx + 1,
                        snTV = d.snTv,
                        tanggal = jsonDateToString(d.date),
                        thumbnail = d.thumbnail ?: "",
                        namaApp = d.appName,
                        urlApp = d.appTitle ?: d.appTitle, // show title if present
                        durasiApp = formatSeconds(d.appDuration ?: 0),
                        durasiTV  = formatSeconds(d.tvDuration ?: 0)
                    )
                }

            appUsageList.clear()
            appUsageList.addAll(uiList)
        } catch (e: Exception) {
            Log.e("Dashboard", "Usage history error", e)
        }
    }

    // listen for history posted + timer reset done
    DisposableEffect(Unit) {
        val filter = IntentFilter().apply {
            addAction(ForegroundAppService.ACTION_HISTORY_POSTED)
            addAction(ForegroundAppService.ACTION_TIMER_RESET_DONE)
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent?.action == ForegroundAppService.ACTION_TIMER_RESET_DONE) {
                    val newStart = intent.getLongExtra(ForegroundAppService.EXTRA_NEW_START_MS, 0L)
                    if (newStart > 0) startMs = newStart
                }
                refreshHistoryNow()
                startMs = prefs.getLong("tv_timer_start_ms", 0L)
            }
        }
        if (Build.VERSION.SDK_INT >= 33) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
        onDispose { context.unregisterReceiver(receiver) }
    }

    // fallback polling
    LaunchedEffect(serial) {
        while (isActive) {
            delay(10_000)
            refreshHistoryNow()
            startMs = prefs.getLong("tv_timer_start_ms", 0L)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {


        // Cards row (left: school info, right: timer)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // School Info Card
            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = namaSekolah,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = getRobotoFontFamily(),
                        color = Color.Black,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text(
                        text = npsn,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = getRobotoFontFamily(),
                        color = Color.Gray,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    Text(
                        text = serial,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = getRobotoFontFamily(),
                        color = Color.Gray
                    )
                }
            }

            // Timer Card
            Card(
                modifier = Modifier.wrapContentWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Box(
                    modifier = Modifier.padding(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    TVDurationCounterStartable(startMs = startMs)
                }
            }
        }

        // Title + actions row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Riwayat Pemakaian Smart TV",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = getRobotoFontFamily(),
                color = Color.Black
            )

            Row(verticalAlignment = Alignment.CenterVertically) {

                // Reset button
                Button(
                    onClick = {
                        context.sendBroadcast(
                            Intent(ForegroundAppService.ACTION_REQUEST_RESET_TIMER)
                        )
                        prefs.edit().putLong("tv_timer_start_ms", 0L).apply()
                        startMs = 0L
                        refreshHistoryNow()
                    },
                    modifier = Modifier
                        .height(36.dp)
                        .padding(end = 8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFD32F2F),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Reset",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Reset",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = getRobotoFontFamily()
                    )
                }

                // Filter menu (presets)
                Box {
                    Button(
                        onClick = { filterExpanded = true },
                        modifier = Modifier,
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DateRange,
                            contentDescription = "Filter",
                            modifier = Modifier.size(16.dp),
                            tint = Color.Black
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Filter: $filterLabel",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            fontFamily = getRobotoFontFamily(),
                            color = Color.Black
                        )
                    }

                    DropdownMenu(
                        expanded = filterExpanded,
                        onDismissRequest = { filterExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("All") },
                            onClick = {
                                filterExpanded = false
                                setPreset("All")
                                refreshHistoryNow()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Today") },
                            onClick = {
                                filterExpanded = false
                                setPreset("Today")
                                refreshHistoryNow()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Last 7 Days") },
                            onClick = {
                                filterExpanded = false
                                setPreset("Last 7 Days")
                                refreshHistoryNow()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Last 30 Days") },
                            onClick = {
                                filterExpanded = false
                                setPreset("Last 30 Days")
                                refreshHistoryNow()
                            }
                        )
                    }

                }
                // (PDF button removed as requested)
            }
        }

        // ===== Table =====
        Card(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF474EF0))
                        .padding(vertical = 12.dp, horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TableHeaderCell("No.", weight = 0.7f)
                    TableHeaderCell("SN-TV", weight = 1.2f)
                    TableHeaderCell("Tanggal", weight = 1.3f)
                    TableHeaderCell("Thumbnail", weight = 1.2f)
                    TableHeaderCell("Nama App", weight = 1.2f)
                    TableHeaderCell("App Title", weight = 1.8f)
                    TableHeaderCell("Durasi App", weight = 1.3f)
                    TableHeaderCell("Durasi TV", weight = 1.3f)
                }

                Divider(color = Color(0xFFE0E0E0), thickness = 1.dp)

                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(appUsageList) { index, item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TableDataCell(item.no.toString(), weight = 0.7f)
                            TableDataCell(item.snTV, weight = 1.2f)
                            TableDataCell(item.tanggal, weight = 1.3f)

                            Box(
                                modifier = Modifier
                                    .weight(1.2f)
                                    .height(32.dp)
                                    .padding(end = 12.dp)
                                    .background(Color(0xFFF0F0F0), RoundedCornerShape(4.dp))
                                    .border(1.dp, Color(0xFFE0E0E0), RoundedCornerShape(4.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.placeholder_img),
                                    contentDescription = "Thumbnail",
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            TableDataCell(item.namaApp, weight = 1.2f)
                            TableDataCell(item.urlApp, weight = 1.8f) // now shows App Title
                            TableDataCell(item.durasiApp, weight = 1.3f)
                            TableDataCell(item.durasiTV, weight = 1.3f)
                        }

                        if (index < appUsageList.size - 1) {
                            Divider(color = Color(0xFFE0E0E0), thickness = 1.dp)
                        }
                    }

                    // spacer rows
                    items(4) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .padding(horizontal = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            repeat(8) { Box(modifier = Modifier.weight(1f)) }
                        }
                        Divider(color = Color(0xFFE0E0E0), thickness = 1.dp)
                    }
                }
            }
        }

        // about
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            Button(
                onClick = onAboutClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color.Gray
                ),
                shape = RoundedCornerShape(20.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                modifier = Modifier.height(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.HelpOutline,
                    contentDescription = "Tentang aplikasi",
                    modifier = Modifier.size(16.dp),
                    tint = Color.Gray
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Tentang aplikasi",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = getRobotoFontFamily()
                )
            }
        }
    }
}

@Composable
fun RowScope.TableHeaderCell(text: String, weight: Float) {
    Box(modifier = Modifier.weight(weight), contentAlignment = Alignment.CenterStart) {
        Text(
            text = text,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = getRobotoFontFamily(),
            color = Color.White,
            maxLines = 1
        )
    }
}

@Composable
fun RowScope.TableDataCell(text: String, weight: Float) {
    Box(modifier = Modifier.weight(weight), contentAlignment = Alignment.CenterStart) {
        Text(
            text = text,
            fontSize = 14.sp,
            fontFamily = getRobotoFontFamily(),
            color = Color.Black,
            maxLines = 1
        )
    }
}

@Preview(showBackground = true, device = "id:tv_1080p")
@Composable
fun DashboardScreenPreview() {
    TerasTVTheme {
        DashboardScreen(
            npsn = "01234567",
            namaSekolah = "SMKN 1 NEGERI TANGGERANG",
            serial = "SN-TV"
        )
    }
}

@Composable
fun TVDurationCounterStartable(startMs: Long) {
    if (startMs <= 0L) {
        Text(
            text = "00:00:00",
            fontSize = 72.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = getRobotoFontFamily(),
            color = Color.Black,
            letterSpacing = 2.sp
        )
        return
    }

    val startTime = rememberSaveable(startMs) { startMs }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }

    LaunchedEffect(startTime) {
        while (isActive) {
            delay(1000L)
            now = System.currentTimeMillis()
        }
    }

    Text(
        text = formatDuration(now - startTime),
        fontSize = 72.sp,
        fontWeight = FontWeight.Medium,
        fontFamily = getRobotoFontFamily(),
        color = Color.Black,
        letterSpacing = 2.sp
    )
}

fun formatDuration(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (ms / 1000 % 3600) / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
}

private fun formatSeconds(totalSecondsIn: Int): String {
    val total = totalSecondsIn.coerceAtLeast(0)
    val hours = total / 3600
    val minutes = (total % 3600) / 60
    val seconds = total % 60
    return String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
}

private fun jsonDateToString(el: JsonElement?): String {
    if (el == null || el.isJsonNull) return ""
    return when (el) {
        is JsonPrimitive -> if (el.isString) el.asString else el.toString()
        is JsonObject -> when {
            el.has("date") && el.get("date").isJsonPrimitive ->
                el.getAsJsonPrimitive("date").asString
            else -> el.toString()
        }
        else -> el.toString()
    }
}
