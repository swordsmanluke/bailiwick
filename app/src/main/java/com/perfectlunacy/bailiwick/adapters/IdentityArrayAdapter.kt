package com.perfectlunacy.bailiwick.adapters

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import com.perfectlunacy.bailiwick.models.ipfs.Identity

class IdentityArrayAdapter(context: Context, items: List<Identity>): ArrayAdapter<Identity>(context, android.R.layout.simple_spinner_dropdown_item, items) {
}