package dev.relaybox.app;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Build;
import android.os.Bundle;
import android.util.Base64;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.SslErrorHandler;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import java.nio.charset.StandardCharsets;

public final class ChatActivity extends Activity {
    private Platform platform;
    private MessageStore.Entry selected;
    private WebView web;
    private TextView hint;
    private ProgressBar progress;
    private ValueCallback<Uri[]> files;
    private static final int PICK=371;

    private String key(String sender) {
        return "chat_"+platform.id+"_"+Base64.encodeToString(sender.getBytes(StandardCharsets.UTF_8),Base64.URL_SAFE|Base64.NO_WRAP);
    }
    private TextView button(String label,LinearLayout parent,Runnable action) {
        TextView b=new TextView(this);
        b.setText(label);b.setTextSize(13);b.setTextColor(0xFFEAF0FC);
        b.setPadding(12,18,12,18);b.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        b.setOnClickListener(v->action.run());
        parent.addView(b,new LinearLayout.LayoutParams(0,-2,1));
        return b;
    }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        platform=Platform.from(getIntent().getStringExtra("platform"));
        if(platform==null) {finish();return;}
        long id=getIntent().getLongExtra("row",-1);
        if(id>0)try(MessageStore db=new MessageStore(this)) {selected=db.byId(id); db.markRead(id);}
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(0xFF101827);
        setContentView(root);
        TextView title=new TextView(this);
        title.setText(platform.label+" · Чат"); title.setTextColor(Color.WHITE);title.setTextSize(20);
        title.setPadding(18,15,18,10);root.addView(title);
        hint=new TextView(this);
        hint.setText(selected==null?"Авторизуйтесь на официальном сайте внутри RelayBox.":
                "Уведомление от "+selected.sender+": "+selected.message);
        hint.setTextColor(0xFFB9C8E0);hint.setTextSize(12);hint.setMaxLines(3);
        hint.setPadding(18,5,18,10);root.addView(hint);
        progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);root.addView(progress,new LinearLayout.LayoutParams(-1,4));
        web=new WebView(this);web.setBackgroundColor(Color.WHITE);
        WebSettings cfg=web.getSettings();
        cfg.setJavaScriptEnabled(true);cfg.setDomStorageEnabled(true);
        cfg.setAllowFileAccess(false);cfg.setAllowContentAccess(true);
        cfg.setAllowFileAccessFromFileURLs(false);cfg.setAllowUniversalAccessFromFileURLs(false);
        cfg.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        cfg.setSupportMultipleWindows(false);
        if(Build.VERSION.SDK_INT>=26)cfg.setSafeBrowsingEnabled(true);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web,true);
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest request) {
                Uri target=request.getUrl();
                if(!request.isForMainFrame()||Platform.isOfficial(platform,target.toString()))return false;
                new AlertDialog.Builder(ChatActivity.this).setTitle("Внешняя ссылка")
                        .setMessage("Открыть "+target.getHost()+" в браузере? Авторизация через сторонние сервисы может быть недоступна в WebView.")
                        .setNegativeButton("Нет",null)
                        .setPositiveButton("Открыть",(d,w)->external(target)).show();
                return true;
            }
            @Override public void onPageStarted(WebView v,String url,android.graphics.Bitmap icon) {
                progress.setVisibility(View.VISIBLE);
            }
            @Override public void onPageFinished(WebView v,String url) {
                progress.setVisibility(View.GONE);CookieManager.getInstance().flush();
            }
            @Override public void onReceivedError(WebView v,WebResourceRequest r,WebResourceError error) {
                if(r.isForMainFrame()){hint.setText("Сайт недоступен или нет интернета.");progress.setVisibility(View.GONE);}
            }
            @Override public void onReceivedSslError(WebView v,SslErrorHandler handler,SslError error) {
                handler.cancel();hint.setText("Недействительный сертификат HTTPS: страница заблокирована.");
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override public void onProgressChanged(WebView v,int percent){progress.setProgress(percent);}
            @Override public boolean onShowFileChooser(WebView v,ValueCallback<Uri[]> callback,FileChooserParams params) {
                if(files!=null)files.onReceiveValue(null);
                files=callback;
                try {
                    Intent picker=new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    picker.addCategory(Intent.CATEGORY_OPENABLE);
                    String[] accept=params.getAcceptTypes();
                    if(accept!=null&&accept.length==1&&accept[0].contains("/"))picker.setType(accept[0]);
                    else {picker.setType("*/*"); if(accept!=null&&accept.length>1)picker.putExtra(Intent.EXTRA_MIME_TYPES,accept);}
                    picker.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,params.getMode()==FileChooserParams.MODE_OPEN_MULTIPLE);
                    startActivityForResult(picker,PICK);return true;
                }catch(Exception ex){files.onReceiveValue(null);files=null;return false;}
            }
        });
        root.addView(web,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout bar=new LinearLayout(this);
        button("Назад",bar,()->{if(web.canGoBack())web.goBack();else finish();});
        button("Чаты",bar,()->web.loadUrl(platform.url));
        button("Обновить",bar,()->web.reload());
        button("В браузер",bar,()->external(Uri.parse(platform.url)));
        root.addView(bar);
        if(selected!=null) {
            LinearLayout saveBar=new LinearLayout(this);
            button("Привязать диалог к "+selected.sender,saveBar,()->{
                String current=web.getUrl();
                if(!Platform.specificLink(platform,current)) {
                    new AlertDialog.Builder(this).setMessage("Откройте конкретный чат на официальном сайте. Привязка доступна только если URL диалога отличается от списка чатов.")
                            .setPositiveButton("ОК",null).show();return;
                }
                getSharedPreferences("relaybox",0).edit().putString(key(selected.sender),current).apply();
                new AlertDialog.Builder(this).setMessage("Ссылка на чат сохранена.")
                        .setPositiveButton("ОК",null).show();
            });
            root.addView(saveBar);
        }
        String saved=selected==null?null:getSharedPreferences("relaybox",0).getString(key(selected.sender),null);
        String restored=state==null?null:state.getString("url");
        web.loadUrl(Platform.isOfficial(platform,restored)?restored:
                Platform.chatLink(platform,saved)?saved:platform.url);
    }
    private void external(Uri uri) {
        if(!"https".equalsIgnoreCase(uri.getScheme()))return;
        try {startActivity(new Intent(Intent.ACTION_VIEW,uri));}
        catch(Exception ignored){new AlertDialog.Builder(this).setMessage("Не найден браузер.")
                .setPositiveButton("ОК",null).show();}
    }
    @Override protected void onSaveInstanceState(Bundle state){
        if(web!=null)state.putString("url",web.getUrl());super.onSaveInstanceState(state);
    }
    @Override protected void onActivityResult(int req,int result,Intent data) {
        super.onActivityResult(req,result,data);
        if(req!=PICK||files==null)return;
        Uri[] resultFiles=null;
        if(result==RESULT_OK&&data!=null) {
            if(data.getClipData()!=null){
                int count=data.getClipData().getItemCount(); resultFiles=new Uri[count];
                for(int i=0;i<count;i++)resultFiles[i]=data.getClipData().getItemAt(i).getUri();
            }else if(data.getData()!=null)resultFiles=new Uri[]{data.getData()};
        }
        files.onReceiveValue(resultFiles);files=null;
    }
    @Override public void onBackPressed(){if(web!=null&&web.canGoBack())web.goBack();else super.onBackPressed();}
    @Override protected void onDestroy() {
        if(files!=null){files.onReceiveValue(null);files=null;}
        if(web!=null){web.stopLoading();web.destroy();web=null;}
        super.onDestroy();
    }
}
