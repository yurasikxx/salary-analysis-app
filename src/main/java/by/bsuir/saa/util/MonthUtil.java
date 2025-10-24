package by.bsuir.saa.util;

import org.springframework.stereotype.Component;

import java.time.Month;
import java.util.HashMap;
import java.util.Map;

@Component
public class MonthUtil {

    private static final Map<Integer, String> RUSSIAN_MONTHS = new HashMap<>();

    static {
        RUSSIAN_MONTHS.put(1, "Январь");
        RUSSIAN_MONTHS.put(2, "Февраль");
        RUSSIAN_MONTHS.put(3, "Март");
        RUSSIAN_MONTHS.put(4, "Апрель");
        RUSSIAN_MONTHS.put(5, "Май");
        RUSSIAN_MONTHS.put(6, "Июнь");
        RUSSIAN_MONTHS.put(7, "Июль");
        RUSSIAN_MONTHS.put(8, "Август");
        RUSSIAN_MONTHS.put(9, "Сентябрь");
        RUSSIAN_MONTHS.put(10, "Октябрь");
        RUSSIAN_MONTHS.put(11, "Ноябрь");
        RUSSIAN_MONTHS.put(12, "Декабрь");
    }

    public static String getRussianMonthName(int month) {
        return RUSSIAN_MONTHS.getOrDefault(month, "Неизвестно");
    }

    public static String getRussianMonthName(Month month) {
        return getRussianMonthName(month.getValue());
    }
}