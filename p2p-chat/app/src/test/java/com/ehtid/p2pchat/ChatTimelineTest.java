package com.ehtid.p2pchat;
import static org.junit.Assert.*;
import java.util.Calendar;
import org.junit.Test;
public class ChatTimelineTest {
    @Test public void todayYesterdayDayBoundaries() {
        Calendar day=Calendar.getInstance();
        day.set(2026,Calendar.NOVEMBER,12,0,5,0);
        long now=day.getTimeInMillis();
        day.add(Calendar.DAY_OF_YEAR,-1);
        assertEquals("Сегодня",ChatTimeline.label(now,now));
        assertEquals("Вчера",ChatTimeline.label(day.getTimeInMillis(),now));
        assertNotEquals(ChatTimeline.dayKey(day.getTimeInMillis()),ChatTimeline.dayKey(now));
    }
}