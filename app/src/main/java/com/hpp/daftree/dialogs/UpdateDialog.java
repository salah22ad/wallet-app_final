package com.hpp.daftree.dialogs;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Base64;
import android.view.ViewGroup;
import android.view.Window;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.hpp.daftree.R;
import com.hpp.daftree.utils.VersionManager;

import java.io.ByteArrayOutputStream;

public class UpdateDialog extends Dialog {

    private VersionManager versionManager;

    public UpdateDialog(Context context) {
        super(context, R.style.FullScreenDialog);
        this.versionManager = new VersionManager(context);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // تعيين محتوى الديالوج
        setContentView(R.layout.dialog_update);

        // جعل الديالوج كامل الشاشة
        Window window = getWindow();
        if (window != null) {
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        }

        // إعداد WebView
        WebView webView = findViewById(R.id.update_webview);
        setupWebView(webView);

        // تحميل صفحة التحديثات
        webView.loadUrl("file:///android_asset/updates_changs.html");
    }

    private void setupWebView(WebView webView) {
        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setBuiltInZoomControls(true);
        webSettings.setDisplayZoomControls(false);
        webSettings.setSupportZoom(true);
        webSettings.setUseWideViewPort(true);
        webSettings.setLoadWithOverviewMode(true);
        webSettings.setAllowFileAccess(true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                // إرسال أيقونة التطبيق إلى JavaScript بعد تحميل الصفحة
                setAppIconToWebView(webView);
            }
        });

        // تمكين JavaScript للتواصل مع التطبيق
        webView.addJavascriptInterface(new WebAppInterface(), "android");
        webView.setWebChromeClient(new WebChromeClient());
    }

    private void setAppIconToWebView(WebView webView) {
        try {
            Bitmap icon = BitmapFactory.decodeResource(getContext().getResources(), R.mipmap.ic_launcher);
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            icon.compress(Bitmap.CompressFormat.PNG, 100, byteArrayOutputStream);
            byte[] byteArray = byteArrayOutputStream.toByteArray();
            String iconBase64 = Base64.encodeToString(byteArray, Base64.DEFAULT);

            webView.loadUrl("javascript:setAppIcon('data:image/png;base64," + iconBase64 + "')");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // للتحقق إذا كان يجب عرض الديالوج
    public static boolean shouldShowUpdate(Context context) {
        VersionManager versionManager = new VersionManager(context);
        return versionManager.isNewVersion() && !versionManager.isUpdateShownForCurrentVersion();
    }

    // واجهة للتواصل بين JavaScript والتطبيق
    public class WebAppInterface {
        @android.webkit.JavascriptInterface
        public void closeUpdateDialog() {
            dismiss();
        }
    }

    @Override
    public void show() {
        super.show();
        versionManager.markUpdateAsShown();
        // حفظ الإصدار الحالي بعد عرض التحديثات
        versionManager.saveCurrentVersion();
    }

    @Override
    public void dismiss() {
        super.dismiss();
    }
}