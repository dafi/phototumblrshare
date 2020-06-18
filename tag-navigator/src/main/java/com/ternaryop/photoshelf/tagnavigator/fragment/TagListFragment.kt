package com.ternaryop.photoshelf.tagnavigator.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.ternaryop.photoshelf.activity.ImageViewerActivityStarter
import com.ternaryop.photoshelf.activity.TagPhotoBrowserData
import com.ternaryop.photoshelf.api.post.TagInfo
import com.ternaryop.photoshelf.tagnavigator.adapter.TagNavigatorAdapter
import com.ternaryop.photoshelf.tagnavigator.adapter.TagNavigatorListener
import com.ternaryop.photoshelf.tagnavigator.databinding.FragmentTagListBinding

class TagListFragment(
    private val imageViewerActivityStarter: ImageViewerActivityStarter
) : Fragment(), TagNavigatorListener {
    private var blogName = ""
    private var _binding: FragmentTagListBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentTagListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        blogName = checkNotNull(arguments?.getString(ARG_BLOG_NAME)) { "Invalid blog name" }

        val adapter = TagNavigatorAdapter(
            requireContext(),
            emptyList(),
            blogName,
            this)

        binding.tagList.adapter = adapter
        binding.tagList.setHasFixedSize(true)
        binding.tagList.layoutManager = LinearLayoutManager(activity)

        // start with list filled
        adapter.filter.filter("")
        binding.searchView1
            .setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String): Boolean {
                    return false
                }

                override fun onQueryTextChange(newText: String): Boolean {
                    adapter.filter.filter(newText)
                    return true
                }
            })
    }

    override fun onClick(item: TagInfo) {
        imageViewerActivityStarter.startTagPhotoBrowser(requireContext(),
            TagPhotoBrowserData(blogName, item.tag, false))
    }

    companion object {
        const val ARG_BLOG_NAME = "blogName"
    }
}
