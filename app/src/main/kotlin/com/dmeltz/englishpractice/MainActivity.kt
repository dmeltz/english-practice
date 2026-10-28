package com.dmeltz.englishpractice

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import com.dmeltz.englishpractice.core.AppState
import com.dmeltz.englishpractice.core.SessionBuilder
import com.dmeltz.englishpractice.core.SessionPlan
import com.dmeltz.englishpractice.reminder.Reminders
import com.dmeltz.englishpractice.ui.AppTheme
import com.dmeltz.englishpractice.ui.HomeScreen
import com.dmeltz.englishpractice.ui.ProgressScreen
import com.dmeltz.englishpractice.ui.RuleScreen
import com.dmeltz.englishpractice.ui.SessionScreen
import com.dmeltz.englishpractice.ui.SettingsScreen
import com.dmeltz.englishpractice.widget.StreakWidget

sealed interface Screen {
    data object Home : Screen
    data class Session(val plan: SessionPlan) : Screen
    data object Progress : Screen
    data class Rule(val ruleId: String) : Screen
    data object Settings : Screen
}

class MainActivity : ComponentActivity() {
    private lateinit var speaker: Speaker

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        speaker = Speaker(this)
        val repo = Repository.get(this)
        Reminders.schedule(this)

        setContent {
            AppTheme {
                var state by remember { mutableStateOf(repo.state) }
                var stack by remember { mutableStateOf(listOf<Screen>(Screen.Home)) }
                val screen = stack.last()

                fun push(s: Screen) { stack = stack + s }
                fun pop() { if (stack.size > 1) stack = stack.dropLast(1) }
                fun update(newState: AppState) {
                    repo.save(newState)
                    state = newState
                }

                val askNotifications = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission(),
                ) { }
                LaunchedEffect(Unit) {
                    if (Build.VERSION.SDK_INT >= 33) askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                }

                BackHandler(enabled = stack.size > 1) { pop() }

                key(screen) { when (screen) {
                    Screen.Home -> HomeScreen(
                        state = state,
                        onStart = {
                            push(Screen.Session(SessionBuilder.build(repo.curriculum, state, today())))
                        },
                        onProgress = { push(Screen.Progress) },
                        onSettings = { push(Screen.Settings) },
                    )
                    is Screen.Session -> SessionScreen(
                        plan = screen.plan,
                        curriculum = repo.curriculum,
                        initialState = state,
                        speaker = speaker,
                        onState = ::update,
                        onAnswer = repo::appendAnswer,
                        onFinished = {
                            StreakWidget.updateAll(this@MainActivity)
                            stack = listOf(Screen.Home)
                        },
                        onQuit = {
                            StreakWidget.updateAll(this@MainActivity)
                            pop()
                        },
                    )
                    Screen.Progress -> ProgressScreen(
                        state = state,
                        curriculum = repo.curriculum,
                        onRule = { push(Screen.Rule(it)) },
                        onBack = ::pop,
                    )
                    is Screen.Rule -> RuleScreen(
                        rule = repo.curriculum.rule(screen.ruleId),
                        state = state,
                        answers = remember(screen.ruleId) {
                            repo.answers().filter { screen.ruleId in it.ruleIds }
                        },
                        onBack = ::pop,
                    )
                    Screen.Settings -> SettingsScreen(
                        state = state,
                        itemCount = repo.curriculum.items.size,
                        version = BuildConfigInfo.versionName(this@MainActivity),
                        onState = {
                            update(it)
                            Reminders.schedule(this@MainActivity)
                        },
                        onExport = ::shareExport,
                        onBack = ::pop,
                    )
                } }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        StreakWidget.updateAll(this)
    }

    override fun onDestroy() {
        speaker.shutdown()
        super.onDestroy()
    }

    private fun shareExport() {
        val file = Repository.get(this).writeExport()
        val uri = FileProvider.getUriForFile(this, "$packageName.files", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, file.name)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(send, "Share progress file"))
    }
}
