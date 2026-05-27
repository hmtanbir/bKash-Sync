package com.example

import android.Manifest
import android.content.Intent
import android.provider.Settings
import android.net.Uri
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Build
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.Lifecycle
import com.example.data.database.OutboxEntity
import com.example.data.database.SyncState
import com.example.data.database.TransactionEntity
import com.example.ui.MainViewModel
import com.example.ui.VerificationStatus
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.bKashPink
import com.example.ui.theme.bKashPinkLight
import com.example.ui.theme.ExpertDarkBackground
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val smsReceived = permissions[Manifest.permission.RECEIVE_SMS] ?: false
        val smsRead = permissions[Manifest.permission.READ_SMS] ?: false
        if (smsReceived && smsRead) {
            Toast.makeText(this, "SMS Monitoring Permissions Granted!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Permissions are required for background reading.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val mainViewModel: MainViewModel = viewModel()
            val isDarkTheme by mainViewModel.isDarkMode.collectAsStateWithLifecycle()
            MyApplicationTheme(darkTheme = isDarkTheme) {
                // Request permissions at launch dynamically
                LaunchedEffect(Unit) {
                    checkAndRequestPermissions()
                }

                MainDashboardScreen(viewModel = mainViewModel)
            }
        }
    }

    private fun checkAndRequestPermissions() {
        val permissionsToRequest = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.RECEIVE_SMS)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.READ_SMS)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, "android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add("android.permission.POST_NOTIFICATIONS")
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            requestPermissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainDashboardScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf(0) }

    val isConfigured by viewModel.isConfigured.collectAsStateWithLifecycle()
    val transactions by viewModel.transactions.collectAsStateWithLifecycle()
    val totalCount by viewModel.totalTransactionsCount.collectAsStateWithLifecycle()
    val pendingCount by viewModel.pendingCount.collectAsStateWithLifecycle()
    val sentCount by viewModel.sentCount.collectAsStateWithLifecycle()
    val failedCount by viewModel.failedCount.collectAsStateWithLifecycle()
    val syncState by viewModel.syncState.collectAsStateWithLifecycle()

    val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()
    val scanResult by viewModel.scanResult.collectAsStateWithLifecycle()
    val isDarkTheme by viewModel.isDarkMode.collectAsStateWithLifecycle()

    // Status notifications for scans
    LaunchedEffect(scanResult) {
        scanResult?.let {
            if (it.isSuccess) {
                Toast.makeText(
                    context,
                    "Scan Finished! Saved: ${it.totalNewInserted} new transactions.",
                    Toast.LENGTH_LONG
                ).show()
            } else {
                Toast.makeText(
                    context,
                    "Scan Error: ${it.error}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    Scaffold(
        topBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 0.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(top = 14.dp, bottom = 0.dp, start = 18.dp, end = 18.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Title layout with theme-toggle on the far left + brand labels
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Theme Switch: Custom animated sliding switch toggle for Light vs Dark mode
                            val animatedOffset by animateDpAsState(
                                targetValue = if (isDarkTheme) 26.dp else 2.dp,
                                label = "theme_thumb_offset"
                            )
                            val containerColor by animateColorAsState(
                                targetValue = if (isDarkTheme) Color(0xFF292024) else Color(0xFFFFECEF),
                                label = "theme_container_color"
                            )
                            val thumbBgColor by animateColorAsState(
                                targetValue = if (isDarkTheme) bKashPink else Color(0xFFE2136E),
                                label = "theme_thumb_bg"
                            )
                            val iconColor by animateColorAsState(
                                targetValue = if (isDarkTheme) Color.White else Color(0xFFFFECEF),
                                label = "theme_icon_color"
                            )

                            Box(
                                modifier = Modifier
                                    .width(52.dp)
                                    .height(28.dp)
                                    .clip(RoundedCornerShape(100.dp))
                                    .background(containerColor)
                                    .clickable { viewModel.toggleDarkMode() }
                                    .testTag("theme_toggle_switch"),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Box(
                                    modifier = Modifier
                                        .padding(start = animatedOffset)
                                        .size(24.dp)
                                        .clip(RoundedCornerShape(50))
                                        .background(thumbBgColor),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(RoundedCornerShape(50))
                                            .background(iconColor)
                                    )
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(bKashPink),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh, // Professional Sync Icon
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Column {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = "bKash",
                                        fontWeight = FontWeight.Normal,
                                        fontSize = 18.sp,
                                        color = bKashPink,
                                        letterSpacing = (-0.3).sp
                                    )
                                    Text(
                                        text = "Sync",
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 18.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        letterSpacing = (-0.3).sp
                                    )
                                }
                                Text(
                                    text = "MONITORING ACTIVE PORT",
                                    color = if (isDarkTheme) Color(0xFF8B777D) else Color(0xFF94A3B8),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 1.sp
                                )
                            }
                        }
                        
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (isConfigured) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(RoundedCornerShape(50))
                                        .background(if (isDarkTheme) Color(0xFF292024) else Color(0xFFEFE4E8))
                                        .clickable { viewModel.triggerManualSync() }
                                        .testTag("manual_sync_button"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Manual sync outbox",
                                        tint = if (isDarkTheme) Color(0xFFEDE6E9) else Color(0xFF5D474C),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            val badgeBg = if (isDarkTheme) {
                                if (isConfigured) Color(0xFF132D24) else Color(0xFF381418)
                            } else {
                                if (isConfigured) Color(0xFFECFDF5) else Color(0xFFFEF2F2)
                            }
                            val badgeText = if (isDarkTheme) {
                                if (isConfigured) Color(0xFF34D399) else Color(0xFFFCA5A5)
                            } else {
                                if (isConfigured) Color(0xFF047857) else Color(0xFFB91C1C)
                            }
                            val circleColor = if (isConfigured) Color(0xFF10B981) else Color(0xFFEF4444)
                            
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(100.dp))
                                    .background(badgeBg)
                                    .padding(horizontal = 10.dp, vertical = 5.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(RoundedCornerShape(50))
                                            .background(circleColor)
                                    )
                                    Text(
                                        text = if (isConfigured) "ACTIVE" else "PAUSED",
                                        color = badgeText,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        letterSpacing = 0.5.sp
                                    )
                                }
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(14.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(if (isDarkTheme) Color(0xFF292024) else Color(0xFFE2E8F0))
                    )
                }
            }
        },
        bottomBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp
            ) {
                Column {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(if (isDarkTheme) Color(0xFF292024) else Color(0xFFE2E8F0))
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .height(64.dp)
                            .padding(horizontal = 24.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val monitorColor = if (selectedTab == 0) bKashPink else if (isDarkTheme) Color(0xFF8B777D) else Color(0xFF94A3B8)
                        val configColor = if (selectedTab == 1) bKashPink else if (isDarkTheme) Color(0xFF8B777D) else Color(0xFF94A3B8)
                        val activeTabColor = if (isDarkTheme) Color(0xFF381423) else bKashPinkLight
                        
                        // Tab 1: Monitor
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clickable(
                                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                    indication = null
                                ) { selectedTab = 0 }
                                .padding(vertical = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(56.dp)
                                    .height(32.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(if (selectedTab == 0) activeTabColor else Color.Transparent),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Home,
                                    contentDescription = "Dashboard",
                                    tint = monitorColor,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Text(
                                text = "Monitor",
                                fontSize = 11.sp,
                                fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Medium,
                                color = monitorColor
                            )
                        }
                        
                        // Tab 2: Settings / API Configuration
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clickable(
                                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                    indication = null
                                ) { selectedTab = 1 }
                                .padding(vertical = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(56.dp)
                                    .height(32.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(if (selectedTab == 1) activeTabColor else Color.Transparent),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Settings,
                                    contentDescription = "API config",
                                    tint = configColor,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Text(
                                text = "Settings",
                                fontSize = 11.sp,
                                fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Medium,
                                color = configColor
                            )
                        }
                    }
                }
            }
        },
        contentWindowInsets = WindowInsets.safeDrawing
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Configuration Warning banner if API system not configured
            if (!isConfigured) {
                val setupBg = if (isDarkTheme) Color(0xFF261014) else Color(0xFFFEE2E2)
                val setupBorder = if (isDarkTheme) Color(0xFF5A1A22) else Color(0xFFFCA5A5)
                val setupTitleText = if (isDarkTheme) Color(0xFFFCA5A5) else Color(0xFF991B1B)
                val setupDescText = if (isDarkTheme) Color(0xFFF87171) else Color(0xFFB91C1C)
                val setupIconTint = if (isDarkTheme) Color(0xFFEF4444) else Color(0xFFDC2626)
                val setupBtnColor = if (isDarkTheme) Color(0xFF991B1B) else Color(0xFFDC2626)

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = setupBg
                    ),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, setupBorder)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(
                            Icons.Outlined.Warning,
                            contentDescription = "System blocked alert",
                            tint = setupIconTint,
                            modifier = Modifier.size(36.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Setup Required",
                                fontWeight = FontWeight.Bold,
                                color = setupTitleText,
                                fontSize = 15.sp
                            )
                            Text(
                                "The monitoring system is paused. Configure and verify your backend credentials to launch automated synchronization.",
                                color = setupDescText,
                                fontSize = 12.sp
                            )
                        }
                        Button(
                            onClick = { selectedTab = 1 },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = setupBtnColor
                            ),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Configure", fontSize = 12.sp)
                        }
                    }
                }
            }

            AnimatedVisibility(visible = isScanning) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = bKashPink
                )
            }

            when (selectedTab) {
                0 -> DashboardTab(
                    viewModel = viewModel,
                    transactions = transactions,
                    totalCount = totalCount,
                    pendingCount = pendingCount,
                    sentCount = sentCount,
                    failedCount = failedCount,
                    syncState = syncState,
                    isConfigured = isConfigured,
                    isScanning = isScanning,
                    onConfigureClick = { selectedTab = 1 }
                )
                1 -> ConfigurationTab(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun DashboardTab(
    viewModel: MainViewModel,
    transactions: List<TransactionEntity>,
    totalCount: Int,
    pendingCount: Int,
    sentCount: Int,
    failedCount: Int,
    syncState: SyncState?,
    isConfigured: Boolean,
    isScanning: Boolean,
    onConfigureClick: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val outboxItems by viewModel.outboxItems.collectAsStateWithLifecycle()
    val isDarkThemeActive by viewModel.isDarkMode.collectAsStateWithLifecycle()
    val outboxMap = remember(outboxItems) {
        outboxItems.associateBy { it.trxId }
    }

    val filteredTransactions = remember(transactions, searchQuery) {
        if (searchQuery.isBlank()) {
            transactions
        } else {
            transactions.filter {
                it.trxId.contains(searchQuery, ignoreCase = true) ||
                it.senderNumber.contains(searchQuery) ||
                it.amount.contains(searchQuery)
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 24.dp, top = 12.dp)
    ) {
        // Queue status panels
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, if (isDarkThemeActive) Color(0xFF2E2428) else Color(0xFFE5DCDD))
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(if (isConfigured) Color(0xFF10B981) else Color(0xFF94A3B8))
                            )
                            Text(
                                text = "TRAFFIC MONITOR",
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                letterSpacing = 1.sp
                            )
                        }
                        
                        val lastSyncString = if (syncState != null && syncState.lastSyncTime > 0) {
                            val format = SimpleDateFormat("HH:mm - dd/MM", Locale.getDefault())
                            format.format(Date(syncState.lastSyncTime))
                        } else {
                            "Never"
                        }
                        
                        Text(
                            text = "Last Sync: $lastSyncString",
                            fontSize = 11.sp,
                            color = if (isDarkThemeActive) Color(0xFF8B777D) else Color(0xFF64748B),
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        // Metric 1: Total captured
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isDarkThemeActive) Color(0xFF2E2428) else Color(0xFFEFE4E8)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.List,
                                    contentDescription = null,
                                    tint = if (isDarkThemeActive) Color(0xFFEDE6E9) else Color(0xFF5D474C),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = String.format("%,d", totalCount),
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 20.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                letterSpacing = (-0.5).sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Total Captured",
                                fontSize = 11.sp,
                                color = if (isDarkThemeActive) Color(0xFF8B777D) else Color(0xFF64748B),
                                fontWeight = FontWeight.Medium
                            )
                        }
                        
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(40.dp)
                                .background(if (isDarkThemeActive) Color(0xFF2E2428) else Color(0xFFE5DCDD))
                        )
                        
                        // Metric 2: Outbox queue
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isDarkThemeActive) Color(0xFF381423) else bKashPinkLight),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Send,
                                    contentDescription = null,
                                    tint = bKashPink,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = pendingCount.toString(),
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 20.sp,
                                color = bKashPink,
                                letterSpacing = (-0.5).sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "In Outbox",
                                fontSize = 11.sp,
                                color = if (isDarkThemeActive) Color(0xFF8B777D) else Color(0xFF64748B),
                                fontWeight = FontWeight.Medium
                            )
                        }
                        
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(40.dp)
                                .background(if (isDarkThemeActive) Color(0xFF2E2428) else Color(0xFFE5DCDD))
                        )
                        
                        // Metric 3: Retrying failures
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            val activeRetrying = failedCount > 0
                            val retryBoxBg = if (activeRetrying) {
                                if (isDarkThemeActive) Color(0xFF45220F) else Color(0xFFFEF3C7)
                            } else {
                                if (isDarkThemeActive) Color(0xFF2E2428) else Color(0xFFEFE4E8)
                            }
                            val retryIconColor = if (activeRetrying) {
                                if (isDarkThemeActive) Color(0xFFFBBF24) else Color(0xFFD97706)
                            } else {
                                if (isDarkThemeActive) Color(0xFFEDE6E9) else Color(0xFF5D474C)
                            }
                            val retryTextColor = if (activeRetrying) {
                                if (isDarkThemeActive) Color(0xFFFBBF24) else Color(0xFFD97706)
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(retryBoxBg),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = retryIconColor,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = failedCount.toString(),
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 20.sp,
                                color = retryTextColor,
                                letterSpacing = (-0.5).sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Sync Retrying",
                                fontSize = 11.sp,
                                color = if (isDarkThemeActive) Color(0xFF8B777D) else Color(0xFF64748B),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }

        // Endpoint Overview Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, if (isDarkThemeActive) Color(0xFF2E2428) else Color(0xFFE5DCDD))
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isDarkThemeActive) Color(0xFF1E293B) else Color(0xFFEFE4E8)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "POST",
                                    color = Color(0xFF10B981), // Emerald green
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            Text(
                                "INTEGRATION CONSOLE",
                                color = if (isDarkThemeActive) Color(0xFFD6C8CE) else MaterialTheme.colorScheme.onSurface,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                        }

                        // Low profile state tag with dark/light mode balance
                        val stateBg = if (isDarkThemeActive) {
                            if (isConfigured) Color(0xFF132D24) else Color(0xFF381418)
                        } else {
                            if (isConfigured) Color(0xFFECFDF5) else Color(0xFFFEF2F2)
                        }
                        val stateText = if (isDarkThemeActive) {
                            if (isConfigured) Color(0xFF34D399) else Color(0xFFFCA5A5)
                        } else {
                            if (isConfigured) Color(0xFF047857) else Color(0xFFB91C1C)
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(stateBg)
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = if (isConfigured) "BROADCASTING" else "STANDBY",
                                color = stateText,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(14.dp))
                    
                    // Code Terminal container
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isDarkThemeActive) Color(0xFF100B0D) else Color(0xFFEFE4E8)) // Soft terminal look matching theme backgrounds
                            .border(BorderStroke(1.dp, if (isDarkThemeActive) Color(0xFF2E2428) else Color(0xFFE5DCDD)), RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            val endpointValue = viewModel.apiEndpointInput.value
                            val displayEndpoint = if (isConfigured && endpointValue.isNotBlank()) {
                                endpointValue
                            } else {
                                "https://yourserver.com/api/v1/sync"
                            }
                            Text(
                                text = displayEndpoint,
                                color = if (isDarkThemeActive) Color(0xFFE2E8F0) else MaterialTheme.colorScheme.onSurface,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "headers: { Authorization: Bearer ${if (isConfigured) "•••••••••••" else "null"} }",
                                    color = if (isDarkThemeActive) Color(0xFF8B777D) else Color(0xFF64748B),
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = "v1 JSON",
                                    color = if (isDarkThemeActive) Color(0xFF38BDF8) else Color(0xFF0284C7), // High visibility highlight
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(14.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(if (isDarkThemeActive) Color(0xFF2E2428) else Color(0xFFE5DCDD))
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "SECURITY PROTOCOL: HTTPS / SSL",
                            color = if (isDarkThemeActive) Color(0xFF8B777D) else Color(0xFF64748B),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = "CONFIGURE ENDPOINT",
                            color = if (isDarkThemeActive) Color(0xFFFF85A1) else bKashPink,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable { onConfigureClick() }
                        )
                    }
                }
            }
        }

        // Action Toolbar
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = { viewModel.triggerHistoricalScan() },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("historical_scan_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = bKashPink,
                        contentColor = Color.White,
                        disabledContainerColor = if (isDarkThemeActive) Color(0xFF211A1D) else Color(0xFFEFE4E8),
                        disabledContentColor = if (isDarkThemeActive) Color(0xFF5A484F) else Color(0xFF94A3B8)
                    ),
                    shape = RoundedCornerShape(16.dp),
                    enabled = isConfigured && !isScanning,
                    contentPadding = PaddingValues(vertical = 14.dp)
                ) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = "Search old records",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Import Old SMS", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }

                OutlinedButton(
                    onClick = { viewModel.clearLocalDatabase() },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("clear_df_button"),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                        containerColor = if (isDarkThemeActive) Color(0xFF211A1D) else Color.Transparent,
                        disabledContainerColor = if (isDarkThemeActive) Color(0xFF1D171A) else Color.Transparent
                    ),
                    border = BorderStroke(1.dp, if (isDarkThemeActive) Color(0xFF5A1A22) else Color(0xFFFECDD3)),
                    contentPadding = PaddingValues(vertical = 14.dp)
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Safe flush records",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Purge DB", color = MaterialTheme.colorScheme.error, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
            }
        }

        // Smart Delta-Sync Utility
        item {
            val context = LocalContext.current
            Button(
                onClick = {
                    viewModel.performDeltaSync()
                    Toast.makeText(context, "Initiating Smart Delta-Sync comparing local & server databases!", Toast.LENGTH_LONG).show()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("force_resync_all_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isDarkThemeActive) Color(0xFF2E2428) else Color(0xFFFAF2F5),
                    contentColor = bKashPink
                ),
                shape = RoundedCornerShape(16.dp),
                enabled = isConfigured,
                border = BorderStroke(1.dp, bKashPink),
                contentPadding = PaddingValues(vertical = 14.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Trigger Smart Delta-Sync now",
                    tint = bKashPink,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Smart Delta-Sync Now", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = bKashPink)
            }
        }

        // Search Input
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Filter logs by TrxID, sender...", fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search text") },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear search")
                        }
                    }
                },
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = bKashPink,
                    unfocusedContainerColor = if (isDarkThemeActive) Color(0xFF1D171A) else Color(0xFFFAF2F5),
                    focusedContainerColor = if (isDarkThemeActive) Color(0xFF1D171A) else Color(0xFFFAF2F5),
                    unfocusedBorderColor = if (isDarkThemeActive) Color(0xFF2E2428) else Color(0xFFE5DCDD)
                ),
                maxLines = 1,
                textStyle = TextStyle(fontSize = 14.sp)
            )
        }

        // Header Title
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Income Logs (${filteredTransactions.size})",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (filteredTransactions.size != transactions.size) {
                    Text(
                        "Filtered",
                        fontSize = 12.sp,
                        color = bKashPink,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // Empty Status placeholder
        if (filteredTransactions.isEmpty()) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, if (isDarkThemeActive) Color(0xFF2E2428) else Color(0xFFE5DCDD))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 40.dp, bottom = 40.dp, start = 24.dp, end = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(RoundedCornerShape(50))
                                .background(bKashPinkLight),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "No receipts found",
                                tint = bKashPink,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Text(
                            text = if (searchQuery.isEmpty()) "No Income Transactions Found" else "No matching transactions",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color(0xFF0F172A)
                        )
                        Text(
                            text = if (searchQuery.isEmpty()) "The system is running and listening in real-time. Any incoming bKash SMS containing 'You have received Tk' will automatically stream into this dashboard." else "No income logs correspond to the search filter. Try clearing the search query or adjusting values.",
                            textAlign = TextAlign.Center,
                            fontSize = 12.sp,
                            color = Color(0xFF64748B),
                            lineHeight = 18.sp
                        )
                    }
                }
            }
        } else {
            items(filteredTransactions, key = { it.trxId }) { tx ->
                TransactionCard(transaction = tx, associatedOutbox = outboxMap[tx.trxId])
            }
        }
    }
}

@Composable
fun StatItem(
    count: Int,
    label: String,
    icon: ImageVector,
    iconColor: Color,
    modifier: Modifier = Modifier
) {
    val isDark = MaterialTheme.colorScheme.background == ExpertDarkBackground
    Column(
        modifier = modifier.padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = count.toString(),
                fontWeight = FontWeight.Black,
                fontSize = 20.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = if (isDark) Color(0xFF8B777D) else Color(0xFF64748B),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun TransactionCard(transaction: TransactionEntity, associatedOutbox: OutboxEntity?) {
    val isDark = MaterialTheme.colorScheme.background == ExpertDarkBackground
    // Get corresponding sync status styling adjusted for Dark Mode compatibility
    val (statusLabel, statusColor, statusBg) = when (associatedOutbox?.status) {
        "SENT" -> {
            if (isDark) {
                Triple("Synced", Color(0xFF34D399), Color(0xFF132D24))
            } else {
                Triple("Synced", Color(0xFF10B981), Color(0xFFECFDF5))
            }
        }
        "FAILED" -> {
            if (isDark) {
                Triple("Sync Failed", Color(0xFFFCA5A5), Color(0xFF381418))
            } else {
                Triple("Sync Failed", Color(0xFFEF4444), Color(0xFFFEF2F2))
            }
        }
        "PENDING" -> {
            if (isDark) {
                Triple("Pending Sync", Color(0xFFFBBF24), Color(0xFF2E220F))
            } else {
                Triple("Pending Sync", Color(0xFFD97706), Color(0xFFFFFBEB))
            }
        }
        else -> {
            if (isDark) {
                Triple("Synced", Color(0xFF34D399), Color(0xFF132D24))
            } else {
                Triple("Synced", Color(0xFF10B981), Color(0xFFECFDF5))
            }
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("transaction_card_${transaction.trxId}"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, if (isDark) Color(0xFF2E2428) else Color(0xFFE5DCDD))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min) // Automatically matches height
        ) {
            // Left visual color strip highlighting status
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .fillMaxHeight()
                    .background(statusColor)
            )
            
            // Core receipt contents
            Column(
                modifier = Modifier
                    .weight(1f)
                    .animateContentSize() // Fluid expandable animation
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Leading receipt icon + Amount
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(50))
                                .background(if (isDark) Color(0xFF381423) else bKashPinkLight),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = null,
                                tint = bKashPink,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "৳${transaction.amount}",
                                fontWeight = FontWeight.ExtraBold,
                                color = if (isDark) Color(0xFF34D399) else Color(0xFF10B981), // Premium emerald green
                                fontSize = 17.sp,
                                letterSpacing = (-0.3).sp
                            )
                            Text(
                                text = "Received Income",
                                fontSize = 10.sp,
                                color = if (isDark) Color(0xFF8B777D) else Color(0xFF64748B),
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.3.sp
                            )
                        }
                    }
                    
                    // Outbox Sync Pill
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(statusBg)
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = statusLabel.uppercase(),
                            color = statusColor,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(14.dp))
                
                // Transaction identifier info grid
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // TrxID box
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "TRXID:",
                            fontSize = 11.sp,
                            color = if (isDark) Color(0xFF8B777D) else Color(0xFF94A3B8),
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = transaction.trxId,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    
                    // Time block
                    Text(
                        text = transaction.datetime,
                        fontSize = 11.sp,
                        color = if (isDark) Color(0xFF8B777D) else Color(0xFF64748B),
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.Medium
                    )
                }
                
                Spacer(modifier = Modifier.height(6.dp))
                
                // Transmitter details
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "FROM:",
                            fontSize = 11.sp,
                            color = if (isDark) Color(0xFF8B777D) else Color(0xFF94A3B8),
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = transaction.senderNumber,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    val simLabel = transaction.simSlot ?: "SIM 1"
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isDark) Color(0xFF2E2428) else Color(0xFFEFE4E8))
                            .padding(horizontal = 7.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = simLabel.uppercase(),
                            color = if (isDark) Color(0xFFEDE6E9) else Color(0xFF5D474C),
                            fontSize = 8.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
                
                // Expandable Raw SMS message toggle
                var showRaw by remember { mutableStateOf(false) }
                Spacer(modifier = Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(if (isDark) Color(0xFF2E2428) else Color(0xFFE5DCDD))
                )
                Spacer(modifier = Modifier.height(6.dp))
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { showRaw = !showRaw }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = if (showRaw) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = "Toggle text trace",
                            tint = bKashPink,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = if (showRaw) "Hide Raw Invoice SMS" else "View Raw Invoice SMS",
                            fontSize = 11.sp,
                            color = bKashPink,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (showRaw) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isDark) Color(0xFF1D171A) else Color(0xFFFAF2F5)) // Theme-appropriate bubble
                            .border(BorderStroke(1.dp, if (isDark) Color(0xFF2E2428) else Color(0xFFE5DCDD)), RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = transaction.rawSms,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = FontFamily.SansSerif,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigurationTab(viewModel: MainViewModel) {
    val endpointInput by viewModel.apiEndpointInput.collectAsStateWithLifecycle()
    val apiKeyInput by viewModel.apiKeyInput.collectAsStateWithLifecycle()
    val userPhoneInput by viewModel.userPhoneNumberInput.collectAsStateWithLifecycle()
    val isConfigured by viewModel.isConfigured.collectAsStateWithLifecycle()
    val verificationStatus by viewModel.verificationStatus.collectAsStateWithLifecycle()
    val isDark by viewModel.isDarkMode.collectAsStateWithLifecycle()
    val sweepIntervalSeconds by viewModel.syncIntervalSeconds.collectAsStateWithLifecycle()
    val autoReconcileOnSweep by viewModel.autoReconcileOnSweep.collectAsStateWithLifecycle()

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.updateBatteryOptimizationStatus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    var showPassword by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, if (isDark) Color(0xFF2E2428) else Color(0xFFE5DCDD))
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        "API Developer Sync Settings",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "Provide your secure web synchronization URL and authentication credentials to auto-broadcast incoming bKash income logs.",
                        fontSize = 12.sp,
                        color = if (isDark) Color(0xFF8B777D) else Color(0xFF64748B),
                        lineHeight = 18.sp
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(if (isDark) Color(0xFF2E2428) else Color(0xFFE5DCDD))
                    )

                    // Endpoint
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            "API Sync Endpoint (POST)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = bKashPink,
                            letterSpacing = 0.5.sp
                        )
                        OutlinedTextField(
                            value = endpointInput,
                            onValueChange = { viewModel.updateEndpoint(it) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("api_endpoint_input"),
                            placeholder = { Text("e.g. https://domain.com/api/v1/sync", fontSize = 13.sp) },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Uri,
                                imeAction = ImeAction.Next
                            ),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = bKashPink,
                                unfocusedContainerColor = if (isDark) Color(0xFF1D171A) else Color(0xFFFAF2F5),
                                focusedContainerColor = if (isDark) Color(0xFF1D171A) else Color(0xFFFAF2F5),
                                unfocusedBorderColor = if (isDark) Color(0xFF2E2428) else Color(0xFFE5DCDD),
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                            ),
                            enabled = !isConfigured
                        )
                    }

                    // API Key
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            "API Authorization bearer key",
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = bKashPink,
                            letterSpacing = 0.5.sp
                        )
                        OutlinedTextField(
                            value = apiKeyInput,
                            onValueChange = { viewModel.updateApiKey(it) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("api_key_input"),
                            placeholder = { Text("e.g. sk_live_xxxxxxxxx", fontSize = 13.sp) },
                            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Next
                            ),
                            trailingIcon = {
                                val icon = if (showPassword) Icons.Default.Info else Icons.Default.Lock
                                IconButton(onClick = { showPassword = !showPassword }) {
                                    Icon(icon, contentDescription = "Toggle password visibility")
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = bKashPink,
                                unfocusedContainerColor = if (isDark) Color(0xFF1D171A) else Color(0xFFFAF2F5),
                                focusedContainerColor = if (isDark) Color(0xFF1D171A) else Color(0xFFFAF2F5),
                                unfocusedBorderColor = if (isDark) Color(0xFF2E2428) else Color(0xFFE5DCDD),
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                            ),
                            enabled = !isConfigured
                        )
                    }

                    // User Phone Number / Sim Identifier
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            "USER PHONE NUMBER (FOR SYNC VERIFICATION)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = bKashPink,
                            letterSpacing = 0.5.sp
                        )
                        OutlinedTextField(
                            value = userPhoneInput,
                            onValueChange = { viewModel.updateUserPhone(it) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("user_phone_input"),
                            placeholder = { Text("e.g. 017XXXXXXXX", fontSize = 13.sp) },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Phone,
                                imeAction = ImeAction.Done
                            ),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = bKashPink,
                                unfocusedContainerColor = if (isDark) Color(0xFF1D171A) else Color(0xFFFAF2F5),
                                focusedContainerColor = if (isDark) Color(0xFF1D171A) else Color(0xFFFAF2F5),
                                unfocusedBorderColor = if (isDark) Color(0xFF2E2428) else Color(0xFFE5DCDD),
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                            ),
                            enabled = !isConfigured
                        )
                    }
                }
            }
        }

        // Instant Sync Sweep Frequency Settings Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, if (isDark) Color(0xFF2E2428) else Color(0xFFE5DCDD))
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "🔄 Active Sweep Frequency Setting",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Text(
                        text = "In addition to real-time SMS listeners, the daemon runs a background verification loop comparing your phone's SMS inbox with the local database to automatically recover any missed transfers.",
                        fontSize = 11.sp,
                        color = if (isDark) Color(0xFF8B777D) else Color(0xFF64748B),
                        lineHeight = 16.sp
                    )

                    // Dynamic Mode Banner describing active frequency choice
                    val (modeTitle, modeDesc, modeColor) = when {
                        sweepIntervalSeconds <= 8 -> Triple("⚡ Turbo Performance Mode", "Near-instantaneous safety sweeps. Essential for rapid high-volume store registers.", bKashPink)
                        sweepIntervalSeconds <= 20 -> Triple("🟢 Recommended Sync Speed", "Outstanding performance offering absolute sync speed with virtually zero battery impact.", if (isDark) Color(0xFF34D399) else Color(0xFF059669))
                        sweepIntervalSeconds <= 90 -> Triple("🔋 Balanced Saving Mode", "Verifies the database every minute. Ideal for extended standby battery periods.", if (isDark) Color(0xFFFB923C) else Color(0xFFEA580C))
                        else -> Triple("☁️ Deep Eco Power Saver", "Checked every 5 minutes. Maximizes phone lifespan when registers are inactive.", if (isDark) Color(0xFF60A5FA) else Color(0xFF2563EB))
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isDark) Color(0xFF1D171A) else Color(0xFFFAF2F5))
                            .border(BorderStroke(1.dp, if (isDark) Color(0xFF2E2428) else Color(0xFFE5DCDD)), RoundedCornerShape(12.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = modeTitle,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = modeColor
                        )
                        Text(
                            text = modeDesc,
                            fontSize = 11.sp,
                            color = if (isDark) Color(0xFFA8A29E) else Color(0xFF4B5563),
                            lineHeight = 15.sp
                        )
                    }

                    var customInputText by remember(sweepIntervalSeconds) { mutableStateOf(sweepIntervalSeconds.toString()) }
                    val context = LocalContext.current
                    val parsedSeconds = customInputText.toIntOrNull() ?: 0
                    val isModified = parsedSeconds != sweepIntervalSeconds && parsedSeconds >= 3

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Configure Custom Speed (in seconds):",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedTextField(
                                value = customInputText,
                                onValueChange = { newValue ->
                                    val clean = newValue.filter { it.isDigit() }
                                    if (clean.length <= 5) {
                                        customInputText = clean
                                    }
                                },
                                placeholder = { Text("15", fontSize = 13.sp) },
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Number,
                                    imeAction = ImeAction.Done
                                ),
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = bKashPink,
                                    unfocusedContainerColor = if (isDark) Color(0xFF1D171A) else Color(0xFFFAF2F5),
                                    focusedContainerColor = if (isDark) Color(0xFF1D171A) else Color(0xFFFAF2F5),
                                    unfocusedBorderColor = if (isDark) Color(0xFF2E2428) else Color(0xFFE5DCDD),
                                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                                )
                            )

                            Button(
                                onClick = {
                                    if (parsedSeconds >= 3) {
                                        viewModel.updateSyncInterval(parsedSeconds)
                                        Toast.makeText(context, "Successfully updated sweep interval to $parsedSeconds seconds!", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Minimum interval is 3 seconds for engine stability", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                enabled = isModified,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = bKashPink,
                                    contentColor = Color.White,
                                    disabledContainerColor = if (isDark) Color(0xFF2E2428) else Color(0xFFECECEC),
                                    disabledContentColor = if (isDark) Color(0xFF5A4C51) else Color(0xFF94A3B8)
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.height(56.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Save icon",
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "SAVE",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }

                        if (isModified) {
                            Text(
                                text = "⚠️ You changed the value to $parsedSeconds seconds. Tap Save to apply.",
                                color = if (isDark) Color(0xFFFDBA74) else Color(0xFFEA580C),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        } else {
                            Text(
                                text = "✓ Active and running at $sweepIntervalSeconds seconds.",
                                color = if (isDark) Color(0xFF34D399) else Color(0xFF059669),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    HorizontalDivider(
                        color = if (isDark) Color(0xFF2E2428) else Color(0xFFE5DCDD),
                        thickness = 1.dp
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "🔄 Auto-Resync All during Sweeps",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Runs bidirectional Smart Delta-Sync comparing local & server databases every $sweepIntervalSeconds seconds to automatically recover deleted transactions both ways.",
                                fontSize = 11.sp,
                                color = if (isDark) Color(0xFF8B777D) else Color(0xFF64748B),
                                lineHeight = 14.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Switch(
                            checked = autoReconcileOnSweep,
                            onCheckedChange = { isChecked ->
                                viewModel.updateAutoReconcileOnSweep(isChecked)
                                Toast.makeText(
                                    context,
                                    if (isChecked) "Auto-Resync enabled. Syncing all records every $sweepIntervalSeconds seconds."
                                    else "Auto-Resync disabled. Normal sync active.",
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = bKashPink,
                                uncheckedThumbColor = if (isDark) Color(0xFF8B777D) else Color(0xFF64748B),
                                uncheckedTrackColor = if (isDark) Color(0xFF1D171A) else Color(0xFFE5DCDD)
                            )
                        )
                    }
                }
            }
        }

        // Active Status Card indicators
        item {
            if (isConfigured) {
                val successBgColor = if (isDark) Color(0xFF132D24) else Color(0xFFECFDF5)
                val successBorderColor = if (isDark) Color(0xFF174234) else Color(0xFFA7F3D0)
                val successTextColor = if (isDark) Color(0xFF34D399) else Color(0xFF047857)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = successBgColor
                    ),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, successBorderColor)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(50))
                                .background(if (isDark) Color(0xFF174234) else Color(0xFFD1FAE5)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = "Active network status icon",
                                tint = if (isDark) Color(0xFF34D399) else Color(0xFF059669),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Port Credentials Active",
                                fontWeight = FontWeight.Bold,
                                color = successTextColor,
                                fontSize = 14.sp
                            )
                            Text(
                                "Your gateway listener is online and active. It will intercept SMS logs instantly and broadcast them matching the webhook schema.",
                                color = if (isDark) Color(0xFF6EDDB0) else Color(0xFF059669),
                                fontSize = 11.sp,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }
        }

        // Constant Background Running (Battery Optimization Exemption status) Card
        item {
            val isIgnored by viewModel.isBatteryOptimizationIgnored.collectAsStateWithLifecycle()
            val context = LocalContext.current

            val cardBgColor = if (isIgnored) {
                if (isDark) Color(0xFF132D24) else Color(0xFFECFDF5)
            } else {
                if (isDark) Color(0xFF382315) else Color(0xFFFFF7ED)
            }
            val cardBorderColor = if (isIgnored) {
                if (isDark) Color(0xFF174234) else Color(0xFFA7F3D0)
            } else {
                if (isDark) Color(0xFF7C2D12) else Color(0xFFFED7AA)
            }
            val textColor = if (isIgnored) {
                if (isDark) Color(0xFF34D399) else Color(0xFF047857)
            } else {
                if (isDark) Color(0xFFF97316) else Color(0xFFC2410C)
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = cardBgColor
                ),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, cardBorderColor)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(50))
                                .background(
                                    if (isIgnored) {
                                        if (isDark) Color(0xFF174234) else Color(0xFFD1FAE5)
                                    } else {
                                        if (isDark) Color(0xFF7C2D12) else Color(0xFFFFEDD5)
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isIgnored) Icons.Default.CheckCircle else Icons.Default.Warning,
                                contentDescription = "Battery optimization status icon",
                                tint = if (isIgnored) {
                                    if (isDark) Color(0xFF34D399) else Color(0xFF059669)
                                } else {
                                    if (isDark) Color(0xFFFB923C) else Color(0xFFEA580C)
                                },
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isIgnored) "Constant Background Running: ACTIVE" else "Constant Background Running: RESTRICTED",
                                fontWeight = FontWeight.Bold,
                                color = textColor,
                                fontSize = 14.sp
                            )
                            Text(
                                text = if (isIgnored) {
                                    "Battery optimizations are bypassed. The background monitor sync engine will run constantly and process incoming bKash SMS logs immediately."
                                } else {
                                    "Android puts the background monitor sync engine to sleep when idle. For instant 24/7 bKash transfers, bypass battery optimization below."
                                },
                                color = if (isIgnored) {
                                    if (isDark) Color(0xFF6EDDB0) else Color(0xFF059669)
                                } else {
                                    if (isDark) Color(0xFFFDBA74) else Color(0xFFC2410C)
                                },
                                fontSize = 11.sp,
                                lineHeight = 16.sp
                            )
                        }
                    }

                    if (!isIgnored) {
                        Button(
                            onClick = {
                                try {
                                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                        data = Uri.parse("package:${context.packageName}")
                                    }
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    try {
                                        val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                        context.startActivity(intent)
                                    } catch (ex: Exception) {
                                        val intent = Intent(Settings.ACTION_SETTINGS)
                                        context.startActivity(intent)
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isDark) Color(0xFFC2410C) else Color(0xFFEA580C)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "ALLOW CONSTANT BACKGROUND RUNNING",
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }

        // Webhook JSON Payload Preview block
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (isDark) Color(0xFF191215) else Color(0xFF1E171B) // Brand terminal color block
                ),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, if (isDark) Color(0xFF292024) else Color(0xFF32252A))
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isDark) Color(0xFF2E2428) else Color(0xFF3E3137)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = Color(0xFFFF85A1),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        Text(
                            "WEBHOOK PAYLOAD SCHEMA",
                            color = Color(0xFFD6C8CE),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Any captured income event is broadcasted as a real-time HTTP POST request with the following JSON structure:",
                        color = Color(0xFFAFA0A6),
                        fontSize = 11.sp,
                        lineHeight = 16.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isDark) Color(0xFF100B0D) else Color(0xFF140E10)) // Eye-safe terminal midnight black
                            .padding(12.dp)
                    ) {
                        Text(
                            text = "{\n  \"trxId\": \"8K4A3B9ZC1\",\n  \"amount\": \"1250.00\",\n  \"senderNumber\": \"017XXXXXXXX\",\n  \"datetime\": \"2026-05-21 16:30:00\",\n  \"balance\": \"14500.25\",\n  \"userPhoneNumber\": \"017XXXXXXXX\",\n  \"rawSms\": \"You have received Tk 1,250.00...\"\n}",
                            color = Color(0xFF34D399), // Monospace green terminal text
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }

        // Action controls
        item {
            when (verificationStatus) {
                is VerificationStatus.LOADING -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(color = bKashPink)
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            "Probing connection `/verify`...",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                is VerificationStatus.ERROR -> {
                    val errBg = if (isDark) Color(0xFF381418) else Color(0xFFFEF2F2)
                    val errBorder = if (isDark) Color(0xFF7F1D1D) else Color(0xFFFCA5A5)
                    val errText = if (isDark) Color(0xFFFCA5A5) else Color(0xFF991B1B)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = errBg
                        ),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, errBorder)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(if (isDark) Color(0xFF7F1D1D) else Color(0xFFFEE2E2)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Warning,
                                    contentDescription = "Verification error details",
                                    tint = if (isDark) Color(0xFFFCA5A5) else Color(0xFFDC2626),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Text(
                                text = (verificationStatus as VerificationStatus.ERROR).message,
                                fontSize = 11.sp,
                                color = errText,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { viewModel.clearVerificationStatus() }) {
                                Icon(
                                    Icons.Default.Clear,
                                    contentDescription = "Dismiss status notification",
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
                else -> {}
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (!isConfigured) {
                Button(
                    onClick = { viewModel.verifyAndSaveConfiguration() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("verify_config_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = bKashPink
                    ),
                    shape = RoundedCornerShape(16.dp),
                    contentPadding = PaddingValues(vertical = 15.dp)
                ) {
                    Text(
                        "SET API & START SYNC",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            } else {
                Button(
                    onClick = { viewModel.deactivateConfiguration() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFDC2626)
                    ),
                    shape = RoundedCornerShape(16.dp),
                    contentPadding = PaddingValues(vertical = 15.dp)
                ) {
                    Text(
                        "DEACTIVATE MONITOR",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}
