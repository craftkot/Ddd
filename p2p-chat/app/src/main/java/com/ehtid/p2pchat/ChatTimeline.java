package com.ehtid.p2pchat;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/** Local calendar-day groups, independent of message length or clock text. */
public final class ChatTimeline {
    private ChatTimeline() {}
    public static String dayKey(long epoch) {
        Calendar c=Calendar.getInstance();
        c.setTimeInMillis(epoch);
        return String.format(Locale.ROOT,"%04d-%02d-%02d",
                c.get(Calendar.YEAR),c.get(Calendar.MONTH)+1,c.get(Calendar.DAY_OF_MONTH));
    }
    public static String label(long epoch,long now) {
        Calendar day=Calendar.getInstance();
        day.setTimeInMillis(epoch);
        Calendar today=Calendar.getInstance();
        today.setTimeInMillis(now);
        if(sameDay(day,today))return "Сегодня";
        today.add(Calendar.DAY_OF_YEAR,-1);
        if(sameDay(day,today))return "Вчера";
        SimpleDateFormat format=new SimpleDateFormat(
                day.get(Calendar.YEAR)==yearOf(now)
                    ? "d MMMM" : "d MMMM yyyy",new Locale("ru"));
        return format.format(new Date(epoch));
    }
    private static int yearOf(long timestamp){
        Calendar c=Calendar.getInstance();c.setTimeInMillis(timestamp);
        return c.get(Calendar.YEAR);
    }
    private static boolean sameDay(Calendar a,Calendar b) {
        return a.get(Calendar.ERA)==b.get(Calendar.ERA)
                && a.get(Calendar.YEAR)==b.get(Calendar.YEAR)
                && a.get(Calendar.DAY_OF_YEAR)==b.get(Calendar.DAY_OF_YEAR);
    }
}
