package com.ternaryop.photoshelf.dialogs;

import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;

import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.content.DialogInterface;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.Toolbar;
import android.text.Html;
import android.text.TextUtils;
import android.util.Pair;
import android.view.ContextThemeWrapper;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.MultiAutoCompleteTextView;
import android.widget.Spinner;

import com.ternaryop.photoshelf.AppSupport;
import com.ternaryop.photoshelf.R;
import com.ternaryop.photoshelf.customsearch.GoogleCustomSearchClient;
import com.ternaryop.photoshelf.db.DBHelper;
import com.ternaryop.photoshelf.db.TagCursorAdapter;
import com.ternaryop.photoshelf.parsers.AndroidTitleParserConfig;
import com.ternaryop.photoshelf.parsers.TitleData;
import com.ternaryop.photoshelf.parsers.TitleParser;
import com.ternaryop.photoshelf.service.PublishIntentService;
import com.ternaryop.tumblr.Tumblr;
import com.ternaryop.tumblr.TumblrPhotoPost;
import com.ternaryop.utils.DialogUtils;
import io.reactivex.Completable;
import io.reactivex.Single;
import io.reactivex.SingleObserver;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Action;
import io.reactivex.functions.Consumer;
import io.reactivex.schedulers.Schedulers;

public class TumblrPostDialog extends DialogFragment implements Toolbar.OnMenuItemClickListener {

    public static final String ARG_PHOTO_POST = "photoPost";
    public static final String ARG_IMAGE_URLS = "imageUrls";
    public static final String ARG_HTML_TITLE = "htmlTitle";
    public static final String ARG_SOURCE_TITLE = "sourceTitle";
    public static final String ARG_INITIAL_TAG_LIST = "initialTagList";

    private static final int NAME_ALREADY_EXISTS = 0;
    private static final int NAME_NOT_FOUND = 1;
    private static final int NAME_MISSPELLED = 2;

    private EditText postTitle;
    private MultiAutoCompleteTextView postTags;
    private Spinner blogList;
    private AppSupport appSupport;
    private TumblrPhotoPost photoPost;
    private TagCursorAdapter tagAdapter;
    private List<Uri> imageUrls;
    private String htmlTitle;
    private String sourceTitle;
    private List<String> initialTagList;
    private ColorStateList defaultPostTagsColor;
    private Drawable defaultPostTagsBackground;

    protected CompositeDisposable compositeDisposable;

    public static TumblrPostDialog newInstance(Bundle args, Fragment target) {
        TumblrPostDialog fragment = new TumblrPostDialog();

        Set<String> keys = args.keySet();
        if ((keys.contains(ARG_PHOTO_POST) && keys.contains(ARG_IMAGE_URLS))) {
            throw new IllegalArgumentException("Only one type must be specified between " + ARG_PHOTO_POST + ", " + ARG_IMAGE_URLS);
        }
        if (!keys.contains(ARG_PHOTO_POST) && !keys.contains(ARG_IMAGE_URLS)) {
            throw new IllegalArgumentException("One type must be specified, allowed values are " + ARG_PHOTO_POST + ", " + ARG_IMAGE_URLS);
        }
        fragment.setArguments(args);
        fragment.setTargetFragment(target, 0);

        return fragment;
    }


    public static TumblrPostDialog newInstance(TumblrPhotoPost photoPost, Fragment target) {
        if (photoPost == null) {
            throw new IllegalArgumentException("photoPost is mandatory");
        }
        TumblrPostDialog fragment = new TumblrPostDialog();

        Bundle args = new Bundle();
        args.putSerializable(ARG_PHOTO_POST, photoPost);
        fragment.setArguments(args);

        fragment.setTargetFragment(target, 0);
        return fragment;
    }

    public TumblrPostDialog() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        appSupport = new AppSupport(getActivity());
        decodeArguments();
        setStyle(DialogFragment.STYLE_NORMAL, R.style.Theme_PhotoShelf_Dialog);

        compositeDisposable = new CompositeDisposable();
    }

    @Override
    public void onDestroy() {
        compositeDisposable.clear();
        super.onDestroy();
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        View view = getActivity().getLayoutInflater().inflate(R.layout.dialog_publish_post, null);
        setupUI(view);
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity())
                .setView(view)
                .setNegativeButton(R.string.cancel_title, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        compositeDisposable.clear();
                    }
                });
        if (photoPost == null) {
            OnClickPublishListener onClickPublishListener = new OnClickPublishListener();
            builder.setNeutralButton(R.string.publish_post, onClickPublishListener);
            builder.setPositiveButton(R.string.draft_title, onClickPublishListener);
            view.findViewById(R.id.refreshBlogList).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    fetchBlogNames();
                }
            });
        } else {
            view.findViewById(R.id.blog_list).setVisibility(View.GONE);
            builder.setPositiveButton(R.string.edit_post_title, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    editPost();
                }
            });
        }

        return builder.create();
    }

    @Override
    public void onResume() {
        super.onResume();
        // Dimensions defined on xml layout are not used so we set them here (it works only if called inside onResume)
        if (getDialog().getWindow() != null) {
            getDialog().getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    private void setupUI(View view) {
        Toolbar toolbar = (Toolbar) view.findViewById(R.id.toolbar);
        toolbar.inflateMenu(R.menu.publish_post_overflow);
        toolbar.setOnMenuItemClickListener(this);

        postTitle = (EditText)view.findViewById(R.id.post_title);
        postTags = (MultiAutoCompleteTextView)view.findViewById(R.id.post_tags);
        blogList = (Spinner) view.findViewById(R.id.blog);

        // the ContextThemeWrapper is necessary otherwise the autocomplete drop down items and the toolbar overflow menu items are styled incorrectly
        // since the switch to the AlertDialog the toolbar isn't styled from code so to fix it the theme is declared directly into xml
        tagAdapter = new TagCursorAdapter(
                new ContextThemeWrapper(getActivity(), R.style.Theme_PhotoShelf_Dialog),
                android.R.layout.simple_dropdown_item_1line,
                "");
        tagAdapter.setBlogName(appSupport.getSelectedBlogName());
        postTags.setAdapter(tagAdapter);
        postTags.setTokenizer(new MultiAutoCompleteTextView.CommaTokenizer());

        blogList.setOnItemSelectedListener(new BlogItemSelectedListener());

        fillTags(initialTagList);
        postTitle.setText(Html.fromHtml(htmlTitle));
        // move caret to end
        postTitle.setSelection(postTitle.length());

        if (photoPost != null) {
            toolbar.setTitle(R.string.edit_post_title);
        } else {
            int size = imageUrls.size();
            toolbar.setTitle(getActivity().getResources().getQuantityString(
                    R.plurals.post_image,
                    size,
                    size));
        }
    }

    public List<Uri> getImageUrls() {
        return imageUrls;
    }

    public String getPostTitle() {
        postTitle.clearComposingText();
        return Html.toHtml(postTitle.getText());
    }

    private void setInitialTagList(List<String> initialTagList) {
        this.initialTagList = initialTagList;
    }

    public String getPostTags() {
        return postTags.getText().toString();
    }

    private void fillTags(List<String> tags) {
        final String firstTag = tags.isEmpty() ? "" : tags.get(0);
        this.postTags.setText(TextUtils.join(", ", tags));

        if (firstTag.isEmpty()) {
            return;
        }
        final Handler handler = new Handler();
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        searchMisspelledName(firstTag);
                    }
                });
            }
        };
        new Thread(runnable).start();
    }

    private void searchMisspelledName(final String name) {
        ((AlertDialog) getDialog()).getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);

        Single
                .fromCallable(new Callable<Pair<Integer, String>>() {
                    @Override
                    public Pair<Integer, String> call() throws Exception {
                        final long count = DBHelper.getInstance(getActivity()).getPostTagDAO()
                                .getPostCountByTag(name, appSupport.getSelectedBlogName());
                        if (count > 0) {
                            return Pair.create(NAME_ALREADY_EXISTS, name);
                        }
                        String correctedName = new GoogleCustomSearchClient(
                                    getString(R.string.GOOGLE_CSE_APIKEY),
                                    getString(R.string.GOOGLE_CSE_CX))
                                    .getCorrectedQuery(name);
                        if (correctedName == null) {
                            return Pair.create(NAME_NOT_FOUND, name);
                        }
                        return Pair.create(NAME_MISSPELLED, correctedName);
                    }
                })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doFinally(new Action() {
                    @Override
                    public void run() throws Exception {
                        ((AlertDialog) getDialog()).getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                    }
                })
                .subscribe(new SingleObserver<Pair<Integer, String>>() {
                    @Override
                    public void onSubscribe(Disposable d) {
                        compositeDisposable.add(d);
                    }

                    @Override
                    public void onSuccess(Pair<Integer, String> mispelledInfo) {
                        highlightTagName(mispelledInfo.first, mispelledInfo.second);
                    }

                    @Override
                    public void onError(Throwable e) {}
                });
    }

    private void highlightTagName(int nameType, String correctedName) {
        if (defaultPostTagsColor == null) {
            defaultPostTagsColor = postTags.getTextColors();
            defaultPostTagsBackground = postTags.getBackground();
        }

        switch (nameType) {
            case NAME_ALREADY_EXISTS:
                postTags.setTextColor(defaultPostTagsColor);
                postTags.setBackground(defaultPostTagsBackground);
                break;
            case NAME_MISSPELLED:
                postTags.setTextColor(Color.RED);
                postTags.setBackgroundColor(Color.YELLOW);
                postTags.setText(correctedName);
                break;
            case NAME_NOT_FOUND:
                postTags.setTextColor(Color.WHITE);
                postTags.setBackgroundColor(Color.RED);
                break;
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        if (photoPost == null) {
            AlertDialog dialog = (AlertDialog) getDialog();
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setEnabled(false);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);

            List<String> blogSetNames = appSupport.getBlogList();
            if (blogSetNames == null) {
                fetchBlogNames();
            } else {
                fillBlogList(blogSetNames);
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setEnabled(true);
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
            }
        }
    }

    private void fillBlogList(List<String> blogNames) {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(getActivity(), android.R.layout.simple_spinner_item, blogNames);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        blogList.setAdapter(adapter);

        String selectedName = appSupport.getSelectedBlogName();
        if (selectedName != null) {
            int position = adapter.getPosition(selectedName);
            if (position >= 0) {
                blogList.setSelection(position);
                tagAdapter.setBlogName(selectedName);
                tagAdapter.notifyDataSetChanged();
            }
        }
    }

    private void fetchBlogNames() {
        final AlertDialog dialog = (AlertDialog) getDialog();
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setEnabled(false);
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);

        appSupport.clearBlogList();
        appSupport.fetchBlogNames(getActivity())
                .subscribe(new SingleObserver<List<String>>() {
                    @Override
                    public void onSubscribe(Disposable d) {
                        compositeDisposable.add(d);
                    }

                    @Override
                    public void onSuccess(List<String> blogNames) {
                        fillBlogList(blogNames);
                        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setEnabled(true);
                        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                    }

                    @Override
                    public void onError(Throwable e) {
                        dismiss();
                        DialogUtils.showErrorDialog(getActivity(), e);
                    }
                });
    }

    @Override
    public boolean onMenuItemClick(MenuItem menuItem) {
        switch (menuItem.getItemId()) {
            case R.id.parse_title:
                parseTitle(false);
                return true;
            case R.id.parse_title_swap:
                parseTitle(true);
                return true;
            case R.id.source_title:
                fillWithSourceTitle();
                return true;
        }
        return false;
    }

    private final class OnClickPublishListener implements DialogInterface.OnClickListener {
        @Override
        public void onClick(DialogInterface dialog, int which) {
            final String selectedBlogName = (String) blogList.getSelectedItem();
            appSupport.setSelectedBlogName(selectedBlogName);

            createPosts(which == DialogInterface.BUTTON_NEUTRAL, selectedBlogName, getImageUrls(), getPostTitle(), getPostTags());
        }

        private void createPosts(boolean publish, String selectedBlogName, List<Uri> urls, String postTitle, String postTags) {
            final String action = publish ? PublishIntentService.PUBLISH_ACTION_PUBLISH : PublishIntentService.PUBLISH_ACTION_DRAFT;
            for (Uri url : urls) {
                PublishIntentService.startActionIntent(getActivity(),
                        url,
                        selectedBlogName,
                        postTitle,
                        postTags,
                        action);
            }
        }
    }

    private void editPost() {
        final HashMap<String, String> newValues = new HashMap<>();
        newValues.put("id", String.valueOf(photoPost.getPostId()));
        newValues.put("caption", getPostTitle());
        newValues.put("tags", getPostTags());
        final String selectedBlogName = appSupport.getSelectedBlogName();

        final Completable completable = Completable
                .fromAction(new Action() {
                    @Override
                    public void run() throws Exception {
                        Tumblr.getSharedTumblr(getActivity()).editPost(selectedBlogName, newValues);
                        newValues.put("tumblrName", selectedBlogName);
                        DBHelper.getInstance(getActivity()).getPostDAO().update(newValues, getActivity());
                    }
                })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread());
        if (getTargetFragment() instanceof PostListener) {
            ((PostListener) getTargetFragment()).onEditDone(this, photoPost, completable);
        } else {
            completable.subscribe(new Action() {
                @Override
                public void run() throws Exception {
                }
            }, new Consumer<Throwable>() {
                @Override
                public void accept(Throwable throwable) throws Exception {}
            });
        }
    }

    private void parseTitle(boolean swapDayMonth) {
        TitleData titleData = TitleParser.instance(new AndroidTitleParserConfig(getActivity())).parseTitle(postTitle.getText().toString(), swapDayMonth);
        // only the edited title is updated, the sourceTitle remains unchanged
        htmlTitle = titleData.toHtml();
        this.postTitle.setText(Html.fromHtml(htmlTitle));

        fillTags(titleData.getTags());
    }

    private class BlogItemSelectedListener implements OnItemSelectedListener {
        @Override
        public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
            tagAdapter.setBlogName((String) blogList.getSelectedItem());
            tagAdapter.notifyDataSetChanged();
        }

        public void onNothingSelected(AdapterView<?> parent) {
        }
    }

    private void fillWithSourceTitle() {
        // treat the sourceTitle always as HTML
        this.postTitle.setText(Html.fromHtml(sourceTitle));
    }

    private void decodeArguments() {
        Bundle args = getArguments();
        TumblrPhotoPost photoPost = (TumblrPhotoPost) args.getSerializable(ARG_PHOTO_POST);
        if (photoPost != null) {
            this.photoPost = photoPost;
            // pass the same HTML text for source title
            this.htmlTitle = photoPost.getCaption();
            this.sourceTitle = photoPost.getCaption();
            setInitialTagList(photoPost.getTags());
        } else {
            this.imageUrls = args.getParcelableArrayList(ARG_IMAGE_URLS);
            this.htmlTitle = args.getString(ARG_HTML_TITLE);
            this.sourceTitle = args.getString(ARG_SOURCE_TITLE);
            setInitialTagList(args.getStringArrayList(ARG_INITIAL_TAG_LIST));
        }
    }

    public interface PostListener {
        void onEditDone(TumblrPostDialog dialog, TumblrPhotoPost post, Completable completable);
    }
}
