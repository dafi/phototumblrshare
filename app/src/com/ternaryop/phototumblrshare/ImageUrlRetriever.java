package com.ternaryop.phototumblrshare;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.os.AsyncTask;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

public class ImageUrlRetriever extends AsyncTask<Object, Integer, List<String>> {
	private final Context context;
	private String title;
	private ProgressDialog progressDialog;
	private Exception error = null;
	private Map<String, String> urlSelectorMap = new HashMap<String, String>();
	private ActionMode actionMode;
	private final OnImagesRetrieved callback;
	private boolean useActionMode;

	public ImageUrlRetriever(Context context, OnImagesRetrieved callback) {
		this.context = context;
		this.callback = callback;
		useActionMode = true;
	}

	public void addOrRemoveUrl(String domSelector, String url) {
		if (urlSelectorMap.get(url) == null) {
			urlSelectorMap.put(url, domSelector);
		} else {
			urlSelectorMap.remove(url);
		}
		if (useActionMode) {
			if (urlSelectorMap.size() == 0) {
				getActionMode((Activity) context).finish();
			} else {
				getActionMode((Activity) context).invalidate();
			}
		}
	}

	@Override
	protected void onPreExecute() {
		progressDialog = new ProgressDialog(context);
		progressDialog.setMessage(context.getResources().getString(R.string.image_retriever_title));
		progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
		progressDialog.setMax(urlSelectorMap.size());
		progressDialog.show();
	}

	@Override
	protected List<String> doInBackground(Object... params) {
		ArrayList<String> imageUrls = new ArrayList<String>();
		try {
			@SuppressWarnings("unchecked")
			Map<String, String> urls = (Map<String, String>) params[0];
			int i = 1;
			for (String url : urls.keySet()) {
				String selector = urls.get(url);
				Document htmlDocument = Jsoup.connect(url).get();
				if (title == null) {
					title = htmlDocument.title();
				}
				String link = htmlDocument.select(selector).attr("src");
				if (!link.isEmpty()) {
					imageUrls.add(link);
				}
				publishProgress(i++);
			}
		} catch (Exception e) {
			error = e;
			return null;
		}
		return imageUrls;
	}

	@Override
	protected void onProgressUpdate(Integer... progress) {
		progressDialog.setProgress(progress[0]);
	}

	@Override
	protected void onPostExecute(List<String> imageUrls) {
		try {
			progressDialog.dismiss();
			if (error == null) {
				callback.onImagesRetrieved(title, imageUrls);
				urlSelectorMap.clear();
			} else {
				new AlertDialog.Builder(context)
				.setTitle(R.string.url_not_found)
				.setMessage(error.getLocalizedMessage())
				.show();
			}
		} catch (Exception e) {
			new AlertDialog.Builder(context)
			.setTitle(R.string.parsing_error)
			.setMessage(title + "\n" + e.getLocalizedMessage())
			.show();
		}
	}

	protected ActionMode getActionMode(Activity activity) {
		if (actionMode == null) {
			actionMode = activity.startActionMode(mActionModeCallback);
		}
		return actionMode;
	}

	public String getTitle() {
		return title;
	}


	public void setTitle(String title) {
		this.title = title;
	}

	private ActionMode.Callback mActionModeCallback = new ActionMode.Callback() {
		public boolean onCreateActionMode(ActionMode mode, Menu menu) {
			MenuInflater inflater = mode.getMenuInflater();
			inflater.inflate(R.menu.action_context, menu);
			return true;
		}

		public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
			menu.findItem(R.id.counter).setTitle(urlSelectorMap.size() + " urls");
			return true;
		}

		public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
			switch (item.getItemId()) {
			case R.id.showDialog:
				retrieve();
				getActionMode((Activity) context).finish();
				return true;
			default:
				return false;
			}
		}

		// Called when the user exits the action mode
		public void onDestroyActionMode(ActionMode mode) {
			actionMode = null;
		}
	};

	public void retrieve() {
		execute(urlSelectorMap);
	}

	public boolean isUseActionMode() {
		return useActionMode;
	}

	public void setUseActionMode(boolean useActionMode) {
		this.useActionMode = useActionMode;
	}

	public interface OnImagesRetrieved {
		public void onImagesRetrieved(String title, List<String> imageUrls);
	}
	
}