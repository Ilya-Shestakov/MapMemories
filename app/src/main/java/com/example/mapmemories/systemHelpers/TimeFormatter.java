package com.example.mapmemories.systemHelpers;

import android.text.format.DateFormat;
import java.util.Calendar;

public class TimeFormatter {

    public static String formatStatus(Object statusObj, boolean isHidden) {
        if (isHidden) return "был(а) недавно";
        if (statusObj == null) return "был(а) недавно";

        if (statusObj instanceof String) {
            String statusStr = (String) statusObj;
            if (statusStr.equals("online")) return "в сети";
            return "был(а) недавно";
        }

        if (statusObj instanceof Long) {
            long time = (Long) statusObj;
            return formatTimeAgo(time);
        }

        return "был(а) недавно";
    }

    private static String formatTimeAgo(long timeInMillis) {
        Calendar now = Calendar.getInstance();
        Calendar time = Calendar.getInstance();
        time.setTimeInMillis(timeInMillis);

        String timeString = DateFormat.format("HH:mm", time).toString();

        if (now.get(Calendar.DATE) == time.get(Calendar.DATE) &&
                now.get(Calendar.MONTH) == time.get(Calendar.MONTH) &&
                now.get(Calendar.YEAR) == time.get(Calendar.YEAR)) {
            return "был(а) сегодня в " + timeString;
        } else if (now.get(Calendar.DATE) - time.get(Calendar.DATE) == 1 &&
                now.get(Calendar.MONTH) == time.get(Calendar.MONTH) &&
                now.get(Calendar.YEAR) == time.get(Calendar.YEAR)) {
            return "был(а) вчера в " + timeString;
        } else {
            String dateString = DateFormat.format("dd.MM.yyyy", time).toString();
            return "был(а) " + dateString + " в " + timeString;
        }
    }
}