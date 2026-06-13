package com.example.utils

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.data.StudentWithStats
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DocumentExporter {

    private fun shareFile(context: Context, file: File, mimeType: String, title: String) {
        try {
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, title))
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "فشل في مشاركة الملف: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    fun exportReportsToExcel(context: Context, stats: List<StudentWithStats>) {
        try {
            val fileName = "تقرير_حضور_الطلاب_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.csv"
            val file = File(context.cacheDir, fileName)
            val outputStream = FileOutputStream(file)
            
            // UTF-8 BOM to make MS Excel open and load Arabic names correctly without encoding glitches
            outputStream.write(0xEF)
            outputStream.write(0xBB)
            outputStream.write(0xBF)
            
            val writer = outputStream.bufferedWriter(Charsets.UTF_8)
            
            // Header
            writer.write("الرمز التعريفي,اسم الطالب,الصف الدراسي,عدد مرات الحضور,عدد مرات الغياب,نسبة الحضور %\n")
            
            for (item in stats) {
                val line = "${item.student.id},${item.student.fullName},${item.student.grade},${item.presentCount},${item.absentCount},${String.format("%.1f", item.attendanceRate)}\n"
                writer.write(line)
            }
            
            writer.flush()
            writer.close()
            outputStream.close()
            
            shareFile(context, file, "text/csv", "مشاركة تقرير CSV/Excel")
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "خطأ في تصدير البيانات: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    fun exportReportsToPdf(context: Context, stats: List<StudentWithStats>) {
        try {
            val pdfDocument = PdfDocument()
            val paint = Paint()
            val boldPaint = Paint().apply { isFakeBoldText = true }
            
            val pageWidth = 595
            val pageHeight = 842
            
            var pageNumber = 1
            var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
            var page = pdfDocument.startPage(pageInfo)
            var canvas = page.canvas
            
            paint.color = Color.BLACK
            paint.textSize = 20f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("تقرير حضور وغياب الطلاب", (pageWidth / 2).toFloat(), 60f, paint)
            
            paint.textSize = 10f
            paint.color = Color.GRAY
            val currentDate = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())
            canvas.drawText("تم التصدير في: $currentDate", (pageWidth / 2).toFloat(), 80f, paint)
            
            val startY = 120f
            val columnWidths = intArrayOf(80, 180, 80, 80, 80, 80)
            val xPositions = intArrayOf(20, 100, 280, 360, 440, 520)
            val headers = arrayOf("المعرف", "الاسم بالكامل", "الصف", "حاضر", "غائب", "النسبة")
            
            paint.color = Color.rgb(230, 230, 230)
            canvas.drawRect(20f, startY - 15f, 575f, startY + 15f, paint)
            
            boldPaint.textSize = 10f
            boldPaint.color = Color.BLACK
            boldPaint.textAlign = Paint.Align.CENTER
            
            for (i in headers.indices) {
                val colCenterX = xPositions[i] + (columnWidths[i] / 2f)
                canvas.drawText(headers[i], colCenterX, startY + 4f, boldPaint)
            }
            
            var currentY = startY + 30f
            paint.textSize = 10f
            paint.textAlign = Paint.Align.CENTER
            paint.color = Color.BLACK
            
            for (item in stats) {
                if (currentY > pageHeight - 50f) {
                    pdfDocument.finishPage(page)
                    pageNumber++
                    pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                    page = pdfDocument.startPage(pageInfo)
                    canvas = page.canvas
                    currentY = 60f
                }
                
                paint.color = Color.rgb(240, 240, 240)
                canvas.drawLine(20f, currentY + 5f, 575f, currentY + 5f, paint)
                
                paint.color = Color.BLACK
                val values = arrayOf(
                    item.student.id,
                    item.student.fullName,
                    item.student.grade,
                    item.presentCount.toString(),
                    item.absentCount.toString(),
                    "${String.format("%.1f", item.attendanceRate)}%"
                )
                
                for (i in values.indices) {
                    val colCenterX = xPositions[i] + (columnWidths[i] / 2f)
                    canvas.drawText(values[i], colCenterX, currentY, paint)
                }
                
                currentY += 25f
            }
            
            pdfDocument.finishPage(page)
            
            val fileName = "تقرير_الحضور_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.pdf"
            val file = File(context.cacheDir, fileName)
            pdfDocument.writeTo(FileOutputStream(file))
            pdfDocument.close()
            
            shareFile(context, file, "application/pdf", "مشاركة تقرير PDF")
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "خطأ في توليد PDF: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    fun printQrCodesA4(context: Context, students: List<com.example.data.Student>) {
        if (students.isEmpty()) {
            Toast.makeText(context, "لا يوجد طلاب لطباعة أكوادهم", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val pdfDocument = PdfDocument()
            val paint = Paint()
            val boldPaint = Paint().apply { isFakeBoldText = true }
            
            val pageWidth = 595
            val pageHeight = 842
            
            val columns = 3
            val rows = 4
            val cardWidth = 175
            val cardHeight = 175
            val gapX = 15
            val gapY = 20
            val startX = 20
            val startY = 70
            
            var studentIndex = 0
            var pageNumber = 1
            
            while (studentIndex < students.size) {
                val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                val page = pdfDocument.startPage(pageInfo)
                val canvas = page.canvas
                
                paint.color = Color.BLACK
                paint.textSize = 16f
                paint.textAlign = Paint.Align.CENTER
                canvas.drawText("بطاقات رموز الاستجابة السريعة للطلاب (QR Codes)", (pageWidth / 2).toFloat(), 35f, paint)
                paint.textSize = 9f
                paint.color = Color.GRAY
                canvas.drawText("الصفحة $pageNumber", (pageWidth / 2).toFloat(), 50f, paint)
                paint.color = Color.BLACK
                
                for (r in 0 until rows) {
                    for (c in 0 until columns) {
                        if (studentIndex >= students.size) break
                        
                        val stud = students[studentIndex]
                        
                        val x = startX + c * (cardWidth + gapX)
                        val y = startY + r * (cardHeight + gapY)
                        
                        paint.style = Paint.Style.STROKE
                        paint.color = Color.rgb(200, 200, 200)
                        paint.strokeWidth = 1f
                        canvas.drawRect(x.toFloat(), y.toFloat(), (x + cardWidth).toFloat(), (y + cardHeight).toFloat(), paint)
                        
                        paint.style = Paint.Style.FILL
                        paint.color = Color.BLACK
                        boldPaint.textSize = 9f
                        boldPaint.textAlign = Paint.Align.CENTER
                        
                        val displayName = if (stud.fullName.length > 20) stud.fullName.substring(0, 18) + ".." else stud.fullName
                        canvas.drawText(displayName, (x + cardWidth / 2).toFloat(), (y + 140).toFloat(), boldPaint)
                        
                        paint.textSize = 8f
                        paint.textAlign = Paint.Align.CENTER
                        canvas.drawText("${stud.id} | ${stud.grade}", (x + cardWidth / 2).toFloat(), (y + 158).toFloat(), paint)
                        
                        val qrBitmap = QRCodeGenerator.generateQRCode(stud.id, size = 150)
                        if (qrBitmap != null) {
                            val scaledQr = Bitmap.createScaledBitmap(qrBitmap, 110, 110, true)
                            canvas.drawBitmap(scaledQr, (x + (cardWidth - 110) / 2).toFloat(), (y + 15).toFloat(), paint)
                        }
                        
                        studentIndex++
                    }
                }
                
                pdfDocument.finishPage(page)
                pageNumber++
            }
            
            val fileName = "رموز_الطلاب_A4_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.pdf"
            val file = File(context.cacheDir, fileName)
            pdfDocument.writeTo(FileOutputStream(file))
            pdfDocument.close()
            
            shareFile(context, file, "application/pdf", "طباعة ومشاركة أكواد QR")
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "حدث خطأ في توليد وثيقة QR: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }
}
