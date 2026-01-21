package com.perfectlunacy.bailiwick.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.viewModelScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.perfectlunacy.bailiwick.R
import com.perfectlunacy.bailiwick.adapters.CircleListAdapter
import com.perfectlunacy.bailiwick.databinding.FragmentCirclesBinding
import com.perfectlunacy.bailiwick.models.db.Circle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Fragment for displaying a list of all circles with member counts.
 */
class CirclesFragment : BailiwickFragment() {

    private var _binding: FragmentCirclesBinding? = null
    private val binding get() = _binding!!

    private var circleListAdapter: CircleListAdapter? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DataBindingUtil.inflate(
            inflater,
            R.layout.fragment_circles,
            container,
            false
        )

        setupHeader()
        setupCirclesList()
        loadCircles()

        return binding.root
    }

    private fun setupHeader() {
        binding.btnBack.setOnClickListener {
            findNavController().popBackStack()
        }
    }

    private fun setupCirclesList() {
        binding.listCircles.layoutManager = LinearLayoutManager(requireContext())
    }

    private fun loadCircles() {
        bwModel.viewModelScope.launch {
            val circlesWithCounts = withContext(Dispatchers.Default) {
                // Get all circles
                val circles = bwModel.network.circles

                // Get member counts for each circle
                circles.map { circle ->
                    val memberCount = bwModel.db.circleMemberDao()
                        .membersFor(circle.id)
                        .size
                    CircleWithMemberCount(circle, memberCount)
                }
            }

            if (circlesWithCounts.isEmpty()) {
                binding.listCircles.visibility = View.GONE
                binding.txtNoCircles.visibility = View.VISIBLE
            } else {
                binding.listCircles.visibility = View.VISIBLE
                binding.txtNoCircles.visibility = View.GONE

                circleListAdapter = CircleListAdapter(
                    requireContext(),
                    circlesWithCounts
                ) { circle ->
                    onCircleClicked(circle)
                }
                binding.listCircles.adapter = circleListAdapter
            }
        }
    }

    private fun onCircleClicked(circle: Circle) {
        // TODO: Navigate to circle detail or filter content by circle
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "CirclesFragment"
    }

    /**
     * Data class to hold circle with its member count.
     */
    data class CircleWithMemberCount(
        val circle: Circle,
        val memberCount: Int
    )
}
