package com.perfectlunacy.bailiwick.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.perfectlunacy.bailiwick.R
import com.perfectlunacy.bailiwick.models.db.Circle

/**
 * Adapter for displaying circle filter chips in a horizontal RecyclerView.
 */
class CircleFilterAdapter(
    private val context: Context,
    private var circles: List<Circle>,
    private var selectedCircleId: Long? = null,
    private val onCircleSelected: (Circle?) -> Unit,
    private val onCircleLongPress: ((Circle) -> Unit)? = null
) : RecyclerView.Adapter<CircleFilterAdapter.CircleViewHolder>() {

    // Virtual option to show posts from all circles (clears the filter)
    private val showAllOption = Circle("Show All", -1, null).apply { id = -1 }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CircleViewHolder {
        val view = LayoutInflater.from(context)
            .inflate(R.layout.item_circle_filter, parent, false)
        return CircleViewHolder(view)
    }

    override fun onBindViewHolder(holder: CircleViewHolder, position: Int) {
        val circle = if (position == 0) showAllOption else circles[position - 1]
        holder.bind(circle, circle.id == (selectedCircleId ?: -1))
    }

    override fun getItemCount(): Int = circles.size + 1  // +1 for "Show All" option

    fun updateCircles(newCircles: List<Circle>) {
        circles = newCircles
        notifyDataSetChanged()
    }

    fun setSelectedCircle(circleId: Long?) {
        selectedCircleId = circleId
        notifyDataSetChanged()
    }

    inner class CircleViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textCircleName: TextView = itemView.findViewById(R.id.textCircleName)

        fun bind(circle: Circle, isSelected: Boolean) {
            textCircleName.text = circle.name

            // Apply selection styling
            val backgroundColor = if (isSelected) {
                ContextCompat.getColor(context, R.color.circleFilterActive)
            } else {
                ContextCompat.getColor(context, R.color.circleFilterInactive)
            }

            val textColor = if (isSelected) {
                ContextCompat.getColor(context, R.color.colorPrimaryDark)
            } else {
                ContextCompat.getColor(context, R.color.colorTextSecondary)
            }

            itemView.setBackgroundColor(backgroundColor)
            textCircleName.setTextColor(textColor)

            itemView.setOnClickListener {
                val newSelection = if (circle.id == -1L) null else circle.id
                setSelectedCircle(newSelection)
                onCircleSelected(if (circle.id == -1L) null else circle)
            }

            // Long-press to edit circle (not available for "All" option)
            if (circle.id != -1L && onCircleLongPress != null) {
                itemView.setOnLongClickListener {
                    onCircleLongPress.invoke(circle)
                    true
                }
            } else {
                itemView.setOnLongClickListener(null)
            }
        }
    }
}
