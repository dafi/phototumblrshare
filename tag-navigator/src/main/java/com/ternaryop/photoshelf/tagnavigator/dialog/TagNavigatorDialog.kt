package com.ternaryop.photoshelf.tagnavigator.dialog

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.ternaryop.photoshelf.api.post.TagInfo
import com.ternaryop.photoshelf.api.post.toTagInfo
import com.ternaryop.photoshelf.tagnavigator.R
import com.ternaryop.photoshelf.tagnavigator.adapter.TagNavigatorAdapter
import com.ternaryop.photoshelf.tagnavigator.adapter.TagNavigatorListener
import com.ternaryop.photoshelf.tagnavigator.databinding.DialogTagNavigatorBinding

/**
 * Created by dave on 17/05/15.
 * Allow to select tag
 */
private const val SORT_TAG_NAME = 0
private const val SORT_TAG_COUNT = 1

private const val ARG_TAG_LIST = "list"
private const val PREF_NAME_TAG_SORT = "tagNavigatorSort"

class TagNavigatorDialog : BottomSheetDialogFragment(), TagNavigatorListener {
    private lateinit var adapter: TagNavigatorAdapter
    private var _binding: DialogTagNavigatorBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        _binding = DialogTagNavigatorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = TagNavigatorAdapter(requireContext(),
            arguments?.getStringArrayList(ARG_TAG_LIST)?.toTagInfo() ?: emptyList(),
            "",
            this)
        binding.tagList.setHasFixedSize(true)
        binding.tagList.layoutManager = LinearLayoutManager(activity)
        binding.tagList.adapter = adapter

        binding.distinctTagCount.text = String.format("%d", adapter.itemCount)
        binding.distinctTagTitle.text = resources.getString(R.string.tag_navigator_distinct_title)

        val preferences = PreferenceManager.getDefaultSharedPreferences(activity)
        changeSortType(preferences.getInt(PREF_NAME_TAG_SORT, SORT_TAG_NAME))
        binding.sortTag.setOnClickListener { v ->
            when (v.id) {
                R.id.sort_tag -> {
                    var sortType = preferences.getInt(PREF_NAME_TAG_SORT, SORT_TAG_NAME)
                    sortType = if (sortType == SORT_TAG_NAME) SORT_TAG_COUNT else SORT_TAG_NAME
                    preferences.edit().putInt(PREF_NAME_TAG_SORT, sortType).apply()
                    changeSortType(sortType)
                }
            }
        }
    }

    override fun onClick(item: TagInfo) {
        targetFragment?.let { target ->
            val intent = Intent().putExtra(EXTRA_SELECTED_TAG, item.tag)
            target.onActivityResult(targetRequestCode, Activity.RESULT_OK, intent)
        }
        dismiss()
    }

    private fun changeSortType(sortType: Int) {
        when (sortType) {
            SORT_TAG_NAME -> {
                binding.sortTag.setText(R.string.sort_by_count)
                adapter.sortByTagName()
            }
            SORT_TAG_COUNT -> {
                binding.sortTag.setText(R.string.sort_by_name)
                adapter.sortByTagCount()
            }
        }
    }

    companion object {
        const val EXTRA_SELECTED_TAG = "selectedTag"

        fun newInstance(tagList: ArrayList<String>, target: Fragment, requestCode: Int): TagNavigatorDialog {
            val args = Bundle()
            args.putStringArrayList(ARG_TAG_LIST, tagList)

            val fragment = TagNavigatorDialog()
            fragment.arguments = args
            fragment.setTargetFragment(target, requestCode)
            return fragment
        }
    }
}
