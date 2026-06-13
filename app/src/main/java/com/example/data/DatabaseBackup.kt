package com.example.data

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class DatabaseBackup(
    val students: List<Student>,
    val sessions: List<AttendanceSession>,
    val records: List<AttendanceRecord>
)

data class StudentWithStats(
    val student: Student,
    val presentCount: Int,
    val absentCount: Int,
    val lastPresent: Long?,
    val lastAbsent: Long?
) {
    val attendanceRate: Float
        get() = if (presentCount + absentCount == 0) 100f else (presentCount.toFloat() / (presentCount + absentCount)) * 100f
}
