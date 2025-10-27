package com.example.rifmopult

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.rifmopult.databinding.ActivityNotesBinding
import com.example.rifmopult.databinding.ItemNoteBinding

class NotesActivity : AppCompatActivity() {

    // Привязка для доступа к элементам интерфейса
    private lateinit var binding: ActivityNotesBinding
    // Адаптер для отображения списка заметок
    private lateinit var notesAdapter: NotesAdapter
    // Полный список всех заметок
    private val allNotes = mutableListOf<Note>()
    // Список заметок после фильтрации (то что показывается пользователю)
    private val filteredNotes = mutableListOf<Note>()

    // Основной метод создания активности
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Инициализация привязки и установка макета
        binding = ActivityNotesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        setupClickListeners()
        setupSearch()
        loadSampleNotes()
    }

    // Настройка RecyclerView для отображения заметок в виде сетки
    private fun setupRecyclerView() {
        val gridLayoutManager = GridLayoutManager(this, 2)
        binding.notesRecyclerView.layoutManager = gridLayoutManager

        notesAdapter = NotesAdapter(filteredNotes) { note ->
            openNoteDetail(note)
        }
        binding.notesRecyclerView.adapter = notesAdapter
    }

    // Настройка обработчиков нажатий на кнопки
    private fun setupClickListeners() {
        binding.fabAddNote.setOnClickListener {
            createNewNote()
        }
    }

    // Настройка поиска по заметкам
    private fun setupSearch() {
        binding.searchEditText.addTextChangedListener(object : TextWatcher {
            // ДО изменения текста
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            // ВО ВРЕМЯ изменения текста
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterNotes(s.toString())
            }

            // ПОСЛЕ изменения текста
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    // Фильтрация заметок по поисковому запросу
    private fun filterNotes(query: String) {
        filteredNotes.clear()

        if (query.isEmpty()) {
            filteredNotes.addAll(allNotes)
        } else {
            val lowerCaseQuery = query.lowercase()
            allNotes.forEach { note ->
                if (note.title.lowercase().contains(lowerCaseQuery) ||
                    note.content.lowercase().contains(lowerCaseQuery)) {
                    filteredNotes.add(note)
                }
            }
        }
        notesAdapter.notifyDataSetChanged()
    }

    // для демонстрации
    private fun loadSampleNotes() {
        allNotes.addAll(listOf(
            Note(1, "Все проходит", "Все проходит в этом мире\n" +
                    "Снег сменяется дождем\n" +
                    "Все проходит, все проходит,\n" +
                    "Мы пришли и мы уйдем!", "12.01.2024"),
            Note(2, "Па па пам", "Прошлые сутки ты провела с другим человеком\n" +
                "И за эти короткие сутки я стал колекой\n" +
                "Я стал корявым деревом с обозженной корой\n" +
                "А ты сушишь свои волосы перед встречей со мной", "11.01.2024"),
            Note(3, "", "Мне нравится, что вы больны не мной\n" +
                "Мне нравится, что я больна не вами", "10.01.2024"),
        ))
        filteredNotes.addAll(allNotes)
        notesAdapter.notifyDataSetChanged()
    }

    private fun createNewNote() {
        val newNote = Note(
            id = System.currentTimeMillis(),
            title = "",
            content = "Новая заметка...",
            date = "Сегодня"
        )
        allNotes.add(0, newNote)
        filterNotes(binding.searchEditText.text.toString())
        openNoteDetail(newNote)
    }

    // Открытие деталей заметки
    private fun openNoteDetail(note: Note) {
        // Для демонстрации. ДОПИСАТЬ
        android.widget.Toast.makeText(
            this,
            "Открыта заметка: ${if (note.title.isEmpty()) "Без названия" else note.title}",
            android.widget.Toast.LENGTH_SHORT
        ).show()
    }
}

// Заметка
data class Note(
    val id: Long,
    val title: String,
    val content: String,
    val date: String
)

// для отображения списка заметок в RecyclerView
class NotesAdapter(
    private val notes: List<Note>,
    private val onItemClick: (Note) -> Unit
) : RecyclerView.Adapter<NotesAdapter.NoteViewHolder>() {

    // Класс для хранения представления одного элемента списка
    class NoteViewHolder(private val binding: ItemNoteBinding) : RecyclerView.ViewHolder(binding.root) {

        // Связывание данных заметки с элементами интерфейса
        fun bind(note: Note, onItemClick: (Note) -> Unit) {
            // Обработка отображения заголовка
            if (note.title.isNotEmpty()) {
                binding.noteTitleTextView.visibility = View.VISIBLE
                binding.emptyTitleTextView.visibility = View.GONE
                binding.noteTitleTextView.text = note.title
            } else {
                binding.noteTitleTextView.visibility = View.GONE
            }

            binding.noteContentTextView.text = note.content

            itemView.setOnClickListener {
                onItemClick(note)
            }
        }
    }

    // Создание нового ViewHolder
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NoteViewHolder {
        // Создаем привязку для элемента списка
        val binding = ItemNoteBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return NoteViewHolder(binding)
    }

    // Связывание данных с существующим ViewHolder
    override fun onBindViewHolder(holder: NoteViewHolder, position: Int) {
        holder.bind(notes[position], onItemClick)
    }

    // Возвращает количество элементов в списке
    override fun getItemCount() = notes.size
}