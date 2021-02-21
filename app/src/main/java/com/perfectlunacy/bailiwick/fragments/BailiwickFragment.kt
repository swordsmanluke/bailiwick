package com.perfectlunacy.bailiwick.fragments

import androidx.fragment.app.Fragment
import com.perfectlunacy.bailiwick.BailiwickActivity
import com.perfectlunacy.bailiwick.viewmodels.BailiwickViewModel

// Helper class to get easy access to the ViewModel
abstract class BailiwickFragment: Fragment() {
    protected val bwModel: BailiwickViewModel
        get() = (activity as BailiwickActivity).bwModel
}