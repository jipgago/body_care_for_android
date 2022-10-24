package com.example.body_care;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

public class ChatActivity extends Activity {
    Button back;
    WebView webview1;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        webview1 = (WebView) findViewById(R.id.webview1);
        webview1.getSettings().setRenderPriority(WebSettings.RenderPriority.HIGH);
        webview1.setWebViewClient(new WebViewClient());
        webview1.setWebChromeClient(new WebChromeClient());
        webview1.setNetworkAvailable(true);
        webview1.getSettings().setJavaScriptEnabled(true);

        //// Sets whether the DOM storage API is enabled.
        webview1.getSettings().setDomStorageEnabled(true);
        ////


        webview1.loadUrl("http://175.106.98.133:8071/chat/#/1/55164b18-3ede-45cc-8a70-5eaaf3c95bb5?embedded=true");

        back = (Button) findViewById(R.id.go_back);
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
            }
        });
    }
}
