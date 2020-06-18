package com.ternaryop.photoshelf.tumblr.dialog.editor.fragment

import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.ActionBar
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.ternaryop.photoshelf.lifecycle.EventObserver
import com.ternaryop.photoshelf.lifecycle.Status
import com.ternaryop.photoshelf.mru.adapter.MRUHolder
import com.ternaryop.photoshelf.tumblr.dialog.EditPostEditorData
import com.ternaryop.photoshelf.tumblr.dialog.NewPostEditorData
import com.ternaryop.photoshelf.tumblr.dialog.PostEditorData
import com.ternaryop.photoshelf.tumblr.dialog.PostViewModel
import com.ternaryop.photoshelf.tumblr.dialog.R
import com.ternaryop.photoshelf.tumblr.dialog.TumblrPostDialog.Companion.ARG_POST_DATA
import com.ternaryop.photoshelf.tumblr.dialog.TumblrPostDialog.Companion.EXTRA_MAX_HIGHLIGHTED_TAGS_MRU_ITEMS
import com.ternaryop.photoshelf.tumblr.dialog.TumblrPostDialog.Companion.EXTRA_MAX_TAGS_MRU_ITEMS
import com.ternaryop.photoshelf.tumblr.dialog.TumblrPostDialog.Companion.EXTRA_THUMBNAILS_ITEMS
import com.ternaryop.photoshelf.tumblr.dialog.TumblrPostDialog.Companion.EXTRA_THUMBNAILS_SIZE
import com.ternaryop.photoshelf.tumblr.dialog.TumblrPostModelResult
import com.ternaryop.photoshelf.tumblr.dialog.databinding.FragmentTumblrPostBinding
import com.ternaryop.photoshelf.tumblr.dialog.editor.AbsTumblrPostEditor
import com.ternaryop.photoshelf.tumblr.dialog.editor.EditTumblrPostEditor
import com.ternaryop.photoshelf.tumblr.dialog.editor.NewTumblrPostEditor
import com.ternaryop.photoshelf.tumblr.dialog.editor.adapter.ThumbnailAdapter
import com.ternaryop.photoshelf.tumblr.dialog.editor.finishActivity
import com.ternaryop.photoshelf.tumblr.dialog.editor.viewholder.TagsHolder
import com.ternaryop.photoshelf.tumblr.dialog.editor.viewholder.TitleHolder
import com.ternaryop.photoshelf.util.menu.enableAll
import org.koin.androidx.viewmodel.ext.android.viewModel

private const val BLOG_VISIBLE_PREF_NAME = "postEditorIsBlogVisible"
private const val THUMBNAIL_VISIBLE_PREF_NAME = "postEditorIsThumbnailListVisible"
private const val DEFAULT_THUMBNAIL_SIZE = 75

fun Map<String, Any>?.getInt(preferences: SharedPreferences, key: String, defaultValue: Int): Int {
    return this?.get(key) as? Int ?: preferences.getInt(key, defaultValue)
}

class PostEditorFragment : Fragment() {

    private lateinit var tumblrPostAction: AbsTumblrPostEditor
    private lateinit var preferences: SharedPreferences
    private val viewModel: PostViewModel by viewModel()

    private var _binding: FragmentTumblrPostBinding? = null
    private val binding get() = _binding!!

    val supportActionBar: ActionBar?
        get() = (activity as AppCompatActivity).supportActionBar

    private var isOptionsMenuEnabled = false
        set(value) {
            field = value
            requireActivity().invalidateOptionsMenu()
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentTumblrPostBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        preferences = PreferenceManager.getDefaultSharedPreferences(context)

        viewModel.result.observe(requireActivity(), EventObserver { result ->
            when (result) {
                is TumblrPostModelResult.TitleParsed -> onTitleParsed(result)
                is TumblrPostModelResult.MisspelledInfo -> onMisspelledInfo(result)
            }
        })

        val data = checkNotNull(arguments?.getSerializable(ARG_POST_DATA) as? PostEditorData)
        val tagsHolder = TagsHolder(requireContext(), binding.postTags, data.blogName)
        val titleHolder = TitleHolder(binding.postTitle, data.sourceTitle, data.htmlSourceTitle)
        val maxMruItems = data.extras.getInt(
            preferences,
            EXTRA_MAX_TAGS_MRU_ITEMS,
            resources.getInteger(R.integer.post_editor_max_mru_items))
        val maxHighlightedMruItems = data.extras.getInt(
            preferences,
            EXTRA_MAX_HIGHLIGHTED_TAGS_MRU_ITEMS,
            resources.getInteger(R.integer.post_editor_max_highlighted_items))

        val mruHolder = MRUHolder(requireContext(), binding.mruList,
            maxMruItems, maxHighlightedMruItems, tagsHolder)

        setHasOptionsMenu(true)
        tumblrPostAction = when (data) {
            is EditPostEditorData -> EditTumblrPostEditor(data, titleHolder, tagsHolder, mruHolder)
            is NewPostEditorData -> NewTumblrPostEditor(data, titleHolder, tagsHolder, mruHolder)
                .apply { lifecycle.addObserver(this) }
            else -> throw IllegalStateException("Unknown post data")
        }
        showBlogList()
        showThumbnails()
        setupThumbnails(data)
        supportActionBar?.subtitle = data.blogName
        tumblrPostAction.setupUI(supportActionBar, view)
        isOptionsMenuEnabled = false
        fillTags(data.tags)
    }

    private fun showThumbnails() {
        binding.thumbnail.visibility = if (preferences.getBoolean(THUMBNAIL_VISIBLE_PREF_NAME, true)) {
            View.VISIBLE
        } else {
            View.GONE
        }
    }

    private fun showBlogList() {
        val visibility = if (preferences.getBoolean(BLOG_VISIBLE_PREF_NAME, true)) {
            View.VISIBLE
        } else {
            View.GONE
        }
        binding.blog.visibility = visibility
        binding.refreshBlogList.visibility = visibility
    }

    @Suppress("UNCHECKED_CAST")
    private fun setupThumbnails(data: PostEditorData) {
        (data.extras?.get(EXTRA_THUMBNAILS_ITEMS) as? List<String>)?.also { thumbnails ->
            val size = (data.extras.get(EXTRA_THUMBNAILS_SIZE) as? Int) ?: DEFAULT_THUMBNAIL_SIZE
            binding.thumbnail.apply {
                adapter = ThumbnailAdapter(context, size).apply { addAll(thumbnails) }
                setHasFixedSize(true)
                layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.tumblr_post_edit, menu)
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        tumblrPostAction.onPrepareMenu(menu)
        menu.findItem(R.id.toggle_blog_list).isChecked = preferences.getBoolean(BLOG_VISIBLE_PREF_NAME, true)
        menu.findItem(R.id.toggle_thumbnails).isChecked = preferences.getBoolean(THUMBNAIL_VISIBLE_PREF_NAME, true)
        menu.enableAll(isOptionsMenuEnabled)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.parse_title -> {
                viewModel.parse(tumblrPostAction.titleHolder.plainTitle, false)
                true
            }
            R.id.parse_title_swap -> {
                viewModel.parse(tumblrPostAction.titleHolder.plainTitle, true)
                true
            }
            R.id.source_title -> {
                tumblrPostAction.titleHolder.restoreSourceTitle()
                true
            }
            R.id.toggle_blog_list -> {
                item.isChecked = !item.isChecked
                preferences.edit().putBoolean(BLOG_VISIBLE_PREF_NAME, item.isChecked).apply()
                showBlogList()
                true
            }
            R.id.toggle_thumbnails -> {
                item.isChecked = !item.isChecked
                preferences.edit().putBoolean(THUMBNAIL_VISIBLE_PREF_NAME, item.isChecked).apply()
                showThumbnails()
                true
            }
            else -> {
                val result = tumblrPostAction.execute(item) ?: return false
                result.finishActivity(requireActivity())
                true
            }
        }
    }

    private fun onTitleParsed(result: TumblrPostModelResult.TitleParsed) {
        when (result.command.status) {
            Status.SUCCESS -> result.command.data?.also { data ->
                tumblrPostAction.titleHolder.htmlTitle = data.html
                fillTags(data.tags)
            }
            Status.ERROR -> result.command.error?.also { error ->
                AlertDialog.Builder(requireContext())
                    .setTitle(R.string.parsing_error)
                    .setMessage(error.localizedMessage)
                    .show()
            }
            Status.PROGRESS -> { }
        }
    }

    private fun onMisspelledInfo(result: TumblrPostModelResult.MisspelledInfo) {
        isOptionsMenuEnabled = true
        when (result.command.status) {
            Status.SUCCESS -> result.command.data?.also { tumblrPostAction.tagsHolder.highlightTagName(it) }
            Status.ERROR -> { }
            Status.PROGRESS -> { }
        }
    }

    private fun fillTags(tags: List<String>) {
        isOptionsMenuEnabled = false
        val firstTag = if (tags.isEmpty()) "" else tags[0]
        tumblrPostAction.tagsHolder.tags = tags.joinToString(", ")

        viewModel.searchMisspelledName(firstTag)
    }
}
