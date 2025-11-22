package com.example.rifmopult

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.content.Context
import android.view.inputmethod.InputMethodManager
import android.view.MenuItem
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.example.rifmopult.databinding.ActivityNoteEditBinding
import java.text.SimpleDateFormat
import java.util.*

class NoteEditActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNoteEditBinding
    private var currentNote: Note? = null
    private var isNewNote = false

    companion object {
        const val EXTRA_NOTE = "extra_note"
        const val EXTRA_NOTE_RESULT = "extra_note_result"
        const val RESULT_DELETED = 2
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)//реализация родительского метода который умее высстанавливать если временно закрывали
        binding = ActivityNoteEditBinding.inflate(layoutInflater)//оздание класса с вьюшками который будет хранить ссылки на них и читаются из XML файла
        setContentView(binding.root)//отрисовка интерфейса

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleExitWithAutoSave()
            }
        })

        loadNoteData()//чтение чего-то там из бд или активи
        setupToolbar()//настройка элементов интерфейса(не только красивые но и функциональные)

    }

    private fun requestFocusAndShowKeyboard() {
        binding.contentEditText.requestFocus()
        binding.contentEditText.post {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(binding.contentEditText, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun setupToolbar() {
        binding.icRetarn.setOnClickListener {//устанавливает обработчик клика кнопки назад
            handleExitWithAutoSave()//метод обработки если на нее нажали
        }
    }

    private fun loadNoteData() {
        currentNote = intent.getSerializableExtra(EXTRA_NOTE) as? Note

        if (currentNote == null) {
            // Создаем новую заметку
            isNewNote = true
            currentNote = Note(
                id = System.currentTimeMillis(),
                title = "",
                content = "",
                date = getCurrentDate()
            )
        } else {
            // Загружаем существующую заметку
            isNewNote = false
            binding.titleEditText.setText(currentNote?.title ?: "")
            binding.contentEditText.setText(currentNote?.content ?: "")
        }
    }

    private fun getCurrentDate(): String {
        val sdf = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
        return sdf.format(Date())
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
            android.R.id.home -> {
                handleExitWithAutoSave()
                true
            }
            R.id.action_save -> {
                saveNote()
                true
            }
            R.id.action_delete -> {
                deleteNote()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun saveNote() {
        val hasRealChanges = hasNoteContentChanged()

        val updatedNote = currentNote?.copy(
            title = binding.titleEditText.text.toString(), //класс.контейнер.вытащить из него текст.тустринг
            content = binding.contentEditText.text.toString(),
            date = if (hasRealChanges) getCurrentDate() else currentNote?.date ?: getCurrentDate() //?: это "если левая часть не ноль вернуть ее иначе вернуть правую
        )

        val resultIntent = Intent().apply { //Intent (конверт для передачи). а .apply - это функция-расширение, позволяет настроить объект после создания.
            putExtra(EXTRA_NOTE_RESULT, updatedNote) //путэкстра добавляет данные (тут просто кладем в канверт данные)
        }
        setResult(Activity.RESULT_OK, resultIntent) //сообщаем предыдущей активности чем закончилось
        finish() //закрыть текущую активность и вернуться к предыдущей
    }

    private fun hasNoteContentChanged(): Boolean {
        val titleChanged = currentNote?.title != binding.titleEditText.text.toString()
        val contentChanged = currentNote?.content != binding.contentEditText.text.toString()
        return titleChanged || contentChanged
    }

    private fun deleteNote() {
        val resultIntent = Intent().apply {
            putExtra(EXTRA_NOTE_RESULT, currentNote)
        }
        setResult(RESULT_DELETED, resultIntent)
        finish()
    }

    private fun handleExitWithAutoSave() { //выход с авто сохранением (никакого отношения с кнопкой "откат от последних изменений" не имеет)
        val titleChanged = currentNote?.title != binding.titleEditText.text.toString()
        val contentChanged = currentNote?.content != binding.contentEditText.text.toString()

        if (titleChanged || contentChanged) {
            saveNote()
        } else {
            setResult(Activity.RESULT_CANCELED)
            finish()
        }
    }

}
