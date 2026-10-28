package dev.relaybox.app;

import android.content.Context;
import android.app.Notification;
import android.os.Bundle;
import java.net.URI;
import java.util.Locale;

public enum Platform {
    AVITO("Авито", "avito", "https://www.avito.ru/profile/messenger", "avito.ru"),
    FUNPAY("FunPay", "funpay", "https://funpay.com/chat/", "funpay.com"),
    PLAYEROK("Playerok", "playerok", "https://playerok.com/chats", "playerok.com");

    public final String label, id, url, host;
    Platform(String label, String id, String url, String host) {
        this.label=label; this.id=id; this.url=url; this.host=host;
    }
    static Platform from(String id) {
        if (id==null) return null;
        for (Platform p: values()) if(p.id.equals(id)) return p;
        return null;
    }
    static boolean allowed(Context context, Platform p) {
        return context.getSharedPreferences("relaybox",0).getBoolean("platform_"+p.id,true);
    }
    static boolean isOfficial(Platform p, String address) {
        if(p==null || address==null || address.length()>2048) return false;
        try {
            URI u=URI.create(address);
            String h=u.getHost();
            return "https".equalsIgnoreCase(u.getScheme()) && h!=null
                    && u.getUserInfo()==null && (u.getPort()==-1 || u.getPort()==443)
                    && (h.equalsIgnoreCase(p.host) || h.toLowerCase(Locale.ROOT).endsWith("."+p.host));
        } catch(Exception e) { return false; }
    }
    static boolean chatLink(Platform p, String address) {
        if(!isOfficial(p,address)) return false;
        try {
            URI u=URI.create(address);
            String path=u.getPath();
            if(path==null) return false;
            switch(p) {
                case AVITO: return path.startsWith("/profile/messenger");
                case FUNPAY: return path.startsWith("/chat") || path.matches("^/(ru|en|de|fr|es|pt|tr)/chat(/.*)?$");
                case PLAYEROK: return path.startsWith("/chats");
                default: return false;
            }
        } catch(Exception e) { return false; }
    }
    static boolean specificLink(Platform p, String address) {
        if(!chatLink(p,address)) return false;
        try {
            URI a=URI.create(address), b=URI.create(p.url);
            return !a.getPath().replaceAll("/+$","").equals(b.getPath().replaceAll("/+$",""))
                    || a.getRawQuery()!=null || a.getRawFragment()!=null;
        } catch(Exception e) { return false; }
    }
    static String str(Bundle bundle, String key) {
        if(bundle==null) return "";
        CharSequence value=bundle.getCharSequence(key);
        return value==null ? "" : value.toString().trim();
    }
    static Platform recognize(Context ctx, String pkg, Notification notification) {
        if(pkg==null || pkg.equals(ctx.getPackageName()) || notification==null) return null;
        if(pkg.equals("com.avito.android") || pkg.startsWith("com.avito.android.")) {
            return allowed(ctx, AVITO) ? AVITO : null;
        }
        boolean browser=pkg.equals("com.android.chrome") || pkg.equals("org.mozilla.firefox")
                || pkg.equals("org.mozilla.fenix") || pkg.equals("com.yandex.browser")
                || pkg.equals("com.brave.browser") || pkg.equals("com.microsoft.emmx")
                || pkg.equals("com.opera.browser") || pkg.equals("com.mi.globalbrowser")
                || pkg.equals("com.miui.globalbrowser") || pkg.equals("com.sec.android.app.sbrowser");
        if(!browser || !ctx.getSharedPreferences("relaybox",0).getBoolean("browsers",true)) return null;
        Bundle e=notification.extras;
        String attribution=(str(e,Notification.EXTRA_SUB_TEXT)+" "+str(e,"android.substName")+" "
                +str(e,Notification.EXTRA_TITLE)+" "
                +(notification.getChannelId()==null?"":notification.getChannelId())).toLowerCase(Locale.ROOT);
        if(ctx.getSharedPreferences("relaybox",0).getBoolean("loose",false))
            attribution += " "+str(e,Notification.EXTRA_TEXT).toLowerCase(Locale.ROOT);
        for(Platform p:values()) if(attribution.contains(p.id) && allowed(ctx,p)) return p;
        return null;
    }
}
