package dev.miyabisun.subfeedbrowser

import org.junit.Assert.assertEquals
import org.junit.Test

class ChoicesTest {
    private fun option(
        id: String,
        label: String,
        selected: Boolean = false,
        disabled: Boolean = false,
    ) = Choice(id, label, selected, disabled)

    @Test
    fun listsTheOptionsOfASelectInTheirOrder() {
        val rows = choiceRows(listOf(option("0", "すべて", selected = true), option("1", "ブルアカ"), option("2", "情報")))
        assertEquals(
            listOf(
                ChoiceRow("0", "すべて", checked = true),
                ChoiceRow("1", "ブルアカ", checked = false),
                ChoiceRow("2", "情報", checked = false),
            ),
            rows,
        )
    }

    @Test
    fun putsAGroupUnderItsNameWhichCannotBePicked() {
        val rows =
            choiceRows(
                listOf(
                    option("0", "すべて"),
                    Choice("g", "ゲーム", items = listOf(option("1", "ブルアカ", selected = true), option("2", "原神"))),
                ),
            )
        assertEquals(
            listOf(
                ChoiceRow("0", "すべて", checked = false),
                ChoiceRow(null, "ゲーム", checked = false),
                ChoiceRow("1", "ブルアカ", checked = true),
                ChoiceRow("2", "原神", checked = false),
            ),
            rows,
        )
    }

    @Test
    fun showsAnOptionThePageTurnedOffButDoesNotOfferIt() {
        assertEquals(
            listOf(ChoiceRow(null, "準備中", checked = false)),
            choiceRows(listOf(option("9", "準備中", disabled = true))),
        )
        assertEquals(
            "everything in a group that is turned off",
            listOf(ChoiceRow(null, "休止", checked = false), ChoiceRow(null, "A", checked = false)),
            choiceRows(listOf(Choice("g", "休止", disabled = true, items = listOf(option("1", "A"))))),
        )
    }

    @Test
    fun leavesOutTheLinesBetweenOptions() {
        assertEquals(
            listOf(ChoiceRow("0", "A", checked = false), ChoiceRow("1", "B", checked = false)),
            choiceRows(listOf(option("0", "A"), Choice("s", "", separator = true), option("1", "B"))),
        )
    }
}
