package app.pocketdictate

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast

/** In-app editor for the US → Australian spelling list (a plain text file). */
class WordListActivity : Activity() {

    private lateinit var editor: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_word_list)
        applyInsets(findViewById(R.id.root))
        editor = findViewById(R.id.editor)
        editor.setText(SpellingConverter.readText(this))

        findViewById<Button>(R.id.saveButton).setOnClickListener {
            SpellingConverter.save(this, editor.text.toString())
            Toast.makeText(this, R.string.word_list_saved, Toast.LENGTH_SHORT).show()
            finish()
        }
        findViewById<Button>(R.id.resetButton).setOnClickListener {
            AlertDialog.Builder(this)
                .setMessage(R.string.reset_confirm)
                .setPositiveButton(R.string.reset_default) { _, _ ->
                    SpellingConverter.resetToDefault(this)
                    editor.setText(SpellingConverter.readText(this))
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
    }
}
