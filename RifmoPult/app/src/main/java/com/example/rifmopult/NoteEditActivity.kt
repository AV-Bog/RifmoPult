package com.example.rifmopult

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.ActionMode
import android.view.Gravity
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.example.rifmopult.databinding.ActivityNoteEditBinding
import java.text.SimpleDateFormat
import android.widget.*
import java.util.*
import androidx.core.graphics.drawable.toDrawable
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class NoteEditActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNoteEditBinding
    private var currentNote: Note? = null
    private var isNewNote = false

    private val history = mutableListOf<NoteState>()
    private var historyIndex = -1
    private var lastAutoSaveTime = 0L
    private var isUndoingOrRedoing = false

    private var selectedWord = ""

    companion object {
        const val EXTRA_NOTE = "extra_note"
        const val EXTRA_NOTE_RESULT = "extra_note_result"
        const val RESULT_DELETED = 2
        private const val AUTO_SAVE_DELAY = 1500L
        private const val MAX_HISTORY_SIZE = 20
    }

    data class NoteState(val title: String, val content: String)

    private lateinit var noteDao: NoteDao

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNoteEditBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val db = AppDatabase.getDatabase(this)
        noteDao = db.noteDao()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleExitWithAutoSave()
            }
        })

        loadNoteData()
        setupToolbar()
        setupTextChangeListeners()
        requestFocusAndShowKeyboard()
    }

    private fun requestFocusAndShowKeyboard() {
        binding.contentEditText.requestFocus()
        val post = binding.contentEditText.post {
            val imm =
                (getSystemService(/* name = */ INPUT_METHOD_SERVICE) as InputMethodManager).also {
                    it.showSoftInput(binding.contentEditText, InputMethodManager.SHOW_IMPLICIT)
                }
        }
    }

    private fun setupToolbar() {
        binding.btnClose.setOnClickListener {
            handleExitWithAutoSave()
        }

        binding.btnUndo.setOnClickListener {
            if (historyIndex > 0) {
                isUndoingOrRedoing = true
                historyIndex--
                val state = history[historyIndex]
                binding.titleEditText.setText(state.title)
                binding.contentEditText.setText(state.content)
                isUndoingOrRedoing = false
                updateUndoRedoButtons()
            }
        }

        binding.btnRedo.setOnClickListener {
            if (historyIndex < history.size - 1) {
                isUndoingOrRedoing = true
                historyIndex++
                val state = history[historyIndex]
                binding.titleEditText.setText(state.title)
                binding.contentEditText.setText(state.content)
                isUndoingOrRedoing = false
                updateUndoRedoButtons()
            }
        }

        binding.btnSave.setOnClickListener {
            saveCurrentNoteToDatabase()
            hideKeyboard()
        }

        updateUndoRedoButtons()
        updateUndoRedoButtons()
    }

    private fun saveCurrentNoteToDatabase() {
        val title = binding.titleEditText.text.toString().trim()
        val content = binding.contentEditText.text.toString().trim()

        if (isNewNote && title.isEmpty() && content.isEmpty()) {
            return
        }

        val updatedNote = currentNote?.copy(
            title = title,
            content = content,
            date = getCurrentDate()
        )

        updatedNote?.let { note ->
            lifecycleScope.launch {
                noteDao.insertNote(note.toEntity())
            }
        }
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.root.windowToken, 0)
    }
    private fun loadNoteData() {
        currentNote = intent.getSerializableExtra(EXTRA_NOTE) as? Note

        if (currentNote == null) {
            isNewNote = true
            currentNote = Note(
                id = System.currentTimeMillis(),
                title = "",
                content = "",
                date = getCurrentDate()
            )
        } else {
            isNewNote = false
        }

        val initialState = NoteState(
            title = currentNote?.title ?: "",
            content = currentNote?.content ?: ""
        )
        history.clear()
        history.add(initialState)
        historyIndex = 0

        binding.titleEditText.setText(initialState.title)
        binding.contentEditText.setText(initialState.content)
    }

    private fun getCurrentDate(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        return sdf.format(Date())
    }

    private fun saveToHistory() {
        if (isUndoingOrRedoing) return

        val currentState = NoteState(
            title = binding.titleEditText.text.toString(),
            content = binding.contentEditText.text.toString()
        )

        if (historyIndex >= 0 && history[historyIndex] == currentState) return

        while (history.size > historyIndex + 1) {
            history.removeAt(history.size - 1)
        }

        if (history.size >= MAX_HISTORY_SIZE) {
            history.removeAt(0)
            historyIndex--
        }

        history.add(currentState)
        historyIndex = history.size - 1
        updateUndoRedoButtons()
    }

    private fun updateUndoRedoButtons() {
        val canUndo = historyIndex > 0
        val canRedo = historyIndex < history.size - 1

        val activeColor = getColor(android.R.color.black)
        val disabledColor = getColor(android.R.color.darker_gray)

        binding.btnUndo.setColorFilter(if (canUndo) activeColor else disabledColor)
        binding.btnRedo.setColorFilter(if (canRedo) activeColor else disabledColor)

        binding.btnUndo.isEnabled = canUndo
        binding.btnRedo.isEnabled = canRedo
    }

    private fun setupTextChangeListeners() {
        var lastText = ""

        val textWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                lastText = s.toString()
            }

            override fun afterTextChanged(s: Editable?) {
                if (isUndoingOrRedoing) return

                val now = System.currentTimeMillis()
                val text = lastText

                val endsWithWordSeparator = text.isNotEmpty() &&
                        (text.last() == ' ' || text.last() == '.' || text.last() == '\n')

                if (endsWithWordSeparator || now - lastAutoSaveTime > AUTO_SAVE_DELAY) {
                    saveToHistory()
                    lastAutoSaveTime = now
                }
            }
        }

        binding.titleEditText.addTextChangedListener(textWatcher)
        binding.contentEditText.addTextChangedListener(textWatcher)

        binding.contentEditText.setCustomSelectionActionModeCallback(object : ActionMode.Callback {
            override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
                menu.add(Menu.NONE, 1, 0, "Рифма к слову")
                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
                return true
            }

            override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean = false
            override fun onDestroyActionMode(mode: ActionMode) = Unit

            override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
                if (item.itemId == 1) {
                    val editText = binding.contentEditText
                    val start = editText.selectionStart
                    val end = editText.selectionEnd
                    if (start >= 0 && end >= 0) {
                        val text = editText.text.toString()
                        val word = text.substring(
                            kotlin.math.min(start, end),
                            kotlin.math.max(start, end)
                        ).trim()
                        if (word.isNotEmpty()) {
                            showRhymePanel(word)
                        }
                    }
                    mode.finish()
                    return true
                }
                return false
            }
        })
    }

    private fun handleExitWithAutoSave() {
        val currentTitle = binding.titleEditText.text.toString()
        val currentContent = binding.contentEditText.text.toString()

        if (isNewNote && currentTitle.isEmpty() && currentContent.isEmpty()) {
            setResult(RESULT_CANCELED)
            finish()
            return
        }

        val updatedNote = currentNote?.copy(
            title = currentTitle,
            content = currentContent,
            date = getCurrentDate()
        )

        val resultIntent = Intent().apply {
            putExtra(EXTRA_NOTE_RESULT, updatedNote)
        }
        setResult(/* resultCode = */ RESULT_OK, /* data = */ resultIntent)
        finish()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_note_edit, menu)
        if (isNewNote) {
            menu.findItem(R.id.action_delete).isVisible = false
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_delete -> {
                deleteNote()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun deleteNote() {
        val resultIntent = Intent().apply {
            putExtra(EXTRA_NOTE_RESULT, currentNote)
        }
        setResult(RESULT_DELETED, resultIntent)
        finish()
    }

    private var rhymePopup: PopupWindow? = null

    @SuppressLint("ClickableViewAccessibility", "InflateParams", "SetTextI18n")

    private fun showRhymePanel(word: String) {
        selectedWord = word

        val popupView = LayoutInflater.from(this).inflate(R.layout.popup_rhyme_panel, null)

        rhymePopup = PopupWindow(
            popupView,
            ViewGroup.LayoutParams.MATCH_PARENT,
            (resources.displayMetrics.heightPixels * 0.4).toInt()
        ).apply {
            isFocusable = false
            setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
        }

        val title = popupView.findViewById<TextView>(R.id.rhymeTitle)
        title.text = "Рифма к слову: $word"

        val container = popupView.findViewById<LinearLayout>(R.id.rhymeWordsContainer)
        val closeBtn = popupView.findViewById<ImageButton>(R.id.btnCloseRhyme)

        val fakeRhymes = listOf("друг", "вокруг", "много", "пирог", "порог", "шар", "жар", "багаж",
            "запах", "смех", "штуки", "туки", "каучуки", "бамбуки", "поруки", "хуки",  "физруки",
            "ультразвуки", "уки")

        fakeRhymes.forEach { rhyme ->
            val textView = TextView(this).apply {
                text = rhyme
                setPadding(16, 8, 16, 8)
                setBackgroundResource(R.drawable.rhyme_word_background)
                setOnTouchListener { v, event ->
                    if (event.action == MotionEvent.ACTION_DOWN) {
                        val shadowBuilder = View.DragShadowBuilder(v)
                        v.startDragAndDrop(
                            ClipData.newPlainText("rhyme_word", rhyme),
                            shadowBuilder,
                            rhyme,
                            0
                        )
                        return@setOnTouchListener true
                    }
                    false
                }
            }
            textView.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 8, 8)
            }
            container.addView(textView)
        }

        closeBtn.setOnClickListener {
            rhymePopup?.dismiss()
        }

        val location = IntArray(2)
        binding.root.getLocationOnScreen(location)

        val keyboardHeight = getKeyboardHeight()
        if (keyboardHeight > 0) {
            resources.displayMetrics.heightPixels - keyboardHeight - (resources.displayMetrics.heightPixels * 0.4).toInt()
        } else {
            resources.displayMetrics.heightPixels - (resources.displayMetrics.heightPixels * 0.4).toInt()
        }

        rhymePopup?.showAtLocation(binding.root, Gravity.BOTTOM, 0, 0)
    }

    private fun getKeyboardHeight(): Int {
        val rect = android.graphics.Rect()
        window.decorView.getWindowVisibleDisplayFrame(rect)
        val screenHeight = window.decorView.height
        return screenHeight - rect.bottom
    }

}
