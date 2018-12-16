package com.ternaryop.photoshelf.fragment.feedly

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.SharedPreferences
import android.os.Bundle
import android.preference.PreferenceManager
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import com.google.gson.GsonBuilder
import com.ternaryop.feedly.AccessToken
import com.ternaryop.feedly.FeedlyClient
import com.ternaryop.feedly.FeedlyRateLimit
import com.ternaryop.feedly.StreamContent
import com.ternaryop.feedly.StreamContentFindParam
import com.ternaryop.feedly.TokenExpiredException
import com.ternaryop.photoshelf.BuildConfig
import com.ternaryop.photoshelf.R
import com.ternaryop.photoshelf.activity.ImagePickerActivity
import com.ternaryop.photoshelf.activity.TagPhotoBrowserActivity
import com.ternaryop.photoshelf.adapter.feedly.FeedlyContentAdapter
import com.ternaryop.photoshelf.adapter.feedly.FeedlyContentDelegate
import com.ternaryop.photoshelf.adapter.feedly.FeedlyContentSortSwitcher.Companion.TITLE_NAME
import com.ternaryop.photoshelf.adapter.feedly.OnFeedlyContentClick
import com.ternaryop.photoshelf.adapter.feedly.titles
import com.ternaryop.photoshelf.adapter.feedly.toContentDelegate
import com.ternaryop.photoshelf.adapter.feedly.update
import com.ternaryop.photoshelf.api.ApiManager
import com.ternaryop.photoshelf.api.post.titlesRequestBody
import com.ternaryop.photoshelf.fragment.AbsPhotoShelfFragment
import com.ternaryop.photoshelf.fragment.BottomMenuSheetDialogFragment
import com.ternaryop.photoshelf.view.PhotoShelfSwipe
import io.reactivex.Completable
import io.reactivex.Single
import io.reactivex.SingleObserver
import io.reactivex.disposables.Disposable
import java.io.InputStreamReader

class FeedlyListFragment : AbsPhotoShelfFragment(), OnFeedlyContentClick {
    private lateinit var adapter: FeedlyContentAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var feedlyClient: FeedlyClient
    private lateinit var preferences: SharedPreferences
    private lateinit var photoShelfSwipe: PhotoShelfSwipe
    private val newerThanHours: Int
        get() = preferences.getInt(PREF_NEWER_THAN_HOURS, DEFAULT_NEWER_THAN_HOURS)
    private val maxFetchItemCount: Int
        get() = preferences.getInt(PREF_MAX_FETCH_ITEMS_COUNT, DEFAULT_MAX_FETCH_ITEMS_COUNT)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.saved_content_list, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initRecyclerView(view)

        setHasOptionsMenu(true)

        photoShelfSwipe = view.findViewById(R.id.swipe_container)
        photoShelfSwipe.setOnRefreshListener { refresh(true) }
    }

    private fun initRecyclerView(rootView: View) {
        adapter = FeedlyContentAdapter(context!!)

        recyclerView = rootView.findViewById(R.id.list)
        recyclerView.setHasFixedSize(true)
        recyclerView.layoutManager = LinearLayoutManager(context!!)
        recyclerView.adapter = adapter
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        preferences = PreferenceManager.getDefaultSharedPreferences(context!!)

        adapter.sortSwitcher.setType(preferences.getInt(PREF_SORT_TYPE, TITLE_NAME))
        adapter.clickListener = this

        feedlyClient = FeedlyClient(
            preferences.getString(PREF_FEEDLY_ACCESS_TOKEN, getString(R.string.FEEDLY_ACCESS_TOKEN))!!,
            getString(R.string.FEEDLY_USER_ID),
            getString(R.string.FEEDLY_REFRESH_TOKEN))

        refresh(false)
    }

    private fun refresh(deleteItemsIfAllowed: Boolean) {
        // do not start another refresh if the current one is running
        if (photoShelfSwipe.isWaitingResult) {
            return
        }
        getFeedlyContentDelegate(deleteItemsIfAllowed)
            .compose(photoShelfSwipe.applySwipe())
            .subscribe(object : FeedlyObserver<List<FeedlyContentDelegate>>() {
                override fun onSuccess(posts: List<FeedlyContentDelegate>) {
                    setItems(posts)
                }
            })
    }

    private fun getFeedlyContentDelegate(deleteItemsIfAllowed: Boolean): Single<List<FeedlyContentDelegate>> {
        return readStreamContent(deleteItemsIfAllowed)
            .map { it.items.toContentDelegate() }
            .flatMap { list ->
                ApiManager.postService(context!!)
                    .getMapLastPublishedTimestampTag(blogName!!, titlesRequestBody(list.titles()))
                    .map {
                        list.update(it.response.pairs)
                        list
                    }
            }
    }

    private fun readStreamContent(deleteItemsIfAllowed: Boolean): Single<StreamContent> {
        return if (BuildConfig.DEBUG) {
            fakeCall()
        } else {
            deleteItems(deleteItemsIfAllowed).andThen(Single.defer { getNewerSavedContent() })
        }
    }

    private fun setItems(items: List<FeedlyContentDelegate>) {
        adapter.clear()
        adapter.addAll(items)
        adapter.sort()
        adapter.notifyDataSetChanged()
        scrollToPosition(0)

        refreshUI()
    }

    private fun deleteItems(deleteItemsIfAllowed: Boolean): Completable {
        if (deleteItemsIfAllowed && deleteOnRefresh()) {
            val idList = adapter.uncheckedItems.map { it.id }
            return feedlyClient.markSaved(idList, false)
        }
        return Completable.complete()
    }

    private fun getNewerSavedContent(): Single<StreamContent> {
        val ms = System.currentTimeMillis() - newerThanHours * ONE_HOUR_MILLIS
        val params = StreamContentFindParam(maxFetchItemCount, ms)
        return feedlyClient.getStreamContents(feedlyClient.globalSavedTag, params.toQueryMap())
    }

    private fun fakeCall(): Single<StreamContent> {
        return Single.fromCallable {
            context!!.assets.open("sample/feedly.json").use { stream ->
                GsonBuilder().create().fromJson(InputStreamReader(stream), StreamContent::class.java)
            }
        }
    }

    override fun refreshUI() {
        supportActionBar?.apply {
            subtitle = resources.getQuantityString(
                R.plurals.posts_count,
                adapter.itemCount,
                adapter.itemCount)
        }
    }

    override fun onAttachFragment(childFragment: Fragment?) {
        super.onAttachFragment(childFragment)
        if (childFragment !is BottomMenuSheetDialogFragment) {
            return
        }
        childFragment.menuListener = when (childFragment.tag) {
            FRAGMENT_TAG_SORT -> FeedlySortBottomMenuListener(this, adapter.sortSwitcher)
            else -> throw IllegalArgumentException("Invalid tag ${childFragment.tag}")
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.feedly, menu)
        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_refresh -> {
                refresh(true)
                return true
            }
            R.id.action_api_usage -> {
                showAPIUsage()
                return true
            }
            R.id.action_refresh_token -> {
                refreshToken()
                return true
            }
            R.id.action_settings -> {
                settings()
                return true
            }
            R.id.action_sort -> {
                BottomMenuSheetDialogFragment().show(childFragmentManager, FRAGMENT_TAG_SORT)
                return true
            }
            else -> return super.onOptionsItemSelected(item)
        }
    }

    private fun refreshToken() {
        feedlyClient.refreshAccessToken(
            getString(R.string.FEEDLY_CLIENT_ID),
            getString(R.string.FEEDLY_CLIENT_SECRET))
            .compose(photoShelfSwipe.applySwipe())
            .subscribe(object : FeedlyObserver<AccessToken>() {
                override fun onSuccess(accessToken: AccessToken) {
                    preferences.edit().putString(PREF_FEEDLY_ACCESS_TOKEN, accessToken.accessToken).apply()
                    feedlyClient.accessToken = preferences.getString(PREF_FEEDLY_ACCESS_TOKEN, accessToken.accessToken)!!
                    // hide swipe otherwise refresh() exists immediately
                    photoShelfSwipe.setRefreshingAndWaitingResult(false)
                    refresh(true)
                }
            })
    }

    private fun saveSortSettings() {
        preferences
            .edit()
            .putInt(PREF_SORT_TYPE, adapter.sortSwitcher.currentSortable.sortId)
            .putBoolean(PREF_SORT_ASCENDING, adapter.sortSwitcher.currentSortable.isAscending)
            .apply()
    }

    private fun scrollToPosition(position: Int) {
        // offset set to 0 put the item to the top
        (recyclerView.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(position, 0)
    }

    private fun showAPIUsage() {
        AlertDialog.Builder(context!!)
            .setTitle(R.string.api_usage)
            .setMessage(getString(R.string.feedly_api_calls_count, FeedlyRateLimit.apiCallsCount) + "\n"
                + getString(R.string.feedly_api_reset_limit, FeedlyRateLimit.apiResetLimitAsString))
            .show()
    }

    @SuppressLint("InflateParams") // for dialogs passing null for root is valid, ignore the warning
    private fun settings() {
        val settingsView = activity!!.layoutInflater.inflate(R.layout.saved_content_settings, null)
        fillSettingsView(settingsView)
        AlertDialog.Builder(context!!)
            .setTitle(R.string.settings)
            .setView(settingsView)
            .setPositiveButton(android.R.string.ok) { _, _ -> updateSettings(settingsView) }
            .setNegativeButton(R.string.cancel_title) { dialog, _ -> dialog.cancel() }
            .show()
    }

    private fun updateSettings(view: View) {
        val fetch = view.findViewById<EditText>(R.id.max_fetch_items_count)
        val newerThanHours = view.findViewById<EditText>(R.id.newer_than_hours)
        val deleteOnRefresh = view.findViewById<CheckBox>(R.id.delete_on_refresh)
        preferences.edit()
            .putInt(PREF_MAX_FETCH_ITEMS_COUNT, Integer.parseInt(fetch.text.toString()))
            .putInt(PREF_NEWER_THAN_HOURS, Integer.parseInt(newerThanHours.text.toString()))
            .putBoolean(PREF_DELETE_ON_REFRESH, deleteOnRefresh.isChecked)
            .apply()
    }

    private fun fillSettingsView(view: View) {
        val fetchView = view.findViewById<EditText>(R.id.max_fetch_items_count)
        val newerThanHoursView = view.findViewById<EditText>(R.id.newer_than_hours)
        val deleteOnRefreshView = view.findViewById<CheckBox>(R.id.delete_on_refresh)

        fetchView.setText(maxFetchItemCount.toString())
        newerThanHoursView.setText(newerThanHours.toString())
        deleteOnRefreshView.isChecked = deleteOnRefresh()
    }

    override fun onTitleClick(position: Int) {
        ImagePickerActivity.startImagePicker(context!!, adapter.getItem(position).originId)
    }

    override fun onTagClick(position: Int) {
        TagPhotoBrowserActivity.startPhotoBrowserActivity(context!!,
            blogName!!, adapter.getItem(position).tag!!, false)
    }

    override fun onToggleClick(position: Int, checked: Boolean) {
        if (deleteOnRefresh()) {
            return
        }
        val d = feedlyClient.markSaved(listOf(adapter.getItem(position).id), checked)
            .compose(photoShelfSwipe.applyCompletableSwipe())
            .subscribe({ }) { t -> showSnackbar(makeSnake(recyclerView, t)) }
        compositeDisposable.add(d)
    }

    private fun deleteOnRefresh(): Boolean {
        return preferences.getBoolean(PREF_DELETE_ON_REFRESH, false)
    }

    override fun makeSnake(view: View, t: Throwable): Snackbar {
        if (t is TokenExpiredException) {
            val snackbar = Snackbar.make(recyclerView, R.string.token_expired, Snackbar.LENGTH_INDEFINITE)
            snackbar
                .setActionTextColor(ContextCompat.getColor(context!!, R.color.snack_error_color))
                .setAction(resources.getString(R.string.refresh).toLowerCase()) { refreshToken() }
            return snackbar
        }
        return super.makeSnake(view, t)
    }

    internal abstract inner class FeedlyObserver<T> : SingleObserver<T> {
        override fun onSubscribe(d: Disposable) {
            compositeDisposable.add(d)
        }

        override fun onError(t: Throwable) {
            showSnackbar(makeSnake(recyclerView, t))
        }
    }

    fun sortBy(sortType: Int) {
        adapter.sortBy(sortType)
        adapter.notifyDataSetChanged()
        scrollToPosition(0)
        saveSortSettings()
    }

    companion object {
        const val PREF_MAX_FETCH_ITEMS_COUNT = "savedContent.MaxFetchItemCount"
        const val PREF_NEWER_THAN_HOURS = "savedContent.NewerThanHours"
        const val PREF_DELETE_ON_REFRESH = "savedContent.DeleteOnRefresh"
        const val PREF_SORT_TYPE = "savedContent.SortType"
        const val PREF_SORT_ASCENDING = "savedContent.SortAscending"

        const val FRAGMENT_TAG_SORT = "sort"

        const val DEFAULT_MAX_FETCH_ITEMS_COUNT = 300
        const val DEFAULT_NEWER_THAN_HOURS = 24
        const val ONE_HOUR_MILLIS = 60 * 60 * 1000
        const val PREF_FEEDLY_ACCESS_TOKEN = "feedlyAccessToken"
    }
}