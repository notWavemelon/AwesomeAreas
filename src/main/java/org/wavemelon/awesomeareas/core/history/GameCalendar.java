package org.wavemelon.awesomeareas.core.history;

import net.minecraft.world.level.Level;

import java.util.Locale;

/**
 * Calendar system that converts Minecraft's built-in days into realistic Months and Years.
 * - 24,000 game ticks = 1 in-game day
 * - 12 real-world months (January to December)
 * - 365 days per year
 */
public class GameCalendar {
    public static final String[] MONTH_NAMES = {
            "January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December"
    };

    public static final int[] DAYS_IN_MONTHS = {
            31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31
    };

    public static final int DAYS_PER_YEAR = 365;

    public record GameDate(int year, String monthName, int monthNumber, int dayOfMonth) {
        @Override
        public String toString() {
            return String.format(Locale.ROOT, "%s %d, Year %d", monthName, dayOfMonth, year);
        }
    }

    public static GameDate getDateFromDayTime(long dayTimeTicks) {
        long totalDays = Math.max(0, dayTimeTicks / 24000L);

        int year = (int) (totalDays / DAYS_PER_YEAR) + 1;
        int dayOfYear = (int) (totalDays % DAYS_PER_YEAR); // 0 to 364

        int curMonth = 0;
        int remDay = dayOfYear;
        for (int i = 0; i < DAYS_IN_MONTHS.length; i++) {
            if (remDay < DAYS_IN_MONTHS[i]) {
                curMonth = i;
                break;
            }
            remDay -= DAYS_IN_MONTHS[i];
        }

        int dayOfMonth = remDay + 1;
        return new GameDate(year, MONTH_NAMES[curMonth], curMonth + 1, dayOfMonth);
    }

    public static String formatDate(long dayTime) {
        return getDateFromDayTime(dayTime).toString();
    }

    public static String formatCurrentDate(Level level) {
        if (level == null) return "Year 1";
        return formatDate(level.getOverworldClockTime());
    }
}
