package com.example.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.MainActivity
import com.example.R
import com.example.data.model.InventoryItem

object NotificationHelper {
    const val CHANNEL_ID_LOW_STOCK = "inventory_low_stock"
    private const val CHANNEL_NAME = "Low Stock Alerts"
    private const val CHANNEL_DESC = "Notifications when warehouse inventory falls below minimum threshold"

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID_LOW_STOCK, CHANNEL_NAME, importance).apply {
                description = CHANNEL_DESC
                enableLights(true)
                enableVibration(true)
            }
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun sendLowStockNotification(context: Context, item: InventoryItem) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("EXTRA_ITEM_ID", item.id)
            putExtra("EXTRA_NAVIGATE_TO", "inventory")
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            item.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID_LOW_STOCK)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("⚠️ Low Stock Alert: ${item.commonName}")
            .setContentText("Remaining: ${item.quantity} ${item.unit} (Min: ${item.minThreshold}) at ${item.location}")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "Stock item '${item.commonName}' (Code: ${item.newCode}, Old: ${item.oldCode}) has fallen below minimum threshold!\n" +
                    "Current Stock: ${item.quantity} ${item.unit}\n" +
                    "Location: ${item.location}\n" +
                    "Please initiate a restock purchase order."
                )
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        try {
            val notificationManager = NotificationManagerCompat.from(context)
            notificationManager.notify(item.id.toInt() + 1000, builder.build())
        } catch (_: SecurityException) {
            // Permission not granted or notification disabled
        }
    }
}
