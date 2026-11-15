package dev.miyabisun.subfeedbrowser

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.WindowInsets
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.TextView

/** Where the user tells the app the address of the list. */
class SettingsActivity : Activity() {
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        setContentView(R.layout.settings)
        val settings = Settings(this)
        val input = findViewById<EditText>(R.id.feed_url)
        val invalid = findViewById<TextView>(R.id.invalid)
        if (state == null) input.setText(settings.feedUrl)

        fun save() {
            val url = feedUrl(input.text.toString())
            if (url == null) {
                invalid.visibility = View.VISIBLE
                return
            }
            settings.feedUrl = url
            // Land on the list, also when the app was not open behind this screen.
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
            finish()
        }
        findViewById<Button>(R.id.save).setOnClickListener { save() }
        input.doAfterTextChanged { invalid.visibility = View.GONE }
        input.setOnEditorActionListener { _, action, _ ->
            if (action == EditorInfo.IME_ACTION_DONE) save()
            action == EditorInfo.IME_ACTION_DONE
        }
        findViewById<View>(R.id.root).setOnApplyWindowInsetsListener { view, insets ->
            val types =
                WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout() or
                    WindowInsets.Type.ime()
            val inset = insets.getInsets(types)
            view.setPadding(inset.left, inset.top, inset.right, inset.bottom)
            WindowInsets.CONSUMED
        }
    }
}

private fun EditText.doAfterTextChanged(action: () -> Unit) =
    addTextChangedListener(
        object : TextWatcher {
            override fun beforeTextChanged(
                text: CharSequence?,
                start: Int,
                count: Int,
                after: Int,
            ) = Unit

            override fun onTextChanged(
                text: CharSequence?,
                start: Int,
                before: Int,
                count: Int,
            ) = Unit

            override fun afterTextChanged(text: Editable?) = action()
        },
    )
