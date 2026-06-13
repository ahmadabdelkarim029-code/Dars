package com.example.data

import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class AttendanceRepository(private val dao: AttendanceDao) {

    val allStudents: Flow<List<Student>> = dao.getAllStudents()
    val allSessions: Flow<List<AttendanceSession>> = dao.getAllSessions()
    val allRecords: Flow<List<AttendanceRecord>> = dao.getAllRecords()

    fun searchStudents(query: String): Flow<List<Student>> =
        dao.searchStudents("%$query%")

    suspend fun getStudentById(id: String): Student? = dao.getStudentById(id)

    suspend fun getNextStudentId(): String {
        val maxId = dao.getMaxStudentId() ?: "STD_0000"
        val nextNum = try {
            maxId.replace("STD_", "").toInt() + 1
        } catch (e: Exception) {
            1
        }
        return "STD_${String.format("%04d", nextNum)}"
    }

    suspend fun insertStudent(student: Student) = dao.insertStudent(student)
    suspend fun updateStudent(student: Student) = dao.updateStudent(student)
    suspend fun deleteStudent(student: Student) = dao.deleteStudent(student)

    suspend fun saveSessionWithRecords(session: AttendanceSession, records: List<AttendanceRecord>) {
        val sessionId = dao.insertSession(session).toInt()
        val recordsWithSessionId = records.map { it.copy(sessionId = sessionId) }
        dao.insertRecords(recordsWithSessionId)
    }

    suspend fun deleteSession(sessionId: Int) {
        dao.deleteSessionById(sessionId)
        dao.deleteRecordsForSession(sessionId)
    }

    suspend fun getRecordsForSession(sessionId: Int): List<AttendanceRecord> =
        dao.getRecordsForSession(sessionId)

    fun getStudentWithStatsList(): Flow<List<StudentWithStats>> {
        return combine(allStudents, allRecords) { students, records ->
            students.map { student ->
                val studRecords = records.filter { it.studentId == student.id }
                val present = studRecords.count { it.isPresent }
                val absent = studRecords.count { !it.isPresent }
                val lastPresent = studRecords.filter { it.isPresent }.maxOfOrNull { it.timestamp }
                val lastAbsent = studRecords.filter { !it.isPresent }.maxOfOrNull { it.timestamp }
                StudentWithStats(
                    student = student,
                    presentCount = present,
                    absentCount = absent,
                    lastPresent = lastPresent,
                    lastAbsent = lastAbsent
                )
            }
        }
    }

    suspend fun getBackupData(): DatabaseBackup {
        val students = allStudents.first()
        val sessions = allSessions.first()
        val records = allRecords.first()
        return DatabaseBackup(students, sessions, records)
    }

    suspend fun restoreBackup(backup: DatabaseBackup) {
        for (student in backup.students) {
            dao.insertStudent(student)
        }
        for (session in backup.sessions) {
            dao.insertSession(session)
        }
        dao.insertRecords(backup.records)
    }
}
