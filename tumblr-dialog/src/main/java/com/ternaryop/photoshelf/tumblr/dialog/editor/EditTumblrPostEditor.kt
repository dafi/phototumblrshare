package com.ternaryop.photoshelf.tumblr.dialog.editor

import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.ActionBar
import com.ternaryop.photoshelf.mru.adapter.MRUHolder
import com.ternaryop.photoshelf.tumblr.dialog.PostEditorData
import com.ternaryop.photoshelf.tumblr.dialog.PostEditorResult
import com.ternaryop.photoshelf.tumblr.dialog.R
import com.ternaryop.photoshelf.tumblr.dialog.databinding.FragmentTumblrPostBinding
import com.ternaryop.photoshelf.tumblr.dialog.editor.viewholder.TagsHolder
import com.ternaryop.photoshelf.tumblr.dialog.editor.viewholder.TitleHolder

class EditTumblrPostEditor(
    private val postEditorData: PostEditorData,
    titleHolder: TitleHolder,
    tagsHolder: TagsHolder,
    mruHolder: MRUHolder
) : AbsTumblrPostEditor(titleHolder, tagsHolder, mruHolder) {
    override fun setupUI(actionBar: ActionBar?, view: View) {
        actionBar?.setTitle(R.string.edit_post_title)

        FragmentTumblrPostBinding.bind(view).apply {
            blog.visibility = View.GONE
            refreshBlogList.visibility = View.GONE
        }
    }

    override fun onPrepareMenu(menu: Menu) {
        menu.findItem(R.id.edit).isVisible = true
    }

    override fun canExecute(item: MenuItem): Boolean = item.itemId == R.id.edit

    override fun execute(item: MenuItem): PostEditorResult? =
        if (canExecute(item)) {
            updateMruList()
            PostEditorResult(
                postEditorData.blogName,
                titleHolder.htmlTitle,
                tagsHolder.tags,
                postEditorData.extras)
        } else {
            null
        }
}
