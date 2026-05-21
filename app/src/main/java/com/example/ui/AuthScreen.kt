package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun LightningLogo(
    modifier: Modifier = Modifier,
    innerColor: Color = Color(0xFF222222)
) {
    androidx.compose.foundation.Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(width * 0.58f, height * 0.12f)
            lineTo(width * 0.28f, height * 0.55f)
            lineTo(width * 0.53f, height * 0.55f)
            lineTo(width * 0.42f, height * 0.88f)
            lineTo(width * 0.72f, height * 0.45f)
            lineTo(width * 0.47f, height * 0.45f)
            close()
        }
        drawPath(path, color = innerColor)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthScreen(
    authViewModel: AuthViewModel,
    onAuthSuccess: (String, String) -> Unit
) {
    val meituanColorScheme = MaterialTheme.colorScheme.copy(
        primary = Color(0xFFFFD100),
        onPrimary = Color(0xFF222222),
        secondary = Color(0xFFFFD100),
        onSecondary = Color(0xFF222222),
        background = Color(0xFFFFFCEF),
        surface = Color(0xFFFFFFFF),
        error = Color(0xFFD32F2F)
    )

    MaterialTheme(colorScheme = meituanColorScheme) {
        val email by authViewModel.email.collectAsState()
        val nickname by authViewModel.nickname.collectAsState()
        val password by authViewModel.password.collectAsState()
        val otpCode by authViewModel.otpCode.collectAsState()
        val isRegisterMode by authViewModel.isRegisterMode.collectAsState()
        val countdown by authViewModel.countdown.collectAsState()
        val isLoading by authViewModel.isLoading.collectAsState()
        val errorMessage by authViewModel.errorMessage.collectAsState()
        val successMessage by authViewModel.successMessage.collectAsState()

        var isPasswordVisible by remember { mutableStateOf(false) }

        val focusManager = LocalFocusManager.current
        val scrollState = rememberScrollState()

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFFFFCEF))  // Cream yellow background matching screenshot
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 24.dp)
                .verticalScroll(scrollState),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 48.dp, bottom = 32.dp)
                    .animateContentSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Circular Lightning Badge with White Halo Ring and Shadow
                Box(
                    modifier = Modifier
                        .size(92.dp)
                        .shadow(6.dp, CircleShape)
                        .background(Color(0xFFFFD100), CircleShape)
                        .border(3.dp, Color.White, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    LightningLogo(
                        modifier = Modifier.size(54.dp),
                        innerColor = Color(0xFF332F00) // Deep Dark Yellow/Bronze matching original screenshot bolt
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                // "Velocity" with Bold Serife/Sans styling
                Text(
                    text = "Velocity",
                    fontSize = 42.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF5C5200), // Olive gold-bronze tone
                    textAlign = TextAlign.Center,
                    letterSpacing = (-0.5).sp
                )

                // Subtitle
                Text(
                    text = "速达跑腿",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF706B4D), // Muted gold-gray
                    modifier = Modifier.padding(top = 4.dp, bottom = 28.dp)
                )

                // Auth Input Card matching the visual mockup
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = Color.White,
                    tonalElevation = 0.dp,
                    shadowElevation = 2.dp
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.Start
                    ) {
                        // Switch tab row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFFF2F2F2))
                                .padding(3.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Login Tab
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (!isRegisterMode) Color.White else Color.Transparent)
                                    .clickable { if (isRegisterMode) authViewModel.toggleMode() },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "登录",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (!isRegisterMode) Color(0xFF5C5200) else Color(0xFF8F8F8F)
                                )
                            }

                            // Register Tab
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isRegisterMode) Color.White else Color.Transparent)
                                    .clickable { if (!isRegisterMode) authViewModel.toggleMode() },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "注册",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isRegisterMode) Color(0xFF5C5200) else Color(0xFF8F8F8F)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // FIELD: EMAIL
                        Text(
                            text = "电子邮箱",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF222222),
                            modifier = Modifier.padding(bottom = 6.dp)
                        )

                        OutlinedTextField(
                            value = email,
                            onValueChange = { authViewModel.updateEmail(it) },
                            placeholder = { Text("请输入邮箱", color = Color(0xFFB5B5B5)) },
                            leadingIcon = { 
                                Icon(
                                    Icons.Default.Email, 
                                    contentDescription = "Email", 
                                    tint = Color(0xFF8F8F8F),
                                    modifier = Modifier.size(20.dp)
                                ) 
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color(0xFF222222),
                                unfocusedTextColor = Color(0xFF222222),
                                focusedBorderColor = Color(0xFFFFD100),
                                unfocusedBorderColor = Color(0xFFE5E2D0),
                                focusedContainerColor = Color(0xFFFCFCFA),
                                unfocusedContainerColor = Color(0xFFFCFCFA)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("email_input"),
                            shape = RoundedCornerShape(10.dp)
                        )

                        // FIELD: NICKNAME (if RegisterMode)
                        AnimatedVisibility(visible = isRegisterMode) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "用户昵称",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF222222),
                                    modifier = Modifier.padding(bottom = 6.dp)
                                )
                                OutlinedTextField(
                                    value = nickname,
                                    onValueChange = { authViewModel.updateNickname(it) },
                                    placeholder = { Text("请输入昵称（如：速达骑手）", color = Color(0xFFB5B5B5)) },
                                    leadingIcon = { 
                                        Icon(
                                            Icons.Default.Person, 
                                            contentDescription = "Nickname", 
                                            tint = Color(0xFF8F8F8F),
                                            modifier = Modifier.size(20.dp)
                                        ) 
                                    },
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = Color(0xFF222222),
                                        unfocusedTextColor = Color(0xFF222222),
                                        focusedBorderColor = Color(0xFFFFD100),
                                        unfocusedBorderColor = Color(0xFFE5E2D0),
                                        focusedContainerColor = Color(0xFFFCFCFA),
                                        unfocusedContainerColor = Color(0xFFFCFCFA)
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("nickname_input"),
                                    shape = RoundedCornerShape(10.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // FIELD: PASSWORD
                        Text(
                            text = "密码",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF222222),
                            modifier = Modifier.padding(bottom = 6.dp)
                        )

                        OutlinedTextField(
                            value = password,
                            onValueChange = { authViewModel.updatePassword(it) },
                            placeholder = { Text("请输入密码", color = Color(0xFFB5B5B5)) },
                            leadingIcon = { 
                                Icon(
                                    Icons.Default.Lock, 
                                    contentDescription = "Lock", 
                                    tint = Color(0xFF8F8F8F),
                                    modifier = Modifier.size(20.dp)
                                ) 
                            },
                            trailingIcon = {
                                val image = if (isPasswordVisible) 
                                    androidx.compose.ui.res.painterResource(id = android.R.drawable.ic_menu_view)
                                else 
                                    androidx.compose.ui.res.painterResource(id = android.R.drawable.ic_secure)

                                IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                    Icon(
                                        painter = image,
                                        contentDescription = "Toggle password visibility",
                                        tint = Color(0xFF8F8F8F),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            },
                            visualTransformation = if (isPasswordVisible) androidx.compose.ui.text.input.VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color(0xFF222222),
                                unfocusedTextColor = Color(0xFF222222),
                                focusedBorderColor = Color(0xFFFFD100),
                                unfocusedBorderColor = Color(0xFFE5E2D0),
                                focusedContainerColor = Color(0xFFFCFCFA),
                                unfocusedContainerColor = Color(0xFFFCFCFA)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("password_input"),
                            shape = RoundedCornerShape(10.dp)
                        )

                        // FIELD: OPT CODE (if RegisterMode)
                        AnimatedVisibility(visible = isRegisterMode) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "邮箱验证码",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF222222),
                                    modifier = Modifier.padding(bottom = 6.dp)
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    OutlinedTextField(
                                        value = otpCode,
                                        onValueChange = { authViewModel.updateOtp(it) },
                                        placeholder = { Text("请输入验证码", color = Color(0xFFB5B5B5)) },
                                        leadingIcon = { 
                                            Icon(
                                                Icons.Default.PhoneAndroid, 
                                                contentDescription = "Code", 
                                                tint = Color(0xFF8F8F8F),
                                                modifier = Modifier.size(20.dp)
                                            ) 
                                        },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        singleLine = true,
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedTextColor = Color(0xFF222222),
                                            unfocusedTextColor = Color(0xFF222222),
                                            focusedBorderColor = Color(0xFFFFD100),
                                            unfocusedBorderColor = Color(0xFFE5E2D0),
                                            focusedContainerColor = Color(0xFFFCFCFA),
                                            unfocusedContainerColor = Color(0xFFFCFCFA)
                                        ),
                                        modifier = Modifier
                                            .weight(0.55f)
                                            .testTag("otp_input"),
                                        shape = RoundedCornerShape(10.dp)
                                    )

                                    Button(
                                        onClick = { 
                                            focusManager.clearFocus()
                                            authViewModel.sendVerificationCode() 
                                        },
                                        enabled = countdown == 0 && !isLoading,
                                        modifier = Modifier
                                            .weight(0.45f)
                                            .height(50.dp)
                                            .testTag("send_otp_button"),
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFFF5F3E5),
                                            contentColor = Color(0xFF5C5200)
                                        )
                                    ) {
                                        Text(
                                            text = if (countdown > 0) "${countdown}s后重发" else "获取验证码",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }

                        // Forgot Password Link right-aligned
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.CenterEnd
                        ) {
                            Text(
                                text = "忘记密码?",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF8B8015),
                                modifier = Modifier.clickable {
                                    authViewModel.sendVerificationCode()
                                }
                            )
                        }

                        // ACTION FEEDBACK MESSAGE HANDLERS
                        AnimatedVisibility(visible = errorMessage != null) {
                            Surface(
                                color = MaterialTheme.colorScheme.errorContainer,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Outlined.ErrorOutline, 
                                        contentDescription = "Error",
                                        tint = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = errorMessage ?: "",
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }

                        AnimatedVisibility(visible = successMessage != null) {
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Outlined.CheckCircle, 
                                        contentDescription = "Success",
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = successMessage ?: "",
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }

                        // GOLDEN PRIMARY BUTTON with "登录 ->"
                        Button(
                            onClick = {
                                focusManager.clearFocus()
                                authViewModel.executeAuthAction { user ->
                                    onAuthSuccess(user.email, user.nickname)
                                }
                            },
                            enabled = !isLoading,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("submit_auth_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFFFD100),
                                contentColor = Color(0xFF222222)
                            )
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = Color(0xFF222222),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = if (isRegisterMode) "注册并安全登录" else "登录",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "→",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        // OR DIVIDER
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            HorizontalDivider(modifier = Modifier.weight(1f), color = Color(0xFFEBEBEB))
                            Text(
                                text = "或",
                                fontSize = 12.sp,
                                color = Color(0xFF999999),
                                modifier = Modifier.padding(horizontal = 12.dp)
                            )
                            HorizontalDivider(modifier = Modifier.weight(1f), color = Color(0xFFEBEBEB))
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        // SECONDARY ACTION: MOBILE LOGIN STYLE BUTTON
                        OutlinedButton(
                            onClick = {
                                // Simple quick-bypass trigger for demo purposes using demo account
                                authViewModel.updateEmail("demo@jingmiao.com")
                                authViewModel.updatePassword("demo123")
                                focusManager.clearFocus()
                                authViewModel.executeAuthAction { user ->
                                    onAuthSuccess(user.email, user.nickname)
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color(0xFF222222)
                            ),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE5E2D0))
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    Icons.Default.PhoneAndroid,
                                    contentDescription = "Phone",
                                    tint = Color(0xFF222222),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "手机号登录",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "注册登录即代表您已同意《极速跑腿服务用户协议》及隐私政策",
                            fontSize = 10.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 14.sp,
                            color = Color(0xFF999999),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // DEMO TIPS BOX
                OutlinedCard(
                    colors = CardDefaults.outlinedCardColors(
                        containerColor = Color.Transparent
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                    border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(Color(0xFFE6E2C4), Color(0xFFFFFCEF))))
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp)
                    ) {
                        Text(
                            text = "💡 极速评测通道",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF5C5200)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "1. 注册模式：随便输入个测试邮箱，然后直接点“获取验证码”便能在上方亮起的横条里直接拿到系统动态生成的密匙，无需查收真邮件，直接手填即可通过注册！\n" +
                                   "2. 快捷登录：底部的“手机号登录”按钮已被智能打通，点击可一秒自动装载测试套餐，助您即刻进站开始接单！",
                            fontSize = 11.sp,
                            color = Color(0xFF706B4D),
                            lineHeight = 15.sp
                        )
                    }
                }
            }
        }
    }
}
