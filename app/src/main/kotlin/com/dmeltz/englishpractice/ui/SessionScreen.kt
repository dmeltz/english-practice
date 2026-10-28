package com.dmeltz.englishpractice.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dmeltz.englishpractice.Speaker
import com.dmeltz.englishpractice.core.AnswerChecker
import com.dmeltz.englishpractice.core.AnswerRecord
import com.dmeltz.englishpractice.core.AppState
import com.dmeltz.englishpractice.core.CheckResult
import com.dmeltz.englishpractice.core.Curriculum
import com.dmeltz.englishpractice.core.DiffKind
import com.dmeltz.englishpractice.core.ExerciseType
import com.dmeltz.englishpractice.core.Mascot
import com.dmeltz.englishpractice.core.Mood
import com.dmeltz.englishpractice.core.Item
import com.dmeltz.englishpractice.core.Progress
import com.dmeltz.englishpractice.core.Rule
import com.dmeltz.englishpractice.core.SessionPlan
import com.dmeltz.englishpractice.core.TextDiff
import com.dmeltz.englishpractice.today

private data class Entry(val item: Item, val retry: Boolean)

private sealed interface Step {
    data class Reminder(val rule: Rule) : Step
    data class Question(val entry: Entry) : Step
    data class Feedback(val entry: Entry, val typed: String, val result: CheckResult) : Step
    data object Done : Step
}

@Composable
fun SessionScreen(
    plan: SessionPlan,
    curriculum: Curriculum,
    initialState: AppState,
    speaker: Speaker,
    onState: (AppState) -> Unit,
    onAnswer: (AnswerRecord) -> Unit,
    onFinished: () -> Unit,
    onQuit: () -> Unit,
) {
    val day = remember { today() }
    // New rules start when the session opens, so each reminder is shown once.
    var state by remember {
        mutableStateOf(plan.newRules.fold(initialState) { s, r -> Progress.introduceRule(s, r.id, day) })
    }
    LaunchedEffect(Unit) { onState(state) }
    val sessionNumber = remember { initialState.sessionsCompleted + 1 }
    val queue = remember { mutableStateListOf(*plan.items.map { Entry(it, retry = false) }.toTypedArray()) }
    val remindersShown = remember { mutableSetOf<String>() }
    var position by remember { mutableIntStateOf(0) }
    var firstTryCorrect by remember { mutableIntStateOf(0) }
    val missedRules = remember { mutableListOf<String>() }
    val newRuleIds = remember { plan.newRules.map { it.id }.toSet() }
    var reminderToShow by remember { mutableStateOf<Rule?>(null) }

    fun stepAt(index: Int): Step {
        if (index >= queue.size) return Step.Done
        val entry = queue[index]
        val ruleId = entry.item.primaryRuleId
        if (ruleId in newRuleIds && ruleId !in remindersShown) {
            return Step.Reminder(curriculum.rule(ruleId))
        }
        return Step.Question(entry)
    }

    var step by remember { mutableStateOf(stepAt(0)) }

    fun advance() {
        position++
        step = stepAt(position)
        if (step == Step.Done) {
            val finished = Progress.completeSession(state, day)
            state = finished
            onState(finished)
        }
    }

    fun submit(entry: Entry, typed: String) {
        val result = AnswerChecker.check(entry.item, typed)
        if (!entry.retry && result.correct) firstTryCorrect++
        if (!entry.retry && !result.ruleCorrect) missedRules += entry.item.primaryRuleId
        onAnswer(
            AnswerRecord(
                timestamp = System.currentTimeMillis(),
                day = day,
                session = sessionNumber,
                itemId = entry.item.id,
                ruleIds = entry.item.ruleIds,
                type = entry.item.type,
                prompt = listOfNotNull(entry.item.hebrew, entry.item.wrong).joinToString(" | ")
                    .ifEmpty { entry.item.displayAnswer },
                expected = result.expected,
                typed = typed,
                correct = result.correct,
                ruleCorrect = result.ruleCorrect,
                mistake = result.mistake,
                retry = entry.retry,
            ),
        )
        val updated = Progress.applyAnswer(state, entry.item, result, day, entry.retry)
        state = updated
        onState(updated)
        if (!result.correct && !entry.retry) queue.add(Entry(entry.item, retry = true))
        step = Step.Feedback(entry, typed, result)
    }

    Column(Modifier.fillMaxSize().padding(top = 32.dp).imePadding()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onQuit) { Icon(Icons.Filled.Close, contentDescription = "Stop") }
            LinearProgressIndicator(
                progress = { if (queue.isEmpty()) 1f else (position.toFloat() / queue.size).coerceIn(0f, 1f) },
                modifier = Modifier.weight(1f).padding(end = 16.dp),
            )
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when (val s = step) {
                is Step.Reminder -> ReminderCard(s.rule, isNew = true) {
                    remindersShown += s.rule.id
                    step = stepAt(position)
                }
                is Step.Question -> QuestionView(s.entry, speaker) { typed -> submit(s.entry, typed) }
                is Step.Feedback -> FeedbackView(
                    s.entry, s.typed, s.result, curriculum, speaker,
                    onShowRule = { reminderToShow = it },
                    onNext = ::advance,
                )
                Step.Done -> DoneView(
                    correct = firstTryCorrect,
                    total = plan.items.size,
                    state = state,
                    weakestRule = missedRules.groupingBy { it }.eachCount().maxByOrNull { it.value }
                        ?.let { curriculum.rule(it.key) },
                    onFinish = onFinished,
                )
            }
        }
    }

    reminderToShow?.let { rule ->
        AlertDialog(
            onDismissRequest = { reminderToShow = null },
            confirmButton = { TextButton(onClick = { reminderToShow = null }) { Text("OK") } },
            title = { Text(rule.title) },
            text = { RuleText(rule) },
        )
    }
}

@Composable
fun RuleText(rule: Rule) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rule.reminder.lines().forEach { Text(it, style = MaterialTheme.typography.bodyLarge) }
        if (rule.examples.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            rule.examples.forEach {
                Text("• $it", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun ReminderCard(rule: Rule, isNew: Boolean, onContinue: () -> Unit) {
    PipSays(Mood.CALM, Mascot.teaching(isNew))
    Text(rule.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) { RuleText(rule) }
    }
    Button(onClick = onContinue, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Let's practice") }
}

@Composable
private fun HebrewText(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.headlineSmall.copy(textDirection = TextDirection.Rtl),
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Start,
    )
}

@Composable
private fun QuestionView(entry: Entry, speaker: Speaker, onSubmit: (String) -> Unit) {
    val item = entry.item
    var typed by remember(entry) { mutableStateOf("") }
    val focus = remember(entry) { FocusRequester() }

    LaunchedEffect(entry) {
        if (item.type == ExerciseType.LISTEN) speaker.speak(item.displayAnswer)
        focus.requestFocus()
    }

    if (entry.retry) {
        PipSays(Mood.WAITING, Mascot.retry(), size = 48.dp)
    }
    when (item.type) {
        ExerciseType.TRANSLATE -> {
            Text("Write in English", style = MaterialTheme.typography.titleMedium)
            HebrewText(item.hebrew.orEmpty())
        }
        ExerciseType.LISTEN -> {
            Text("Type what you hear", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { speaker.speak(item.displayAnswer) }) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Text("Play")
                }
                OutlinedButton(onClick = { speaker.speak(item.displayAnswer, slow = true) }) { Text("Slowly") }
            }
            if (!speaker.available) {
                Text(
                    "No English voice found. Install one in the phone's Settings > Text-to-speech.",
                    color = Bad,
                )
            }
        }
        ExerciseType.FIX -> {
            Text("Correct the English sentence", style = MaterialTheme.typography.titleMedium)
            HebrewText(item.hebrew.orEmpty())
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Text(
                    item.wrong.orEmpty(),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(12.dp),
                )
            }
        }
    }
    OutlinedTextField(
        value = typed,
        onValueChange = { typed = it },
        modifier = Modifier.fillMaxWidth().focusRequester(focus),
        textStyle = MaterialTheme.typography.titleLarge.copy(color = MaterialTheme.colorScheme.onSurface),
        placeholder = { Text("Your answer") },
        // Password-type keyboards don't offer suggestions or autocorrect, which would give the spelling away.
        // The text itself stays visible.
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.None,
            autoCorrectEnabled = false,
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(onDone = { if (typed.isNotBlank()) onSubmit(typed) }),
    )
    Button(
        onClick = { onSubmit(typed) },
        enabled = typed.isNotBlank(),
        modifier = Modifier.fillMaxWidth().height(52.dp),
    ) { Text("Check") }
}

@Composable
private fun FeedbackView(
    entry: Entry,
    typed: String,
    result: CheckResult,
    curriculum: Curriculum,
    speaker: Speaker,
    onShowRule: (Rule) -> Unit,
    onNext: () -> Unit,
) {
    val rule = curriculum.rule(entry.item.primaryRuleId)
    LaunchedEffect(entry, typed) {
        if (entry.item.type == ExerciseType.LISTEN && !result.correct) speaker.speak(result.expected)
    }
    if (result.correct) {
        PipSays(Mood.HAPPY, Mascot.correct(typed.hashCode().toLong()))
        Text(result.expected, style = MaterialTheme.typography.titleLarge)
    } else {
        PipSays(Mood.WAITING, Mascot.wrong(result.ruleCorrect, typed.hashCode().toLong()))
        val diff = TextDiff.diff(typed, result.expected)
        Text("You wrote", style = MaterialTheme.typography.labelLarge)
        Text(
            buildAnnotatedString {
                for (seg in diff) when (seg.kind) {
                    DiffKind.SAME -> append(seg.text)
                    DiffKind.EXTRA -> withStyle(
                        SpanStyle(color = Bad, textDecoration = TextDecoration.LineThrough, fontWeight = FontWeight.Bold),
                    ) { append(seg.text) }
                    DiffKind.MISSING -> Unit
                }
            },
            fontSize = 22.sp,
        )
        Text("Correct answer", style = MaterialTheme.typography.labelLarge)
        Text(
            buildAnnotatedString {
                for (seg in diff) when (seg.kind) {
                    DiffKind.SAME -> append(seg.text)
                    DiffKind.MISSING -> withStyle(
                        SpanStyle(color = Good, fontWeight = FontWeight.Bold, background = Good.copy(alpha = 0.15f)),
                    ) { append(seg.text) }
                    DiffKind.EXTRA -> Unit
                }
            },
            fontSize = 22.sp,
        )
        if (result.ruleCorrect) {
            Text(
                "You got the part this rule tests right. The mistake was somewhere else, so this rule's score isn't lowered.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (!entry.retry) {
            Text("You'll see this one again at the end.", style = MaterialTheme.typography.bodyMedium)
        }
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(rule.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(rule.reminder.lines().first(), style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = { onShowRule(rule) }) { Text("Show the whole rule") }
        }
    }
    if (entry.item.type == ExerciseType.LISTEN) {
        OutlinedButton(onClick = { speaker.speak(result.expected) }) {
            Icon(Icons.Filled.PlayArrow, contentDescription = null)
            Text("Hear it again")
        }
    }
    Button(onClick = onNext, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Continue") }
}

@Composable
private fun DoneView(correct: Int, total: Int, state: AppState, weakestRule: Rule?, onFinish: () -> Unit) {
    val day = today()
    Spacer(Modifier.height(24.dp))
    Text("Practice done", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
    PipSays(Mood.HAPPY, Mascot.sessionDone(correct, total, weakestRule), size = 96.dp)
    Text("Streak: ${state.streak(day)} days", style = MaterialTheme.typography.titleMedium)
    state.averageGrade(day)?.let { Text("Average grade: $it%", style = MaterialTheme.typography.titleMedium) }
    Spacer(Modifier.height(12.dp))
    Button(onClick = onFinish, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Finish") }
}
