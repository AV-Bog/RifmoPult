package com.example.rifmopult

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.ActionMode
import android.view.Gravity
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
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

fun Editable.insert(position: Int, text: String) {
    replace(position, position, text)
}

class NoteEditActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNoteEditBinding
    private var currentNote: Note? = null
    private var isNewNote = false

    private val history = mutableListOf<NoteState>()
    private var historyIndex = -1
    private var lastAutoSaveTime = 0L
    private var isUndoingOrRedoing = false

    private var selectedWord = ""

    private var dragView: TextView? = null
    private var dragWindow: PopupWindow? = null
    private var draggedRhyme: String? = null
    private var isDragging = false

    private var dragInitHandler = Handler(Looper.getMainLooper())
    private var isDraggingInitiated = false
    private var currentDragWord = ""

    private var hasUnsavedChanges = false
    private var originalState: NoteState? = null

    companion object {
        const val EXTRA_NOTE = "extra_note"
        const val EXTRA_NOTE_RESULT = "extra_note_result"
        const val RESULT_DELETED = 2
        private const val AUTO_SAVE_DELAY = 1500L
        private const val MAX_HISTORY_SIZE = 20
    }

    data class NoteState(val title: String, val content: String)

    private lateinit var noteDao: NoteDao

    @SuppressLint("ClickableViewAccessibility")
    private val rhymeTouchListener = View.OnTouchListener { view, event ->
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                val word = (view as TextView).text.toString()
                startDraggingRhyme(word, event)
                true
            }
            MotionEvent.ACTION_MOVE -> {
                updateCursorPosition(event)
                true
            }
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                dropRhyme(event)
                true
            }
            else -> false
        }
    }

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
            if (!hasUnsavedChanges) return@setOnClickListener

            saveCurrentNoteToDatabase()
            hideKeyboard()

            val currentState = NoteState(
                title = binding.titleEditText.text.toString(),
                content = stripSyllableHints(binding.contentEditText.text.toString())
            )
            history.clear()
            history.add(currentState)
            historyIndex = 0
            hasUnsavedChanges = false

            binding.btnUndo.visibility = View.GONE
            binding.btnRedo.visibility = View.GONE

            rhymePopup?.dismiss()
            rhymePopup = null

            updateSaveButton()
        }

        binding.btnShare.setOnClickListener {
            val title = binding.titleEditText.text.toString().trim()
            val cleanContent = stripSyllableHints(binding.contentEditText.text.toString()).trim()

            val textToShare = if (title.isNotEmpty()) "$title\n\n$cleanContent" else cleanContent

            if (textToShare.isNotEmpty()) {
                val sendIntent = Intent().apply {
                    action = Intent.ACTION_SEND
                    putExtra(Intent.EXTRA_TEXT, textToShare)
                    type = "text/plain"
                }
                val shareIntent = Intent.createChooser(sendIntent, null)
                startActivity(shareIntent)
            }
        }

        updateUndoRedoButtons()
        updateUndoRedoButtons()
    }

    private fun saveCurrentNoteToDatabase() {
        val title = binding.titleEditText.text.toString().trim()
        val cleanContent = binding.contentEditText.text.toString().trim()

        if (isNewNote && title.isEmpty() && cleanContent.isEmpty()) {
            return
        }

        val updatedNote = currentNote?.copy(
            title = title,
            content = cleanContent,
            date = getCurrentDate()
        )

        updatedNote?.let { note ->
            lifecycleScope.launch {
                noteDao.insertNote(note.toEntity())
            }
        }

        val currentState = NoteState(title = title, content = cleanContent)
        history.clear()
        history.add(currentState)
        historyIndex = 0
        hasUnsavedChanges = false
        updateSaveButton()
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

        originalState = initialState

        binding.titleEditText.setText(initialState.title)
        if (isNewNote) {
            binding.noteDateTextView.visibility = View.GONE
        } else {
            binding.noteDateTextView.visibility = View.VISIBLE
            val displayDate = try {
                val inputFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                val date = inputFormat.parse(currentNote?.date ?: "") ?: Date()
                val outputFormat = SimpleDateFormat("d MMMM yyyy, HH:mm", Locale("ru"))
                outputFormat.format(date)
            } catch (e: Exception) {
                currentNote?.date ?: ""
            }
            binding.noteDateTextView.text = "Изменено: $displayDate"
        }

        binding.contentEditText.setText(initialState.content)

        binding.btnUndo.visibility = View.GONE
        binding.btnRedo.visibility = View.GONE

        hasUnsavedChanges = false
        updateSaveButton()
    }

    private fun updateSaveButton() {
        val color = if (hasUnsavedChanges) {
            getColor(android.R.color.black)
        } else {
            getColor(android.R.color.darker_gray)
        }
        binding.btnSave.setColorFilter(color)
        binding.btnSave.isEnabled = hasUnsavedChanges
    }

    private fun getCurrentDate(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        return sdf.format(Date())
    }

    private fun saveToHistoryWithCleanText(cleanContent: String) {
        if (isUndoingOrRedoing) return

        val currentTitle = binding.titleEditText.text.toString()
        val currentState = NoteState(title = currentTitle, content = cleanContent)

        if (historyIndex >= 0 && history[historyIndex] == currentState) {
            hasUnsavedChanges = false
            updateSaveButton()
            return
        }

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

        hasUnsavedChanges = true
        binding.btnUndo.visibility = View.VISIBLE
        binding.btnRedo.visibility = View.VISIBLE
        updateSaveButton()
    }

    private fun updateUndoRedoButtons() {
        binding.btnSave.setOnClickListener {
            if (!hasUnsavedChanges) return@setOnClickListener

            saveCurrentNoteToDatabase()

            val currentState = NoteState(
                title = binding.titleEditText.text.toString(),
                content = stripSyllableHints(binding.contentEditText.text.toString())
            )
            history.clear()
            history.add(currentState)
            historyIndex = 0
            hasUnsavedChanges = false

            hideKeyboard()

            binding.btnUndo.visibility = View.GONE
            binding.btnRedo.visibility = View.GONE

            rhymePopup?.dismiss()
            rhymePopup = null

            updateSaveButton()
        }
    }

    private var isUpdatingText = false

    private fun setupTextChangeListeners() {

        val titleTextWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                if (isUndoingOrRedoing) return

                val now = System.currentTimeMillis()
                if (now - lastAutoSaveTime > AUTO_SAVE_DELAY) {
                    saveToHistoryWithCleanText(binding.contentEditText.text.toString().let { stripSyllableHints(it) })
                    lastAutoSaveTime = now
                }
            }
        }
        binding.contentEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val cleanText = s?.toString() ?: ""
                val hintedText = addSyllableHintsForDisplay(cleanText)
                binding.syllableOverlay.text = hintedText
            }
        })

        binding.titleEditText.addTextChangedListener(titleTextWatcher)

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
                        val cleanText = stripSyllableHints(text)
                        val word = cleanText.substring(
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

    private fun addSyllableHintsForDisplay(text: String): String {
        return text.split('\n').joinToString("\n") { line ->
            if (line.isBlank()) line else "$line ·${countSyllables(line)}"
        }
    }

    private fun handleExitWithAutoSave() {
        val currentTitle = binding.titleEditText.text.toString()
        val currentContentWithHints = binding.contentEditText.text.toString()
        val cleanContent = stripSyllableHints(currentContentWithHints).trim()

        if (isNewNote && currentTitle.isEmpty() && cleanContent.isEmpty()) {
            setResult(RESULT_CANCELED)
            finish()
            return
        }

        val originalTitle = currentNote?.title ?: ""
        val originalContent = currentNote?.content ?: ""

        val hasChanges = (currentTitle != originalTitle) || (cleanContent != originalContent)

        if (!hasChanges) {
            setResult(RESULT_CANCELED)
            finish()
            return
        }

        val updatedNote = Note(
            id = currentNote?.id ?: System.currentTimeMillis(),
            title = currentTitle,
            content = cleanContent,
            date = getCurrentDate()
        )

        val resultIntent = Intent().apply {
            putExtra(EXTRA_NOTE_RESULT, updatedNote)
        }
        setResult(RESULT_OK, resultIntent)
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
            R.id.action_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
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

        val scrollView = popupView.findViewById<ScrollView>(R.id.rhymeScrollView)
        val container = popupView.findViewById<com.google.android.flexbox.FlexboxLayout>(R.id.rhymeWordsContainer)
        val closeBtn = popupView.findViewById<ImageButton>(R.id.btnCloseRhyme)

        container.removeAllViews()
        val loadingView = TextView(this).apply {
            text = "Загрузка..."
            setPadding(16, 8, 16, 8)
        }
        container.addView(loadingView)

        lifecycleScope.launch {
            val rhymes = RifmeNetParser.fetchRhymes(word)
            container.removeAllViews()

            if (rhymes.isEmpty()) {
                container.addView(TextView(this@NoteEditActivity).apply {
                    text = "Не найдено рифм"
                    setTextColor(getColor(android.R.color.darker_gray))
                    setPadding(16, 8, 16, 8)
                })
            } else {
                rhymes.forEach { rhyme ->
                    val textView = TextView(this@NoteEditActivity).apply {
                        text = rhyme
                        setPadding(16, 8, 16, 8)
                        setBackgroundResource(R.drawable.rhyme_word_background)
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply {
                            setMargins(8, 0, 16, 8)
                        }

                        setOnTouchListener { _, event ->
                            when (event.action) {
                                MotionEvent.ACTION_DOWN -> {
                                    currentDragWord = text.toString()
                                    isDraggingInitiated = false

                                    scrollView.requestDisallowInterceptTouchEvent(true)

                                    dragInitHandler.removeCallbacksAndMessages(null)
                                    dragInitHandler.postDelayed({
                                        isDraggingInitiated = true
                                        runOnUiThread {
                                            startDraggingRhyme(currentDragWord, event)
                                        }
                                    }, ViewConfiguration.getLongPressTimeout().toLong())
                                    true
                                }

                                MotionEvent.ACTION_MOVE -> {
                                    if (isDraggingInitiated) {
                                        updateCursorPosition(event)
                                        true
                                    } else {
                                        true
                                    }
                                }

                                MotionEvent.ACTION_UP,
                                MotionEvent.ACTION_CANCEL -> {
                                    dragInitHandler.removeCallbacksAndMessages(null)
                                    scrollView.requestDisallowInterceptTouchEvent(false)

                                    if (isDraggingInitiated) {
                                        dropRhyme(event)
                                        isDraggingInitiated = false
                                        true
                                    } else {
                                        false
                                    }
                                }

                                else -> false
                            }
                        }
                    }
                    container.addView(textView)
                }
            }
        }

        closeBtn.setOnClickListener {
            rhymePopup?.dismiss()
        }

        rhymePopup?.showAtLocation(binding.root, Gravity.BOTTOM, 0, 0)
    }

    private fun getKeyboardHeight(): Int {
        val rect = android.graphics.Rect()
        window.decorView.getWindowVisibleDisplayFrame(rect)
        val screenHeight = window.decorView.height
        return screenHeight - rect.bottom
    }

    private fun Char.isPunctuation(): Boolean {
        return this in setOf('.', ',', '!', '?', ':', ';', '-', '—', '(', ')', '"', '\'', '…', '[', ']', '{', '}')
    }

    private fun startDraggingRhyme(word: String, event: MotionEvent) {
        draggedRhyme = word
        isDragging = true

        val dragTextView = TextView(this).apply {
            text = word
            setTextColor(Color.BLACK)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setBackgroundResource(R.drawable.rhyme_word_background)
            setPadding(16, 8, 16, 8)
        }

        dragView = dragTextView

        val popup = PopupWindow(
            dragTextView,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            isFocusable = false
            isOutsideTouchable = false
            setBackgroundDrawable(null)
            elevation = 8f
        }

        dragWindow = popup
        popup.showAtLocation(binding.root, Gravity.NO_GRAVITY,
            (event.rawX - 30).toInt(),
            (event.rawY - 30).toInt()
        )
    }

    private fun updateCursorPosition(event: MotionEvent) {
        if (!isDragging || dragWindow == null) return

        dragWindow?.update(
            (event.rawX - 30).toInt(),
            (event.rawY - 30).toInt(),
            -1,
            -1
        )

        val editText = binding.contentEditText
        val location = IntArray(2)
        editText.getLocationOnScreen(location)

        val x = event.rawX - location[0]
        val y = event.rawY - location[1]

        if (x >= 0 && x <= editText.width && y >= 0 && y <= editText.height) {
            val layout = editText.layout ?: return
            val line = layout.getLineForVertical(y.toInt())
            val offset = layout.getOffsetForHorizontal(line, x.toFloat())
            editText.setSelection(offset)
        }
    }

    private fun dropRhyme(event: MotionEvent) {
        if (!isDragging) return

        dragWindow?.dismiss()
        dragWindow = null
        dragView = null

        val editText = binding.contentEditText
        val location = IntArray(2)
        editText.getLocationOnScreen(location)

        val x = event.rawX - location[0]
        val y = event.rawY - location[1]

        if (x >= 0 && x <= editText.width && y >= 0 && y <= editText.height) {
            val word = draggedRhyme ?: return
            val cursorPos = editText.selectionStart
            val text = editText.text

            val needsSpace = cursorPos > 0 && cursorPos < text.length &&
                    !text[cursorPos - 1].isWhitespace() &&
                    !text[cursorPos - 1].isPunctuation()

            val toInsert = if (needsSpace) " $word" else word
            text.insert(cursorPos, toInsert)
            editText.setSelection(cursorPos + toInsert.length)
        }

        draggedRhyme = null
        isDragging = false
    }

    private fun countSyllables(text: String): Int {
        val vowels = "аеёиоуыэюяАЕЁИОУЫЭЮЯ"
        return text.count { it in vowels }
    }

    private val syllableHintRegex = """\s*·[0-9]+\s*$""".toRegex()

    private fun stripSyllableHints(text: String): String {
        return text.lines().joinToString("\n") { line ->
            line.replace(syllableHintRegex, "")
        }
    }

    private fun addSyllableHints(text: String): String {
        return text.split('\n').joinToString("\n") { line ->
            val cleanLine = line.replace(syllableHintRegex, "")
            if (cleanLine.isBlank()) {
                cleanLine
            } else {
                val count = countSyllables(cleanLine)
                "$cleanLine ·$count"
            }
        }
    }
}