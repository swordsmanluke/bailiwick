package com.perfectlunacy.bailiwick.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.perfectlunacy.bailiwick.R
import com.perfectlunacy.bailiwick.models.db.Circle

/**
 * Adapter for displaying circles a contact is a member of, with remove functionality.
 */
class ContactCircleAdapter(
    private val context: Context,
    private var circles: MutableList<Circle>,
    private val onRemoveClicked: (Circle, Int) -> Unit
) : RecyclerView.Adapter<ContactCircleAdapter.CircleViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CircleViewHolder {
        val view = LayoutInflater.from(context)
            .inflate(R.layout.item_contact_circle, parent, false)
        return CircleViewHolder(view)
    }

    override fun onBindViewHolder(holder: CircleViewHolder, position: Int) {
        val circle = circles[position]
        holder.bind(circle) {
            onRemoveClicked(circle, position)
        }
    }

    override fun getItemCount(): Int = circles.size

    fun removeCircle(position: Int) {
        if (position in circles.indices) {
            circles.removeAt(position)
            notifyItemRemoved(position)
            notifyItemRangeChanged(position, circles.size - position)
        }
    }

    fun addCircle(circle: Circle) {
        circles.add(circle)
        circles.sortBy { it.name }
        val newPosition = circles.indexOf(circle)
        notifyItemInserted(newPosition)
    }

    fun getCircles(): List<Circle> = circles.toList()

    inner class CircleViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val txtCircleName: TextView = itemView.findViewById(R.id.txt_circle_name)
        private val btnRemove: ImageButton = itemView.findViewById(R.id.btn_remove)

        fun bind(circle: Circle, onRemove: () -> Unit) {
            txtCircleName.text = circle.name
            btnRemove.setOnClickListener {
                onRemove()
            }
        }
    }
}
