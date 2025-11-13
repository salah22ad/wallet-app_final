package com.hpp.daftree;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class DeleteAccountActivity extends AppCompatActivity {

    private WebView webView;
    private static final String DELETE_ACCOUNT_URL = "https://hpp-daftree.web.app/delete-account.html";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_delete_account);

        webView = findViewById(R.id.webView);
        setupWebView();

        // JS ↔ Java bridge
        webView.addJavascriptInterface(new WebAppInterface(this), "Android");

        // تحميل صفحة الحذف
        webView.loadUrl(DELETE_ACCOUNT_URL);
    }

    private void setupWebView() {
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setDatabaseEnabled(true);
        webView.getSettings().setAllowFileAccess(true);
        webView.getSettings().setAllowContentAccess(true);

        webView.getSettings().setUserAgentString(
                "Mozilla/5.0 (Linux; Android 10; WebView App) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.120 Mobile Safari/537.36"
        );

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                view.loadUrl(url);
                return true;
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
            }
        });
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    public class WebAppInterface {
        Context mContext;

        WebAppInterface(Context c) {
            mContext = c;
        }

        @JavascriptInterface
        public void onAccountDeleted() {
            runOnUiThread(() -> {
                Toast.makeText(mContext, "تم حذف الحساب بنجاح", Toast.LENGTH_SHORT).show();
                ((MyApplication) getApplication()).performLogout();
                finish();
            });
        }

        @JavascriptInterface
        public void showToast(String message) {
            runOnUiThread(() ->
                    Toast.makeText(mContext, message, Toast.LENGTH_SHORT).show()
            );
        }

        @JavascriptInterface
        public void logError(String error) {
            runOnUiThread(() ->
                    System.out.println("Firebase Error: " + error)
            );
        }
    }
}
