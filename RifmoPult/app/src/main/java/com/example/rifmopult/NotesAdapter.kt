package com.example.rifmopult

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.rifmopult.databinding.ItemNoteBinding

///реализует адаптер для RecyclerView
class NotesAdapter(
    private val notes: List<Note>,
    private val onItemClick: (Note) -> Unit
) : RecyclerView.Adapter<NotesAdapter.NoteViewHolder>() {

    ///RecyclerView.ViewHolder хранит ссылки на переиспользуемые вьюшки
    class NoteViewHolder(private val binding: ItemNoteBinding) : RecyclerView.ViewHolder(binding.root) {
        ///подготавливает один элемент списка (item) к отображению
        fun bind(note: Note, onItemClick: (Note) -> Unit) {
            if (!note.title.isNullOrBlank()) {
                binding.noteTitleTextView.visibility = View.VISIBLE
                binding.noteTitleTextView.text = note.title
            } else {
                binding.noteTitleTextView.visibility = View.GONE
            }

            binding.noteContentTextView.text = note.content
            binding.noteDateTextView.text = note.date

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
}