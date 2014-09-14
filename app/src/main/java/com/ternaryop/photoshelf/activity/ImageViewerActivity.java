package com.ternaryop.photoshelf.activity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

import android.annotation.SuppressLint;
import android.app.WallpaperManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.text.Html;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.ternaryop.photoshelf.AppSupport;
import com.ternaryop.photoshelf.R;
import com.ternaryop.tumblr.TumblrPhotoPost;
import com.ternaryop.utils.AbsProgressIndicatorAsyncTask;
import com.ternaryop.utils.DialogUtils;
import com.ternaryop.utils.IOUtils;
import com.ternaryop.utils.ImageUtils;
import com.ternaryop.utils.ShareUtils;

@SuppressLint("SetJavaScriptEnabled")
public class ImageViewerActivity extends AbsPhotoShelfActivity {
    private static final String IMAGE_URL = "imageUrl";
    private static final String IMAGE_TITLE = "imageTitle";
    private static final String IMAGE_TAG = "imageTag";
    private boolean webViewLoaded;
    private Menu optionsMenu;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_webview);
        setTitle(R.string.image_viewer_activity_title);

        ProgressBar progressBar = (ProgressBar) findViewById(R.id.webview_progressbar);

        Bundle bundle = getIntent().getExtras();
        String imageUrl = bundle.getString(IMAGE_URL);
        String data = "<body><img src=\"" + imageUrl + "\"/></body>";
        prepareWebView(progressBar).loadData(data, "text/html", "UTF-8");
        try {
            getActionBar().setSubtitle(new URI(imageUrl).getHost());
        } catch (URISyntaxException ignored) {
        }
    }

    private WebView prepareWebView(final ProgressBar progressBar) {
        WebView webView = (WebView) findViewById(R.id.webview_view);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.addJavascriptInterface(this, "dimRetriever");
        webViewLoaded = false;

        webView.setInitialScale(1);
        webView.getSettings().setLoadWithOverviewMode(true);
        webView.getSettings().setUseWideViewPort(true);
        webView.getSettings().setBuiltInZoomControls(true);
        webView.getSettings().setSupportZoom(true);

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                progressBar.setProgress(newProgress);
            }
        });
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                progressBar.setProgress(0);
                progressBar.setVisibility(View.VISIBLE);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                webViewLoaded = true;
                progressBar.setVisibility(View.GONE);
                view.loadUrl("javascript:var img = document.querySelector('img');dimRetriever.setDimensions(img.width, img.height)");
                // onPrepareOptionsMenu should be called after onPageFinished
                if (optionsMenu != null) {
                    showMenus(optionsMenu, true);
                }
            }
        });
        return webView;
    }

    public static void startImageViewer(Context context, String url, TumblrPhotoPost post) {
        Intent intent = new Intent(context, ImageViewerActivity.class);
        Bundle bundle = new Bundle();

        bundle.putString(IMAGE_URL, url);
        if (post != null) {
            bundle.putString(IMAGE_TITLE, post.getCaption());
            if (!post.getTags().isEmpty()) {
                bundle.putString(IMAGE_TAG, post.getTags().get(0));
            }
        }
        intent.putExtras(bundle);

        context.startActivity(intent);
    }

    @JavascriptInterface
    public void setDimensions(final int w, final int h) {
        runOnUiThread(new Runnable() {
            public void run() {
                getActionBar().setSubtitle(getActionBar().getSubtitle() + String.format(" (%1dx%2d)", w, h));
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.image_viewer, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        optionsMenu = menu;
        if (!webViewLoaded) {
            showMenus(menu, false);
        }
        return true;
    }

    private void showMenus(Menu menu, boolean isVisible) {
        menu.findItem(R.id.action_image_viewer_wallpaper).setVisible(isVisible);
        menu.findItem(R.id.action_image_viewer_share).setVisible(isVisible);
        menu.findItem(R.id.action_image_viewer_download).setVisible(isVisible);
        menu.findItem(R.id.action_image_viewer_copy_url).setVisible(isVisible);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_image_viewer_wallpaper:
                setWallpaper();
                return true;
            case R.id.action_image_viewer_share:
                shareImage();
                return true;
            case R.id.action_image_viewer_download:
                download();
                return true;
            case R.id.action_image_viewer_copy_url:
                copyURL();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void download() {
        try {
            final String imageUrl = getIntent().getExtras().getString(IMAGE_URL);
            String fileName = getIntent().getExtras().getString(IMAGE_TAG);

            if (fileName == null) {
                fileName = new URI(imageUrl).getPath();
                int index = fileName.lastIndexOf('/');
                if (index != -1) {
                    fileName = fileName.substring(index + 1);
                }
            } else {
                int index = imageUrl.lastIndexOf(".");
                // append extension with "."
                if (index != -1) {
                    fileName += imageUrl.substring(index);
                }
            }

            final File destFile = new File(AppSupport.getPicturesDirectory(), fileName);
            new DownloadImageUrl(this, getString(R.string.downloading_image), new URL(imageUrl), destFile) {
                @Override
                protected void onPostExecute(Void result) {
                    super.onPostExecute(result);
                    if (hasError()) {
                        return;
                    }
                    Toast.makeText(getContext(),
                            getString(R.string.image_saved_at_path, destFile.getAbsolutePath()),
                            Toast.LENGTH_SHORT)
                            .show();
                }
            }.execute();
        } catch (Exception e) {
            DialogUtils.showErrorDialog(this, e);
        }
    }

    private void copyURL() {
        Bundle bundle = getIntent().getExtras();
        String imageUrl = bundle.getString(IMAGE_URL);

        ClipboardManager clipboardManager = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newRawUri(getString(R.string.image_url_description), Uri.parse(imageUrl));
        clipboardManager.setPrimaryClip(clip);
        Toast.makeText(this,
                R.string.url_copied_to_clipboard_title,
                Toast.LENGTH_SHORT)
                .show();
    }

    private void shareImage() {
        try {
            final String imageUrl = getIntent().getExtras().getString(IMAGE_URL);
            String fileName = new URI(imageUrl).getPath();
            int index = fileName.lastIndexOf('/');
            if (index != -1) {
                fileName = fileName.substring(index + 1);
            }
            // write to a public location otherwise the called app can't access to file
            final File destFile = new File(AppSupport.getPicturesDirectory(), fileName);
            new DownloadImageUrl(this, getString(R.string.downloading_image), new URL(imageUrl), destFile) {
                @Override
                protected void onPostExecute(Void result) {
                    super.onPostExecute(result);
                    if (hasError()) {
                        return;
                    }
                    String title = getIntent().getExtras().getString(IMAGE_TITLE);
                    if (title == null) {
                        title = "";
                    } else {
                        title = Html.fromHtml(title).toString();
                    }

                    ShareUtils.shareImage(ImageViewerActivity.this,
                            destFile.getAbsolutePath(),
                            "image/jpeg",
                            title,
                            getString(R.string.share_image_title));
                }
            }.execute();
        } catch (Exception e) {
            DialogUtils.showErrorDialog(this, e);
        }
    }

    private void setWallpaper() {
        new AbsProgressIndicatorAsyncTask<Void, Void, Bitmap>(this, getString(R.string.downloading_image)) {
            @Override
            protected Bitmap doInBackground(Void... params) {
                try {
                    final String imageUrl = getIntent().getExtras().getString(IMAGE_URL);
                    return ImageUtils.readImage(imageUrl);
                } catch (Exception e) {
                    setError(e);
                }

                return null;
            }

            @Override
            protected void onPostExecute(Bitmap bitmap) {
                super.onPostExecute(null);
                if (bitmap == null) {
                    return;
                }
                try {
                    WallpaperManager wpm = WallpaperManager.getInstance(ImageViewerActivity.this);
                    float width = wpm.getDesiredMinimumWidth();
                    float height = wpm.getDesiredMinimumHeight();
                    bitmap = ImageUtils.getResizedBitmap(bitmap, width, height);
                    wpm.setBitmap(bitmap);
                    Toast.makeText(ImageViewerActivity.this,
                            getString(R.string.wallpaper_changed_title),
                            Toast.LENGTH_LONG)
                            .show();
                } catch (IOException e) {
                    DialogUtils.showErrorDialog(ImageViewerActivity.this, e);
                }
            }
        }.execute();
    }

    private abstract class DownloadImageUrl extends AbsProgressIndicatorAsyncTask<Void, Void, Void> {
        private final URL imageUrl;
        private final File destFile;

        public DownloadImageUrl(Context context, String message, URL imageUrl, File destFile) {
            super(context, message);
            this.imageUrl = imageUrl;
            this.destFile = destFile;
        }

        @Override
        protected Void doInBackground(Void... voidParams) {
            FileOutputStream os = null;
            InputStream is = null;
            try {
                HttpURLConnection connection = (HttpURLConnection) imageUrl.openConnection();
                is = connection.getInputStream();
                os = new FileOutputStream(destFile);
                IOUtils.copy(is, os);
            } catch (Exception e) {
                setError(e);
            } finally {
                if (is != null) try {
                    is.close();
                } catch (Exception ignored) {
                }
                if (os != null) try {
                    os.close();
                } catch (Exception ignored) {
                }
            }
            return null;
        }
    }
}