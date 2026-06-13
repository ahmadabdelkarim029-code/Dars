package com.example.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.data.AttendanceRepository
import com.example.data.DatabaseBackup
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object BackupHelper {

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()
    private val adapter = moshi.adapter(DatabaseBackup::class.java)

    fun exportBackup(context: Context, repository: AttendanceRepository, onComplete: () -> Unit) {
        try {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val backup = repository.getBackupData()
                    val json = adapter.toJson(backup)
                    
                    val fileName = "حضور_الطلاب_نسخة_احتياطية_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.json"
                    val file = File(context.cacheDir, fileName)
                    val outputStream = FileOutputStream(file)
                    outputStream.write(json.toByteArray(Charsets.UTF_8))
                    outputStream.close()
                    
                    withContext(Dispatchers.Main) {
                        val uri: Uri = FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            file
                        )
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "application/json"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(intent, "تصدير النسخة الاحتياطية"))
                        onComplete()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "فشل تصدير النسخة الاحتياطية: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "خطأ: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    fun importBackup(context: Context, uri: Uri, repository: AttendanceRepository, onComplete: () -> Unit) {
        try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            val json = inputStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
            if (json.isNullOrEmpty()) {
                Toast.makeText(context, "الملف فارغ أو غير صالح", Toast.LENGTH_SHORT).show()
                return
            }
            val backup = adapter.fromJson(json)
            if (backup == null) {
                Toast.makeText(context, "صيغة ملف النسخة الاحتياطية غير صالحة", Toast.LENGTH_SHORT).show()
                return
            }
            
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    repository.restoreBackup(backup)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "تم استعادة البيانات بنجاح!", Toast.LENGTH_SHORT).show()
                        onComplete()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "فشل في حفظ البيانات المستعادة: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "خطأ في استيراد النسخة الاحتياطية: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }
}
