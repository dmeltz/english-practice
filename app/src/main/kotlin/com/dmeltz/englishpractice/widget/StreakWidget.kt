package com.dmeltz.englishpractice.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.dmeltz.englishpractice.MainActivity
import com.dmeltz.englishpractice.R
import com.dmeltz.englishpractice.Repository
import com.dmeltz.englishpractice.today

/** Home screen widget: streak, average grade, and whether today's practice is done. */
class StreakWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        for (id in ids) manager.updateAppWidget(id, views(context))
    }

    companion object {
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, StreakWidget::class.java))
            if (ids.isNotEmpty()) manager.updateAppWidget(ids, views(context))
        }

        private fun views(context: Context): RemoteViews {
            val state = Repository.get(context).state
            val day = today()
            val streak = state.streak(day)
            val grade = state.averageGrade(day)
            val done = state.practicedOn(day)

            val open = PendingIntent.getActivity(
                context, 0,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            return RemoteViews(context.packageName, R.layout.widget_streak).apply {
                setTextViewText(R.id.widget_streak, streak.toString())
                setTextViewText(
                    R.id.widget_streak_label,
                    context.resources.getQuantityString(R.plurals.streak_days, streak),
                )
                setTextViewText(R.id.widget_grade, grade?.let { "$it%" } ?: "–")
                setTextViewText(
                    R.id.widget_status,
                    context.getString(if (done) R.string.widget_done else R.string.widget_practice),
                )
                setInt(
                    R.id.widget_status, "setBackgroundResource",
                    if (done) R.drawable.widget_pill_done else R.drawable.widget_pill_todo,
                )
                setOnClickPendingIntent(R.id.widget_root, open)
            }
        }
    }
}
