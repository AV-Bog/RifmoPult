package com.example.rifmopult

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.rifmopult.databinding.ItemNoteBinding

class NotesAdapter(
    private val notes: List<Note>,
    private val onItemClick: (Note) -> Unit
) : RecyclerView.Adapter<NotesAdapter.NoteViewHolder>() {

    class NoteViewHolder(private val binding: ItemNoteBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(note: Note, onItemClick: (Note) -> Unit) {
            if (!note.title.isNullOrBlank()) {
                binding.noteTitleTextView.visibility = View.VISIBLE
                binding.noteTitleTextView.text = note.title
            } else {
                binding.noteTitleTextView.visibility = View.GONE
            }

            binding.noteContentTextView.text = note.content
            binding.noteDateTextView.text = DateUtils.formatForDisplay(note.date)

            itemView.setOnClickListener {
                onItemClick(note)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NoteViewHolder {
        val binding = ItemNoteBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return NoteViewHolder(binding)
    }

    override fun onBindViewHolder(holder: NoteViewHolder, position: Int) {
        holder.bind(notes[position], onItemClick)
    }

    override fun getItemCount(): Int = notes.size

    object DateUtils {
        fun formatForDisplay(storedDate: String): String {
            return try {
                val inputFormat = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
                val outputFormat = java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.getDefault())
                val date = inputFormat.parse(storedDate)
                outputFormat.format(date ?: java.util.Date())
            } catch (e: Exception) {
                storedDate
            }
        }
    }
}