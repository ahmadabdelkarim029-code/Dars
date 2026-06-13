package com.example.ui

import android.app.Application
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class AttendanceViewModel(application: Application) : AndroidViewModel(application) {
    
    private val database = AppDatabase.getDatabase(application)
    val repository = AttendanceRepository(database.attendanceDao())

    val allStudents: StateFlow<List<Student>> = repository.allStudents
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allSessions: StateFlow<List<AttendanceSession>> = repository.allSessions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allRecords: StateFlow<List<AttendanceRecord>> = repository.allRecords
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val studentStatsList: StateFlow<List<StudentWithStats>> = repository.getStudentWithStatsList()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val searchedStudents: StateFlow<List<Student>> = _searchQuery
        .flatMapLatest { query ->
            if (query.isBlank()) {
                repository.allStudents
            } else {
                repository.searchStudents(query)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isSessionActive = MutableStateFlow(false)
    val isSessionActive = _isSessionActive.asStateFlow()

    private val _sessionGrade = MutableStateFlow("")
    val sessionGrade = _sessionGrade.asStateFlow()

    private val _sessionNote = MutableStateFlow("")
    val sessionNote = _sessionNote.asStateFlow()

    private val _scannedStudentIds = MutableStateFlow<Set<String>>(emptySet())
    val scannedStudentIds = _scannedStudentIds.asStateFlow()

    val activeSessionStudents: StateFlow<List<Student>> = combine(allStudents, _sessionGrade) { students, grade ->
        if (grade.isBlank()) emptyList()
        else students.filter { it.grade.equals(grade, ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun addStudent(fullName: String, grade: String, parentPhone: String?, onSuccess: () -> Unit) {
        viewModelScope.launch {
            val id = repository.getNextStudentId()
            val student = Student(id = id, fullName = fullName, grade = grade, parentPhone = parentPhone)
            repository.insertStudent(student)
            onSuccess()
        }
    }

    fun updateStudent(student: Student, onSuccess: () -> Unit) {
        viewModelScope.launch {
            repository.updateStudent(student)
            onSuccess()
        }
    }

    fun deleteStudent(student: Student) {
        viewModelScope.launch {
            repository.deleteStudent(student)
        }
    }

    fun startNewSession(grade: String, note: String) {
        _sessionGrade.value = grade
        _sessionNote.value = note
        _scannedStudentIds.value = emptySet()
        _isSessionActive.value = true
    }

    fun cancelActiveSession() {
        _isSessionActive.value = false
        _sessionGrade.value = ""
        _sessionNote.value = ""
        _scannedStudentIds.value = emptySet()
    }

    fun scanStudentQR(context: Context, qrCode: String): Pair<Boolean, String> {
        val trimmedCode = qrCode.trim()
        val student = activeSessionStudents.value.find { it.id == trimmedCode }
        if (student == null) {
            val anyStudent = allStudents.value.find { it.id == trimmedCode }
            return if (anyStudent != null) {
                Pair(false, "الطالب '${anyStudent.fullName}' مسجل في صف آخر (${anyStudent.grade}) وليس في ${sessionGrade.value}")
            } else {
                Pair(false, "رمز QR غير معروف ($trimmedCode)!")
            }
        }

        if (_scannedStudentIds.value.contains(student.id)) {
            return Pair(false, "الطالب '${student.fullName}' مسجل كحاضر بالفعل!")
        }

        val newScanned = _scannedStudentIds.value.toMutableSet()
        newScanned.add(student.id)
        _scannedStudentIds.value = newScanned

        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                manager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(150, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(150)
            }
        } catch (e: Exception) {
            // Squelch permission or hardware mismatch errors silently
        }

        return Pair(true, "تم تسجيل حضور '${student.fullName}' بنجاح")
    }

    fun toggleStudentScanned(studentId: String) {
        val currentScanned = _scannedStudentIds.value.toMutableSet()
        if (currentScanned.contains(studentId)) {
            currentScanned.remove(studentId)
        } else {
            currentScanned.add(studentId)
        }
        _scannedStudentIds.value = currentScanned
    }

    fun endAndSaveSession(onSuccess: () -> Unit) {
        val grade = _sessionGrade.value
        val note = _sessionNote.value
        val scannedIds = _scannedStudentIds.value
        val listStudents = activeSessionStudents.value

        if (listStudents.isEmpty()) {
            _isSessionActive.value = false
            onSuccess()
            return
        }

        viewModelScope.launch {
            val session = AttendanceSession(grade = grade, note = note)
            val records = listStudents.map { student ->
                AttendanceRecord(
                    sessionId = 0, 
                    studentId = student.id,
                    isPresent = scannedIds.contains(student.id)
                )
            }
            repository.saveSessionWithRecords(session, records)
            _isSessionActive.value = false
            _sessionGrade.value = ""
            _sessionNote.value = ""
            _scannedStudentIds.value = emptySet()
            onSuccess()
        }
    }

    fun deleteSession(sessionId: Int) {
        viewModelScope.launch {
            repository.deleteSession(sessionId)
        }
    }
}
