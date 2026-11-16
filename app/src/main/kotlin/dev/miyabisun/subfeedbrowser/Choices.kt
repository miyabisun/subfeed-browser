package dev.miyabisun.subfeedbrowser

/**
 * One of the choices a page offers in a `<select>`, as the engine hands it over. A group holds
 * its options in `items`; a `separator` is a line between options.
 */
data class Choice(
    val id: String,
    val label: String,
    val selected: Boolean = false,
    val disabled: Boolean = false,
    val separator: Boolean = false,
    val items: List<Choice>? = null,
)

/**
 * A line of the list the app shows for them. `id` is null for what cannot be picked: the name of
 * a group, and an option the page turned off.
 */
data class ChoiceRow(
    val id: String?,
    val label: String,
    val checked: Boolean,
)

/** The lines to show for the choices of a page, in the page's order, groups under their names. */
fun choiceRows(
    choices: List<Choice>,
    off: Boolean = false,
): List<ChoiceRow> =
    choices.flatMap { choice ->
        val disabled = off || choice.disabled
        when {
            choice.separator -> emptyList()
            choice.items != null -> listOf(ChoiceRow(null, choice.label, false)) + choiceRows(choice.items, disabled)
            else -> listOf(ChoiceRow(choice.id.takeUnless { disabled }, choice.label, choice.selected && !disabled))
        }
    }
