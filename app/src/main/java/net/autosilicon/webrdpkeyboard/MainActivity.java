package net.autosilicon.webrdpkeyboard;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.InputType;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.Toast;

import org.json.JSONObject;

import java.util.Locale;

public final class MainActivity extends Activity {
    private static final String PREFS = "web_rdp_keyboard";
    private static final String PREF_HOME_URL = "home_url";
    private static final String DEFAULT_HOME_URL = "https://cangyuan.cloudflareaccess.com/#/Launcher";
    private static final int TOOLBAR_HEIGHT_DP = 54;

    private WebView webView;
    private ImeProxyView imeProxy;
    private SharedPreferences preferences;
    private Button shiftButton;
    private Button ctrlButton;
    private boolean shiftLocked;
    private boolean ctrlLocked;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        preferences = getSharedPreferences(PREFS, MODE_PRIVATE);
        buildUi();
        configureWebView();

        webView.loadUrl(homeUrl());
    }

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(16, 18, 23));
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            } else {
                view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                        insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            }
            return insets;
        });

        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        root.addView(column, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        webView = new WebView(this);
        webView.setBackgroundColor(Color.BLACK);
        webView.setFocusable(true);
        webView.setFocusableInTouchMode(true);
        column.addView(webView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(22, 25, 31));
        column.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(TOOLBAR_HEIGHT_DP)));

        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(dp(4), dp(4), dp(4), dp(4));
        scroll.addView(toolbar, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        toolbar.addView(keyButton("⌂", "Cloudflare Launcher 主页", v -> webView.loadUrl(homeUrl())));
        toolbar.addView(keyButton("‹", "后退", v -> {
            if (webView.canGoBack()) webView.goBack();
        }));
        toolbar.addView(keyButton("›", "前进", v -> {
            if (webView.canGoForward()) webView.goForward();
        }));
        toolbar.addView(keyButton("↻", "刷新", v -> webView.reload()));
        toolbar.addView(keyButton("⌨", "显示输入法", v -> showKeyboard()));
        toolbar.addView(keyButton("Esc", "Escape", v -> sendKey(KeyEvent.KEYCODE_ESCAPE)));
        toolbar.addView(keyButton("Tab", "Tab", v -> sendKey(KeyEvent.KEYCODE_TAB)));

        shiftButton = keyButton("Shift", "锁定 Shift", v -> toggleShift());
        ctrlButton = keyButton("Ctrl", "锁定 Ctrl", v -> toggleCtrl());
        toolbar.addView(shiftButton);
        toolbar.addView(ctrlButton);

        toolbar.addView(keyButton("Enter", "Enter", v -> sendKey(KeyEvent.KEYCODE_ENTER)));
        toolbar.addView(keyButton("⌫", "退格", v -> sendKey(KeyEvent.KEYCODE_DEL)));
        toolbar.addView(keyButton("←", "左方向键", v -> sendKey(KeyEvent.KEYCODE_DPAD_LEFT)));
        toolbar.addView(keyButton("↑", "上方向键", v -> sendKey(KeyEvent.KEYCODE_DPAD_UP)));
        toolbar.addView(keyButton("↓", "下方向键", v -> sendKey(KeyEvent.KEYCODE_DPAD_DOWN)));
        toolbar.addView(keyButton("→", "右方向键", v -> sendKey(KeyEvent.KEYCODE_DPAD_RIGHT)));
        toolbar.addView(keyButton("⚙", "设置浏览器主页", v -> showHomeDialog()));

        imeProxy = new ImeProxyView(this);
        imeProxy.setFocusable(true);
        imeProxy.setFocusableInTouchMode(true);
        imeProxy.setBackgroundColor(Color.TRANSPARENT);
        FrameLayout.LayoutParams proxyParams = new FrameLayout.LayoutParams(dp(1), dp(1), Gravity.BOTTOM | Gravity.END);
        root.addView(imeProxy, proxyParams);

        setContentView(root);
        updateModifierButtons();
    }

    private void configureWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setLoadsImagesAutomatically(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(false);
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setUserAgentString(settings.getUserAgentString() + " WebRdpKeyboard/1.0 AndroidWrapper");

        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.setAcceptThirdPartyCookies(webView, true);
        boolean debuggable = (getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
        WebView.setWebContentsDebuggingEnabled(debuggable);

        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return false;
            }

            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                releaseModifiers();
            }

            @Override
            public void onReceivedHttpError(WebView view, WebResourceRequest request,
                                            WebResourceResponse errorResponse) {
                if (request.isForMainFrame()) {
                    Toast.makeText(MainActivity.this,
                            "页面返回 HTTP " + errorResponse.getStatusCode(), Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private Button keyButton(String label, String description, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(13);
        button.setTextColor(Color.WHITE);
        button.setAllCaps(false);
        button.setContentDescription(description);
        button.setMinWidth(dp(label.length() <= 1 ? 48 : 58));
        button.setMinimumWidth(0);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setPadding(dp(10), 0, dp(10), 0);
        button.setFocusable(false);
        button.setFocusableInTouchMode(false);
        button.setOnClickListener(listener);
        applyButtonBackground(button, false);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
        params.setMargins(dp(2), 0, dp(2), 0);
        button.setLayoutParams(params);
        return button;
    }

    private void applyButtonBackground(Button button, boolean locked) {
        GradientDrawable background = new GradientDrawable();
        background.setCornerRadius(dp(8));
        background.setColor(locked ? Color.rgb(31, 111, 235) : Color.rgb(45, 50, 60));
        background.setStroke(dp(1), locked ? Color.rgb(110, 168, 254) : Color.rgb(71, 78, 92));
        button.setBackground(background);
    }

    private void toggleShift() {
        long now = SystemClock.uptimeMillis();
        if (shiftLocked) {
            dispatchRawKey(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_SHIFT_LEFT, metaState(), now, now);
            shiftLocked = false;
        } else {
            shiftLocked = true;
            dispatchRawKey(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_SHIFT_LEFT, metaState(), now, now);
        }
        updateModifierButtons();
        keepKeyboardFocusIfVisible();
    }

    private void toggleCtrl() {
        long now = SystemClock.uptimeMillis();
        if (ctrlLocked) {
            dispatchRawKey(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_CTRL_LEFT, metaState(), now, now);
            ctrlLocked = false;
        } else {
            ctrlLocked = true;
            dispatchRawKey(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_CTRL_LEFT, metaState(), now, now);
        }
        updateModifierButtons();
        keepKeyboardFocusIfVisible();
    }

    private void updateModifierButtons() {
        if (shiftButton != null) {
            shiftButton.setText(shiftLocked ? "Shift 🔒" : "Shift");
            applyButtonBackground(shiftButton, shiftLocked);
        }
        if (ctrlButton != null) {
            ctrlButton.setText(ctrlLocked ? "Ctrl 🔒" : "Ctrl");
            applyButtonBackground(ctrlButton, ctrlLocked);
        }
    }

    private int metaState() {
        int state = 0;
        if (shiftLocked) {
            state |= KeyEvent.META_SHIFT_ON | KeyEvent.META_SHIFT_LEFT_ON;
        }
        if (ctrlLocked) {
            state |= KeyEvent.META_CTRL_ON | KeyEvent.META_CTRL_LEFT_ON;
        }
        return KeyEvent.normalizeMetaState(state);
    }

    private void sendKey(int keyCode) {
        long now = SystemClock.uptimeMillis();
        int meta = metaState();
        boolean downHandled = dispatchRawKey(KeyEvent.ACTION_DOWN, keyCode, meta, now, now);
        boolean upHandled = dispatchRawKey(KeyEvent.ACTION_UP, keyCode, meta, now, now + 1);
        if (!downHandled && !upHandled) {
            dispatchKeyWithJavaScript(keyCode);
        }
        keepKeyboardFocusIfVisible();
    }

    private boolean dispatchRawKey(int action, int keyCode, int meta, long downTime, long eventTime) {
        KeyEvent event = new KeyEvent(
                downTime,
                eventTime,
                action,
                keyCode,
                0,
                meta,
                KeyCharacterMap.VIRTUAL_KEYBOARD,
                0,
                KeyEvent.FLAG_SOFT_KEYBOARD | KeyEvent.FLAG_KEEP_TOUCH_MODE,
                InputDevice.SOURCE_KEYBOARD);
        return webView.dispatchKeyEvent(event);
    }

    private void sendCommittedText(CharSequence committed) {
        if (committed == null || committed.length() == 0) {
            return;
        }
        String text = committed.toString();
        int index = 0;
        while (index < text.length()) {
            int codePoint = text.codePointAt(index);
            String unit = new String(Character.toChars(codePoint));
            index += Character.charCount(codePoint);

            if (codePoint == '\n' || codePoint == '\r') {
                sendKey(KeyEvent.KEYCODE_ENTER);
                continue;
            }

            KeyEvent[] events = KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD)
                    .getEvents(unit.toCharArray());
            if (events == null || events.length == 0) {
                dispatchUnicodeWithJavaScript(unit);
                continue;
            }

            boolean handled = false;
            for (KeyEvent original : events) {
                long now = SystemClock.uptimeMillis();
                handled |= dispatchRawKey(original.getAction(), original.getKeyCode(),
                        original.getMetaState() | metaState(), now, now);
            }
            if (!handled) {
                dispatchUnicodeWithJavaScript(unit);
            }
        }
    }

    private void dispatchUnicodeWithJavaScript(String text) {
        String quoted = JSONObject.quote(text);
        String script = "(function(t,c,s){" +
                "var e=document.activeElement||document.body||window;" +
                "Array.from(t).forEach(function(ch){" +
                "try{e.dispatchEvent(new CompositionEvent('compositionstart',{data:'',bubbles:true}));}catch(_){}" +
                "try{e.dispatchEvent(new KeyboardEvent('keydown',{key:ch,ctrlKey:c,shiftKey:s,bubbles:true,cancelable:true}));}catch(_){}" +
                "try{e.dispatchEvent(new InputEvent('beforeinput',{data:ch,inputType:'insertText',bubbles:true,cancelable:true}));}catch(_){}" +
                "try{e.dispatchEvent(new InputEvent('input',{data:ch,inputType:'insertText',bubbles:true}));}catch(_){}" +
                "try{e.dispatchEvent(new CompositionEvent('compositionend',{data:ch,bubbles:true}));}catch(_){}" +
                "try{e.dispatchEvent(new KeyboardEvent('keyup',{key:ch,ctrlKey:c,shiftKey:s,bubbles:true,cancelable:true}));}catch(_){}" +
                "});return true;})(" + quoted + "," + ctrlLocked + "," + shiftLocked + ");";
        webView.evaluateJavascript(script, null);
    }

    private void dispatchKeyWithJavaScript(int keyCode) {
        String key;
        String code;
        switch (keyCode) {
            case KeyEvent.KEYCODE_ESCAPE: key = "Escape"; code = "Escape"; break;
            case KeyEvent.KEYCODE_TAB: key = "Tab"; code = "Tab"; break;
            case KeyEvent.KEYCODE_ENTER: key = "Enter"; code = "Enter"; break;
            case KeyEvent.KEYCODE_DEL: key = "Backspace"; code = "Backspace"; break;
            case KeyEvent.KEYCODE_DPAD_LEFT: key = "ArrowLeft"; code = "ArrowLeft"; break;
            case KeyEvent.KEYCODE_DPAD_UP: key = "ArrowUp"; code = "ArrowUp"; break;
            case KeyEvent.KEYCODE_DPAD_DOWN: key = "ArrowDown"; code = "ArrowDown"; break;
            case KeyEvent.KEYCODE_DPAD_RIGHT: key = "ArrowRight"; code = "ArrowRight"; break;
            default: return;
        }
        String script = String.format(Locale.US,
                "(function(){var e=document.activeElement||document.body||window;" +
                        "e.dispatchEvent(new KeyboardEvent('keydown',{key:%s,code:%s,ctrlKey:%s,shiftKey:%s,bubbles:true,cancelable:true}));" +
                        "e.dispatchEvent(new KeyboardEvent('keyup',{key:%s,code:%s,ctrlKey:%s,shiftKey:%s,bubbles:true,cancelable:true}));})();",
                JSONObject.quote(key), JSONObject.quote(code), ctrlLocked, shiftLocked,
                JSONObject.quote(key), JSONObject.quote(code), ctrlLocked, shiftLocked);
        webView.evaluateJavascript(script, null);
    }

    private void showKeyboard() {
        imeProxy.requestFocus();
        InputMethodManager manager = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        manager.restartInput(imeProxy);
        if (!manager.showSoftInput(imeProxy, InputMethodManager.SHOW_IMPLICIT)) {
            imeProxy.postDelayed(() -> manager.showSoftInput(imeProxy, InputMethodManager.SHOW_IMPLICIT), 120);
        }
    }

    private void keepKeyboardFocusIfVisible() {
        if (imeProxy.hasFocus()) {
            imeProxy.post(imeProxy::requestFocus);
        }
    }

    private void releaseModifiers() {
        long now = SystemClock.uptimeMillis();
        if (shiftLocked) {
            dispatchRawKey(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_SHIFT_LEFT, metaState(), now, now);
            shiftLocked = false;
        }
        if (ctrlLocked) {
            dispatchRawKey(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_CTRL_LEFT, metaState(), now, now);
            ctrlLocked = false;
        }
        updateModifierButtons();
    }

    private String homeUrl() {
        String saved = preferences.getString(PREF_HOME_URL, DEFAULT_HOME_URL);
        if (saved == null || saved.trim().isEmpty()) return DEFAULT_HOME_URL;
        return saved;
    }

    private void showHomeDialog() {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        input.setHint(DEFAULT_HOME_URL);
        input.setText(homeUrl());
        input.setSelectAllOnFocus(true);

        FrameLayout box = new FrameLayout(this);
        int margin = dp(20);
        box.setPadding(margin, 0, margin, 0);
        box.addView(input, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("浏览器主页")
                .setMessage("默认是 Cloudflare App Launcher。修改后会设为主页并立即打开。")
                .setView(box)
                .setPositiveButton("保存并打开", null)
                .setNegativeButton("取消", null)
                .setNeutralButton("恢复默认", null)
                .setCancelable(true)
                .create();

        dialog.setOnShowListener(ignored -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                    String url = normalizeHttpsUrl(input.getText().toString());
                    if (url == null) {
                        input.setError("请输入有效的 HTTPS 地址");
                        return;
                    }
                    preferences.edit().putString(PREF_HOME_URL, url).apply();
                    webView.loadUrl(url);
                    dialog.dismiss();
                });
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
                preferences.edit().remove(PREF_HOME_URL).apply();
                webView.loadUrl(DEFAULT_HOME_URL);
                dialog.dismiss();
            });
        });
        dialog.show();
    }

    private String normalizeHttpsUrl(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty()) return null;
        if (!value.contains("://")) value = "https://" + value;
        Uri uri = Uri.parse(value);
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) return null;
        return uri.toString();
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onPause() {
        releaseModifiers();
        CookieManager.getInstance().flush();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.stopLoading();
            webView.setWebChromeClient(null);
            webView.setWebViewClient(null);
            webView.destroy();
        }
        super.onDestroy();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private final class ImeProxyView extends View {
        ImeProxyView(Context context) {
            super(context);
        }

        @Override
        public boolean onCheckIsTextEditor() {
            return true;
        }

        @Override
        public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
            outAttrs.inputType = InputType.TYPE_CLASS_TEXT |
                    InputType.TYPE_TEXT_FLAG_MULTI_LINE |
                    InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS;
            outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI |
                    EditorInfo.IME_FLAG_NO_FULLSCREEN |
                    EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING |
                    EditorInfo.IME_ACTION_NONE;
            outAttrs.initialSelStart = 0;
            outAttrs.initialSelEnd = 0;

            return new BaseInputConnection(this, false) {
                @Override
                public boolean commitText(CharSequence text, int newCursorPosition) {
                    sendCommittedText(text);
                    return true;
                }

                @Override
                public boolean setComposingText(CharSequence text, int newCursorPosition) {
                    return true;
                }

                @Override
                public boolean finishComposingText() {
                    return true;
                }

                @Override
                public boolean deleteSurroundingText(int beforeLength, int afterLength) {
                    for (int i = 0; i < Math.max(1, beforeLength); i++) {
                        sendKey(KeyEvent.KEYCODE_DEL);
                    }
                    return true;
                }

                @Override
                public boolean sendKeyEvent(KeyEvent event) {
                    return webView.dispatchKeyEvent(event);
                }

                @Override
                public boolean performEditorAction(int actionCode) {
                    sendKey(KeyEvent.KEYCODE_ENTER);
                    return true;
                }
            };
        }
    }
}
