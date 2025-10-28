package by.bsuir.saa.util;

import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;

@Component
public class WorkingHoursCalculator {

    /**
     * Рассчитывает количество рабочих часов в месяце (без учета праздников)
     *
     * @param year        год
     * @param month       месяц (1-12)
     * @param hoursPerDay часов в рабочем дне
     * @return количество рабочих часов
     */
    public int calculateWorkingHours(int year, int month, int hoursPerDay) {
        YearMonth yearMonth = YearMonth.of(year, month);
        LocalDate firstDay = yearMonth.atDay(1);
        LocalDate lastDay = yearMonth.atEndOfMonth();

        int workingDays = 0;
        LocalDate current = firstDay;

        while (!current.isAfter(lastDay)) {
            if (current.getDayOfWeek() != DayOfWeek.SATURDAY &&
                    current.getDayOfWeek() != DayOfWeek.SUNDAY) {
                workingDays++;
            }
            current = current.plusDays(1);
        }

        return workingDays * hoursPerDay;
    }

    /**
     * Рассчитывает количество рабочих дней в месяце
     *
     * @param year  год
     * @param month месяц (1-12)
     * @return количество рабочих дней
     */
    public int calculateWorkingDays(int year, int month) {
        YearMonth yearMonth = YearMonth.of(year, month);
        LocalDate firstDay = yearMonth.atDay(1);
        LocalDate lastDay = yearMonth.atEndOfMonth();

        int workingDays = 0;
        LocalDate current = firstDay;

        while (!current.isAfter(lastDay)) {
            if (current.getDayOfWeek() != DayOfWeek.SATURDAY &&
                    current.getDayOfWeek() != DayOfWeek.SUNDAY) {
                workingDays++;
            }
            current = current.plusDays(1);
        }

        return workingDays;
    }
}