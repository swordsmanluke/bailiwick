package com.perfectlunacy.bailiwick.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.perfectlunacy.bailiwick.R
import com.perfectlunacy.bailiwick.models.db.Circle

/**
 * Adapter for displaying circles with checkboxes to manage user membership.
 */
class CircleMembershipAdapter(
    private val context: Context,
    private var circles: List<Circle>,
    private var memberOfCircleIds: Set<Long>,
    private val onMembershipChanged: (Circle, Boolean) -> Unit
) : RecyclerView.Adapter<CircleMembershipAdapter.CircleMembershipViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CircleMembershipViewHolder {
        val view = LayoutInflater.from(context)
            .inflate(R.layout.item_circle_membership, parent, false)
        return CircleMembershipViewHolder(view)
    }

    override fun onBindViewHolder(holder: CircleMembershipViewHolder, position: Int) {
        val circle = circles[position]
        val isMember = memberOfCircleIds.contains(circle.id)
        holder.bind(circle, isMember)
    }

    override fun getItemCount(): Int = circles.size

    fun updateMembership(circleId: Long, isMember: Boolean) {
        memberOfCircleIds = if (isMember) {
            memberOfCircleIds + circleId
        } else {
            memberOfCircleIds - circleId
        }
        val position = circles.indexOfFirst { it.id == circleId }
        if (position >= 0) {
            notifyItemChanged(position)
        }
    }

    inner class CircleMembershipViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val checkBox: CheckBox = itemView.findViewById(R.id.checkbox_circle)
        private val txtCircleName: TextView = itemView.findViewById(R.id.txt_circle_name)

        fun bind(circle: Circle, isMember: Boolean) {
            txtCircleName.text = circle.name
            checkBox.isChecked = isMember

            // Handle click on whole row
            itemView.setOnClickListener {
                val newState = !checkBox.isChecked
                checkBox.isChecked = newState
                onMembershipChanged(circle, newState)
            }

            // Also handle direct checkbox clicks
            checkBox.setOnClickListener {
                onMembershipChanged(circle, checkBox.isChecked)
            }
        }
    }
}
