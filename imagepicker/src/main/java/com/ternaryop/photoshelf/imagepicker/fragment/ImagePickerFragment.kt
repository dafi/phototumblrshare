package com.ternaryop.photoshelf.imagepicker.fragment

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.ActionMode
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import com.ternaryop.photoshelf.activity.ImageViewerActivityStarter
import com.ternaryop.photoshelf.activity.ImageViewerData
import com.ternaryop.photoshelf.adapter.OnPhotoBrowseClickMultiChoice
import com.ternaryop.photoshelf.api.extractor.ImageGallery
import com.ternaryop.photoshelf.fragment.AbsPhotoShelfFragment
import com.ternaryop.photoshelf.imagepicker.R
import com.ternaryop.photoshelf.imagepicker.adapter.ImagePickerAdapter
import com.ternaryop.photoshelf.imagepicker.service.OnPublish
import com.ternaryop.photoshelf.imagepicker.service.PostPublisherService
import com.ternaryop.photoshelf.lifecycle.EventObserver
import com.ternaryop.photoshelf.lifecycle.Status
import com.ternaryop.photoshelf.tumblr.dialog.NewPostEditorData
import com.ternaryop.photoshelf.tumblr.dialog.NewPostEditorResult
import com.ternaryop.photoshelf.tumblr.dialog.TumblrPostDialog
import com.ternaryop.photoshelf.tumblr.dialog.TumblrPostDialog.Companion.ARG_RESULT
import com.ternaryop.photoshelf.tumblr.dialog.TumblrPostDialog.Companion.EXTRA_THUMBNAILS_ITEMS
import com.ternaryop.utils.dialog.DialogUtils
import com.ternaryop.utils.dialog.showErrorDialog
import com.ternaryop.utils.recyclerview.AutofitGridLayoutManager
import com.ternaryop.widget.ProgressHighlightViewLayout
import org.koin.androidx.viewmodel.ext.android.viewModel

private const val NEW_POST_REQUEST_CODE = 1

// The url can contain extraneous text
const val EXTRA_URL = "com.ternaryop.photoshelf.extra.URL"

class ImagePickerFragment(
    private val imageViewerActivityStarter: ImageViewerActivityStarter,
    private val tumblrPostDialog: TumblrPostDialog,
    private val publishClassName: Class<out OnPublish>
) : AbsPhotoShelfFragment(),
    OnPhotoBrowseClickMultiChoice, ActionMode.Callback {
    private lateinit var gridView: RecyclerView
    private lateinit var progressHighlightViewLayout: ProgressHighlightViewLayout
    private lateinit var progressbar: ProgressBar

    private lateinit var imagePickerAdapter: ImagePickerAdapter
    private lateinit var selectedItemsViewContainer: SelectedItemsViewContainer
    private lateinit var imageGallery: ImageGallery
    private val viewModel: ImagePickerViewModel by viewModel()

    // Search on fragment arguments
    private val textWithUrl: String?
        get() {
            val arguments = arguments
            if (arguments != null && arguments.containsKey(EXTRA_URL)) {
                return arguments.getString(EXTRA_URL)
            }
            // Search on activity intent
            // Get intent, action and MIME type
            val intent = requireActivity().intent
            val action = intent.action
            val type = intent.type
            val uri = intent.data

            var textWithUrl: String? = null

            if (Intent.ACTION_SEND == action && type != null) {
                if ("text/plain" == type) {
                    textWithUrl = intent.getStringExtra(Intent.EXTRA_TEXT)
                }
            } else if (Intent.ACTION_VIEW == action && uri != null) {
                textWithUrl = uri.toString()
            }
            return textWithUrl
        }

    private val currentTextView: TextView
        get() = progressHighlightViewLayout.currentView as TextView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_image_picker, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel.result.observe(viewLifecycleOwner, EventObserver { result ->
            when (result) {
                is ImagePickerModelResult.Gallery -> onGalleryModelResult(result)
                is ImagePickerModelResult.ImageList -> onImageListModelResult(result)
                is ImagePickerModelResult.Image -> onImageModelResult(result)
            }
        })

        setHasOptionsMenu(true)
        setupUI(view, requireContext())
    }

    private fun onGalleryModelResult(result: ImagePickerModelResult.Gallery) {
        when (result.command.status) {
            Status.SUCCESS -> onGalleryRetrieved(result.command.data?.gallery)
            Status.ERROR -> showError(result.command.error, false)
            Status.PROGRESS -> { }
        }
    }

    private fun onImageListModelResult(result: ImagePickerModelResult.ImageList) {
        when (result.command.status) {
            Status.SUCCESS -> onRetrievedImageList(result.command.data)
            Status.ERROR -> showError(result.command.error, true)
            Status.PROGRESS -> progressbar.incrementProgressBy(1)
        }
    }

    private fun onImageModelResult(result: ImagePickerModelResult.Image) {
        when (result.command.status) {
            Status.SUCCESS -> onRetrievedSingleImage(result.command.data)
            Status.ERROR -> showError(result.command.error, true)
            Status.PROGRESS -> progressbar.incrementProgressBy(1)
        }
    }

    private fun showError(error: Throwable?, showAlert: Boolean) {
        progressbar.visibility = GONE
        if (showAlert) {
            DialogUtils.showSimpleMessageDialog(requireContext(), R.string.url_not_found, error?.localizedMessage ?: "")
        } else {
            snackbarHolder.show(gridView, error, resources.getString(R.string.refresh)) { refreshUI() }
        }
    }

    private fun onRetrievedSingleImage(uri: ImageInfoUriPair?) {
        progressbar.visibility = GONE
        uri ?: return
        // cache retrieved value
        val url = uri.second.toString()
        uri.first.imageUrl = url
        imageViewerActivityStarter.startImageViewer(requireContext(), ImageViewerData(url))
    }

    private fun setupUI(view: View, context: Context) {
        progressHighlightViewLayout = view.findViewById(android.R.id.empty)
        progressHighlightViewLayout.progressAnimation = AnimationUtils.loadAnimation(context, R.anim.fade_loop)
        progressHighlightViewLayout.visibility = VISIBLE

        imagePickerAdapter = ImagePickerAdapter(context)
        imagePickerAdapter.setOnPhotoBrowseClick(this)
        imagePickerAdapter.setEmptyView(progressHighlightViewLayout)

        val layoutManager = AutofitGridLayoutManager(context,
            resources.getDimension(R.dimen.image_picker_grid_width).toInt())
        gridView = view.findViewById(R.id.gridview)
        gridView.adapter = imagePickerAdapter
        gridView.setHasFixedSize(true)
        gridView.layoutManager = layoutManager
        // animating the constraintLayout results in grid animations, so we disable them
        gridView.itemAnimator = null

        selectedItemsViewContainer = SelectedItemsViewContainer(
            context,
            view.findViewById(R.id.constraintlayout),
            view.findViewById(R.id.selectedItems))
        selectedItemsViewContainer.adapter
            .setOnPhotoBrowseClick(object : OnPhotoBrowseClickMultiChoice {
                override fun onItemClick(position: Int) {
                    val index = imagePickerAdapter.getIndex(selectedItemsViewContainer.adapter.getItem(position))
                    layoutManager.scrollToPosition(index)
                }

                override fun onItemLongClick(position: Int) = Unit
                override fun onTagClick(position: Int, clickedTag: String) = Unit
                override fun onThumbnailImageClick(position: Int) = Unit
                override fun onOverflowClick(position: Int, view: View) = Unit
            })

        progressbar = view.findViewById(R.id.progressbar)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        requireActivity().setTitle(R.string.image_picker_activity_title)

        refreshUI()
    }

    override fun refreshUI() = openUrl(textWithUrl)

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        job.cancel()
    }

    private fun openUrl(textWithUrl: String?) {
        if (textWithUrl == null) {
            return
        }
        progressHighlightViewLayout.startProgress()
        val message = resources.getQuantityString(R.plurals.download_url_with_count, 1, 0)
        currentTextView.text = message
        val url = "(https?:.*)".toRegex().find(textWithUrl)?.value

        if (url == null) {
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.url_not_found)
                .setMessage(getString(R.string.url_not_found_description, textWithUrl))
                .show()
        } else {
            viewModel.readImageGallery(url)
        }
    }

    override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
        mode.title = getString(R.string.select_images)
        mode.subtitle = resources.getQuantityString(
                R.plurals.selected_items_total,
                1,
                1,
                imagePickerAdapter.itemCount)
        mode.menuInflater.inflate(R.menu.image_picker_context, menu)
        imagePickerAdapter.showButtons = true
        imagePickerAdapter.notifyDataSetChanged()
        return true
    }

    override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
        return true
    }

    override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.showDialog -> {
                showProgressbar(imagePickerAdapter.selectedItems.size)
                viewModel.imageList(imagePickerAdapter.selectedItems)
                finish(mode)
                true
            }
            R.id.create_from_file -> {
                showProgressbar(imagePickerAdapter.selectedItems.size)
                viewModel.imageList(imagePickerAdapter.selectedItems, requireContext().cacheDir)
                finish(mode)
                true
            }
            R.id.show_selected_items -> {
                selectedItemsViewContainer.toggleVisibility()
                true
            }
            else -> false
        }
    }

    override fun onDestroyActionMode(mode: ActionMode) {
        this.actionMode = null
        imagePickerAdapter.showButtons = false
        imagePickerAdapter.selection.clear()
        selectedItemsViewContainer.updateList(emptyList())
    }

    private fun finish(mode: ActionMode) {
        mode.finish()
        // if visibility is set (to GONE) on layout file
        // the constraintLayout restores it to GONE after any view modification
        // this means the progress bar is not visible while progress goes on
        // The workaround consists to make the progressbar visible
        // when the actionMode is 'finished' programmatically
        progressbar.visibility = VISIBLE
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode == Activity.RESULT_OK && requestCode == NEW_POST_REQUEST_CODE) {
            (data?.extras?.getSerializable(ARG_RESULT) as? NewPostEditorResult)?.also { onPublish(it) }
        }
    }

    private fun onRetrievedImageList(imageUriList: List<ImageInfoUriPair>?) {
        progressbar.visibility = GONE

        imageUriList ?: return
        try {
            val data = NewPostEditorData(
                imageUriList.map { it.second.toString() },
                requireBlogName,
                imageGallery.parsableTitle,
                imageGallery.titleParsed.html,
                imageGallery.titleParsed.tags,
                mapOf(
                    EXTRA_THUMBNAILS_ITEMS to imageUriList.map { it.first.thumbnailUrl }
                ))
            tumblrPostDialog.newPostEditor(data, this, NEW_POST_REQUEST_CODE)
        } catch (e: Exception) {
            e.showErrorDialog(requireContext())
        }
    }

    private fun onGalleryRetrieved(imageGallery: ImageGallery?) {
        progressHighlightViewLayout.stopProgress()

        imageGallery ?: return

        this.imageGallery = imageGallery
        showDetails(Snackbar.LENGTH_LONG)
        val imageInfoList = imageGallery.imageInfoList
        supportActionBar?.subtitle = resources.getQuantityString(R.plurals.image_found,
            imageInfoList.size, imageInfoList.size)
        imagePickerAdapter.addAll(imageInfoList)
    }

    override fun onTagClick(position: Int, clickedTag: String) = Unit

    override fun onThumbnailImageClick(position: Int) {
        val imageInfo = imagePickerAdapter.getItem(position)
        val imageUrl = imageInfo.imageUrl
        if (imageUrl == null) {
            showProgressbar(1)
            viewModel.image(imageInfo)
        } else {
            imageViewerActivityStarter.startImageViewer(requireContext(), ImageViewerData(imageUrl))
        }
    }

    override fun onOverflowClick(position: Int, view: View) = Unit

    override fun onItemClick(position: Int) {
        if (actionMode == null) {
            onThumbnailImageClick(position)
        } else {
            updateSelection(position)
        }
    }

    override fun onItemLongClick(position: Int) {
        if (actionMode == null) {
            actionMode = requireActivity().startActionMode(this)
        }
        updateSelection(position)
    }

    private fun updateSelection(position: Int) {
        val selection = imagePickerAdapter.selection
        selection.toggle(position)
        selectedItemsViewContainer.updateList(imagePickerAdapter.selectedItems)
        if (selection.itemCount == 0) {
            actionMode?.finish()
        } else {
            val selectionCount = selection.itemCount
            actionMode?.subtitle = resources.getQuantityString(
                    R.plurals.selected_items_total,
                    selectionCount,
                    selectionCount,
                    imagePickerAdapter.itemCount)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.image_picker, menu)
        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_image_viewer_details -> {
                showDetails(Snackbar.LENGTH_INDEFINITE)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showDetails(duration: Int) {
        snackbarHolder.backgroundColor = ContextCompat.getColor(requireContext(), R.color.image_picker_detail_text_bg)
        snackbarHolder.textColor = ContextCompat.getColor(requireContext(), R.color.image_picker_detail_text_text)
        snackbarHolder.show(Snackbar.make(gridView, imageGallery.title ?: "No title", duration))
    }

    private fun showProgressbar(max: Int) {
        progressbar.progress = 0
        progressbar.max = max
        progressbar.visibility = VISIBLE
    }

    private fun onPublish(resultData: NewPostEditorResult) {
        resultData
            .urls
            .forEach { url ->
                PostPublisherService.startPublish(
                    requireContext(),
                    resultData.toPostPublisherData(url, publishClassName.name))
            }
    }
}