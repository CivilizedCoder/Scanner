package com.example.util

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.data.model.InventoryItem
import com.example.data.model.StockLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ExportHelper {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    private val fileDateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())

    suspend fun exportInventoryToCsv(
        context: Context,
        items: List<InventoryItem>,
        logs: List<StockLog>? = null
    ): Uri? = withContext(Dispatchers.IO) {
        try {
            val fileName = "Warehouse_Inventory_${fileDateFormat.format(Date())}.csv"
            val file = File(context.cacheDir, fileName)
            val outputStream = FileOutputStream(file)

            val writer = outputStream.bufferedWriter()
            // CSV Header
            writer.write("Item ID,Common Name,Old Code,New Code (Barcode),Location,Quantity,Unit,Min Threshold,Category,Low Stock Alert,Last Updated\n")

            for (item in items) {
                val escapedName = escapeCsv(item.commonName)
                val escapedOldCode = escapeCsv(item.oldCode)
                val escapedNewCode = escapeCsv(item.newCode)
                val escapedLocation = escapeCsv(item.location)
                val isLow = if (item.isLowStock) "YES (LOW STOCK)" else "OK"
                val dateStr = dateFormat.format(Date(item.lastUpdated))

                writer.write(
                    "${item.id},\"$escapedName\",\"$escapedOldCode\",\"$escapedNewCode\",\"$escapedLocation\"," +
                    "${item.quantity},${item.unit},${item.minThreshold},\"${item.category}\",$isLow,\"$dateStr\"\n"
                )
            }

            if (!logs.isNullOrEmpty()) {
                writer.write("\n\n--- STOCK AUDIT & PULL TRANSACTION LOGS ---\n")
                writer.write("Log ID,Item Name,Barcode,Action,Qty Change,Resulting Qty,Reference,Timestamp\n")
                for (log in logs) {
                    val logDate = dateFormat.format(Date(log.timestamp))
                    writer.write(
                        "${log.logId},\"${escapeCsv(log.itemName)}\",\"${escapeCsv(log.barcode)}\"," +
                        "${log.actionType},${log.quantityChanged},${log.resultingQuantity},\"${log.referenceOrder ?: ""}\",\"$logDate\"\n"
                    )
                }
            }

            writer.flush()
            writer.close()

            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun exportInventoryToPdf(
        context: Context,
        items: List<InventoryItem>,
        reportTitle: String = "Warehouse Inventory & Stock Report"
    ): Uri? = withContext(Dispatchers.IO) {
        try {
            val doc = PdfDocument()
            val pageWidth = 595 // A4 standard point width
            val pageHeight = 842 // A4 standard point height
            val margin = 36f
            val contentWidth = pageWidth - (margin * 2)

            val titlePaint = Paint().apply {
                color = Color.rgb(15, 23, 42)
                textSize = 16f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                isAntiAlias = true
            }

            val subTitlePaint = Paint().apply {
                color = Color.rgb(71, 85, 105)
                textSize = 9f
                isAntiAlias = true
            }

            val tableHeaderPaint = Paint().apply {
                color = Color.WHITE
                textSize = 8.5f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                isAntiAlias = true
            }

            val cellPaint = Paint().apply {
                color = Color.rgb(30, 41, 59)
                textSize = 8f
                isAntiAlias = true
            }

            val lowStockCellPaint = Paint().apply {
                color = Color.rgb(220, 38, 38)
                textSize = 8f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                isAntiAlias = true
            }

            val bgHeaderPaint = Paint().apply {
                color = Color.rgb(30, 64, 175) // Industrial Blue
                style = Paint.Style.FILL
            }

            val bgAltRowPaint = Paint().apply {
                color = Color.rgb(241, 245, 249)
                style = Paint.Style.FILL
            }

            val bgLowStockRowPaint = Paint().apply {
                color = Color.rgb(254, 242, 242)
                style = Paint.Style.FILL
            }

            val linePaint = Paint().apply {
                color = Color.rgb(226, 232, 240)
                strokeWidth = 0.5f
            }

            var pageNumber = 1
            var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
            var page = doc.startPage(pageInfo)
            var canvas = page.canvas

            var y = margin + 20f

            // Helper to draw Header
            fun drawPageHeader() {
                canvas.drawText(reportTitle, margin, y, titlePaint)
                y += 14f
                val dateStr = "Generated: ${dateFormat.format(Date())}  |  Total SKUs: ${items.size}  |  Low Stock Alerts: ${items.count { it.isLowStock }}"
                canvas.drawText(dateStr, margin, y, subTitlePaint)
                y += 18f

                // Table column header box
                val headerHeight = 20f
                canvas.drawRect(margin, y, margin + contentWidth, y + headerHeight, bgHeaderPaint)

                // Columns: [Item / Common Name, Old Code, Barcode (New Code), Location, Qty, Status]
                val textY = y + 13f
                canvas.drawText("Common Name", margin + 6f, textY, tableHeaderPaint)
                canvas.drawText("Old Code", margin + 140f, textY, tableHeaderPaint)
                canvas.drawText("Barcode (New)", margin + 210f, textY, tableHeaderPaint)
                canvas.drawText("Warehouse Location", margin + 295f, textY, tableHeaderPaint)
                canvas.drawText("Qty", margin + 440f, textY, tableHeaderPaint)
                canvas.drawText("Status", margin + 475f, textY, tableHeaderPaint)

                y += headerHeight + 2f
            }

            drawPageHeader()

            val rowHeight = 22f

            items.forEachIndexed { index, item ->
                if (y + rowHeight > pageHeight - margin - 30f) {
                    // Page footer
                    val footerText = "Page $pageNumber  •  Confidential Warehouse Inventory Report"
                    canvas.drawText(footerText, margin, pageHeight - margin, subTitlePaint)

                    doc.finishPage(page)
                    pageNumber++
                    pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                    page = doc.startPage(pageInfo)
                    canvas = page.canvas
                    y = margin + 20f
                    drawPageHeader()
                }

                // Row background
                if (item.isLowStock) {
                    canvas.drawRect(margin, y, margin + contentWidth, y + rowHeight, bgLowStockRowPaint)
                } else if (index % 2 == 1) {
                    canvas.drawRect(margin, y, margin + contentWidth, y + rowHeight, bgAltRowPaint)
                }

                val rowTextY = y + 14f
                val truncatedName = if (item.commonName.length > 24) item.commonName.take(22) + ".." else item.commonName
                val truncatedLoc = if (item.location.length > 28) item.location.take(26) + ".." else item.location

                canvas.drawText(truncatedName, margin + 6f, rowTextY, cellPaint)
                canvas.drawText(item.oldCode, margin + 140f, rowTextY, cellPaint)
                canvas.drawText(item.newCode, margin + 210f, rowTextY, cellPaint)
                canvas.drawText(truncatedLoc, margin + 295f, rowTextY, cellPaint)

                val qtyPaint = if (item.isLowStock) lowStockCellPaint else cellPaint
                canvas.drawText("${item.quantity} ${item.unit}", margin + 440f, rowTextY, qtyPaint)

                val statusText = if (item.isLowStock) "LOW (${item.quantity}/${item.minThreshold})" else "OK"
                val statusPaint = if (item.isLowStock) lowStockCellPaint else cellPaint
                canvas.drawText(statusText, margin + 475f, rowTextY, statusPaint)

                // Row bottom separator line
                canvas.drawLine(margin, y + rowHeight, margin + contentWidth, y + rowHeight, linePaint)
                y += rowHeight
            }

            // Draw last page footer
            val footerText = "Page $pageNumber  •  Confidential Warehouse Inventory Report"
            canvas.drawText(footerText, margin, pageHeight - margin, subTitlePaint)

            doc.finishPage(page)

            val fileName = "Warehouse_Report_${fileDateFormat.format(Date())}.pdf"
            val file = File(context.cacheDir, fileName)
            val outputStream = FileOutputStream(file)
            doc.writeTo(outputStream)
            outputStream.flush()
            outputStream.close()
            doc.close()

            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun shareExportedFile(context: Context, uri: Uri, mimeType: String, subject: String) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, "Attached is the latest warehouse inventory report.")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Export Report via"))
    }

    private fun escapeCsv(value: String): String {
        return value.replace("\"", "\"\"")
    }
}
