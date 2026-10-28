package com.dmeltz.englishpractice.core

enum class Category { SPELLING, GRAMMAR }

enum class ExerciseType { TRANSLATE, LISTEN, FIX }

data class Rule(
    val id: String,
    val category: Category,
    val title: String,
    val reminder: String,
    val examples: List<String>,
)

/**
 * One practice item.
 *
 * [answers] are the accepted answers. Square brackets mark the part the rule is
 * testing ("They [stopped] talking."); an answer without brackets is tested as a whole.
 */
data class Item(
    val id: String,
    val ruleIds: List<String>,
    val type: ExerciseType,
    val hebrew: String?,
    val wrong: String?,
    val answers: List<String>,
) {
    val primaryRuleId: String get() = ruleIds.first()

    /** The first accepted answer, without focus brackets. Also what the phone reads for LISTEN items. */
    val displayAnswer: String get() = stripFocus(answers.first())
}

data class Curriculum(val rules: List<Rule>, val items: List<Item>) {
    private val rulesById = rules.associateBy { it.id }
    private val itemsById = items.associateBy { it.id }
    private val itemsByRule = items.groupBy { it.primaryRuleId }

    fun rule(id: String): Rule = rulesById.getValue(id)
    fun item(id: String): Item? = itemsById[id]
    fun itemsFor(ruleId: String): List<Item> = itemsByRule[ruleId].orEmpty()

    /** The order rules are introduced in: spelling and grammar alternate. */
    val introductionOrder: List<Rule> by lazy {
        val spelling = rules.filter { it.category == Category.SPELLING }
        val grammar = rules.filter { it.category == Category.GRAMMAR }
        val out = mutableListOf<Rule>()
        for (i in 0 until maxOf(spelling.size, grammar.size)) {
            spelling.getOrNull(i)?.let(out::add)
            grammar.getOrNull(i)?.let(out::add)
        }
        out
    }
}

fun stripFocus(s: String): String = s.replace("[", "").replace("]", "")

/**
 * Parses the curriculum text format:
 *
 * ```
 * @rule SP01
 * category: spelling
 * title: Doubling the final consonant
 * reminder: First line of the reminder.
 * reminder: Second line.
 * example: run → running
 * T: הם הפסיקו לדבר = They [stopped] talking.
 * L: [beginning]
 * F: הוא רץ מהר | He is runing fast. = He is [running] fast.
 * ```
 *
 * Alternative answers are separated by " || ". A trailing " #GR09" tags an item with
 * extra rules. Lines starting with "//" and blank lines are ignored.
 */
object CurriculumParser {
    fun parse(text: String): Curriculum {
        val rules = mutableListOf<Rule>()
        val items = mutableListOf<Item>()

        var id: String? = null
        var category: Category? = null
        var title = ""
        val reminder = mutableListOf<String>()
        val examples = mutableListOf<String>()
        var itemCount = 0

        fun flush() {
            val ruleId = id ?: return
            rules += Rule(
                id = ruleId,
                category = category ?: error("Rule $ruleId has no category"),
                title = title,
                reminder = reminder.joinToString("\n"),
                examples = examples.toList(),
            )
            id = null; category = null; title = ""
            reminder.clear(); examples.clear(); itemCount = 0
        }

        text.lineSequence().forEachIndexed { index, raw ->
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("//")) return@forEachIndexed
            fun fail(msg: String): Nothing = error("Curriculum line ${index + 1}: $msg: $line")

            when {
                line.startsWith("@rule ") -> {
                    flush()
                    id = line.removePrefix("@rule ").trim()
                }
                line.startsWith("category:") -> category =
                    when (line.substringAfter(":").trim().lowercase()) {
                        "spelling" -> Category.SPELLING
                        "grammar" -> Category.GRAMMAR
                        else -> fail("unknown category")
                    }
                line.startsWith("title:") -> title = line.substringAfter(":").trim()
                line.startsWith("reminder:") -> reminder += line.substringAfter(":").trim()
                line.startsWith("example:") -> examples += line.substringAfter(":").trim()
                line.length > 2 && line[1] == ':' && line[0] in "TLF" -> {
                    val ruleId = id ?: fail("item outside a rule")
                    itemCount++
                    var body = line.substring(2).trim()
                    val extraRules = mutableListOf<String>()
                    val tagMatch = Regex("""\s#([A-Z]{2}\d{2}(?:\s+#[A-Z]{2}\d{2})*)\s*$""").find(body)
                    if (tagMatch != null) {
                        extraRules += tagMatch.groupValues[1].split(Regex("""\s+#"""))
                        body = body.substring(0, tagMatch.range.first).trim()
                    }
                    val itemId = "$ruleId-%02d".format(itemCount)
                    val ruleIds = listOf(ruleId) + extraRules
                    fun answers(s: String) = s.split(" || ").map { it.trim() }.filter { it.isNotEmpty() }
                    items += when (line[0]) {
                        'T' -> {
                            val parts = body.split(" = ", limit = 2)
                            if (parts.size != 2) fail("translate item needs 'hebrew = answer'")
                            Item(itemId, ruleIds, ExerciseType.TRANSLATE, parts[0].trim(), null, answers(parts[1]))
                        }
                        'L' -> Item(itemId, ruleIds, ExerciseType.LISTEN, null, null, answers(body))
                        else -> {
                            val parts = body.split(" = ", limit = 2)
                            val left = parts[0].split(" | ", limit = 2)
                            if (parts.size != 2 || left.size != 2) fail("fix item needs 'hebrew | wrong = answer'")
                            Item(itemId, ruleIds, ExerciseType.FIX, left[0].trim(), left[1].trim(), answers(parts[1]))
                        }
                    }
                }
                else -> fail("unrecognized line")
            }
        }
        flush()

        val ruleIds = rules.map { it.id }.toSet()
        items.flatMap { it.ruleIds }.firstOrNull { it !in ruleIds }?.let { error("Unknown rule tag $it") }
        return Curriculum(rules, items)
    }
}
