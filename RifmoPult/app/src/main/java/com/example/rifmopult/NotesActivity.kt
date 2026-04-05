/*
 * Rifmopult – поэтический редактор с подбором рифм
 * Copyright (c) 2025 Arina Viktorovna Bogdanova
 *
 * MIT License
 */

package com.example.rifmopult

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.rifmopult.databinding.ActivityNotesBinding
import kotlinx.coroutines.launch

class NotesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNotesBinding
    private lateinit var notesAdapter: NotesAdapter
    private val allNotes = mutableListOf<Note>()
    private val filteredNotes = mutableListOf<Note>()

    private lateinit var db: AppDatabase
    private lateinit var noteDao: NoteDao

    private val noteEditLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        handleNoteEditResult(result)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNotesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        AnalyticsHelper.trackScreenOpen("NotesActivity")

        db = AppDatabase.getDatabase(this)
        noteDao = db.noteDao()
        setupRecyclerView()
        setupClickListeners()
        setupSearch()

        loadNotesFromDatabase()
    }

    private fun setupRecyclerView() {
        val staggeredGrid = androidx.recyclerview.widget.StaggeredGridLayoutManager(2, androidx.recyclerview.widget.StaggeredGridLayoutManager.VERTICAL)
        binding.notesRecyclerView.layoutManager = staggeredGrid

        notesAdapter = NotesAdapter(
            filteredNotes,
            onItemClick = { note ->
                AnalyticsHelper.trackNoteOpened(note.content.length)
                openNoteDetail(note) },
            onItemLongClick = { note ->
                showDeleteDialog(note)
                true
            }
        )
        binding.notesRecyclerView.adapter = notesAdapter
    }

    private fun showDeleteDialog(note: Note) {
        AlertDialog.Builder(this)
            .setTitle("Удалить стих?")
            .setMessage("Вы уверены, что хотите удалить «${note.title.ifEmpty { "Без названия" }}»?")
            .setPositiveButton("Удалить") { _, _ ->
                val noteAgeHours = try {
                    val dateLong = note.date.toLongOrNull()
                    if (dateLong != null) {
                        (System.currentTimeMillis() - dateLong) / (1000 * 60 * 60)
                    } else {
                        0L
                    }
                } catch (e: Exception) {
                    0L
                }

                AnalyticsHelper.trackNoteDeleted(
                    noteLength = note.content.length,
                    noteAgeHours = noteAgeHours
                )

                lifecycleScope.launch {
                    noteDao.deleteNote(note.toEntity())
                    loadNotesFromDatabase()
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun setupClickListeners() {
        binding.fabAddNote.setOnClickListener {
            AnalyticsHelper.trackButtonClick("fab_add_note")
            createNewNote()
        }

        binding.btnSettings.setOnClickListener {
            AnalyticsHelper.trackButtonClick("btn_settings")
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    private fun setupSearch() {
        binding.searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterNotes(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    @SuppressLint("NotifyDataSetChanged")
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

            if (query.isNotBlank()) {
                AnalyticsHelper.trackSearchPerformed(
                    queryLength = query.length,
                    resultsCount = filteredNotes.size
                )
            }
        }
        notesAdapter.notifyDataSetChanged()
    }



    private var isFirstLoad = true

    @SuppressLint("NotifyDataSetChanged")
    private fun loadNotesFromDatabase() {
        lifecycleScope.launch {
            val notesFromDb = noteDao.getAllNotesSortedByDateDesc()
            allNotes.clear()
            allNotes.addAll(notesFromDb.map { it.toNote() })
            filteredNotes.clear()
            filteredNotes.addAll(allNotes)
            notesAdapter.notifyDataSetChanged()

            if (isFirstLoad && allNotes.isEmpty()) {
                isFirstLoad = false
                createNewNote()
            }
        }
    }

    private fun createNewNote() {
        AnalyticsHelper.trackEvent("note_creation_started")

        val intent = Intent(this, NoteEditActivity::class.java)
        noteEditLauncher.launch(intent)
    }

    private fun openNoteDetail(note: Note) {
        val intent = Intent(this, NoteEditActivity::class.java).apply {
            putExtra(NoteEditActivity.EXTRA_NOTE, note)
        }
        noteEditLauncher.launch(intent)
    }

    private fun handleNoteEditResult(result: androidx.activity.result.ActivityResult) {
        when (result.resultCode) {
            Activity.RESULT_OK -> {
                val updatedNote = result.data?.getSerializableExtra(NoteEditActivity.EXTRA_NOTE_RESULT) as? Note
                updatedNote?.let { note ->
                    val cleanNote = note.copy(content = stripSyllableHints(note.content))
                    lifecycleScope.launch {
                        noteDao.insertNote(cleanNote.toEntity())
                        loadNotesFromDatabase()
                    }
                }
            }
            NoteEditActivity.RESULT_DELETED -> {
                val deletedNote = result.data?.getSerializableExtra(NoteEditActivity.EXTRA_NOTE_RESULT) as? Note
                deletedNote?.let { note ->
                    lifecycleScope.launch {
                        noteDao.deleteNote(note.toEntity())
                        loadNotesFromDatabase()
                    }
                }
            }
        }
    }
    private fun stripSyllableHints(text: String): String {
        val syllableHintRegex = """\s*·[0-9]+\s*$""".toRegex()
        return text.lines()
            .map { it.replace(syllableHintRegex, "") }
            .joinToString("\n")
    }
}