package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "students")
data class Student(
    @PrimaryKey val id: String, // STD_0001, STD_0002 etc.
    val fullName: String,
    val grade: String,
    val parentPhone: String?,
    val createdAt: Long = System.currentTimeMillis()
)
