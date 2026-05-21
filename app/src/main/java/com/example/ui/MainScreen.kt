package com.example.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.draw.scale
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.db.OrderEntity
import com.example.db.UserEntity
import com.example.ui.theme.*
import com.example.location.*
import java.text.SimpleDateFormat

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.asImageBitmap
import java.io.ByteArrayOutputStream
import java.util.*

enum class BookingSubScreen {
    HOME,
    FILL_ORDER,
    PAYMENT
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    mainViewModel: MainViewModel,
    userEmail: String,
    userNickname: String,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val selectedTab by mainViewModel.selectedTab.collectAsState()
    val orders by mainViewModel.ordersList.collectAsState()
    val trackingOrderId by mainViewModel.trackingOrderId.collectAsState()
    val showMapSelector by mainViewModel.showMapSelector.collectAsState()
    val mapTargetField by mainViewModel.mapTargetField.collectAsState()

    // Pass email context to vm
    LaunchedEffect(userEmail) {
        mainViewModel.setUserEmail(userEmail)
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[android.Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseGranted = permissions[android.Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        if (fineGranted || coarseGranted) {
            mainViewModel.autoAcquireHeaderLocation(context)
        }
    }

    LaunchedEffect(Unit) {
        val fineGranted = androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val coarseGranted = androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        if (fineGranted || coarseGranted) {
            mainViewModel.autoAcquireHeaderLocation(context)
        } else {
            locationPermissionLauncher.launch(
                arrayOf(
                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                    android.Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    // Interactive booking dynamic sub-screens
    var bookingSubScreen by remember { mutableStateOf(BookingSubScreen.HOME) }
    
    // Additional high-fidelity states matching design screenshots
    var selectedWeightRange by remember { mutableStateOf("< 5kg") }
    var itemEstimatedPrice by remember { mutableStateOf("0.00") }
    var paymentMethod by remember { mutableStateOf("WECHAT") } // "WECHAT", "ALIPAY", "BALANCE"
    var selectedTipBoost by remember { mutableStateOf(0) } // 0, 2, 5 (¥)

    // Prepopulate fields when choosing service to ensure flawless simulated ordering
    fun initiateBookingFlow(type: String) {
        mainViewModel.setErrandType(type)
        if (type == "BUY") {
            mainViewModel.updateItemName("2杯冰美式，少冰，中杯")
            mainViewModel.updateFromAddress("Starbucks (Central Plaza)")
            mainViewModel.updateToAddress("上海科技园 A座 1003室")
        } else {
            mainViewModel.updateItemName("数码配件及工作文件")
            mainViewModel.updateFromAddress("张江高科投递站点")
            mainViewModel.updateToAddress("上海科技园 A座 1003室")
        }
        selectedTipBoost = 0
        bookingSubScreen = BookingSubScreen.FILL_ORDER
    }

    Scaffold(
        bottomBar = {
            // Only display main bar if in HOME booking screen
            if (bookingSubScreen == BookingSubScreen.HOME) {
                NavigationBar(
                    modifier = Modifier.navigationBarsPadding(),
                    containerColor = Color.White,
                    tonalElevation = 8.dp
                ) {
                    NavigationBarItem(
                        selected = selectedTab == 0,
                        onClick = { mainViewModel.selectTab(0) },
                        icon = { Icon(Icons.Default.Home, contentDescription = "首页") },
                        label = { Text("首页", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color(0xFF222222),
                            selectedTextColor = Color(0xFF222210),
                            indicatorColor = Color(0xFFFFD100)
                        )
                    )

                    NavigationBarItem(
                        selected = selectedTab == 3,
                        onClick = { mainViewModel.selectTab(3) },
                        icon = { Icon(Icons.Default.MyLocation, contentDescription = "附近") },
                        label = { Text("附近", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color(0xFF222222),
                            selectedTextColor = Color(0xFF222210),
                            indicatorColor = Color(0xFFFFD100)
                        )
                    )

                    NavigationBarItem(
                        selected = selectedTab == 1,
                        onClick = { mainViewModel.selectTab(1) },
                        icon = { Icon(Icons.Default.ReceiptLong, contentDescription = "订单") },
                        label = { Text("订单", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color(0xFF222222),
                            selectedTextColor = Color(0xFF222210),
                            indicatorColor = Color(0xFFFFD100)
                        )
                    )

                    NavigationBarItem(
                        selected = selectedTab == 2,
                        onClick = { mainViewModel.selectTab(2) },
                        icon = { Icon(Icons.Default.Person, contentDescription = "我的") },
                        label = { Text("我的", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color(0xFF222222),
                            selectedTextColor = Color(0xFF222210),
                            indicatorColor = Color(0xFFFFD100)
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        val backgroundPadding: PaddingValues = if (bookingSubScreen == BookingSubScreen.HOME) {
            innerPadding
        } else {
            PaddingValues()
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(backgroundPadding)
                .background(Color(0xFFFFFCEF)) // Cozy cream yellow canvas matching mockups
        ) {
            Crossfade(targetState = Pair(selectedTab, bookingSubScreen), label = "mainFlowCrossfade") { (tab, sub) ->
                if (sub != BookingSubScreen.HOME && tab == 0) {
                    when (sub) {
                        BookingSubScreen.FILL_ORDER -> {
                            FillOrderScreen(
                                mainViewModel = mainViewModel,
                                selectedWeightRange = selectedWeightRange,
                                onWeightSelected = { selectedWeightRange = it },
                                itemEstimatedPrice = itemEstimatedPrice,
                                onPriceChanged = { itemEstimatedPrice = it },
                                onBack = { bookingSubScreen = BookingSubScreen.HOME },
                                onNext = { bookingSubScreen = BookingSubScreen.PAYMENT }
                            )
                        }
                        BookingSubScreen.PAYMENT -> {
                            PaymentOrderScreen(
                                mainViewModel = mainViewModel,
                                selectedTipBoost = selectedTipBoost,
                                onTipSelected = { speedTip ->
                                    selectedTipBoost = speedTip
                                    mainViewModel.updateTipFee(speedTip.toDouble())
                                },
                                paymentMethod = paymentMethod,
                                onPaymentMethodSelected = { paymentMethod = it },
                                onBack = { bookingSubScreen = BookingSubScreen.FILL_ORDER },
                                onPay = {
                                    mainViewModel.placeErrandOrder { success ->
                                        if (success) {
                                            android.widget.Toast.makeText(context, "积分代扣成功，配送开启！", android.widget.Toast.LENGTH_SHORT).show()
                                            bookingSubScreen = BookingSubScreen.HOME
                                            mainViewModel.selectTab(2) // Move directly to tracking order list in TAB 2!
                                        } else {
                                            val err = mainViewModel.orderPlacementError.value ?: "积分订单支付失败"
                                            android.widget.Toast.makeText(context, err, android.widget.Toast.LENGTH_LONG).show()
                                        }
                                    }
                                }
                            )
                        }
                        else -> {}
                    }
                } else {
                    when (tab) {
                        0 -> ErrandHomeTab(
                            mainViewModel = mainViewModel,
                            onNavigateToService = { type -> initiateBookingFlow(type) },
                            onShowAllOrders = { mainViewModel.selectTab(1) }
                        )
                        1 -> OrdersTabScreen(mainViewModel)
                        2 -> ProfileTabScreen(
                            mainViewModel = mainViewModel,
                            nickname = userNickname,
                            email = userEmail,
                            onLogout = onLogout
                        )
                        3 -> NearbyTabScreen(mainViewModel)
                    }
                }
            }
        }

        // Active Order detail tracking bottom sheet overlay
        trackingOrderId?.let { orderId ->
            OrderTrackingDetailSheet(
                mainViewModel = mainViewModel,
                orderId = orderId,
                onDismiss = { mainViewModel.selectTrackingOrder(null) }
            )
        }

        // Built-In Intelligent Free Map Selector Dialog Overlay
        if (showMapSelector) {
            IntelligentMapSelectorDialog(
                mainViewModel = mainViewModel,
                targetField = mapTargetField,
                onDismiss = { mainViewModel.closeMapSelector() }
            )
        }

        // Real-Time Communication Interactive Overlay
        val isChatOpen by mainViewModel.isChatOpen.collectAsState()
        if (isChatOpen && trackingOrderId != null) {
            OrderRiderChatOverlay(
                mainViewModel = mainViewModel,
                orderId = trackingOrderId!!,
                onDismiss = { mainViewModel.closeChat() }
            )
        }

        // Customer service contacts dialog overlay
        val showCustomerServiceDialog by mainViewModel.showCustomerServiceDialog.collectAsState()
        if (showCustomerServiceDialog) {
            CustomerServiceContactDialog(
                onDismiss = { mainViewModel.closeCustomerServiceDialog() }
            )
        }

        // Points Recharge dialog overlay
        val showRechargeDialog by mainViewModel.showRechargeDialog.collectAsState()
        if (showRechargeDialog) {
            RechargeDialog(
                mainViewModel = mainViewModel,
                onDismiss = { mainViewModel.closeRechargeDialog() }
            )
        }

        // Custom Photo Album user avatar picker dialog overlay
        val showAvatarSelector by mainViewModel.showAvatarSelector.collectAsState()
        if (showAvatarSelector) {
            SimulatedAlbumAvatarPickerDialog(
                mainViewModel = mainViewModel,
                onDismiss = { mainViewModel.closeAvatarSelector() }
            )
        }
    }
}


// ==================== SCREEN 1: 跑腿首页 (ErrandHomeTab) ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ErrandHomeTab(
    mainViewModel: MainViewModel,
    onNavigateToService: (String) -> Unit,
    onShowAllOrders: () -> Unit
) {
    val orders by mainViewModel.ordersList.collectAsState()
    val context = LocalContext.current
    val headerLocName by mainViewModel.headerLocationName.collectAsState()
    var searchKeyword by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 20.dp)
    ) {
        // Top Location Header Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
                .clickable { mainViewModel.autoAcquireHeaderLocation(context) },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.LocationOn,
                contentDescription = "Location Pin",
                tint = Color(0xFFC0A000),
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = headerLocName,
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFF222222)
            )
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = "Pick location",
                tint = Color(0xFF333333),
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.weight(1.0f))
            
            // Bell Button
            IconButton(
                onClick = {},
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Notifications,
                    contentDescription = "Notifications",
                    tint = Color(0xFF222222)
                )
            }
            // QR Scan Button
            IconButton(
                onClick = {},
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.QrCodeScanner,
                    contentDescription = "Scan",
                    tint = Color(0xFF222222)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Rounded Search Bar with "搜索" button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color.White)
                .border(1.dp, Color(0xFFEBE6D0), RoundedCornerShape(24.dp))
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(modifier = Modifier.width(12.dp))
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Search",
                tint = Color(0xFF999999),
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            val searchColors = SearchTextFieldDefaults()
            OutlinedTextField(
                value = searchKeyword,
                onValueChange = { searchKeyword = it },
                placeholder = { Text("寻找服务或商品...", color = Color(0xFFB0AC95), fontSize = 13.sp) },
                colors = searchColors,
                singleLine = true,
                modifier = Modifier
                    .weight(1.0f)
                    .fillMaxHeight(),
                textStyle = LocalTextStyle.current.copy(fontSize = 13.sp, color = Color(0xFF222222))
            )
            // Beautiful golden yellow search button
            Button(
                onClick = { focusManager.clearFocus() },
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFFD100),
                    contentColor = Color(0xFF222222)
                ),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                modifier = Modifier.height(40.dp)
            ) {
                Text(
                    text = "搜索",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 2x2 Services Categories Grid
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            // Box 1: 帮我送
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(130.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.White)
                    .clickable { onNavigateToService("DELIVER") }
                    .padding(14.dp)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .background(Color(0xFFFFD100), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocalShipping,
                            contentDescription = "Deliver",
                            tint = Color(0xFF222222),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = "帮我送",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF222222)
                    )
                    Text(
                        text = "极速送达，安全放心",
                        fontSize = 10.sp,
                        color = Color(0xFF918D72),
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }

            // Box 2: 帮我买 (High-fidelity highlight click leads to workflow)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(130.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.White)
                    .border(1.5.dp, Color(0xFFFFD100), RoundedCornerShape(18.dp)) // Highlighted border
                    .clickable { onNavigateToService("BUY") }
                    .padding(14.dp)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .border(1.5.dp, Color(0xFFFFD100), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ShoppingBasket,
                            contentDescription = "Buy",
                            tint = Color(0xFFC09E00),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = "帮我买",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF222222)
                    )
                    Text(
                        text = "万能陪跑，代购到家",
                        fontSize = 10.sp,
                        color = Color(0xFF918D72),
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            // Box 3: 代办
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(130.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.White)
                    .clickable { onNavigateToService("PICK_UP") }
                    .padding(14.dp)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .border(1.dp, Color(0xFFDDDAB5), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Assignment,
                            contentDescription = "Errands",
                            tint = Color(0xFF555555),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = "代办",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF222222)
                    )
                    Text(
                        text = "省心生活，帮您处理",
                        fontSize = 10.sp,
                        color = Color(0xFF918D72),
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }

            // Box 4: 全城送
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(130.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.White)
                    .clickable { onNavigateToService("DELIVER") }
                    .padding(14.dp)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .border(1.dp, Color(0xFFDDDAB5), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Map,
                            contentDescription = "City Wide",
                            tint = Color(0xFF555555),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = "全城送",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF222222)
                    )
                    Text(
                        text = "跨城快递，服务全国",
                        fontSize = 10.sp,
                        color = Color(0xFF918D72),
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // Recent Orders Header Row with "查看全部 >"
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "最近订单",
                fontSize = 16.sp,
                fontWeight = FontWeight.Black,
                color = Color(0xFF222222)
            )
            Text(
                text = "查看全部 >",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF8B8015),
                modifier = Modifier.clickable { onShowAllOrders() }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Bottom active or placeholder card matching design mockup
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1.0f),
            contentAlignment = Alignment.TopCenter
        ) {
            if (orders.isNotEmpty()) {
                val latestOrder = orders.first()
                InteractiveOrderViewCard(order = latestOrder, onClick = { mainViewModel.selectTrackingOrder(latestOrder.id) })
            } else {
                // Interactive Simulated Mock card
                InteractiveOrderViewCard(
                    order = OrderEntity(
                        id = "#v-88291",
                        userEmail = "user@demo.com",
                        type = "BUY",
                        itemName = "精品美式咖啡及热焦糖玛奇朵",
                        fromAddress = "曹家·科技园店",
                        toAddress = "上海科技园 A座 1003室",
                        tip = 5.0,
                        distance = 4.2,
                        status = "OUT_FOR_DELIVERY",
                        runnerName = "王伟",
                        runnerPhone = "13500008888"
                    ),
                    onClick = {}
                )
            }

            // Beautiful FAB in the lower right corner of the sheet container
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 16.dp),
                contentAlignment = Alignment.BottomEnd
            ) {
                FloatingActionButton(
                    onClick = { onNavigateToService("BUY") },
                    containerColor = Color(0xFFFFD100),
                    contentColor = Color(0xFF222222),
                    shape = CircleShape,
                    modifier = Modifier
                        .size(56.dp)
                        .shadow(4.dp, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "New Order",
                        modifier = Modifier.size(28.dp),
                        tint = Color(0xFF222222)
                    )
                }
            }
        }
    }
}

@Composable
fun InteractiveOrderViewCard(order: OrderEntity, onClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Circular delivery box icon
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color(0xFFFFF7C0), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CardGiftcard,
                        contentDescription = "Order Item Icon",
                        tint = Color(0xFF918200),
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "订单 ${order.id}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF222222)
                    )
                    Text(
                        text = "来自：${order.fromAddress}",
                        fontSize = 11.sp,
                        color = Color(0xFF888888)
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                
                // Status delivery badge
                Surface(
                    color = Color(0xFFE2F9E9),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "配送中",
                        color = Color(0xFF2E7D32),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp),
                color = Color(0xFFF2EFE0)
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Courier avatar placeholder
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(Color(0xFFFFD100), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.DirectionsRun,
                        contentDescription = "Runner",
                        tint = Color(0xFF222222),
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "骑手：${if (order.runnerName.isNotBlank()) order.runnerName else "王伟"}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF333333)
                )
                Spacer(modifier = Modifier.weight(1f))
                
                // Clock element countdown matching mockup
                Icon(
                    imageVector = Icons.Default.AccessTime,
                    contentDescription = "Clock",
                    tint = Color(0xFFC09E00),
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "预计 8分钟送达",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF8B8015)
                )
            }
        }
    }
}


// ==================== SCREEN 3: 填写订单 (FillOrderScreen) ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FillOrderScreen(
    mainViewModel: MainViewModel,
    selectedWeightRange: String,
    onWeightSelected: (String) -> Unit,
    itemEstimatedPrice: String,
    onPriceChanged: (String) -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit
) {
    val itemName by mainViewModel.itemName.collectAsState()
    val fromAddress by mainViewModel.fromAddress.collectAsState()
    val toAddress by mainViewModel.toAddress.collectAsState()
    val totalCost = mainViewModel.calculateErrandCost()
    
    val context = androidx.compose.ui.platform.LocalContext.current
    val focusManager = LocalFocusManager.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 20.dp)
    ) {
        // App bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = Color(0xFF222222)
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "选择地址",
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFF222222)
            )
            Spacer(modifier = Modifier.weight(1f))
            IconButton(
                onClick = {},
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Notifications,
                    contentDescription = "Notifications",
                    tint = Color(0xFF222222)
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            // Active highlighted banner
            Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFD100)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.ShoppingBasket,
                    contentDescription = "Basket",
                    tint = Color(0xFF222222),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "当前服务：帮我买 (Buy for Me)",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF222222)
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Addresses selector block card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // From Address (Merchant buy location)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(Color(0xFF2E7D32), CircleShape)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1.0f)) {
                        Text(
                            text = "购买地址",
                            fontSize = 11.sp,
                            color = Color(0xFF999999)
                        )
                        OutlinedTextField(
                            value = fromAddress,
                            onValueChange = { mainViewModel.updateFromAddress(it) },
                            textStyle = LocalTextStyle.current.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF212121)),
                            colors = CleanTextFieldColors(),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    IconButton(
                        onClick = { mainViewModel.openMapSelector("FROM") },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Map,
                            contentDescription = "Map Select",
                            tint = Color(0xFF7CB342),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                // Downward Arrow separator
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    IconButton(onClick = {}, modifier = Modifier.size(32.dp).padding(start = 14.dp)) {
                        Icon(
                            imageVector = Icons.Default.ArrowDownward,
                            contentDescription = "Down Direction",
                            tint = Color(0xFFCCCCCC),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // To Address (Customer deliver target)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(Color(0xFFD32F2F), CircleShape)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1.0f)) {
                        Text(
                            text = "送达地址",
                            fontSize = 11.sp,
                            color = Color(0xFF999999)
                        )
                        OutlinedTextField(
                            value = toAddress,
                            onValueChange = { mainViewModel.updateToAddress(it) },
                            placeholder = { Text("请输入收货详细门牌号", color = Color(0xFFB5B5B5)) },
                            textStyle = LocalTextStyle.current.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF212121)),
                            colors = CleanTextFieldColors(),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    IconButton(
                        onClick = { mainViewModel.openMapSelector("TO") },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MyLocation,
                            contentDescription = "GPS target",
                            tint = Color(0xFFC0A000),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        // Real high-precision GPS automated positioning triggers
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    mainViewModel.autoAcquireDetailedLocation(context, "FROM")
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFE8F5E9),
                    contentColor = Color(0xFF2E7D32)
                ),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                modifier = Modifier.weight(1f).height(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.GpsFixed,
                    contentDescription = "Locate From",
                    modifier = Modifier.size(12.dp),
                    tint = Color(0xFF2E7D32)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("购买地 自动真实定位", fontSize = 10.sp, fontWeight = FontWeight.Black)
            }

            Button(
                onClick = {
                    mainViewModel.autoAcquireDetailedLocation(context, "TO")
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFFF3E0),
                    contentColor = Color(0xFFE65100)
                ),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                modifier = Modifier.weight(1f).height(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.GpsFixed,
                    contentDescription = "Locate To",
                    modifier = Modifier.size(12.dp),
                    tint = Color(0xFFE65100)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("送达地 自动真实定位", fontSize = 10.sp, fontWeight = FontWeight.Black)
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Item details form Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "物品详情",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF222222),
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Text(
                    text = "想买什么？",
                    fontSize = 12.sp,
                    color = Color(0xFF888888),
                    modifier = Modifier.padding(bottom = 6.dp)
                )

                // Large customizable TextArea placeholder for item list details
                OutlinedTextField(
                    value = itemName,
                    onValueChange = { mainViewModel.updateItemName(it) },
                    placeholder = { Text("例如：2杯冰美式，少冰，中杯，多加糖...", color = Color(0xFFB5B5B5), fontSize = 12.sp) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color(0xFF222222),
                        unfocusedTextColor = Color(0xFF222222),
                        focusedBorderColor = Color(0xFFFFD100),
                        unfocusedBorderColor = Color(0xFFF2EFE0),
                        focusedContainerColor = Color(0xFFFAF9F2),
                        unfocusedContainerColor = Color(0xFFFAF9F2)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp)
                        .testTag("itemNameInput"),
                    shape = RoundedCornerShape(10.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "预估重量",
                    fontSize = 12.sp,
                    color = Color(0xFF888888),
                    modifier = Modifier.padding(bottom = 6.dp)
                )

                // Horizontal selection weight chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val weights = listOf("< 5kg", "5-10kg", "> 10kg")
                    weights.forEach { wt ->
                        val isSel = wt == selectedWeightRange
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSel) Color(0xFFFFD100) else Color(0xFFF8F8F8))
                                .border(
                                    1.dp,
                                    if (isSel) Color(0xFFFFD100) else Color(0xFFE5E2D0),
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable { onWeightSelected(wt) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = wt,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSel) Color(0xFF222222) else Color(0xFF666666)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Estimated item worth input bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "商品价格",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF222222)
                        )
                        Text(
                            text = "预估商品费用",
                            fontSize = 11.sp,
                            color = Color(0xFF888888)
                        )
                    }

                    // Value box
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End
                    ) {
                        OutlinedTextField(
                            value = itemEstimatedPrice,
                            onValueChange = { onPriceChanged(it) },
                            textStyle = LocalTextStyle.current.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF222222), textAlign = TextAlign.End),
                            colors = CleanTextFieldColors(),
                            singleLine = true,
                            modifier = Modifier.width(80.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "积分",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF222222)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Delivery speed selector
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "配送选项",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF222222),
                    modifier = Modifier.padding(bottom = 10.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Flash delivery option
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .border(1.5.dp, Color(0xFFFFD100), RoundedCornerShape(10.dp))
                            .background(Color(0xFFFFFCEF), RoundedCornerShape(10.dp))
                            .padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.ElectricBolt,
                                contentDescription = "Flash Bolt",
                                tint = Color(0xFF8B8015),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "立即配送",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF333333)
                            )
                        }
                        Text(
                            text = "~30 分钟",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF8B8015),
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }

                    // Scheduled delay option
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .border(1.dp, Color(0xFFE5E2D0), RoundedCornerShape(10.dp))
                            .background(Color.White, RoundedCornerShape(10.dp))
                            .padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.AccessTime,
                                contentDescription = "Clock Scheduling",
                                tint = Color(0xFF777777),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "预约时间",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF666666)
                            )
                        }
                        Text(
                            text = "选择时间段",
                            fontSize = 11.sp,
                            color = Color(0xFF999999),
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        }

        } // End of scrollable Column

        // Large Bottom Buy action bar
        HorizontalDivider(color = Color(0xFFEBE6D0))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "待支付配送费",
                    fontSize = 11.sp,
                    color = Color(0xFF888888)
                )
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = String.format("%.2f", totalCost),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                        color = Color(0xFF222222)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "积分",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF222222)
                    )
                }
            }

            // Beautiful Confirm button going to Screen 2 Checkout state
            Button(
                onClick = {
                    if (itemName.trim().isBlank()) {
                        mainViewModel.updateItemName("2杯冰美式，少冰，中杯")
                    }
                    onNext()
                },
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFFD100),
                    contentColor = Color(0xFF222222)
                ),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
                modifier = Modifier
                    .height(48.dp)
                    .widthIn(min = 150.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "确认下单",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "→",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}


// ==================== SCREEN 2: 支付订单 (PaymentOrderScreen) ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentOrderScreen(
    mainViewModel: MainViewModel,
    selectedTipBoost: Int,
    onTipSelected: (Int) -> Unit,
    paymentMethod: String,
    onPaymentMethodSelected: (String) -> Unit,
    onBack: () -> Unit,
    onPay: () -> Unit
) {
    val currentUserEntity by mainViewModel.currentUserEntity.collectAsState()
    val userPoints = currentUserEntity?.points ?: 100.0
    val distance by mainViewModel.distance.collectAsState()
    val fromAddress by mainViewModel.fromAddress.collectAsState()
    val totalErrandCost = mainViewModel.calculateErrandCost()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 20.dp)
    ) {
        // App header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = Color(0xFF222222)
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "支付订单",
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFF222222)
            )
            Spacer(modifier = Modifier.weight(1f))
            IconButton(
                onClick = {},
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.HelpOutline,
                    contentDescription = "Help",
                    tint = Color(0xFF222222)
                )
            }
        }

        // General description card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "订单类型",
                            fontSize = 11.sp,
                            color = Color(0xFF999999)
                        )
                        Text(
                            text = "生鲜配送与代购服务",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF222222)
                        )
                    }

                    // Yellow tag
                    Surface(
                        color = Color(0xFFFFF6D0),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "距离 ${String.format("%.1f", distance)} km",
                            color = Color(0xFF8B8015),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "取货地",
                            fontSize = 11.sp,
                            color = Color(0xFF999999)
                        )
                        Text(
                            text = fromAddress,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF444444)
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "订单编号",
                            fontSize = 11.sp,
                            color = Color(0xFF999999)
                        )
                        Text(
                            text = "#ER-9821",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF444444)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Price details card "支付明细"
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "支付明细",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF222222),
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                // Cost Items
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("配送费", fontSize = 12.sp, color = Color(0xFF666666))
                    Text("12.50 积分", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF212121))
                }

                // Interactive Tips Boost Buttons (+2, +5 points)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("呼叫加急小费", fontSize = 12.sp, color = Color(0xFF666666))
                        Spacer(modifier = Modifier.width(2.dp))
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Tips info",
                            tint = Color(0xFFCCCCCC),
                            modifier = Modifier.size(12.dp)
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val active2 = selectedTipBoost == 2
                        val active5 = selectedTipBoost == 5
                        
                        // Chip +2
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (active2) Color(0xFFFFD100) else Color(0xFFFAF9F2))
                                .border(1.dp, if (active2) Color(0xFFFFD100) else Color(0xFFE0DCBA), RoundedCornerShape(6.dp))
                                .clickable { onTipSelected(if (active2) 0 else 2) }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("+ 2 积分", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }

                        // Chip +5
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (active5) Color(0xFFFFD100) else Color(0xFFFAF9F2))
                                .border(1.dp, if (active5) Color(0xFFFFD100) else Color(0xFFE0DCBA), RoundedCornerShape(6.dp))
                                .clickable { onTipSelected(if (active5) 0 else 5) }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("+ 5 积分", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Coupon row
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("优惠券抵扣", fontSize = 12.sp, color = Color(0xFF666666))
                    Text("- 3.00 积分", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = Color(0xFFF2EFE0))

                // Total display
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("合计", fontSize = 14.sp, fontWeight = FontWeight.Black)
                    Text(
                        text = "${String.format("%.2f", totalErrandCost - 3.0)} 积分",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        color = Color(0xFF222222)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Select Payment Route Card "选择支付方式" - Points payment is now mandatory
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "选择支付方式",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF222222)
                    )
                    
                    // Quick recharge action button if balance is low
                    Button(
                        onClick = { mainViewModel.openRechargeDialog() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFFFDE7),
                            contentColor = Color(0xFFE65100)
                        ),
                        border = BorderStroke(1.dp, Color(0xFFFFD54F)),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = "recharge icon", modifier = Modifier.size(10.dp))
                        Spacer(modifier = Modifier.width(2.dp))
                        Text("充值积分", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Points payment Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .background(Color(0xFFFFF9C4), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountBalanceWallet,
                            contentDescription = "Wallet Balance",
                            tint = Color(0xFFF57F17),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "积分支付 (扣减可用积分)",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF222222)
                        )
                        Text(
                            text = "账户余额 ${String.format("%.2f", userPoints)} 积分",
                            fontSize = 11.sp,
                            color = if (userPoints < (totalErrandCost - 3.0)) Color.Red else Color(0xFF2E7D32),
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    RadioButton(
                        selected = true,
                        onClick = { },
                        colors = RadioButtonDefaults.colors(selectedColor = Color(0xFFFFD100))
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Shield / Security note at the bottom
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.VerifiedUser,
                contentDescription = "Safe Lock Icon",
                tint = Color(0xFF7CB342),
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "银行级支付安全保障",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF818671)
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        // Bottom instant Checkout bar
        HorizontalDivider(color = Color(0xFFEBE6D0))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "准备好开启配送了吗？",
                    fontSize = 11.sp,
                    color = Color(0xFF918D72)
                )
            }

            Button(
                onClick = onPay,
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFFD100),
                    contentColor = Color(0xFF222222)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text(
                    text = "确认并扣减 ${String.format("%.2f", totalErrandCost - 3.0)} 积分支付",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }
    }
}


// ==================== TAB 2: 我的订单 (OrdersTabScreen) ====================

@Composable
fun OrdersTabScreen(mainViewModel: MainViewModel) {
    val orders by mainViewModel.ordersList.collectAsState()
    val isRefreshing by mainViewModel.isRefreshingOrders.collectAsState()
    val isLoadingMore by mainViewModel.isLoadingMoreOrders.collectAsState()

    var dragOffset by remember { mutableStateOf(0f) }
    val animatedOffset by animateFloatAsState(targetValue = dragOffset)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        if (dragOffset > 200f) {
                            mainViewModel.refreshOrders()
                        }
                        dragOffset = 0f
                    },
                    onDragCancel = {
                        dragOffset = 0f
                    },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        dragOffset = (dragOffset + dragAmount).coerceIn(0f, 300f)
                    }
                )
            }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "包裹配送订单",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF222222),
                    modifier = Modifier.padding(top = 8.dp)
                )
                Text(
                    text = "智能跑腿订单追踪・全天候物流管家",
                    fontSize = 11.sp,
                    color = Color(0xFF88846C),
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }
            
            // Quick refresh pill button
            IconButton(
                onClick = { mainViewModel.refreshOrders() },
                modifier = Modifier
                    .size(36.dp)
                    .background(Color(0xFFFFF7C2), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Sync Data",
                    tint = Color(0xFF8B7300),
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // Pull Down Elastic Active Status Indicator Header
        AnimatedVisibility(
            visible = dragOffset > 30f || isRefreshing,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(((animatedOffset / 3.5f).coerceIn(40f, 80f)).dp)
                    .background(Color(0xFFFFFAED), RoundedCornerShape(12.dp))
                    .border(1.dp, Color(0xFFFFF0B3), RoundedCornerShape(12.dp))
                    .padding(8.dp)
                    .padding(bottom = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isRefreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Color(0xFFFF9100),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "正在同步云端 cdb 数据库状态...",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF827717)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.ArrowDownward,
                            contentDescription = "Pull",
                            tint = Color(0xFFFFB300),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (dragOffset > 180f) "极速松开，立即触发刷新" else "继续下拉：拉伸刷新订单状态",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF5D4037)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        if (orders.isEmpty()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.ReceiptLong,
                    contentDescription = "Empty order icon",
                    modifier = Modifier.size(64.dp),
                    tint = Color(0xFFDDD9C0)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "暂无活跃订单",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF888888)
                )
                Text(
                    text = "下拉此区域或返回首页发起代购任务即可跟踪！",
                    fontSize = 11.sp,
                    color = Color(0xFFB0AA8C),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp).padding(top = 4.dp)
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                items(orders, key = { it.id }) { order ->
                    OrderHistoryListItem(
                        order = order,
                        onClick = { mainViewModel.selectTrackingOrder(order.id) }
                    )
                }

                // Footprint pull-up-to-load visual card
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (isLoadingMore) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = Color(0xFFFFD100),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "配货员正在努力连接数据库...",
                                    fontSize = 11.sp,
                                    color = Color(0xFF888888)
                                )
                            }
                        } else {
                            Button(
                                onClick = { mainViewModel.loadMoreOrders() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.White,
                                    contentColor = Color(0xFF555555)
                                ),
                                border = BorderStroke(1.dp, Color(0xFFECE7D0)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ArrowUpward,
                                    contentDescription = "Load",
                                    modifier = Modifier.size(14.dp),
                                    tint = Color(0xFF777777)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "拖动或点击加载历史备份记录",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun OrderHistoryListItem(order: OrderEntity, onClick: () -> Unit) {
    val statusColors = when (order.status) {
        "COMPLETED" -> Pair("妥投已送达", Color(0xFF2E7D32))
        "SUBMITTED" -> Pair("等待接单中", Color(0xFFE65100))
        else -> Pair("骑手配送中", Color(0xFF1976D2))
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("order_item_${order.id}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (order.type == "BUY") Icons.Default.ShoppingBag else Icons.Default.DirectionsRun,
                        contentDescription = "Type",
                        tint = Color(0xFF918200),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (order.type == "BUY") "同城代购" else "速达送物",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF333333)
                    )
                }

                Surface(
                    color = statusColors.second.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = statusColors.first,
                        color = statusColors.second,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = order.itemName,
                fontSize = 14.sp,
                fontWeight = FontWeight.Black,
                color = Color(0xFF222222),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (order.notes.isNotBlank()) {
                Text(
                    text = "备注: ${order.notes}",
                    fontSize = 11.sp,
                    color = Color(0xFF888888),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 10.dp),
                color = Color(0xFFF7F5EA)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = "Target Loc",
                        modifier = Modifier.size(12.dp),
                        tint = Color(0xFF999999)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "送至：${order.toAddress}",
                        fontSize = 11.sp,
                        color = Color(0xFF666666),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 220.dp)
                    )
                }

                Text(
                    text = "${String.format("%.2f", order.distance * 2.0 + 12.0 + order.tip - 3.0)} 积分",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF222222)
                )
            }
        }
    }
}


// ==================== TAB 3: 我的用户 (ProfileTabScreen) ====================

@Composable
fun ProfileTabScreen(
    mainViewModel: MainViewModel,
    nickname: String,
    email: String,
    onLogout: () -> Unit
) {
    val avatarIndex by mainViewModel.userAvatarIndex.collectAsState()
    val customAvatarBase64 by mainViewModel.customAvatarBase64.collectAsState()
    val currentUserEntity by mainViewModel.currentUserEntity.collectAsState()
    val points = currentUserEntity?.points ?: 100.0

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 6.dp)
    ) {
        Text(
            text = "自我中心",
            fontSize = 18.sp,
            fontWeight = FontWeight.Black,
            color = Color(0xFF222222),
            modifier = Modifier.padding(bottom = 16.dp, top = 8.dp)
        )

        // Customer Card Section
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Clickable Avatar with edit badge
                    Box(
                        modifier = Modifier.size(62.dp),
                        contentAlignment = Alignment.BottomEnd
                    ) {
                        UserAvatarView(
                            avatarIndex = avatarIndex,
                            size = 58.dp,
                            customAvatarBase64 = customAvatarBase64,
                            onClick = { mainViewModel.openAvatarSelector() }
                        )
                        // Floating 📷 Edit Symbol Overlay
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .background(Color(0xFF222222), CircleShape)
                                .border(1.dp, Color.White, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CameraAlt,
                                contentDescription = "Edit Avatar",
                                tint = Color(0xFFFFD100),
                                modifier = Modifier.size(10.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = nickname,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF222222)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                color = Color(0xFFFFF6D0),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = "尊贵星达客",
                                    color = Color(0xFF8B8015),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = email,
                            fontSize = 11.sp,
                            color = Color(0xFF888888),
                            modifier = Modifier.padding(top = 2.dp)
                        )
                        Text(
                            text = "点击头像可从相册更换新图片",
                            fontSize = 9.sp,
                            color = Color(0xFFFFA000),
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 3.dp)
                        )
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 14.dp),
                    color = Color(0xFFF7F5EA)
                )

                // Simulated statistics
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("3", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text("进行中订单", fontSize = 11.sp, color = Color(0xFF888888))
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("99.8%", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text("赞美配送率", fontSize = 11.sp, color = Color(0xFF888888))
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${String.format("%.2f", points)}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE65100))
                        Text("可用积分", fontSize = 11.sp, color = Color(0xFF888888))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Prominent Points Recharge Button Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFDE7)), // Beautiful light warm yellow
            border = BorderStroke(1.dp, Color(0xFFFFD54F)),
            modifier = Modifier
                .fillMaxWidth()
                .clickable { mainViewModel.openRechargeDialog() }
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.AddCard,
                    contentDescription = "积分充值",
                    tint = Color(0xFFF57F17),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "积分充值与打款通道",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF5D4037)
                    )
                    Text(
                        text = "1元 = 1积分 · 点此联系微信号 [qq278159132] 或在沙盒秒充",
                        fontSize = 10.sp,
                        color = Color(0xFF795548)
                    )
                }
                Icon(
                    imageVector = Icons.Default.KeyboardArrowRight,
                    contentDescription = "Next",
                    tint = Color(0xFF8D6E63)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // System information cells
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                ProfileOptionRow(
                    icon = Icons.Default.Shield, 
                    label = "用户安全等级与隐私条款"
                )
                ProfileOptionRow(
                    icon = Icons.Default.Chat, 
                    label = "全天候在线客服中心",
                    onClick = { mainViewModel.openCustomerServiceDialog() }
                )
                ProfileOptionRow(
                    icon = Icons.Default.AccountBalanceWallet, 
                    label = "我的代金券与历史发票"
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Logout Action
        Button(
            onClick = onLogout,
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFFFEBEE),
                contentColor = Color(0xFFC62828)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            Text("退出登录", fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun ProfileOptionRow(icon: ImageVector, label: String, onClick: () -> Unit = {}) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = label, tint = Color(0xFF706B4D), modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(10.dp))
        Text(text = label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF333333))
        Spacer(modifier = Modifier.weight(1f))
        Icon(Icons.Default.KeyboardArrowRight, contentDescription = "Goto", tint = Color(0xFFCCCCCC))
    }
}


// ==================== SUBSIDIARY POPUP DETAIL: OrderTrackingDetailSheet ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderTrackingDetailSheet(
    mainViewModel: MainViewModel,
    orderId: String,
    onDismiss: () -> Unit
) {
    val orderOpt by mainViewModel.trackingOrder.collectAsState()
    
    // Auto query sheet level details
    LaunchedEffect(orderId) {
        mainViewModel.selectTrackingOrder(orderId)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        tonalElevation = 8.dp
    ) {
        orderOpt?.let { order ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 42.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "包裹追踪详情",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF222222)
                        )
                        Text(
                            text = "订单 ID: ${order.id}",
                            fontSize = 12.sp,
                            color = Color(0xFF888888)
                        )
                    }

                    Surface(
                        color = Color(0xFFE8F5E9),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "配送中",
                            color = Color(0xFF2E7D32),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 14.dp), color = Color(0xFFF7F5EA))

                // Detail data
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CardGiftcard, contentDescription = "Item Box", tint = Color(0xFFFFD100), modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "配送物品：${order.itemName}", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }

                // Exclusive Rider Profile & Online Action Row
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                        .testTag("chat_rider_profile_card"),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFDF5)), // matches theme
                    border = BorderStroke(1.dp, Color(0xFFFFEB9C))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(Color(0xFFFFD100), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DirectionsBike,
                                    contentDescription = "Rider Logo",
                                    tint = Color(0xFF222222),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "专属骑手：王伟",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF222222)
                                )
                                Text(
                                    text = "星级配送员 ★ 4.9 (同城极速直派)",
                                    fontSize = 10.sp,
                                    color = Color(0xFF777777)
                                )
                            }
                        }

                        Button(
                            onClick = {
                                mainViewModel.openChat()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF222222),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(30.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                            modifier = Modifier.height(32.dp).testTag("dialog_contact_rider_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Forum,
                                contentDescription = "Chat Logo",
                                modifier = Modifier.size(12.dp),
                                tint = Color(0xFFFFD100)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "在线沟通",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Vertical timeline representing node dispatch steps
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    TrackingNodeRow(
                        time = "10:02",
                        title = "骑手已接单 (接单骑手：王伟)",
                        desc = "正在前往 ${order.fromAddress} 包裹取货点",
                        isDone = true
                    )
                    TrackingNodeRow(
                        time = "10:05",
                        title = "骑手已到达目的地取纸盒打包",
                        desc = "正在进行商品温度密封封装与分量核查",
                        isDone = true
                    )
                    TrackingNodeRow(
                        time = "10:15",
                        title = "包裹正在极速派送中",
                        desc = "由于小费抢单加成，开启优先派件绿色通道",
                        isDone = true
                    )
                    TrackingNodeRow(
                        time = "--:--",
                        title = "等待妥投送达",
                        desc = "收尾递送至上海科技园详细楼层",
                        isDone = false
                    )
                }
            }
        } ?: run {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color(0xFFFFD100))
            }
        }
    }
}

@Composable
fun TrackingNodeRow(
    time: String,
    title: String,
    desc: String,
    isDone: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = time,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = if (isDone) Color(0xFF222222) else Color(0xFF999999),
            modifier = Modifier.width(42.dp)
        )

        Spacer(modifier = Modifier.width(10.dp))

        // Line representation anchor
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(if (isDone) Color(0xFFFFD100) else Color(0xFFEBE6D0))
                    .border(2.dp, if (isDone) Color(0xFFFF9F00) else Color.Transparent, CircleShape)
            )
            // dotted connector
            Box(
                modifier = Modifier
                    .width(1.5.dp)
                    .height(30.dp)
                    .background(Color(0xFFEBE6D0))
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
                color = if (isDone) Color(0xFF222222) else Color(0xFF777777)
            )
            Text(
                text = desc,
                fontSize = 10.sp,
                color = if (isDone) Color(0xFF666666) else Color(0xFF999999),
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}


// ==================== COZY CLEAN UTILS ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CleanTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Color.Transparent,
    unfocusedBorderColor = Color.Transparent,
    focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent,
    focusedTextColor = Color(0xFF212121),
    unfocusedTextColor = Color(0xFF212121)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchTextFieldDefaults() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Color.Transparent,
    unfocusedBorderColor = Color.Transparent,
    focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent,
    focusedTextColor = Color(0xFF222222),
    unfocusedTextColor = Color(0xFF222222)
)

// ==================== DESIGNER INTEGRATED FREE MAP COMPONENT ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IntelligentMapSelectorDialog(
    mainViewModel: MainViewModel,
    targetField: String,
    onDismiss: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var selectedPointName by remember { mutableStateOf("") }
    
    // Map view parameters for interactive pan/offset simulation
    var mapScrollX by remember { mutableStateOf(0f) }
    var mapScrollY by remember { mutableStateOf(0f) }
    
    // GPS targeting pulse animation
    val infiniteTransition = rememberInfiniteTransition(label = "gpsPulse")
    val pulseRadius by infiniteTransition.animateFloat(
        initialValue = 10f,
        targetValue = 60f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "radius"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "alpha"
    )

    // Pre-pinned hotspots in Shanghai
    val allHotspots = listOf(
        "上海浦东张江高科技园 A区 5号楼",
        "静安里·静安嘉里中心二期写字楼",
        "徐家汇美罗城百脑汇商业广场 B1层",
        "上海虹桥枢纽港出发航站厅一等座通道",
        "徐汇滨江龙腾大道滨水艺术营地",
        "曹家渡老街弄堂本帮精品配售大厅",
        "同济大学杨浦校区正校门北侧 102室",
        "浦东陆家嘴环路环球金融商厦 R栋"
    )

    val filteredHotspots = if (searchQuery.isBlank()) {
        allHotspots
    } else {
        allHotspots.filter { it.contains(searchQuery) }
    }

    // Default immediate selection
    LaunchedEffect(Unit) {
        selectedPointName = if (targetField == "FROM") "上海浦东张江高科技园 A区 5号楼" else "静安里·静安嘉里中心二期写字楼"
    }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFDF5)), // Matching warm cream palette
            border = BorderStroke(1.5.dp, Color(0xFFFFD100)),
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .testTag("dynamic_map_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header with target field description
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(
                                    if (targetField == "FROM") Color(0xFF2E7D32) else Color(0xFFD32F2F),
                                    CircleShape
                                )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "内置免费智能地图已配置 - 选择" + (if (targetField == "FROM") "【取货/购买】" else "【收货/送达】") + "位点",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF222222)
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Map",
                            tint = Color(0xFF888888),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Elegant Search/Filter textfield
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White)
                        .border(1.dp, Color(0xFFEBE5C8), RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search POI",
                        tint = Color(0xFF999999),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    val searchColors = SearchTextFieldDefaults()
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("搜索附近地标、写字楼、商圈...", color = Color(0xFFB0AA90), fontSize = 12.sp) },
                        colors = searchColors,
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        textStyle = LocalTextStyle.current.copy(fontSize = 12.sp, color = Color(0xFF222222))
                    )
                    if (searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { searchQuery = "" },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear",
                                tint = Color(0xFF888888),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // ==================== THE VECTOR VECTOR MAP CANVAS ====================
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1.1f)
                        .clip(RoundedCornerShape(16.dp))
                        .border(1.dp, Color(0xFFE2DCB8), RoundedCornerShape(16.dp))
                        .background(Color(0xFFE8F5E9)) // Ambient soft green-cream terrain bg
                        .pointerInput(Unit) {
                            detectVerticalDragGestures(
                                onVerticalDrag = { change, dragAmount ->
                                    change.consume()
                                    mapScrollY += dragAmount
                                }
                            )
                        }
                ) {
                    // Let's paint beautiful vectorized city elements
                    Canvas(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        val width = size.width
                        val height = size.height
                        
                        // Centering offsets
                        val centerX = width / 2f + mapScrollX
                        val centerY = height / 2f + mapScrollY

                        // 1. Draw river (Huangpu River styling)
                        val riverPath = Path().apply {
                            moveTo(0f, height * 0.75f + mapScrollY)
                            cubicTo(
                                width * 0.35f, height * 0.68f + mapScrollY,
                                width * 0.65f, height * 0.42f + mapScrollY,
                                width, height * 0.3f + mapScrollY
                            )
                        }
                        drawPath(
                            path = riverPath,
                            color = Color(0xFFB3E5FC),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 44f, cap = StrokeCap.Round)
                        )

                        // 2. Draw Green Parks (Pudong Century Park style)
                        drawCircle(
                            color = Color(0xFFA5D6A7),
                            radius = 120f,
                            center = androidx.compose.ui.geometry.Offset(width * 0.22f + mapScrollX, height * 0.25f + mapScrollY)
                        )
                        drawRoundRect(
                            color = Color(0xFFC8E6C9),
                            topLeft = androidx.compose.ui.geometry.Offset(width * 0.7f + mapScrollX, height * 0.65f + mapScrollY),
                            size = androidx.compose.ui.geometry.Size(180f, 100f),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(16f, 16f)
                        )

                        // 3. Gray Residential Grid Blocks
                        val gridPaintColor = Color(0xFFF1EDE4)
                        for (i in 0..4) {
                            for (j in 0..4) {
                                if (i != 2 && j != 2) {
                                    drawRoundRect(
                                        color = gridPaintColor,
                                        topLeft = androidx.compose.ui.geometry.Offset(i * 180f - 100f + mapScrollX * 0.4f, j * 180f - 120f + mapScrollY * 0.4f),
                                        size = androidx.compose.ui.geometry.Size(120f, 95f),
                                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f, 8f)
                                    )
                                }
                            }
                        }

                        // 4. City highways/roads lines
                        // Main avenue y-cross
                        drawLine(
                            color = Color(0xFFFFF9C4),
                            start = androidx.compose.ui.geometry.Offset(0f, height / 2f + mapScrollY),
                            end = androidx.compose.ui.geometry.Offset(width, height / 2f + mapScrollY),
                            strokeWidth = 24f
                        )
                        drawLine(
                            color = Color(0xFFFFF9C4),
                            start = androidx.compose.ui.geometry.Offset(width / 2f + mapScrollX, 0f),
                            end = androidx.compose.ui.geometry.Offset(width / 2f + mapScrollX, height),
                            strokeWidth = 24f
                        )
                        
                        // Dashed road lines divider markings
                        drawLine(
                            color = Color(0xFFFFA000),
                            start = androidx.compose.ui.geometry.Offset(0f, height / 2f + mapScrollY),
                            end = androidx.compose.ui.geometry.Offset(width, height / 2f + mapScrollY),
                            strokeWidth = 2f
                        )

                        // 5. User's current location radar pulse dot (GPS anchor)
                        val pulseAnchorX = width * 0.5f + mapScrollX
                        val pulseAnchorY = height * 0.42f + mapScrollY
                        
                        drawCircle(
                            color = Color(0xFF3F51B5).copy(alpha = pulseAlpha),
                            radius = pulseRadius,
                            center = androidx.compose.ui.geometry.Offset(pulseAnchorX, pulseAnchorY)
                        )
                        
                        drawCircle(
                            color = Color(0xFF2196F3),
                            radius = 12f,
                            center = androidx.compose.ui.geometry.Offset(pulseAnchorX, pulseAnchorY)
                        )
                        drawCircle(
                            color = Color.White,
                            radius = 6f,
                            center = androidx.compose.ui.geometry.Offset(pulseAnchorX, pulseAnchorY)
                        )
                    }

                    // Floating GPS Snapper button
                    IconButton(
                        onClick = {
                            // Centering map offset
                            mapScrollX = 0f
                            mapScrollY = 0f
                            // Trigger real high precision GPS positioning and reverse-geocoding
                            mainViewModel.autoAcquireDetailedLocation(context, targetField) { resolvedAddress ->
                                selectedPointName = resolvedAddress
                            }
                        },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(12.dp)
                            .size(40.dp)
                            .shadow(3.dp, CircleShape)
                            .background(Color.White, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MyLocation,
                            contentDescription = "GPS Target Button",
                            tint = Color(0xFFFFA000),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Centred hovering map pointer icon
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .offset(y = (-14).dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Place,
                                contentDescription = "Map Center Pointer",
                                tint = if (targetField == "FROM") Color(0xFF2E7D32) else Color(0xFFD32F2F),
                                modifier = Modifier
                                    .size(36.dp)
                                    .shadow(2.dp, CircleShape)
                            )
                            Box(
                                modifier = Modifier
                                    .size(8.dp, 3.dp)
                                    .background(Color.Black.copy(alpha = 0.3f), CircleShape)
                            )
                        }
                    }

                    // Display the dynamic selected address snippet floating at the top center of the map
                    Surface(
                        color = Color.Black.copy(alpha = 0.75f),
                        shape = RoundedCornerShape(30.dp),
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Explore,
                                contentDescription = "Active Pointer",
                                tint = Color(0xFFFFD100),
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "拖拽地图以精确定位高德中心点",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // POI hotspots listing
                Text(
                    text = "附近搜索到的实景推荐点 (推荐免费选择)",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF555555),
                    modifier = Modifier.padding(bottom = 6.dp)
                )

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.9f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredHotspots) { option ->
                        val isSel = option == selectedPointName
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedPointName = option },
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSel) Color(0xFFFFFBE6) else Color.White
                            ),
                            border = BorderStroke(
                                1.dp,
                                if (isSel) Color(0xFFFFD100) else Color(0xFFFAF7DE)
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (isSel) Icons.Default.CheckCircle else Icons.Default.LocationOn,
                                    contentDescription = "POI Item",
                                    tint = if (isSel) Color(0xFFFFA000) else Color(0xFF7CB342),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = option,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                    color = Color(0xFF333333),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Confirm Action Button
                Button(
                    onClick = {
                        mainViewModel.selectMapAddress(selectedPointName)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFFD100),
                        contentColor = Color(0xFF222222)
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("map_confirm_selection_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Done,
                        contentDescription = "Done Icon",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "确定选择并填入：" + selectedPointName.take(15) + "...",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }
    }
}

// ==================== REAL-TIME RIDER COMMUNICATION OVERLAY ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderRiderChatOverlay(
    mainViewModel: MainViewModel,
    orderId: String,
    onDismiss: () -> Unit
) {
    val messagesMap by mainViewModel.chatMessagesMap.collectAsState()
    val messages = mainViewModel.getChatMessagesForOrder(orderId)
    
    var inputText by remember { mutableStateOf("") }
    var isRecordingSimActive by remember { mutableStateOf(false) }
    var simulatedRecordSeconds by remember { mutableStateOf(0) }
    var showCameraSimPanel by remember { mutableStateOf(false) }
    
    // Auto-scroll when messages update
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    // Voice record timer simulation
    LaunchedEffect(isRecordingSimActive) {
        if (isRecordingSimActive) {
            simulatedRecordSeconds = 1
            while (isRecordingSimActive && simulatedRecordSeconds < 10) {
                delay(1000)
                simulatedRecordSeconds++
            }
        }
    }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFDF6F)), // matching warm yellow frame base
            border = BorderStroke(1.5.dp, Color(0xFF222222)),
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .testTag("rider_chat_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFFFFFDF9)) // Very clean off-white internal canvas
            ) {
                // Thread Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFFFD100))
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(Color.White, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.DirectionsBike,
                                contentDescription = "Active Rider",
                                tint = Color(0xFF222222),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "骑手：王伟",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color(0xFF222222)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0xFF4CAF50))
                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                ) {
                                    Text("在线沟通", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            Text(
                                text = "配送单号: ${orderId.take(12)}...",
                                fontSize = 10.sp,
                                color = Color(0xFF555555)
                            )
                        }
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Chat",
                            tint = Color(0xFF222222),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Chat Messages Scroll Frame
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(messages) { msg ->
                        ChatBubbleRow(msg = msg, onPlayVoice = {
                            mainViewModel.toggleVoicePlay(orderId, msg.id)
                        })
                    }
                }

                // Shortcut tags
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFF7F5EA))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val shortcuts = listOf("麻烦放门口", "到了电话联系", "路上注意安全！")
                    shortcuts.forEach { phrase ->
                        Box(
                            modifier = Modifier
                                .background(Color.White, RoundedCornerShape(12.dp))
                                .border(1.dp, Color(0xFFECE7D0), RoundedCornerShape(12.dp))
                                .clickable {
                                    mainViewModel.sendTextMessage(orderId, phrase)
                                }
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(text = phrase, fontSize = 10.sp, color = Color(0xFF333333), fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Voice Recording Subpanel
                if (isRecordingSimActive) {
                    RecordingSimOverlayPanel(
                        seconds = simulatedRecordSeconds,
                        onCancel = { isRecordingSimActive = false },
                        onSend = {
                            isRecordingSimActive = false
                            mainViewModel.sendVoiceMessage(orderId, simulatedRecordSeconds)
                        }
                    )
                }

                // Camera Upload Subpanel
                if (showCameraSimPanel) {
                    CameraSimOverlayPanel(
                        onDismiss = { showCameraSimPanel = false },
                        onPhotoSelected = { urlLabel ->
                            showCameraSimPanel = false
                            mainViewModel.sendImageMessage(orderId, urlLabel)
                        }
                    )
                }

                // Controls Input Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White)
                        .border(BorderStroke(0.5.dp, Color(0xFFECE7D0)))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            isRecordingSimActive = true
                        },
                        modifier = Modifier
                            .size(34.dp)
                            .testTag("voice_trigger_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "Voice Input",
                            tint = Color(0xFFFF9800),
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            showCameraSimPanel = true
                        },
                        modifier = Modifier
                            .size(34.dp)
                            .testTag("photo_trigger_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhotoCamera,
                            contentDescription = "Photo Camera",
                            tint = Color(0xFF2196F3),
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        placeholder = { Text("输入和配送员交谈内容...", color = Color.Gray, fontSize = 11.sp) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color(0xFF222222),
                            unfocusedTextColor = Color(0xFF222222),
                            focusedBorderColor = Color(0xFFFFD100),
                            unfocusedBorderColor = Color(0xFFEBE5C8),
                            focusedContainerColor = Color(0xFFFAF9F2),
                            unfocusedContainerColor = Color(0xFFFAF9F2)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .testTag("chat_message_input_field"),
                        shape = RoundedCornerShape(20.dp),
                        singleLine = true,
                        textStyle = LocalTextStyle.current.copy(fontSize = 12.sp)
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    Button(
                        onClick = {
                            if (inputText.isNotBlank()) {
                                mainViewModel.sendTextMessage(orderId, inputText)
                                inputText = ""
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFFD100),
                            contentColor = Color(0xFF222222)
                        ),
                        shape = RoundedCornerShape(18.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp),
                        modifier = Modifier
                            .height(36.dp)
                            .testTag("chat_send_button_cta")
                    ) {
                        Text("发送", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun ChatBubbleRow(
    msg: ChatMessage,
    onPlayVoice: () -> Unit
) {
    val isUser = msg.sender == "USER"
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Column(
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
            modifier = Modifier.fillMaxWidth(0.85f)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (isUser) "我" else "骑手王伟",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF999999)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = msg.timestamp,
                    fontSize = 9.sp,
                    color = Color(0xFFBBBBBB)
                )
            }
            
            Spacer(modifier = Modifier.height(4.dp))

            Card(
                shape = RoundedCornerShape(
                    topStart = if (isUser) 14.dp else 2.dp,
                    topEnd = if (isUser) 2.dp else 14.dp,
                    bottomStart = 14.dp,
                    bottomEnd = 14.dp
                ),
                colors = CardDefaults.cardColors(
                    containerColor = if (isUser) Color(0xFFFFFBE0) else Color(0xFFF1F1F1)
                ),
                border = BorderStroke(0.5.dp, if (isUser) Color(0xFFFFE082) else Color(0xFFE0E0E0))
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    when (msg.type) {
                        "TEXT" -> {
                            Text(
                                text = msg.content,
                                fontSize = 12.sp,
                                color = Color(0xFF222222)
                            )
                        }
                        "VOICE" -> {
                            Row(
                                modifier = Modifier
                                    .clickable { onPlayVoice() }
                                    .padding(horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (msg.isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                                    contentDescription = "Play voice",
                                    tint = Color(0xFFFFA000),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val barCount = 4
                                    for (i in 0 until barCount) {
                                        val barHeight = if (msg.isPlaying) {
                                            val t = rememberInfiniteTransition(label = "audioBar")
                                            val h by t.animateFloat(
                                                initialValue = 4f,
                                                targetValue = 16f,
                                                animationSpec = infiniteRepeatable(
                                                    animation = tween(300 + i * 100, easing = LinearEasing),
                                                    repeatMode = RepeatMode.Reverse
                                                ), label = "h"
                                            )
                                            h.dp
                                        } else {
                                            (6 + i * 3).dp
                                        }
                                        Box(
                                            modifier = Modifier
                                                .size(2.dp, barHeight)
                                                .background(Color(0xFFFFA000), RoundedCornerShape(1.dp))
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "${msg.voiceDurationSec ?: 3}\"",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF666666)
                                )
                            }
                        }
                        "IMAGE" -> {
                            Column(
                                modifier = Modifier
                                    .width(140.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFFE1F5FE))
                                    .border(1.dp, Color(0xFFB3E5FC), RoundedCornerShape(8.dp))
                                    .padding(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .background(Color.White, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CameraAlt,
                                        contentDescription = "Simulated uploading",
                                        tint = Color(0xFF0288D1),
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = msg.mediaUrl ?: "照片核验中",
                                    fontSize = 9.sp,
                                    color = Color(0xFF01579B),
                                    fontWeight = FontWeight.Black,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    text = "包裹验证图与发票凭证",
                                    fontSize = 7.sp,
                                    color = Color(0xFF0288D1),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RecordingSimOverlayPanel(
    seconds: Int,
    onCancel: () -> Unit,
    onSend: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(10.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0)),
        border = BorderStroke(1.dp, Color(0xFFFFB74D)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val infiniteTransition = rememberInfiniteTransition(label = "pulseBullet")
                val scale by infiniteTransition.animateFloat(
                    initialValue = 0.5f,
                    targetValue = 1.0f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(800),
                        repeatMode = RepeatMode.Reverse
                    ), label = "scale"
                )
                Box(
                    modifier = Modifier
                        .size((12 * scale).dp)
                        .scale(scale)
                        .background(Color.Red, CircleShape)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "正在录制语音补充... 0:0$seconds",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFE65100)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Button(
                    onClick = onCancel,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF9E9E9E)),
                    border = BorderStroke(1.dp, Color(0xFFE0E0E0)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text("取消", fontSize = 10.sp)
                }
                Button(
                    onClick = onSend,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFFE65100)),
                    border = BorderStroke(1.dp, Color(0xFFF57C00)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text("发送语音 (${seconds}秒)", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun CameraSimOverlayPanel(
    onDismiss: () -> Unit,
    onPhotoSelected: (String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(10.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFE1F5FE)),
        border = BorderStroke(1.dp, Color(0xFF90CAF9)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "模拟相机与本相册图库上传",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF0D47A1)
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(20.dp)) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close Camera Selector", tint = Color.Gray, modifier = Modifier.size(14.dp))
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "点击从下列3个实景验证样照中选择1个发送：",
                fontSize = 10.sp,
                color = Color(0xFF1565C0),
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val samples = listOf(
                    "包裹封口.jpg" to "验证袋贴密封条",
                    "代购发票.png" to "垫付买货电脑小票",
                    "门前架子.jpg" to "放指定置物架"
                )
                samples.forEach { (title, desc) ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(Color.White, RoundedCornerShape(8.dp))
                            .border(1.dp, Color(0xFFBBDEFB), RoundedCornerShape(8.dp))
                            .clickable { onPhotoSelected(title) }
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(imageVector = Icons.Default.CameraAlt, contentDescription = "Sample Vector", tint = Color(0xFF1976D2), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(text = title, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0D47A1))
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(text = desc, fontSize = 7.sp, color = Color.Gray, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                        }
                    }
                }
            }
        }
    }
}

// ==================== USER PROFILE: DYNAMIC CHOSEN AVATAR PAINTER ====================

@Composable
fun UserAvatarView(
    avatarIndex: Int,
    size: androidx.compose.ui.unit.Dp,
    customAvatarBase64: String? = null,
    onClick: (() -> Unit)? = null
) {
    val modifier = Modifier
        .size(size)
        .clip(CircleShape)
        .let { if (onClick != null) it.clickable { onClick() } else it }

    val bitmap = remember(customAvatarBase64, avatarIndex) {
        if (avatarIndex == 99 && !customAvatarBase64.isNullOrEmpty()) {
            try {
                val decodedBytes = android.util.Base64.decode(customAvatarBase64, android.util.Base64.DEFAULT)
                BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }
    }

    Box(
        modifier = modifier.background(
            if (bitmap != null) Color.Transparent else when (avatarIndex) {
                1 -> Color(0xFFFFECEB) // Warm sun morning red
                2 -> Color(0xFFFFF0F5) // Fluffy pink pet
                3 -> Color(0xFFFFF3E0) // Warm sunset orange
                4 -> Color(0xFFF3E5F5) // Soft purple dream
                5 -> Color(0xFFEFEBE9) // Styled woody clay
                6 -> Color(0xFFE0F7FA) // Neon cyberspace cyan
                else -> Color(0xFFFFD100) // Default premium star gold
            },
            CircleShape
        ),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            androidx.compose.foundation.Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Custom Avatar",
                modifier = Modifier.fillMaxSize(),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop
            )
        } else {
            when (avatarIndex) {
                1 -> {
                    Icon(
                        imageVector = Icons.Default.WbSunny,
                        contentDescription = "午后阳光",
                        tint = Color(0xFFFF9800),
                        modifier = Modifier.size((size.value * 0.55).dp)
                    )
                }
                2 -> {
                    Icon(
                        imageVector = Icons.Default.Pets,
                        contentDescription = "元气猫咪",
                        tint = Color(0xFFEC407A),
                        modifier = Modifier.size((size.value * 0.52).dp)
                    )
                }
                3 -> {
                    Icon(
                        imageVector = Icons.Default.Terrain,
                        contentDescription = "落日余晖",
                        tint = Color(0xFFF57C00),
                        modifier = Modifier.size((size.value * 0.55).dp)
                    )
                }
                4 -> {
                    Icon(
                        imageVector = Icons.Default.SentimentSatisfiedAlt,
                        contentDescription = "卡通过山车",
                        tint = Color(0xFFab47bc),
                        modifier = Modifier.size((size.value * 0.58).dp)
                    )
                }
                5 -> {
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = "萌趣柴犬",
                        tint = Color(0xFF8d6e63),
                        modifier = Modifier.size((size.value * 0.5).dp)
                    )
                }
                6 -> {
                    Icon(
                        imageVector = Icons.Default.Bolt,
                        contentDescription = "赛博霓虹",
                        tint = Color(0xFF26c6da),
                        modifier = Modifier.size((size.value * 0.6).dp)
                    )
                }
                else -> {
                    Text(
                        text = "S",
                        fontSize = (size.value * 0.42).sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF222222)
                    )
                }
            }
        }
    }
}

// ==================== SIMULATED PHOTO GALLERY / ALBUM AVATAR PICKER ====================

@Composable
fun SimulatedAlbumAvatarPickerDialog(
    mainViewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var activeAlbumTab by remember { mutableStateOf("相机胶卷") }

    val selectAvatarImageLauncher = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        if (uri != null) {
            coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    context.contentResolver.openInputStream(uri)?.use {
                        BitmapFactory.decodeStream(it, null, options)
                    }

                    options.inSampleSize = calculateInSampleSize(options, 250, 250)
                    options.inJustDecodeBounds = false

                    val decodedBitmap = context.contentResolver.openInputStream(uri)?.use {
                        BitmapFactory.decodeStream(it, null, options)
                    }

                    if (decodedBitmap != null) {
                        val outStream = java.io.ByteArrayOutputStream()
                        decodedBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, outStream)
                        val compressedBytes = outStream.toByteArray()
                        val base64Data = android.util.Base64.encodeToString(compressedBytes, android.util.Base64.DEFAULT)

                        mainViewModel.setCustomAvatar(base64Data)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("AvatarPicker", "Error setting custom avatar", e)
                }
            }
        }
    }

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.5.dp, Color(0xFF222222)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .testTag("album_avatar_picker_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // Header with photo gallery metadata
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.PhotoLibrary,
                            contentDescription = "Gallery",
                            tint = Color(0xFFFFA000),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "选择相册更换头像",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF222222)
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.Gray,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Gallery directory category tabs
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val tabs = listOf("相机胶卷", "微信保存", "云端同步")
                    tabs.forEach { tab ->
                        val isSelected = activeAlbumTab == tab
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) Color(0xFFFFD100) else Color(0xFFF5F4EC))
                                .clickable { activeAlbumTab = tab }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = tab,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Normal,
                                color = if (isSelected) Color(0xFF222222) else Color(0xFF666666)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (activeAlbumTab == "相机胶卷") {
                    Text(
                        text = "本相册（DCIM/Camera）共识别到 6 张高清人脸/写意图，点击即可确认使用：",
                        fontSize = 10.sp,
                        color = Color(0xFF888888),
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    // Photo Grid entries
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val rowItems = listOf(
                            listOf(
                                Triple(1, "午后阳光.jpg", "拍自 2026/05/18"),
                                Triple(2, "元气猫咪.png", "截图 2026/05/20")
                            ),
                            listOf(
                                Triple(3, "落日余晖.jpg", "相册 2026/04/12"),
                                Triple(4, "卡通过山车.jpg", "动漫插画 昨天")
                            ),
                            listOf(
                                Triple(5, "萌趣柴犬.png", "相册 2026/02/10"),
                                Triple(6, "赛博霓虹.jpg", "酷炫特效 刚刚")
                            )
                        )

                        rowItems.forEach { row ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                row.forEach { (index, title, dateStr) ->
                                    Card(
                                        shape = RoundedCornerShape(12.dp),
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFDF5)),
                                        border = BorderStroke(1.dp, Color(0xFFEBE5C8)),
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable {
                                                mainViewModel.selectAvatarIndex(index)
                                            }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            UserAvatarView(avatarIndex = index, size = 36.dp)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column {
                                                Text(
                                                    text = title,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF222222),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = dateStr,
                                                    fontSize = 8.sp,
                                                    color = Color.Gray
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Empty or virtual other tabs
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudQueue,
                            contentDescription = "Empty Folder",
                            tint = Color.LightGray,
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "该相册暂未放入人像图片",
                            fontSize = 11.sp,
                            color = Color.Gray
                        )
                        Text(
                            text = "请前往「相机胶卷」或重新拍摄再上传",
                            fontSize = 9.sp,
                            color = Color.LightGray,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Bottom actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFF5F4EC),
                            contentColor = Color(0xFF666666)
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("取 消", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            // Restore defaults
                            mainViewModel.selectAvatarIndex(0)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFFD100),
                            contentColor = Color(0xFF222222)
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("恢复默认头像", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ==================== CUSTOMER SERVICE EXCLUSIVE POPUP DIALOG ====================

@Composable
fun CustomerServiceContactDialog(
    onDismiss: () -> Unit
) {
    var copyNoticeText by remember { mutableStateOf<String?>(null) }

    // Clear alert text after a while
    LaunchedEffect(copyNoticeText) {
        if (copyNoticeText != null) {
            delay(2000)
            copyNoticeText = null
        }
    }

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.5.dp, Color(0xFF222222)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .testTag("customer_service_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header Panel
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(Color(0xFFFFF6D0), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.SupportAgent,
                                contentDescription = "Agent icon",
                                tint = Color(0xFF8B8015),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "尊享在线客服中心",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF222222)
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.Gray,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "系统在运行、结算、退抱过程中遇到任何申诉或疑难问题，都可以通过以下两种通道与我们取得即时联系：",
                    fontSize = 11.sp,
                    color = Color(0xFF555555),
                    lineHeight = 16.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Channel 1: QQ Number Contact
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF7F9FC)),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(Color(0xFFE1F5FE), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Chat,
                                    contentDescription = "QQ icon",
                                    tint = Color(0xFF0288D1),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "通道一：QQ官方客服",
                                    fontSize = 11.sp,
                                    color = Color.Gray
                                )
                                Text(
                                    text = "278159132",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF0288D1)
                                )
                            }
                        }

                        Button(
                            onClick = {
                                copyNoticeText = "QQ客服 278159132 已虚拟复制到剪贴板！"
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF0288D1),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(18.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Text("点击复制", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Channel 2: WeChat Number Contact
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF6FBF7)),
                    border = BorderStroke(1.dp, Color(0xFFE7F3EC))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(Color(0xFFE8F5E9), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContactSupport,
                                    contentDescription = "WeChat icon",
                                    tint = Color(0xFF4CAF50),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "通道二：微信官方客服",
                                    fontSize = 11.sp,
                                    color = Color.Gray
                                )
                                Text(
                                    text = "qq278159132",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF388E3C)
                                )
                            }
                        }

                        Button(
                            onClick = {
                                copyNoticeText = "微信客服 qq278159132 已虚拟复制到剪贴板！"
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF4CAF50),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(18.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Text("点击复制", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Temporary Copy success bubble notice anim
                AnimatedVisibility(
                    visible = copyNoticeText != null,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                            .background(Color(0xFFFFFAEB), RoundedCornerShape(8.dp))
                            .border(1.dp, Color(0xFFFFEB9C), RoundedCornerShape(8.dp))
                            .padding(10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = copyNoticeText ?: "",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFE65100),
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // small notice details: “添加联系管理员解决”
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "添加联系管理员解决",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        color = Color(0xFF333333)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "（添加时请备注您的注册账户邮箱以获得快速核实）",
                        fontSize = 9.sp,
                        color = Color.Gray,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF222222),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                ) {
                    Text("确 认 并 关 闭", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun RechargeDialog(
    mainViewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    var copyNoticeText by remember { mutableStateOf<String?>(null) }
    var inputAmountStr by remember { mutableStateOf("100") }
    val rechargeResult by mainViewModel.rechargeResult.collectAsState()

    // Clear copy notice after 2s
    LaunchedEffect(copyNoticeText) {
        if (copyNoticeText != null) {
            delay(2000)
            copyNoticeText = null
        }
    }

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.5.dp, Color(0xFFFFD100)), // Yellow focus outline
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .testTag("recharge_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AddCard,
                            contentDescription = "Recharge Title",
                            tint = Color(0xFFF57F17),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "账户积分充值中心",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF222222)
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.Gray,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // WeChat contact instruction
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F8E9)),
                    border = BorderStroke(1.dp, Color(0xFFDCEDC8))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "💬 官方微信手动充值 (1元 = 1积分)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF33691E)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "请复制文末微信号，添加客服并发送您的注册邮箱，微信转账即可实时为您后台上分。",
                            fontSize = 10.sp,
                            color = Color(0xFF558B2F),
                            lineHeight = 14.sp
                        )
                        
                        Spacer(modifier = Modifier.height(10.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("微信号：", fontSize = 11.sp, color = Color.Gray)
                                Text("qq278159132", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF2E7D32))
                            }
                            Button(
                                onClick = {
                                    copyNoticeText = "微信号 qq278159132 已复制到剪贴板！"
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF4CAF50),
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(18.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp),
                                modifier = Modifier.height(30.dp)
                            ) {
                                Text("复制微信号", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Sandbox fast simulator section
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFDE7)),
                    border = BorderStroke(1.dp, Color(0xFFFFF9C4))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "🛠️ 开发者沙盒快捷充值 (模拟上分)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFF57F17)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // Select popular amounts quick-chips
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf("50", "100", "200", "500").forEach { amount ->
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (inputAmountStr == amount) Color(0xFFFFD100) else Color.White)
                                        .border(1.dp, if (inputAmountStr == amount) Color(0xFFFFD100) else Color(0xFFE0E0E0), RoundedCornerShape(8.dp))
                                        .clickable { inputAmountStr = amount }
                                        .padding(vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "${amount}元",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (inputAmountStr == amount) Color(0xFF222222) else Color(0xFF555555)
                                    )
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(10.dp))
                        
                        OutlinedTextField(
                            value = inputAmountStr,
                            onValueChange = { inputAmountStr = it.filter { char -> char.isDigit() } },
                            label = { Text("充值金额（元）", fontSize = 11.sp) },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        )
                        
                        Spacer(modifier = Modifier.height(10.dp))
                        
                        Button(
                            onClick = {
                                val amtNum = inputAmountStr.toDoubleOrNull() ?: 0.0
                                if (amtNum > 0.0) {
                                    mainViewModel.simulateRecharge(amtNum)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFFFD100),
                                contentColor = Color(0xFF222222)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(38.dp)
                        ) {
                            Text("立即模拟打款并充值", fontSize = 11.sp, fontWeight = FontWeight.Black)
                        }
                    }
                }

                // Notices
                AnimatedVisibility(
                    visible = copyNoticeText != null || rechargeResult != null,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp)
                            .background(Color(0xFFFFFAEB), RoundedCornerShape(8.dp))
                            .border(1.dp, Color(0xFFFFEB9C), RoundedCornerShape(8.dp))
                            .padding(10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = (copyNoticeText ?: rechargeResult) ?: "",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFE65100),
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF222222),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                ) {
                    Text("关 闭 窗 口", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ==================== TAB 4: 附近地图 (NearbyTabScreen) ====================

@Composable
fun NearbyTabScreen(mainViewModel: MainViewModel) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val userCoords by mainViewModel.userCoordinates.collectAsState()
    val isAcquiringLoc by mainViewModel.isAcquiringLocation.collectAsState()
    val locMessage by mainViewModel.locationMessage.collectAsState()

    val isUploading by mainViewModel.isUploadingFile.collectAsState()
    val uploadMsg by mainViewModel.uploadStatusMessage.collectAsState()
    val nearbyParcels by mainViewModel.nearbyParcels.collectAsState()

    val isContinuous by mainViewModel.isContinuousTrackingRun.collectAsState()
    val filteringMode by mainViewModel.locationFilteringMode.collectAsState()
    val rawCoords by mainViewModel.rawSimulatedCoordinates.collectAsState()
    val smoothCoords by mainViewModel.smoothSimulatedCoordinates.collectAsState()
    val calibratedCoords by mainViewModel.calibratedSimulatedCoordinates.collectAsState()
    val scannedWifiList by mainViewModel.scannedWifiList.collectAsState()
    val isScanningWifi by mainViewModel.isScanningWifi.collectAsState()

    var highlightedParcelId by remember { mutableStateOf<String?>(null) }

    // Interactive Photo Album Picker with dynamic compression & upload
    val selectImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val originalFileName = getFileNameFromUri(context, uri) ?: "photo_${System.currentTimeMillis()}.jpg"
                    
                    // Decode original dimensions
                    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    context.contentResolver.openInputStream(uri)?.use {
                        BitmapFactory.decodeStream(it, null, options)
                    }

                    // Calculate safe compression scale sample size (max 800px on both sides)
                    options.inSampleSize = calculateInSampleSize(options, 800, 800)
                    options.inJustDecodeBounds = false

                    val decodedBitmap = context.contentResolver.openInputStream(uri)?.use {
                        BitmapFactory.decodeStream(it, null, options)
                    }

                    if (decodedBitmap != null) {
                        val outStream = ByteArrayOutputStream()
                        // Compress to JPEG with 80% quality
                        decodedBitmap.compress(Bitmap.CompressFormat.JPEG, 80, outStream)
                        val compressedBytes = outStream.toByteArray()
                        val sizeKb = compressedBytes.size / 1024
                        val base64Data = android.util.Base64.encodeToString(compressedBytes, android.util.Base64.DEFAULT)

                        // Forward to viewmodel for mock upload API query and coordinate pairing
                        mainViewModel.uploadCompressedPhoto(originalFileName, base64Data, sizeKb)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("ImageCompressor", "Error compressing or reading photo from gallery", e)
                }
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Core Section 1: Header title
        item {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "同城实景配送",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        color = Color(0xFF222222)
                    )
                    
                    // Active GPS tracking badge
                    Card(
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = if (isAcquiringLoc) Color(0xFFFFF9DB) else Color(0xFFEDFBF0)),
                        border = BorderStroke(1.dp, if (isAcquiringLoc) Color(0xFFFFD100) else Color(0xFFC6F6D5))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(if (isAcquiringLoc) Color(0xFFFF9800) else Color(0xFF388E3C), CircleShape)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isAcquiringLoc) "GPS 定位中..." else "GPS 模块就绪",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isAcquiringLoc) Color(0xFFE65100) else Color(0xFF276749)
                            )
                        }
                    }
                }
                Text(
                    text = "实时地图展示、相册压缩上传与周边包裹核实中心",
                    fontSize = 11.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }

        // Core Section 2: Maps with Vector Coordinates display (Point 2 and Point 5)
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    // Customizable live vector map simulation representing grid nodes
                    AndroidVectorInteractiveMap(
                        userCoords = userCoords,
                        parcels = nearbyParcels,
                        highlightedId = highlightedParcelId,
                        onNodeClick = { id -> highlightedParcelId = id },
                        rawCoords = rawCoords,
                        smoothCoords = smoothCoords,
                        calibratedCoords = calibratedCoords,
                        isContinuous = isContinuous,
                        filteringMode = filteringMode
                    )

                    // Overlay GPS metadata console
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(10.dp)
                            .background(Color(0xE6222222), RoundedCornerShape(8.dp))
                            .padding(8.dp)
                    ) {
                        Column {
                            Text(
                                text = "🛰️ 导航卫星位置存贮",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFFFFD100)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = locMessage,
                                fontSize = 8.sp,
                                color = Color.White,
                                lineHeight = 10.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 200.dp)
                            )
                        }
                    }

                    // Floating GPS locate trigger
                    FloatingActionButton(
                        onClick = { mainViewModel.triggerAcquireGPSLocation() },
                        containerColor = Color(0xFFFFD100),
                        contentColor = Color(0xFF222222),
                        shape = CircleShape,
                        modifier = Modifier
                            .size(44.dp)
                            .align(Alignment.BottomEnd)
                            .padding(bottom = 10.dp, end = 10.dp)
                            .testTag("locate_gps_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.MyLocation,
                            contentDescription = "定位",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        // =========================================================================
        // PROV 1-5: 高精度定位与纠偏滤波测试中心 (5大核心指南实机演示)
        // =========================================================================
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF4F7FB)),
                border = BorderStroke(1.dp, Color(0xFFCFDCE8)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("location_test_center_card")
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.MyLocation,
                                contentDescription = "Satellite Tracker",
                                tint = Color(0xFF1E88E5),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "定位芯片高精度及纠偏算法中心",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0F2C59)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .background(Color(0xFFE1F5FE), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("5大核理适配", fontSize = 8.sp, color = Color(0xFF0288D1), fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "本模块完美演示了连续高精度滤波的核心逻辑：强开定位级别(Priority.HIGH_ACCURACY)、精细位置权限(ACCESS_FINE_LOCATION)、卡尔曼平滑降噪(Kalman)、火星坐标纠偏转换、AP辅助。可在上方地图实时联动！",
                        fontSize = 10.sp,
                        color = Color(0xFF546E7A),
                        lineHeight = 14.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // 1. Double Toggle Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { mainViewModel.toggleContinuousTracking() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isContinuous) Color(0xFF43A047) else Color(0xFF1976D2),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                                .testTag("toggle_continuous_tracking")
                        ) {
                            Icon(
                                imageVector = if (isContinuous) Icons.Default.Stop else Icons.Default.PlayArrow,
                                contentDescription = "Start/Stop",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isContinuous) "停止连续跟踪" else "连续移动模拟 (1.2s)",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Button(
                            onClick = { mainViewModel.runWifiFingerprintScan(context) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF546E7A),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                                .testTag("trigger_wifi_scan")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Wifi,
                                contentDescription = "WiFi Scan",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isScanningWifi) "AP检索中..." else "Wi-Fi辅助定位",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // 2. Filter Modes Segment Selector
                    Text(
                        text = "🔬 联动切换当前地图滤波/渲染模式:",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF37474F)
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            Triple(0, "WGS-84原始", Color(0xFFE53935)),
                            Triple(1, "卡尔曼滤波", Color(0xFFFB8C00)),
                            Triple(2, "纠偏融合(高德)", Color(0xFF1976D2))
                        ).forEach { (modeIdx, labelStr, colorAccent) ->
                            val isSelected = filteringMode == modeIdx
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(
                                        color = if (isSelected) colorAccent.copy(alpha = 0.12f) else Color.White,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .border(
                                        width = if (isSelected) 1.5.dp else 1.dp,
                                        color = if (isSelected) colorAccent else Color(0xFFCFD8DC),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable { mainViewModel.setLocationFilteringMode(modeIdx) }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = labelStr,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) colorAccent else Color(0xFF546E7A)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = when (filteringMode) {
                            0 -> "🔴 [WGS-84模式] 未经任何阻尼过滤的原始GPS。城市重楼多径散射下产生强烈连续晃动 (红点抖动较剧烈)。"
                            1 -> "🟠 [卡尔曼平滑] 通过自适应前向误差预测，实时剔除信号反弹与伪距跃变。运动平坦顺滑 (金黄色中点线)。"
                            else -> "🔵 [融合纠偏(高德腾讯均可)] 国内加密偏移修正。将物理坐标转火星坐标系，使其跟高保真交通路线精准重叠 (蓝点完美着点)。"
                        },
                        fontSize = 9.sp,
                        color = Color(0xFF546E7A),
                        lineHeight = 12.sp,
                        modifier = Modifier.padding(horizontal = 2.dp)
                    )

                    // 3. Wi-Fi Access Points list
                    if (scannedWifiList.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        HorizontalDivider(thickness = 1.dp, color = Color(0xFFE0E0E0))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "📡 双向 Wi-Fi SSID 指纹(基站/网关辅助数据库):",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF263238)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            scannedWifiList.take(3).forEach { ap ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color.White, RoundedCornerShape(6.dp))
                                        .padding(horizontal = 8.dp, vertical = 5.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Wifi, contentDescription = null, modifier = Modifier.size(12.dp), tint = Color(0xFF43A047))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(text = ap.ssid, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF212121))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(text = "(${ap.bssid})", fontSize = 8.sp, color = Color.Gray)
                                    }
                                    Text(
                                        text = "${ap.levelDb} dBm",
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (ap.levelDb > -60) Color(0xFF2E7D32) else Color(0xFFEF6C00)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(thickness = 1.dp, color = Color(0xFFE0E0E0))
                    Spacer(modifier = Modifier.height(10.dp))

                    // 4. Code Selector Drawer (Native Android Kotlin vs Flutter Dart)
                    var codeSubTab by remember { mutableStateOf(0) }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "🧑‍💻 底层全栈定位配置代码库 (点切换):",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F2C59)
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf("Native Kotlin", "Flutter Dart").forEachIndexed { index, title ->
                                val active = codeSubTab == index
                                Box(
                                    modifier = Modifier
                                        .background(
                                            color = if (active) Color(0xFF1976D2) else Color(0xFFE3F2FD),
                                            shape = RoundedCornerShape(4.dp)
                                        )
                                        .clickable { codeSubTab = index }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = title,
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (active) Color.White else Color(0xFF1976D2)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp)
                            .background(Color(0xFF263238), RoundedCornerShape(8.dp))
                            .padding(8.dp)
                    ) {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            item {
                                Text(
                                    text = if (codeSubTab == 0) NativeLocationGuideDetails.KOTLIN_CODE_GUIDE else NativeLocationGuideDetails.FLUTTER_CODE_GUIDE,
                                    color = Color(0xFFECEFF1),
                                    fontSize = 7.5.sp,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                    lineHeight = 11.sp
                                )
                            }
                        }
                    }
                    Text(
                        text = "💡 滑动上方的黑色源码视窗，可直接复印底层的强开高精度策略、位置事件周期频率、卡尔曼和火星算法公式。",
                        fontSize = 8.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }

        // Core Section 3: Photo Selection & Compression Actions (Point 3)
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFBE8)),
                border = BorderStroke(1.dp, Color(0xFFF0E5BC)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "📸 现场包裹相册匹配",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        color = Color(0xFF424242)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "点击下方按钮调用手机本地相册或相机。选取照片后，应用内置智能算法自动对其进行 80% 比例的体积压缩（最高 800px 尺寸），并模拟调用后端 `/api/upload-photo` 接口存储，反馈至附近列表。",
                        fontSize = 10.sp,
                        color = Color.Gray,
                        lineHeight = 14.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = { selectImageLauncher.launch("image/*") },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF222222),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp)
                                .testTag("pick_compress_upload_button")
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Image, contentDescription = "album", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("从相册选取包裹照并压缩上传", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // Uploading states or status indicators
                    if (isUploading) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = Color(0xFFFFD100)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "正在启动后端上传...",
                                fontSize = 10.sp,
                                color = Color(0xFFFF6D00),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else if (uploadMsg != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFE8F5E9), RoundedCornerShape(8.dp))
                                .border(1.dp, Color(0xFFC8E6C9), RoundedCornerShape(8.dp))
                                .padding(8.dp)
                        ) {
                            Text(
                                text = "✓ $uploadMsg",
                                fontSize = 10.sp,
                                color = Color(0xFF2E7D32),
                                fontWeight = FontWeight.Bold,
                                lineHeight = 12.sp
                            )
                        }
                    }
                }
            }
        }

        // Core Section 4: "附近列表" (Nearby dynamic parcel lists - Point 5)
        item {
            Text(
                text = "附近同城配送实拍快照列表 (共 ${nearbyParcels.size} 个记录)",
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
                color = Color(0xFF222222),
                modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
            )
        }

        if (nearbyParcels.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("当前附近区域暂无公开相片快照，欢迎点击上方按钮上传首张！", fontSize = 11.sp, color = Color.Gray)
                }
            }
        } else {
            items(nearbyParcels) { parcel ->
                val isHighlighted = parcel.id == highlightedParcelId
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = if (isHighlighted) Color(0xFFFFFEEB) else Color.White),
                    border = BorderStroke(if (isHighlighted) 1.5.dp else 1.dp, if (isHighlighted) Color(0xFFFFD100) else Color(0xFFECEAD3)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { highlightedParcelId = parcel.id }
                ) {
                    Row(
                        modifier = Modifier
                            .padding(12.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.Top
                    ) {
                        // Render photo thumbnail (Point 3 representation / base64 bitmap loader)
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFF7F4EC)),
                            contentAlignment = Alignment.Center
                        ) {
                            val bitmap = remember(parcel.imageBase64) {
                                if (parcel.imageBase64.isNotEmpty()) {
                                    try {
                                        val decodedBytes = android.util.Base64.decode(parcel.imageBase64, android.util.Base64.DEFAULT)
                                        BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
                                    } catch (e: Exception) {
                                        null
                                    }
                                } else {
                                    null
                                }
                            }

                            if (bitmap != null) {
                                androidx.compose.foundation.Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = "Uploaded Photo",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                )
                            } else {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                    Icon(
                                        imageVector = Icons.Default.LocalMall,
                                        contentDescription = "Parcel Icon",
                                        tint = Color.Gray,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text("无图/占位", fontSize = 8.sp, color = Color.LightGray)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = parcel.title,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color(0xFF222222)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                if (parcel.imageBase64.isNotEmpty()) {
                                    Box(
                                        modifier = Modifier
                                            .background(Color(0xFFFFFAEB), RoundedCornerShape(4.dp))
                                            .border(1.dp, Color(0xFFFFE082), RoundedCornerShape(4.dp))
                                            .padding(horizontal = 4.dp, vertical = 2.dp)
                                    ) {
                                        Text("已压缩实拍", fontSize = 7.sp, color = Color(0xFFE65100), fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = parcel.details,
                                fontSize = 10.sp,
                                color = Color(0xFF666666),
                                lineHeight = 13.sp
                            )
                            
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.LocationOn,
                                    contentDescription = "Marker Location",
                                    modifier = Modifier.size(10.dp),
                                    tint = Color.Gray
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(
                                    text = "上传人: ${parcel.uploader} · 坐标: (${"%.4f".format(parcel.latitude)}, ${"%.4f".format(parcel.longitude)})",
                                    fontSize = 8.sp,
                                    color = Color.LightGray
                                )
                            }
                        }
                    }
                }
            }
        }
        
        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

// ==================== CUSTOM VECTOR INTERACTIVE GRID MAP CANVAS ====================

@Composable
fun AndroidVectorInteractiveMap(
    userCoords: Pair<Double, Double>,
    parcels: List<NearbyParcelEntity>,
    highlightedId: String?,
    onNodeClick: (String) -> Unit,
    rawCoords: Pair<Double, Double> = Pair(31.2304, 121.4737),
    smoothCoords: Pair<Double, Double> = Pair(31.2304, 121.4737),
    calibratedCoords: Pair<Double, Double> = Pair(31.2304, 121.4737),
    isContinuous: Boolean = false,
    filteringMode: Int = 2
) {
    // Map bounds (lat: 31.215 - 31.250, lng: 121.450 - 121.510)
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(parcels, userCoords) {
                detectTapGestures { offset ->
                    val width = size.width
                    val height = size.height
                    
                    parcels.forEach { p ->
                        val yRatio = 1.0 - ((p.latitude - 31.215) / 0.035).coerceIn(0.0, 1.0)
                        val xRatio = ((p.longitude - 121.450) / 0.060).coerceIn(0.0, 1.0)
                        val pX = xRatio * width
                        val pY = yRatio * height
                        
                        val distance = Math.sqrt(Math.pow((offset.x - pX).toDouble(), 2.0) + Math.pow((offset.y - pY).toDouble(), 2.0))
                        if (distance < 30.0) {
                            onNodeClick(p.id)
                        }
                    }
                }
            }
    ) {
        val width = size.width
        val height = size.height

        // 1. Draw elegant maps grid background
        drawRect(color = Color(0xFFF9F7EF))

        // 2. Draw Simulated River (Huangpu River styling)
        val riverPath = Path().apply {
            moveTo(width * 0.7f, 0f)
            cubicTo(
                width * 0.65f, height * 0.3f,
                width * 0.85f, height * 0.7f,
                width * 0.8f, height
            )
            lineTo(width, height)
            lineTo(width, 0f)
            close()
        }
        drawPath(path = riverPath, color = Color(0xFFD6E4FA))

        // 3. Draw Grid Streets lines representing city blocks
        val gridLinesColor = Color(0xFFE5DEC9)
        val streetStroke = 4f
        
        // Horizontal Streets
        for (i in 1..5) {
            val y = height * (i / 6.0f)
            drawLine(
                color = gridLinesColor,
                start = androidx.compose.ui.geometry.Offset(0f, y),
                end = androidx.compose.ui.geometry.Offset(width, y),
                strokeWidth = streetStroke
            )
        }
        // Vertical Streets
        for (i in 1..5) {
            val x = width * (i / 6.0f)
            drawLine(
                color = gridLinesColor,
                start = androidx.compose.ui.geometry.Offset(x, 0f),
                end = androidx.compose.ui.geometry.Offset(x, height),
                strokeWidth = streetStroke
            )
        }

        // Draw highway lanes
        drawLine(
            color = Color(0xFFFFF9DE),
            start = androidx.compose.ui.geometry.Offset(0f, height * 0.4f),
            end = androidx.compose.ui.geometry.Offset(width, height * 0.4f),
            strokeWidth = 14f
        )
        drawLine(
            color = Color(0xFFFFD54F),
            start = androidx.compose.ui.geometry.Offset(0f, height * 0.4f),
            end = androidx.compose.ui.geometry.Offset(width, height * 0.4f),
            strokeWidth = 2f
        )

        drawLine(
            color = Color(0xFFFFF9DE),
            start = androidx.compose.ui.geometry.Offset(width * 0.45f, 0f),
            end = androidx.compose.ui.geometry.Offset(width * 0.45f, height),
            strokeWidth = 14f
        )
        drawLine(
            color = Color(0xFFFFD54F),
            start = androidx.compose.ui.geometry.Offset(width * 0.45f, 0f),
            end = androidx.compose.ui.geometry.Offset(width * 0.45f, height),
            strokeWidth = 2f
        )

        // 4. Draw Parcel Markers from the State database (Point 5)
        parcels.forEach { p ->
            val yRatio = 1.0 - ((p.latitude - 31.215) / 0.035).coerceIn(0.0, 1.0)
            val xRatio = ((p.longitude - 121.450) / 0.060).coerceIn(0.0, 1.0)
            val pX = xRatio * width
            val pY = yRatio * height

            val isHighlighted = p.id == highlightedId
            
            // Draw Pulsating Background
            if (isHighlighted) {
                drawCircle(
                    color = Color(0x66FFD100),
                    radius = 32f,
                    center = androidx.compose.ui.geometry.Offset(pX.toFloat(), pY.toFloat())
                )
            } else {
                drawCircle(
                    color = Color(0x33FFA000),
                    radius = 18f,
                    center = androidx.compose.ui.geometry.Offset(pX.toFloat(), pY.toFloat())
                )
            }

            // Draw pin anchor
            drawCircle(
                color = if (isHighlighted) Color(0xFFFFA000) else Color(0xFFE65100),
                radius = 12f,
                center = androidx.compose.ui.geometry.Offset(pX.toFloat(), pY.toFloat())
            )

            drawCircle(
                color = Color.White,
                radius = 5f,
                center = androidx.compose.ui.geometry.Offset(pX.toFloat(), pY.toFloat())
            )
        }

        // 5. Draw User current location locator with pulsating halo (Point 2 display)
        if (isContinuous) {
            // Visualize all three models side-by-side! This is incredibly informative!
            
            // 1. Raw Jittery coordinates (Red bouncing dots representing unstable raw satellite stream)
            val ry = (1.0 - ((rawCoords.first - 31.215) / 0.035).coerceIn(0.0, 1.0)) * height
            val rx = (((rawCoords.second - 121.450) / 0.060).coerceIn(0.0, 1.0)) * width
            drawCircle(color = Color(0x22F44336), radius = 30f, center = androidx.compose.ui.geometry.Offset(rx.toFloat(), ry.toFloat()))
            drawCircle(color = Color(0xFFE53935), radius = 6f, center = androidx.compose.ui.geometry.Offset(rx.toFloat(), ry.toFloat()))

            // 2. Kalman Filtered coordinates (Orange dots showing smooth filtering without offset corrections)
            val sy = (1.0 - ((smoothCoords.first - 31.215) / 0.035).coerceIn(0.0, 1.0)) * height
            val sx = (((smoothCoords.second - 121.450) / 0.060).coerceIn(0.0, 1.0)) * width
            drawCircle(color = Color(0x22FF9800), radius = 34f, center = androidx.compose.ui.geometry.Offset(sx.toFloat(), sy.toFloat()))
            drawCircle(color = Color(0xFFFB8C00), radius = 6f, center = androidx.compose.ui.geometry.Offset(sx.toFloat(), sy.toFloat()))

            // 3. GCJ-02 calibrated coordinates (Blue glowing locator which snaps perfectly with roads!)
            val cy = (1.0 - ((calibratedCoords.first - 31.215) / 0.035).coerceIn(0.0, 1.0)) * height
            val cx = (((calibratedCoords.second - 121.450) / 0.060).coerceIn(0.0, 1.0)) * width
            drawCircle(color = Color(0x442196F3), radius = 44f, center = androidx.compose.ui.geometry.Offset(cx.toFloat(), cy.toFloat()))
            drawCircle(color = Color.White, radius = 10f, center = androidx.compose.ui.geometry.Offset(cx.toFloat(), cy.toFloat()))
            drawCircle(color = Color(0xFF1E88E5), radius = 6f, center = androidx.compose.ui.geometry.Offset(cx.toFloat(), cy.toFloat()))
        } else {
            val latCenter = userCoords.first
            val lngCenter = userCoords.second
            val userYRatio = 1.0 - ((latCenter - 31.215) / 0.035).coerceIn(0.0, 1.0)
            val userXRatio = ((lngCenter - 121.450) / 0.060).coerceIn(0.0, 1.0)
            val uX = userXRatio * width
            val uY = userYRatio * height

            drawCircle(
                color = Color(0x331E88E5),
                radius = 44f,
                center = androidx.compose.ui.geometry.Offset(uX.toFloat(), uY.toFloat())
            )
            drawCircle(
                color = Color(0x551E88E5),
                radius = 24f,
                center = androidx.compose.ui.geometry.Offset(uX.toFloat(), uY.toFloat())
            )
            // Pulsating glowing blue GPS pin dot
            drawCircle(
                color = Color.White,
                radius = 12f,
                center = androidx.compose.ui.geometry.Offset(uX.toFloat(), uY.toFloat())
            )
            drawCircle(
                color = Color(0xFF1976D2),
                radius = 8f,
                center = androidx.compose.ui.geometry.Offset(uX.toFloat(), uY.toFloat())
            )
        }
    }
}

// ==================== IMAGE FILE UTILITIES & COMPONENT HELPERS ====================

fun getFileNameFromUri(context: android.content.Context, uri: android.net.Uri): String? {
    var result: String? = null
    if (uri.scheme == "content") {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        try {
            if (cursor != null && cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (index != -1) {
                    result = cursor.getString(index)
                }
            }
        } finally {
            cursor?.close()
        }
    }
    if (result == null) {
        result = uri.path
        val cut = result?.lastIndexOf('/') ?: -1
        if (cut != -1) {
            result = result?.substring(cut + 1)
        }
    }
    return result
}

fun calculateInSampleSize(options: android.graphics.BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
    val height = options.outHeight
    val width = options.outWidth
    var inSampleSize = 1
    if (height > reqHeight || width > reqWidth) {
        val halfHeight = height / 2
        val halfWidth = width / 2
        while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
            inSampleSize *= 2
        }
    }
    return inSampleSize
}
