package uk.hozdogus.grocerytracker.notify

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.TaskStackBuilder
import androidx.core.content.ContextCompat
import uk.hozdogus.grocerytracker.ItemDetailActivity
import uk.hozdogus.grocerytracker.MainActivity
import uk.hozdogus.grocerytracker.R
import uk.hozdogus.grocerytracker.data.CheckOutcome
import uk.hozdogus.grocerytracker.ui.formatPence
import uk.hozdogus.grocerytracker.ui.shortName

/** Posts price-drop and target notifications: one per item, or a single summary when several change. */
class Notifier(context: Context) {

    private val context = context.applicationContext

    /** One line of news about an item, used for both single and summary notifications. */
    data class News(val itemId: Long, val title: String, val text: String)

    @SuppressLint("MissingPermission") // Checked in canNotify().
    fun notifyChanges(outcomes: List<CheckOutcome>) {
        val news = outcomes.mapNotNull(::describe)
        if (news.isEmpty() || !canNotify()) return
        ensureChannel()
        val manager = NotificationManagerCompat.from(context)

        if (news.size == 1) {
            val n = news.single()
            manager.notify(
                n.itemId.toInt(),
                base(n.title, n.text)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(n.text))
                    .setContentIntent(openItem(n.itemId))
                    .build(),
            )
        } else {
            val title = "${news.size} price changes worth a look"
            val style = NotificationCompat.InboxStyle().setBigContentTitle(title)
            news.forEach { style.addLine(it.title) }
            manager.notify(
                SUMMARY_ID,
                base(title, news.joinToString(" · ") { it.title })
                    .setStyle(style)
                    .setContentIntent(openList())
                    .build(),
            )
        }
    }

    private fun describe(o: CheckOutcome): News? {
        val item = o.item
        val now = item.currentPrice ?: return null
        val name = shortName(item.name)
        val shop = item.shop.label
        return when {
            o.hitTarget -> News(
                item.id,
                "🎯 $name hit your target: ${formatPence(now)} at $shop",
                "Target ${formatPence(item.targetPrice)}, was ${formatPence(o.before.currentPrice)}.",
            )
            o.dropped -> News(
                item.id,
                "📉 $name: ${formatPence(o.before.currentPrice)} → ${formatPence(now)} at $shop",
                if (now <= (o.before.lowestPrice ?: now)) "Lowest price seen so far."
                else "Lowest seen ${formatPence(item.lowestPrice)}.",
            )
            else -> null
        }
    }

    private fun base(title: String, text: String) =
        NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

    private fun openItem(id: Long): PendingIntent =
        TaskStackBuilder.create(context)
            .addNextIntent(Intent(context, MainActivity::class.java))
            .addNextIntent(ItemDetailActivity.intent(context, id))
            .getPendingIntent(id.toInt(), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)!!

    private fun openList(): PendingIntent =
        PendingIntent.getActivity(
            context, SUMMARY_ID,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun canNotify(): Boolean =
        (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()

    private fun ensureChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "Price drops", NotificationManager.IMPORTANCE_DEFAULT)
        channel.description = "Price drops and target prices reached"
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private companion object {
        const val CHANNEL_ID = "price_drops"
        const val SUMMARY_ID = -1
    }
}
