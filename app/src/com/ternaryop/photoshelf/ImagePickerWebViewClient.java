package com.ternaryop.photoshelf;

import java.io.ByteArrayInputStream;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.view.View;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.ternaryop.photoshelf.activity.ImageViewerActivity;

public class ImagePickerWebViewClient extends WebViewClient {
	private final ProgressBar progressBar;

	public ImagePickerWebViewClient(ProgressBar progressBar) {
		this.progressBar = progressBar;
		progressBar.setVisibility(View.GONE);
	}
	
	@Override
	public boolean shouldOverrideUrlLoading(WebView view, String url) {
		final Context context = view.getContext();

		String domSelector = new ImageDOMSelectorFinder(context).getSelectorFromUrl(url);
		if (domSelector != null) {

			ImageUrlRetriever imageUrlRetriever = new ImageUrlRetriever(context, new ImageUrlRetriever.OnImagesRetrieved() {
				@Override
				public void onImagesRetrieved(ImageUrlRetriever imageUrlRetriever) {
					ImageViewerActivity.startImageViewer(context, imageUrlRetriever.getImageUrls().get(0), null);
				}
			});
			imageUrlRetriever.setUseActionMode(false);
			imageUrlRetriever.addOrRemoveUrl(domSelector, url);
			imageUrlRetriever.retrieve();
			return true;
		}
		String message = context.getString(R.string.unable_to_find_domain_mapper_for_url);
		Toast.makeText(context.getApplicationContext(),
				String.format(message, url),
				Toast.LENGTH_LONG).show();
		return false;
	}

	@Override
	public void onPageStarted(WebView view, String url, Bitmap favicon) {
		progressBar.setProgress(0);
		progressBar.setVisibility(View.VISIBLE);
	}

	@Override
	public void onPageFinished(WebView view, String url) {
		progressBar.setVisibility(View.GONE);
		if (view.getTitle() != null) {
		    ((Activity)view.getContext()).getActionBar().setSubtitle(view.getTitle());
		}
	}
	
	@Override
	public WebResourceResponse shouldInterceptRequest(WebView view, String url) {
	    // rudimental way to removed javascript and other resources
	    // javascript can't be simply disabled because of kitkat bug
	    if (url.indexOf(".gif") >= 0 || url.indexOf(".js") >= 0) {
	        return new WebResourceResponse("text/html", "UTF-8", new ByteArrayInputStream(new byte[0]));
	    }
	    return null; 
	}
}