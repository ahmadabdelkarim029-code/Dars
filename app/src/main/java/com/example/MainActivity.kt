package com.example

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.data.AttendanceSession
import com.example.data.Student
import com.example.data.StudentWithStats
import com.example.ui.AttendanceViewModel
import com.example.ui.CameraScannerView
import com.example.ui.theme.MyApplicationTheme
import com.example.utils.BackupHelper
import com.example.utils.DocumentExporter
import com.example.utils.QRCodeGenerator
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                // Enforce Arabic RTL Directionality
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                        val navController = rememberNavController()
                        val viewModel: AttendanceViewModel = viewModel()
                        
                        NavigationGraph(
                            navController = navController,
                            viewModel = viewModel,
                            modifier = Modifier.padding(innerPadding)
                        )
                    }
                }
            }
        }
    }
}

// Global Custom Colors for styling to avoid bland visual styles
object AppColors {
    val SeaBlue = Color(0xFF0F4C81)
    val FreshGreen = Color(0xFF2E7D32)
    val PaleGreen = Color(0xFFE8F5E9)
    val FireRed = Color(0xFFC62828)
    val LightGray = Color(0xFFF5F5F5)
}

@Composable
fun NavigationGraph(
    navController: NavHostController,
    viewModel: AttendanceViewModel,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = "home",
        modifier = modifier
    ) {
        composable("home") {
            HomeScreen(navController, viewModel)
        }
        composable("students") {
            StudentsScreen(navController, viewModel)
        }
        composable("scan") {
            ScanScreen(navController, viewModel)
        }
        composable("reports") {
            ReportsScreen(navController, viewModel)
        }
    }
}

@Composable
fun HomeScreen(
    navController: NavHostController,
    viewModel: AttendanceViewModel
) {
    val context = LocalContext.current
    val students by viewModel.allStudents.collectAsState()
    val sessions by viewModel.allSessions.collectAsState()
    val records by viewModel.allRecords.collectAsState()
    val isSessionActive by viewModel.isSessionActive.collectAsState()

    var showStartSessionDialog by remember { mutableStateOf(false) }
    var selectedGrade by remember { mutableStateOf("") }
    var sessionNote by remember { mutableStateOf("") }

    // File Backup launchers
    val backupFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            BackupHelper.importBackup(context, uri, viewModel.repository) {
                Toast.makeText(context, "البيانات مستعادة بنجاح", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // App Header Accent
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(AppColors.SeaBlue, Color(0xFF3B82F6))
                    )
                )
                .padding(24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "نظام حضور الطلاب",
                        fontSize = 26.sp,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "تسجيل الحضور اللاسلكي السريع",
                        fontSize = 14.sp,
                        color = Color.White.copy(alpha = 0.82f)
                    )
                }
                Icon(
                    imageVector = Icons.Filled.School,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(48.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Active Session Indicator Banner
        if (isSessionActive) {
            Card(
                colors = CardDefaults.cardColors(containerColor = AppColors.PaleGreen),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
                    .border(1.dp, AppColors.FreshGreen.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(AppColors.FreshGreen)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "هناك حصة نشطة حالياً للصف: ${viewModel.sessionGrade.collectAsState().value}",
                            fontWeight = FontWeight.Bold,
                            color = AppColors.FreshGreen
                        )
                    }
                    Button(
                        onClick = { navController.navigate("scan") },
                        colors = ButtonDefaults.buttonColors(containerColor = AppColors.FreshGreen)
                    ) {
                        Text("متابعة المسح")
                    }
                }
            }
        }

        // Metrics Grid (Dashboard Stats widgets)
        Text(
            text = "الإحصائيات العامة والملخص",
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            textAlign = TextAlign.Start
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // General Student count card
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.weight(1f)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("الطلاب المسجلين", color = Color(0xFF1E40AF), fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${students.size}",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black,
                        color = Color(0xFF1E40AF)
                    )
                }
            }

            // Sessions ran card
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF7ED)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.weight(1f)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("عدد الحصص الكلي", color = Color(0xFF9A3412), fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${sessions.size}",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black,
                        color = Color(0xFF9A3412)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Main Navigation Options / Launcher Buttons
        Text(
            text = "الوصول السريع",
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            textAlign = TextAlign.Start
        )

        // Button Cards Layout Grid
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.weight(1f)
        ) {
            // Start class code scan button
            item {
                MenuLauncherCard(
                    title = "بدء حصة جديدة",
                    subtitle = "مسح وبدء تسجيل",
                    icon = Icons.Filled.QrCodeScanner,
                    backgroundColor = Color(0xFFECFDF5),
                    accentColor = AppColors.FreshGreen
                ) {
                    if (isSessionActive) {
                        navController.navigate("scan")
                    } else {
                        showStartSessionDialog = true
                    }
                }
            }

            // Students directory button
            item {
                MenuLauncherCard(
                    title = "إدارة الطلاب",
                    subtitle = "إضافة وقائمة الطلاب",
                    icon = Icons.Filled.Group,
                    backgroundColor = Color(0xFFF0FDF4),
                    accentColor = Color(0xFF15803D)
                ) {
                    navController.navigate("students")
                }
            }

            // Reports viewer button
            item {
                MenuLauncherCard(
                    title = "سجل التقارير",
                    subtitle = "الملخص وجدول البيانات",
                    icon = Icons.Filled.BarChart,
                    backgroundColor = Color(0xFFFDF2F8),
                    accentColor = Color(0xFFBE185D)
                ) {
                    navController.navigate("reports")
                }
            }

            // Database Backups and system settings button
            item {
                MenuLauncherCard(
                    title = "النسخ الاحتياطي",
                    subtitle = "تصدير واستيراد البيانات",
                    icon = Icons.Filled.Backup,
                    backgroundColor = Color(0xFFFAF5FF),
                    accentColor = Color(0xFF6B21A8)
                ) {
                    // Show double choices sheet
                    val itemsChoices = arrayOf("تصدير نسخة احتياطية (.json)", "استيراد واستعادة بيانات")
                    val alertProps = android.app.AlertDialog.Builder(context)
                        .setTitle("إدارة قواعد البيانات")
                        .setItems(itemsChoices) { _, which ->
                            if (which == 0) {
                                BackupHelper.exportBackup(context, viewModel.repository) {
                                    Toast.makeText(context, "اكتمل تصدير النسخة بنجاح", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                backupFileLauncher.launch("application/json")
                            }
                        }
                    alertProps.show()
                }
            }
        }
    }

    // Start New Session dialog form
    if (showStartSessionDialog) {
        Dialog(onDismissRequest = { showStartSessionDialog = false }) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Filled.Alarm,
                        contentDescription = null,
                        tint = AppColors.SeaBlue,
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "تفاصيل الحصة الجديدة",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = Color.Black
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    // Select Grade/Classroom
                    val classrooms = listOf("الصف الأول", "الصف الثاني", "الصف الثالث", "الصف الرابع", "الصف الخامس", "الصف السادس")
                    var expandedDropdown by remember { mutableStateOf(false) }

                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { expandedDropdown = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = if (selectedGrade.isBlank()) "اختر الصف الدراسي *" else selectedGrade,
                                color = if (selectedGrade.isBlank()) Color.Gray else AppColors.SeaBlue
                            )
                        }
                        DropdownMenu(
                            expanded = expandedDropdown,
                            onDismissRequest = { expandedDropdown = false },
                            modifier = Modifier.fillMaxWidth(0.9f)
                        ) {
                            classrooms.forEach { classroom ->
                                DropdownMenuItem(
                                    text = { Text(classroom) },
                                    onClick = {
                                        selectedGrade = classroom
                                        expandedDropdown = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // optional session note
                    OutlinedTextField(
                        value = sessionNote,
                        onValueChange = { sessionNote = it },
                        label = { Text("ملاحظة أو اسم الحصة (اختياري)") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = {
                                if (selectedGrade.isBlank()) {
                                    Toast.makeText(context, "الرجاء اختيار الصف أولاً", Toast.LENGTH_SHORT).show()
                                } else {
                                    viewModel.startNewSession(selectedGrade, sessionNote)
                                    showStartSessionDialog = false
                                    navController.navigate("scan")
                                }
                            },
                            modifier = Modifier.weight(1.5f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AppColors.SeaBlue)
                        ) {
                            Text("بدء الكاميرا والمسح")
                        }

                        OutlinedButton(
                            onClick = { showStartSessionDialog = false },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("إلغاء")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MenuLauncherCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    backgroundColor: Color,
    accentColor: Color,
    onClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.Start
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accentColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(24.dp)
                )
            }
            Column {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color.Black
                )
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = Color.Gray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun StudentsScreen(
    navController: NavHostController,
    viewModel: AttendanceViewModel
) {
    val context = LocalContext.current
    val searchQuery by viewModel.searchQuery.collectAsState()
    val studentsList by viewModel.searchedStudents.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }
    var editingStudent by remember { mutableStateOf<Student?>(null) }
    var selectedStudentForQR by remember { mutableStateOf<Student?>(null) }

    // Inputs
    var nameInput by remember { mutableStateOf("") }
    var parentPhoneInput by remember { mutableStateOf("") }
    var selectedGradeInput by remember { mutableStateOf("") }
    var gradeDropdownExpanded by remember { mutableStateOf(false) }

    // Constants
    val classrooms = listOf("الصف الأول", "الصف الثاني", "الصف الثالث", "الصف الرابع", "الصف الخامس", "الصف السادس")

    Scaffold(
        topBar = {
            OptIn(ExperimentalMaterial3Api::class)
            TopAppBar(
                title = { Text("قائمة وإدارة الطلاب", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { DocumentExporter.printQrCodesA4(context, studentsList) }) {
                        Icon(imageVector = Icons.Default.Print, contentDescription = "A4 print all", tint = AppColors.SeaBlue)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    nameInput = ""
                    parentPhoneInput = ""
                    selectedGradeInput = ""
                    editingStudent = null
                    showAddDialog = true
                },
                containerColor = AppColors.SeaBlue,
                contentColor = Color.White
            ) {
                Icon(imageVector = Icons.Filled.PersonAdd, contentDescription = "Add Student")
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color.White)
                .padding(16.dp)
        ) {
            // Search Input Block
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = null) },
                placeholder = { Text("البحث عن طريق اسم الطالب أو الصف...") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AppColors.SeaBlue)
            )

            if (studentsList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Group,
                            contentDescription = null,
                            tint = Color.Gray.copy(alpha = 0.5f),
                            modifier = Modifier.size(72.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "لا يوجد طلاب حالياً",
                            color = Color.Gray,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "اضغط على زر الإضافة (+) لإدراج طالب جديد في النظام.",
                            color = Color.Gray,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(studentsList, key = { it.id }) { student ->
                        StudentCardItem(
                            student = student,
                            onQRClicked = { selectedStudentForQR = student },
                            onEditClicked = {
                                editingStudent = student
                                nameInput = student.fullName
                                parentPhoneInput = student.parentPhone ?: ""
                                selectedGradeInput = student.grade
                                showAddDialog = true
                            },
                            onDeleteClicked = {
                                viewModel.deleteStudent(student)
                                Toast.makeText(context, "تم حذف الطالب بنجاح", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            }
        }
    }

    // Add and Edit Dialog Form
    if (showAddDialog) {
        Dialog(onDismissRequest = { showAddDialog = false }) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (editingStudent == null) "إضافة طالب جديد" else "تعديل بيانات الطالب",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = Color.Black
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        label = { Text("الاسم بالكامل *") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Dropdown for grade selector
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { gradeDropdownExpanded = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = if (selectedGradeInput.isBlank()) "اختر الصف الدراسي *" else selectedGradeInput,
                                color = if (selectedGradeInput.isBlank()) Color.Gray else AppColors.SeaBlue
                            )
                        }
                        DropdownMenu(
                            expanded = gradeDropdownExpanded,
                            onDismissRequest = { gradeDropdownExpanded = false },
                            modifier = Modifier.fillMaxWidth(0.9f)
                        ) {
                            classrooms.forEach { classroom ->
                                DropdownMenuItem(
                                    text = { Text(classroom) },
                                    onClick = {
                                        selectedGradeInput = classroom
                                        gradeDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = parentPhoneInput,
                        onValueChange = { parentPhoneInput = it },
                        label = { Text("رقم ولي الأمر (اختياري)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = {
                                if (nameInput.isBlank() || selectedGradeInput.isBlank()) {
                                    Toast.makeText(context, "الرجاء تعبئة الحقول المطلوبة", Toast.LENGTH_SHORT).show()
                                } else {
                                    if (editingStudent == null) {
                                        viewModel.addStudent(
                                            fullName = nameInput.trim(),
                                            grade = selectedGradeInput,
                                            parentPhone = parentPhoneInput.trim().ifBlank { null }
                                        ) {
                                            Toast.makeText(context, "تم حفظ الطالب والـ QR بنجاح", Toast.LENGTH_SHORT).show()
                                            showAddDialog = false
                                        }
                                    } else {
                                        viewModel.updateStudent(
                                            editingStudent!!.copy(
                                                fullName = nameInput.trim(),
                                                grade = selectedGradeInput,
                                                parentPhone = parentPhoneInput.trim().ifBlank { null }
                                            )
                                        ) {
                                            Toast.makeText(context, "تم تعديل البيانات بنجاح", Toast.LENGTH_SHORT).show()
                                            showAddDialog = false
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.weight(1.5f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AppColors.SeaBlue)
                        ) {
                            Text("حفظ البيانات")
                        }

                        OutlinedButton(
                            onClick = { showAddDialog = false },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("إلغاء")
                        }
                    }
                }
            }
        }
    }

    // QR Code Viewer and Sharing Dialog
    if (selectedStudentForQR != null) {
        val student = selectedStudentForQR!!
        val qrBitmap = remember(student.id) { QRCodeGenerator.generateQRCode(student.id, size = 512) }

        Dialog(onDismissRequest = { selectedStudentForQR = null }) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "رمز الاستجابة السريعة (QR)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    if (qrBitmap != null) {
                        Image(
                            bitmap = qrBitmap.asImageBitmap(),
                            contentDescription = "Student QR Code ID ${student.id}",
                            modifier = Modifier
                                .size(240.dp)
                                .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(8.dp))
                                .padding(12.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = student.fullName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "الرمز التعريفي: ${student.id}",
                        color = AppColors.SeaBlue,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "الصف الدراسي: ${student.grade}",
                        color = Color.Gray,
                        fontSize = 14.sp
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                // PDF Printing layout of the student card directly
                                DocumentExporter.printQrCodesA4(context, listOf(student))
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AppColors.SeaBlue)
                        ) {
                            Icon(imageVector = Icons.Default.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("طباعة الكود")
                        }

                        OutlinedButton(
                            onClick = { selectedStudentForQR = null },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("إغلاق")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StudentCardItem(
    student: Student,
    onQRClicked: () -> Unit,
    onEditClicked: () -> Unit,
    onDeleteClicked: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = AppColors.LightGray),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                // QR Quick Click icon
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White)
                        .clickable(onClick = onQRClicked)
                        .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.QrCode,
                        contentDescription = "Open QR Code",
                        tint = AppColors.SeaBlue,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = student.fullName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${student.id} | ${student.grade}",
                        fontSize = 12.sp,
                        color = AppColors.SeaBlue,
                        fontWeight = FontWeight.Medium
                    )
                    if (!student.parentPhone.isNullOrBlank()) {
                        Text(
                            text = "هاتف ولي الأمر: ${student.parentPhone}",
                            fontSize = 11.sp,
                            color = Color.Gray
                        )
                    }
                }
            }

            // Edit and Delete icons
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = onEditClicked) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit Student",
                        tint = Color(0xFF475569)
                    )
                }
                IconButton(onClick = onDeleteClicked) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete Student",
                        tint = AppColors.FireRed
                    )
                }
            }
        }
    }
}

@Composable
fun ScanScreen(
    navController: NavHostController,
    viewModel: AttendanceViewModel
) {
    val context = LocalContext.current
    val isSessionActive by viewModel.isSessionActive.collectAsState()
    val sessionGrade by viewModel.sessionGrade.collectAsState()
    val sessionNote by viewModel.sessionNote.collectAsState()
    
    val studentsInGrade by viewModel.activeSessionStudents.collectAsState()
    val scannedIds by viewModel.scannedStudentIds.collectAsState()
    
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }

    // Request permissions on view
    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // If session ended or cancelled, go home
    if (!isSessionActive) {
        LaunchedEffect(Unit) {
            navController.navigate("home") {
                popUpTo("home") { inclusive = true }
            }
        }
    }

    Scaffold(
        topBar = {
            OptIn(ExperimentalMaterial3Api::class)
            TopAppBar(
                title = {
                    Column {
                        Text("تسجيل حضور: $sessionGrade", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        if (sessionNote.isNotBlank()) {
                            Text(sessionNote, fontSize = 11.sp, color = Color.Gray)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        }
    ) { paddingValues ->
        if (!hasCameraPermission) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Icon(imageVector = Icons.Default.Camera, contentDescription = null, size = 64.dp, tint = Color.Gray)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "يتطلب الهاتف الحصول على إذن الكاميرا لمسح الرموز المربعة للطلاب.",
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                        Text("منح الإذن الآن")
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(Color.White)
            ) {
                // Camera HUD Section (Takes up 42% height)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1.2f)
                        .background(Color.Black)
                ) {
                    CameraScannerView(
                        onQRCodeScanned = { rawCode ->
                            val result = viewModel.scanStudentQR(context, rawCode)
                            if (result.first) {
                                Toast.makeText(context, result.second, Toast.LENGTH_SHORT).show()
                            } else if (result.second.isNotBlank()) {
                                // show warning error toast
                                Toast.makeText(context, result.second, Toast.LENGTH_LONG).show()
                            }
                        }
                    )
                    
                    // Live Scanning Indicators Overlay Overlay
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.6f)),
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Text(
                                text = "وجه الكاميرا نحو الرمز المربع (QR Code) للطالب",
                                color = Color.White,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                            )
                        }
                    }

                    // Scan HUD progress display
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = AppColors.SeaBlue),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "الحاضرين: ${scannedIds.size} / ${studentsInGrade.size}",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(Color.Green)
                                )
                                Text(
                                    text = "الغائبين: ${studentsInGrade.size - scannedIds.size}",
                                    color = Color.White.copy(alpha = 0.82f)
                                )
                            }
                        }
                    }
                }

                // Student lists check HUD (Below camera preview)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1.5f)
                        .padding(16.dp)
                ) {
                    Text(
                        text = "تفاصيل الطلاب (اضغط للتحضير اليدوي في حال تلف الكود):",
                        fontSize = 13.sp,
                        color = Color.Gray,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    if (studentsInGrade.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "لا يوجد طلاب مسجلين في '$sessionGrade' حالياً لتوثيقهم.",
                                color = Color.DarkGray,
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            items(studentsInGrade) { student ->
                                val isPresent = scannedIds.contains(student.id)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (isPresent) AppColors.PaleGreen else AppColors.LightGray)
                                        .clickable { viewModel.toggleStudentScanned(student.id) }
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .clip(CircleShape)
                                                .background(if (isPresent) AppColors.FreshGreen else Color.Gray)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text(
                                                text = student.fullName,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isPresent) AppColors.FreshGreen else Color.Black
                                            )
                                            Text(student.id, fontSize = 11.sp, color = Color.Gray)
                                        }
                                    }
                                    
                                    // Status Badge Check label
                                    if (isPresent) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = "Present check symbol",
                                            tint = AppColors.FreshGreen
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.Cancel,
                                            contentDescription = "Absent check symbol",
                                            tint = Color.Gray
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Action controllers - Save list or Cancel session
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = {
                                viewModel.endAndSaveSession {
                                    Toast.makeText(context, "تم حفظ سجل الحصة بنجاح!", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.weight(1.5f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AppColors.FreshGreen)
                        ) {
                            Text("تثبيت الحضور وإغلاق الجلسة")
                        }

                        OutlinedButton(
                            onClick = {
                                viewModel.cancelActiveSession()
                                Toast.makeText(context, "تم إلغاء الحصة النشطة", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.FireRed)
                        ) {
                            Text("إلغاء تماماً")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ReportsScreen(
    navController: NavHostController,
    viewModel: AttendanceViewModel
) {
    val context = LocalContext.current
    val statsList by viewModel.studentStatsList.collectAsState()
    val sessionList by viewModel.allSessions.collectAsState()

    var activeTab by remember { mutableIntStateOf(0) } // 0: general stats, 1: list sessions
    var selectedSessionForReview by remember { mutableStateOf<AttendanceSession?>(null) }
    var sessionRecordsList by remember { mutableStateOf<List<com.example.data.AttendanceRecord>>(emptyList()) }
    var allStudentsMap by remember { mutableStateOf<Map<String, Student>>(emptyMap()) }

    // Fetch lists helper
    val rawStudents by viewModel.allStudents.collectAsState()
    
    // Bind students mapping for session overlays
    LaunchedEffect(rawStudents) {
        allStudentsMap = rawStudents.associateBy { it.id }
    }

    LaunchedEffect(selectedSessionForReview) {
        val session = selectedSessionForReview
        if (session != null) {
            sessionRecordsList = viewModel.repository.getRecordsForSession(session.id)
        }
    }

    Scaffold(
        topBar = {
            OptIn(ExperimentalMaterial3Api::class)
            TopAppBar(
                title = { Text("التقارير وسجل الحصص", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (activeTab == 0 && statsList.isNotEmpty()) {
                        IconButton(onClick = { DocumentExporter.exportReportsToPdf(context, statsList) }) {
                            Icon(imageVector = Icons.Default.PictureAsPdf, contentDescription = "PDF Report", tint = AppColors.FireRed)
                        }
                        IconButton(onClick = { DocumentExporter.exportReportsToExcel(context, statsList) }) {
                            Icon(imageVector = Icons.Default.TableChart, contentDescription = "Excel Report", tint = AppColors.FreshGreen)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color.White)
        ) {
            // Tab Switch Block
            TabRow(selectedTabIndex = activeTab, contentColor = AppColors.SeaBlue) {
                Tab(
                    selected = activeTab == 0,
                    onClick = { activeTab = 0 },
                    text = { Text("إحصائيات الطلاب الكلية", fontWeight = FontWeight.Bold) }
                )
                Tab(
                    selected = activeTab == 1,
                    onClick = { activeTab = 1 },
                    text = { Text("سجل الحصص السابقة", fontWeight = FontWeight.Bold) }
                )
            }

            Column(modifier = Modifier.padding(16.dp)) {
                if (activeTab == 0) {
                    // TAB 0: Student Statistics
                    if (statsList.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("لا يتوفر تقارير حضور وغياب حالياً. قم ببدء حصة جديدة.", color = Color.Gray, textAlign = TextAlign.Center)
                        }
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(statsList) { item ->
                                StudentStatsCardItem(item = item)
                            }
                        }
                    }
                } else {
                    // TAB 1: Previous Sessions Table
                    if (sessionList.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("لم يتم إنهاء أو تسجيل أي حصة سابقة حالياً.", color = Color.Gray, textAlign = TextAlign.Center)
                        }
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(sessionList) { session ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(AppColors.LightGray)
                                        .clickable { selectedSessionForReview = session }
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text(
                                            text = session.grade,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp
                                        )
                                        if (!session.note.isNullOrBlank()) {
                                            Text(session.note, fontSize = 12.sp, color = Color.DarkGray)
                                        }
                                        Text(
                                            text = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(session.date)),
                                            fontSize = 11.sp,
                                            color = Color.Gray
                                        )
                                    }
                                    
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.List,
                                            contentDescription = "Review details symbol",
                                            tint = AppColors.SeaBlue
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        IconButton(onClick = { viewModel.deleteSession(session.id) }) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Delete Session",
                                                tint = AppColors.FireRed
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Historical Session Review Overlay Dialog
    if (selectedSessionForReview != null) {
        val session = selectedSessionForReview!!
        Dialog(onDismissRequest = { selectedSessionForReview = null }) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.82f)
                    .padding(8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp)
                ) {
                    Text(
                        text = "تفاصيل الحصة التاريخية",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "الصف الدراسي: ${session.grade}",
                        fontWeight = FontWeight.Bold,
                        color = AppColors.SeaBlue
                    )
                    if (!session.note.isNullOrBlank()) {
                        Text(text = "الملاحظة: ${session.note}", fontSize = 13.sp, color = Color.DarkGray)
                    }
                    Text(
                        text = "التاريخ: ${SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(session.date))}",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "كشف حضور الطلاب في هذه الحصة:",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    if (sessionRecordsList.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("لا تتوفر تفاصيل كشف أو تم إلغاء كشف الحصة.", color = Color.Gray)
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            items(sessionRecordsList) { record ->
                                val student = allStudentsMap[record.studentId]
                                val studentName = student?.fullName ?: "طالب مجهول أو محذوف"
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (record.isPresent) AppColors.PaleGreen else Color(0xFFFFEBEE))
                                        .padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = studentName,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = if (record.isPresent) AppColors.FreshGreen else AppColors.FireRed
                                    )
                                    Text(
                                        text = if (record.isPresent) "حاضر" else "غائب",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = if (record.isPresent) AppColors.FreshGreen else AppColors.FireRed
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { selectedSessionForReview = null },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AppColors.SeaBlue)
                    ) {
                        Text("إغلاق الكشف")
                    }
                }
            }
        }
    }
}

@Composable
fun StudentStatsCardItem(item: StudentWithStats) {
    Card(
        colors = CardDefaults.cardColors(containerColor = AppColors.LightGray),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = item.student.fullName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Text(
                        text = "المعرف: ${item.student.id} | الصف: ${item.student.grade}",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }

                // Dial attendance percentage
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            if (item.attendanceRate >= 75f) AppColors.PaleGreen
                            else Color(0xFFFFEBEE)
                        )
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "${String.format("%.1f", item.attendanceRate)}%",
                        color = if (item.attendanceRate >= 75f) AppColors.FreshGreen else AppColors.FireRed,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = Color.LightGray)
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("حضور", fontSize = 11.sp, color = Color.Gray)
                        Text(
                            text = "${item.presentCount}",
                            fontWeight = FontWeight.Bold,
                            color = AppColors.FreshGreen
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("غياب", fontSize = 11.sp, color = Color.Gray)
                        Text(
                            text = "${item.absentCount}",
                            fontWeight = FontWeight.Bold,
                            color = AppColors.FireRed
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    if (item.lastPresent != null) {
                        val formatted = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(item.lastPresent))
                        Text(text = "آخر حضور: $formatted", fontSize = 11.sp, color = Color.Gray)
                    } else {
                        Text(text = "آخر حضور: -", fontSize = 11.sp, color = Color.Gray)
                    }
                }
            }
        }
    }
}
