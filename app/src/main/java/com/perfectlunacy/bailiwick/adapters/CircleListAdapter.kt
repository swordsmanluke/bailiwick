package com.perfectlunacy.bailiwick.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.perfectlunacy.bailiwick.R
import com.perfectlunacy.bailiwick.fragments.CirclesFragment.CircleWithMemberCount
import com.perfectlunacy.bailiwick.models.db.Circle

/**
 * Adapter for displaying circles in a list with member counts.
 */
class CircleListAdapter(
    private val context: Context,
    private var circles: List<CircleWithMemberCount>,
    private val onCircleClicked: (Circle) -> Unit
) : RecyclerView.Adapter<CircleListAdapter.CircleViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CircleViewHolder {
        val view = LayoutInflater.from(context)
            .inflate(R.layout.item_circle_list, parent, false)
        return CircleViewHolder(view)
    }

    override fun onBindViewHolder(holder: CircleViewHolder, position: Int) {
        val circleWithCount = circles[position]
        holder.bind(circleWithCount)
    }

    override fun getItemCount(): Int = circles.size

    fun updateCircles(newCircles: List<CircleWithMemberCount>) {
        circles = newCircles
        notifyDataSetChanged()
    }

    inner class CircleViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val txtCircleName: TextView = itemView.findViewById(R.id.txt_circle_name)
        private val txtMemberCount: TextView = itemView.findViewById(R.id.txt_member_count)

        fun bind(circleWithCount: CircleWithMemberCount) {
            txtCircleName.text = circleWithCount.circle.name

            val memberText = context.resources.getQuantityString(
                R.plurals.member_count,
                circleWithCount.memberCount,
                circleWithCount.memberCount
            )
            txtMemberCount.text = memberText

            itemView.setOnClickListener {
                onCircleClicked(circleWithCount.circle)
            }
        }
    }
}
