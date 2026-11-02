package com.signallab.wifi;

import android.Manifest;
import android.app.*;
import android.os.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.location.LocationManager;
import android.net.*;
import android.net.wifi.*;
import android.provider.Settings;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class MainActivity extends Activity {
  final int BG=0xff0b1220, PANEL=0xff172337, FG=0xffedf4ff, MUTED=0xff9eb2c9, AQUA=0xff61e0ce;
  LinearLayout body, bar; TextView status; int tab=0; WifiManager wifi; ConnectivityManager cm; ExecutorService jobs=Executors.newSingleThreadExecutor();
  String report=""; volatile boolean busy=false; ArrayList<ScanResult> found=new ArrayList<>();
  String routerStatus="Пока не подключено", hilinkStatus="Не проверено";
  String session=null; String openWrtGateway=null; int suggestion=1; int scanned24Count=0; boolean scanned=false; boolean scanAfterPermission=true;
  @Override public void onCreate(Bundle b){super.onCreate(b);
    getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
    wifi=(WifiManager)getApplicationContext().getSystemService(WIFI_SERVICE);
    cm=(ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);
    LinearLayout root=column();root.setBackgroundColor(BG);setContentView(root);
    root.setOnApplyWindowInsetsListener((view,insets)->{
      int top,bottom;
      if(Build.VERSION.SDK_INT>=30){
        android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars());
        top=bars.top;bottom=bars.bottom;
      }else{
        top=insets.getSystemWindowInsetTop();bottom=insets.getSystemWindowInsetBottom();
      }
      root.setPadding(0,top,0,bottom);
      return insets;
    });
    TextView heading=text("◉  SIGNAL / LAB",23,AQUA,true); heading.setPadding(dp(19),dp(20),0,0);root.addView(heading);
    TextView sub=text("Анализ Wi-Fi · Диагностика · Роутер",12,MUTED,false);sub.setPadding(dp(19),0,0,dp(18));root.addView(sub);
    ScrollView sc=new ScrollView(this);body=column();body.setPadding(dp(16),dp(10),dp(16),dp(20));sc.addView(body);
    root.addView(sc,new LinearLayout.LayoutParams(-1,0,1));
    status=text("Все измерения остаются на устройстве",11,MUTED,false);status.setGravity(Gravity.CENTER);status.setPadding(5,8,5,10);root.addView(status);
    bar=row();bar.setPadding(dp(5),dp(8),dp(5),dp(12));root.addView(bar);render();
  }
  @Override protected void onDestroy(){jobs.shutdownNow();super.onDestroy();}
  int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
  LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(1);return l;}
  LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setOrientation(0);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
  TextView text(String s,int size,int c,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextColor(c);t.setTextSize(size);if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);t.setLineSpacing(dp(3),1);return t;}
  GradientDrawable bg(int c){GradientDrawable d=new GradientDrawable();d.setColor(c);d.setCornerRadius(dp(17));d.setStroke(dp(1),0xff304359);return d;}
  LinearLayout card(String name){LinearLayout c=column();c.setBackground(bg(PANEL));c.setPadding(dp(16),dp(14),dp(16),dp(16));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(13);body.addView(c,p);
    TextView t=text(name,15,AQUA,true);t.setPadding(0,0,0,dp(10));c.addView(t);return c;}
  void line(LinearLayout c,String key,String val){TextView t=text(key+"    "+val,13,FG,false);t.setPadding(0,dp(3),0,dp(4));c.addView(t);}
  void note(LinearLayout c,String val){TextView t=text(val,13,MUTED,false);t.setPadding(0,dp(5),0,dp(9));c.addView(t);}
  void button(LinearLayout c,String label,Runnable action){Button b=new Button(this);b.setAllCaps(false);b.setText(label);b.setTextSize(13);b.setTextColor(BG);b.setBackground(bg(AQUA));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(48));p.topMargin=dp(9);c.addView(b,p);b.setOnClickListener(v->{if(!busy)action.run();});}
  void page(String title){TextView t=text(title,24,FG,true);t.setPadding(0,dp(5),0,dp(16));body.addView(t);}
  void render(){body.removeAllViews();bar.removeAllViews();String[] tabs={"ОБЗОР","ЭФИР","ТЕСТЫ","РОУТЕР"};for(int i=0;i<4;i++){final int idx=i;TextView b=text(tabs[i],11,tab==i?AQUA:MUTED,true);b.setGravity(Gravity.CENTER);b.setPadding(dp(4),dp(12),dp(4),dp(12));bar.addView(b,new LinearLayout.LayoutParams(0,-2,1));b.setOnClickListener(v->{tab=idx;render();});}
    if(tab==0)overview();else if(tab==1)air();else if(tab==2)tests();else routers();}
  WifiInfo info(){return wifi==null?null:wifi.getConnectionInfo();}
  String name(){WifiInfo w=info();if(w==null)return "Нет подключения";String s=w.getSSID();if(s==null||s.toLowerCase(Locale.ROOT).contains("unknown ssid")){if(!hasLoc())return "Скрыто: нет разрешения на геолокацию";if(!locationEnabled())return "Скрыто: геолокация отключена";return "Скрыто системой Android";}return s;}
  int freq(){WifiInfo w=info();return w==null?0:w.getFrequency();}
  int channel(int f){if(f==2484)return 14;if(f>=2412&&f<2484)return (f-2407)/5;if(f>=5000&&f<5925)return (f-5000)/5;if(f>=5955&&f<=7115)return (f-5950)/5;return 0;}
  String gw(){
    try{
      Network n=network();if(n==null)return "0.0.0.0";
      LinkProperties props=cm.getLinkProperties(n);
      if(props!=null)for(RouteInfo r:props.getRoutes()){
        InetAddress ip=r.getGateway();
        if(r.isDefaultRoute()&&ip instanceof Inet4Address&&!ip.isAnyLocalAddress())return ip.getHostAddress();
      }
    }catch(Exception ignored){}
    return "0.0.0.0";
  }
  Network network(){
    Network active=cm.getActiveNetwork();
    if(active!=null){NetworkCapabilities cap=cm.getNetworkCapabilities(active);if(cap!=null&&cap.hasTransport(NetworkCapabilities.TRANSPORT_WIFI))return active;}
    for(Network n:cm.getAllNetworks()){NetworkCapabilities cap=cm.getNetworkCapabilities(n);if(cap!=null&&cap.hasTransport(NetworkCapabilities.TRANSPORT_WIFI))return n;}
    return null;
  }
  boolean ready(){return network()!=null&&!"0.0.0.0".equals(gw());}
  String ip(){
    try{Network n=network();if(n!=null){LinkProperties p=cm.getLinkProperties(n);if(p!=null)for(LinkAddress a:p.getLinkAddresses())if(a.getAddress() instanceof Inet4Address)return a.getAddress().getHostAddress();}}catch(Exception ignored){}
    return "—";
  }
  boolean hasLoc(){return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED;}
  boolean locationEnabled(){LocationManager l=(LocationManager)getSystemService(LOCATION_SERVICE);return l!=null&&l.isLocationEnabled();}
  void permission(){requestWifiAccess(true);}
  void requestWifiAccess(boolean scanOnSuccess){
    scanAfterPermission=scanOnSuccess;
    ArrayList<String> list=new ArrayList<>();
    if(!hasLoc()){list.add(Manifest.permission.ACCESS_COARSE_LOCATION);list.add(Manifest.permission.ACCESS_FINE_LOCATION);}
    if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.NEARBY_WIFI_DEVICES)!=PackageManager.PERMISSION_GRANTED)
      list.add(Manifest.permission.NEARBY_WIFI_DEVICES);
    if(!list.isEmpty()){requestPermissions(list.toArray(new String[0]),80);return;}
    if(!locationEnabled()){
      new AlertDialog.Builder(this).setTitle("Нужна геолокация Android")
        .setMessage("Для просмотра SSID/BSSID и списка Wi-Fi Android требует включить геолокацию. Приложение не сохраняет координаты.")
        .setPositiveButton("Открыть настройки",(d,w)->startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)))
        .setNegativeButton("Отмена",null).show();
      return;
    }
    if(scanOnSuccess)scan();else render();
  }
  @Override public void onRequestPermissionsResult(int req,String[] perm,int[] grants){
    super.onRequestPermissionsResult(req,perm,grants);
    if(req!=80)return;
    if(hasLoc())requestWifiAccess(scanAfterPermission);
    else toast("Нужно разрешить точное местоположение в настройках приложения");
  }
  @Override protected void onResume(){super.onResume();if(body!=null&&bar!=null)render();}
  void overview(){page("Состояние подключения");LinearLayout c=card("ТЕКУЩАЯ СЕТЬ");WifiInfo w=info();
    line(c,"SSID:",name());line(c,"RSSI:",w==null?"—":w.getRssi()+" dBm");line(c,"Оценка силы сигнала:",w==null?"—":WifiManager.calculateSignalLevel(w.getRssi(),100)+"% (условно)");
    line(c,"Частота:",freq()+" МГц");line(c,"Канал:",String.valueOf(channel(freq())));
    line(c,"Скорость линка:",w==null?"—":w.getLinkSpeed()+" Мбит/с");
    line(c,"BSSID:",w==null||"02:00:00:00:00:00".equals(w.getBSSID())?"Скрыт Android":w.getBSSID());
    if(!hasLoc()||!locationEnabled()||name().startsWith("Скрыт"))button(c,"Разрешить показ SSID/BSSID",()->requestWifiAccess(false));
    button(c,"Обновить",this::render);
    LinearLayout n=card("СЕТЕВЫЕ ПАРАМЕТРЫ");line(n,"IPv4:",ip());line(n,"Шлюз:",gw());Network net=network();
    if(net!=null){LinkProperties p=cm.getLinkProperties(net);if(p!=null)for(InetAddress dns:p.getDnsServers())line(n,"DNS:",dns.getHostAddress());NetworkCapabilities caps=cm.getNetworkCapabilities(net);line(n,"Интернет:",caps!=null&&caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)?"Подтверждён":"Не подтверждён");}
    button(n,"Открыть роутер",this::routerWeb);
    LinearLayout a=card("БЫСТРЫЕ ДЕЙСТВИЯ");button(a,"Анализ радиоэфира",()->{tab=1;render();permission();});button(a,"Диагностика сети",()->{tab=2;render();diagnose();});
  }
  void air(){page("Анализ радиоэфира");LinearLayout c=card("СКАНИРОВАНИЕ");
    note(c,"Результаты могут быть кешированными: Android ограничивает частоту сканирования.");
    button(c,"Найти точки Wi-Fi",this::permission);
    int[] scores={0,0,0};int count=0;for(ScanResult r:found){if(r.frequency<2400||r.frequency>2500)continue;WifiInfo own=info();if(own!=null&&r.BSSID!=null&&r.BSSID.equalsIgnoreCase(own.getBSSID()))continue;count++;
      int ch=channel(r.frequency);int power=Math.max(1,100+r.level);int[] targets={1,6,11};for(int j=0;j<3;j++)scores[j]+=Math.max(0,5-Math.abs(ch-targets[j]))*power;}
    int m=0;for(int i=1;i<3;i++)if(scores[i]<scores[m])m=i;suggestion=new int[]{1,6,11}[m];scanned24Count=scanned?count:0;
    LinearLayout rec=card("ОПТИМИЗАЦИЯ 2,4 ГГц");line(rec,"Точек 2,4 ГГц:",String.valueOf(count));
    line(rec,"Рекомендуемый канал:",scanned24Count>0?String.valueOf(suggestion):"Не определён");
    line(rec,"Оценка 1 / 6 / 11:",scores[0]+" / "+scores[1]+" / "+scores[2]);
    note(rec,"Это оценка по сигналу соседних точек, а не измерение реальной загрузки спектра.");
    LinearLayout list=card("ДОСТУПНЫЕ СЕТИ: "+found.size());for(int i=0;i<Math.min(60,found.size());i++){ScanResult s=found.get(i);line(list,s.SSID==null||s.SSID.isEmpty()?"<скрытая>":s.SSID,channel(s.frequency)+" ch  · "+s.level+" dBm");}
  }
  void scan(){try{scanned=true;boolean scanRequested=wifi.startScan();if(!scanRequested)status.setText("Новый Wi-Fi скан ограничен Android; показан кэш");found=new ArrayList<>(wifi.getScanResults());Collections.sort(found,(a,b)->Integer.compare(b.level,a.level));if(scanRequested)status.setText("Wi-Fi сетей в кэше: "+found.size());render();
    new Handler(Looper.getMainLooper()).postDelayed(()->{try{found=new ArrayList<>(wifi.getScanResults());Collections.sort(found,(a,b)->Integer.compare(b.level,a.level));render();}catch(Exception ignored){}},1800);}
    catch(SecurityException ex){status.setText("Требуется разрешение на поиск Wi-Fi");}}
  void tests(){page("Диагностика сети");LinearLayout a=card("ПОДКЛЮЧЕНИЕ");note(a,"Тест TCP-задержки, DNS, доступа к шлюзу и загрузки тестового файла. Все результаты сохраняются локально.");
    button(a,busy?"Проверка выполняется":"Начать проверку",this::diagnose);
    LinearLayout r=card("ОТЧЁТ");note(r,report.isEmpty()?"Ещё не выполнялось":report);button(r,"Сохранить TXT-отчёт",this::save);}
  void diagnose(){if(!ready()){toast("Подключитесь к Wi-Fi");return;}if(busy)return;busy=true;status.setText("Проверяем сеть…");render();
    final String base="SIGNALLAB · "+new Date()+"\nSSID: "+name()+"\nRSSI: "+(info()==null?"—":info().getRssi())+" dBm\nIP: "+ip()+"\nGateway: "+gw()+"\n" + "Wi-Fi band: "+freq()+" MHz / channel "+channel(freq())+"\n";
    jobs.execute(()->{StringBuilder b=new StringBuilder(base);long[] ms=new long[5];int ok=0;
      try{Network net=network();if(net==null)throw new IOException("Wi-Fi отключился");InetAddress[] addr=net.getAllByName("example.com");b.append("DNS example.com: ").append(addr.length>0?addr[0].getHostAddress():"пустой ответ").append("\n");}catch(Exception e){b.append("DNS: ошибка ").append(e.getMessage()).append("\n");}
      for(int i=0;i<5;i++)try{Socket s=network().getSocketFactory().createSocket();long at=System.nanoTime();s.connect(new InetSocketAddress("1.1.1.1",443),2500);ms[ok++]=TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-at);s.close();}catch(Exception ignored){}
      if(ok>0){Arrays.sort(ms,0,ok);b.append("TCP Интернет 1.1.1.1:443: ").append(ms[ok/2]).append(" ms (медиана, успех ").append(ok).append("/5)\n");}else b.append("TCP Интернет 1.1.1.1:443: недоступен (0/5)\n");
      try{
        Network net=network();if(net==null)throw new IOException("Wi-Fi отключился");
        String gateway=gw();long[] localMs=new long[3];int localOk=0;
        for(int i=0;i<3;i++)try(Socket sock=net.getSocketFactory().createSocket()){
          long start=System.nanoTime();sock.connect(new InetSocketAddress(gateway,80),1800);
          localMs[localOk++]=TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start);
        }catch(IOException ignored){}
        if(localOk>0){Arrays.sort(localMs,0,localOk);b.append("TCP шлюз ").append(gateway).append(":80: ").append(localMs[localOk/2]).append(" ms (успех ").append(localOk).append("/3)\n");}
        else b.append("TCP шлюз :80 не отвечает (это не обязательно обрыв Wi-Fi)\n");
        URL u=new URL("http://"+gateway+"/");
        HttpURLConnection h=(HttpURLConnection)net.openConnection(u);
        h.setConnectTimeout(2300);h.setReadTimeout(2300);h.setInstanceFollowRedirects(false);
        try{int httpStatus=h.getResponseCode();b.append("Панель роутера HTTP: ").append(httpStatus).append("\n");}finally{h.disconnect();}
      }catch(Exception e){b.append("HTTP шлюза: недоступен\n");}
      try{
        Network net=network();if(net==null)throw new IOException("Wi-Fi отключился");
        HttpURLConnection h=(HttpURLConnection)net.openConnection(new URL("https://speed.cloudflare.com/__down?bytes=5000000"));
        h.setConnectTimeout(6000);h.setReadTimeout(20000);
        try{
          int httpStatus=h.getResponseCode();String type=h.getContentType();
          if(httpStatus!=200)throw new IOException("HTTP "+httpStatus);
          if(type!=null&&type.toLowerCase(Locale.ROOT).contains("text/html"))throw new IOException("Получена HTML-страница, не тестовые данные");
          long start=System.nanoTime();long total=0;
          try(InputStream in=h.getInputStream()){
            byte[] buf=new byte[16384];int n;while((n=in.read(buf))!=-1)total+=n;
          }
          if(total<4000000)throw new IOException("Получено лишь "+total+" байт");
          double seconds=(System.nanoTime()-start)/1e9;
          if(seconds<=0)throw new IOException("Некорректное время");
          b.append(String.format(Locale.US,"HTTP загрузка: %.1f Мбит/с (%.2f МБ за %.1f с, оценка)\n",total*8/seconds/1e6,total/1e6,seconds));
        }finally{h.disconnect();}
      }catch(Exception e){b.append("HTTP загрузка: тест не завершён (").append(e.getClass().getSimpleName()).append(")\n");}
      report=b.toString();runOnUiThread(()->{busy=false;status.setText("Диагностика завершена");render();});});
  }
  void save(){if(report.isEmpty()){toast("Сначала выполните проверку");return;}Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("text/plain").putExtra(Intent.EXTRA_TITLE,"SignalLab_Report.txt");startActivityForResult(i,12);}
  @Override protected void onActivityResult(int req,int result,Intent data){super.onActivityResult(req,result,data);if(req==12&&result==RESULT_OK&&data!=null){try(OutputStream out=getContentResolver().openOutputStream(data.getData())){out.write(report.getBytes(java.nio.charset.StandardCharsets.UTF_8));toast("Отчёт сохранён");}catch(Exception e){toast("Ошибка сохранения: "+e.getMessage());}}}
  void routers(){page("Настройка роутера");LinearLayout basic=card("АВТООПРЕДЕЛЕНИЕ");line(basic,"Шлюз:",gw());note(basic,"Открывает штатную панель. Для изменения настроек нужны логин и пароль администратора.");button(basic,"Открыть веб-панель",this::routerWeb);
    LinearLayout h=card("HUAWEI HILINK · LTE / 5G");note(h,hilinkStatus);button(h,"Считать показатели модема",this::hilink);
    LinearLayout o=card("OPENWRT · АВТОНАСТРОЙКА");note(o,routerStatus);note(o,"Подключение к локальному OpenWrt через ubus. Разрешено изменение только канала Wi-Fi 2,4 ГГц 20 МГц после подтверждения. Если связь пропадёт, OpenWrt должен откатить изменение через 60 с. Работает только при разрешённом RPC-доступе.");
    button(o,"Войти в OpenWrt (HTTP в локальной сети)",this::credentials);
  }
  void routerWeb(){if(!ready()){toast("Шлюз Wi-Fi не определён");return;}try{Intent i=new Intent(Intent.ACTION_VIEW,Uri.parse("http://"+gw()+"/"));startActivity(i);}catch(Exception e){toast("Не удалось открыть браузер");}}
  void hilink(){if(!ready()){toast("Нет Wi-Fi");return;}busy=true;jobs.execute(()->{String txt;
      try{HttpURLConnection h=(HttpURLConnection)network().openConnection(new URL("http://"+gw()+"/api/device/signal"));h.setConnectTimeout(2500);h.setReadTimeout(3000);try(InputStream in=h.getInputStream()){byte[] b=new byte[12000];int n=in.read(b);txt=n>0?new String(b,0,n,java.nio.charset.StandardCharsets.UTF_8):"Пустой ответ";if(txt.length()>2500)txt=txt.substring(0,2500);}h.disconnect();}catch(Exception e){txt="HiLink не отвечает или требует авторизацию: "+e.getMessage();}
      final String result=txt.replaceAll("(?is)<(password|token|sessionid)>.*?</\\1>","[скрыто]");runOnUiThread(()->{busy=false;hilinkStatus=result;render();});});}
  void credentials(){if(!ready()){toast("Нет Wi-Fi");return;}openWrtGateway=gw();session=null;LinearLayout c=column();c.setPadding(dp(15),0,dp(15),0);
    EditText username=new EditText(this);username.setSingleLine(true);username.setText("root");username.setHint("Логин");c.addView(username);
    EditText pw=new EditText(this);pw.setSingleLine(true);pw.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);pw.setHint("Пароль");c.addView(pw);
    new AlertDialog.Builder(this).setTitle("Вход в OpenWrt (HTTP)").setMessage("Пароль будет передан по локальному Wi-Fi без шифрования. Используйте только доверенную сеть. Учётные данные не сохраняются.").setView(c).setNegativeButton("Отмена",null).setPositiveButton("Войти",(d,w)->{String user=username.getText().toString(),pass=pw.getText().toString();if(user.isEmpty()||pass.isEmpty()){toast("Введите учётные данные");return;}login(user,pass);}).show();}
  JSONObject rpc(String sid,String method,String obj,JSONObject params) throws Exception{
    JSONArray arr=new JSONArray().put(sid).put(obj).put(method).put(params);
    JSONObject req=new JSONObject().put("jsonrpc","2.0").put("method","call").put("params",arr).put("id",1);
    Network net=network();if(net==null||openWrtGateway==null||!openWrtGateway.equals(gw()))throw new IOException("Wi-Fi/шлюз изменился. Подключение остановлено.");
    HttpURLConnection h=(HttpURLConnection)net.openConnection(new URL("http://"+openWrtGateway+"/ubus"));
    h.setRequestMethod("POST");h.setConnectTimeout(3500);h.setReadTimeout(4000);h.setDoOutput(true);h.setInstanceFollowRedirects(false);h.setRequestProperty("Content-Type","application/json");
    try{try(OutputStream out=h.getOutputStream()){out.write(req.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));}
      if(h.getResponseCode()!=200)throw new IOException("HTTP "+h.getResponseCode());
      ByteArrayOutputStream out=new ByteArrayOutputStream();try(InputStream in=h.getInputStream()){byte[] buf=new byte[4096];int n;while((n=in.read(buf))!=-1){out.write(buf,0,n);if(out.size()>100000)throw new IOException("Ответ слишком большой");}}
      JSONArray result=new JSONObject(out.toString("UTF-8")).getJSONArray("result");if(result.getInt(0)!=0)throw new IOException("ubus code "+result.getInt(0));return result.length()>1?result.getJSONObject(1):new JSONObject();
    }finally{h.disconnect();}}
  JSONObject uciGet() throws Exception{return rpc(session,"get","uci",new JSONObject().put("config","wireless"));}
  String selectedRadio(){try{JSONObject vals=uciGet().getJSONObject("values");Iterator<String> keys=vals.keys();while(keys.hasNext()){String key=keys.next();JSONObject v=vals.optJSONObject(key);
      if(v!=null&&"wifi-device".equals(v.optString(".type"))&&("2g".equals(v.optString("band"))||"11g".equals(v.optString("hwmode")))&&("HT20".equals(v.optString("htmode"))||"HE20".equals(v.optString("htmode"))))return key;}}catch(Exception ignored){}return "";}
  void login(String user,String pass){busy=true;status.setText("Авторизация OpenWrt…");
    jobs.execute(()->{try{JSONObject p=new JSONObject().put("username",user).put("password",pass).put("timeout",300);
      JSONObject r=rpc("00000000000000000000000000000000","login","session",p);
      String sid=r.getString("ubus_rpc_session");if(!sid.matches("[0-9a-fA-F]{32}"))throw new IOException("Некорректная сессия");session=sid;
      String radio=selectedRadio();routerStatus="Вход выполнен. 2,4 ГГц радио: "+(radio.isEmpty()?"не найдено":radio);
      runOnUiThread(()->{busy=false;render();if(!radio.isEmpty()&&scanned24Count>0)new AlertDialog.Builder(this).setTitle("Оптимизировать канал?")
        .setMessage("Переключить "+radio+" на канал "+suggestion+"? Сеть может пропасть. OpenWrt должен откатить изменение через 60 секунд, если не подтвердить. Убедитесь, что у вас есть резервный доступ к роутеру.")
        .setPositiveButton("Применить",(d,w)->applyChannel(radio,suggestion)).setNegativeButton("Отмена",null).show();else if(!radio.isEmpty())toast("Сначала просканируйте соседние Wi-Fi сети");});
      }catch(Exception e){runOnUiThread(()->{busy=false;routerStatus="Ошибка: "+e.getMessage();render();});}});
  }
  void applyChannel(String radio,int ch){if(session==null||!(ch==1||ch==6||ch==11))return;busy=true;status.setText("Применяем изменения…");
    jobs.execute(()->{try{rpc(session,"set","uci",new JSONObject().put("config","wireless").put("section",radio).put("values",new JSONObject().put("channel",String.valueOf(ch))));
      rpc(session,"apply","uci",new JSONObject().put("rollback",true).put("timeout",60));
      Thread.sleep(5000);JSONObject vals=uciGet().getJSONObject("values");if(!String.valueOf(ch).equals(vals.getJSONObject(radio).optString("channel")))throw new IOException("Канал не подтвердился");
      rpc(session,"confirm","uci",new JSONObject());routerStatus="Канал "+ch+" подтверждён OpenWrt";
    }catch(Exception e){routerStatus="Ошибка/откат OpenWrt: "+e.getMessage();}
      runOnUiThread(()->{busy=false;status.setText(routerStatus);render();});});
  }
  void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
}