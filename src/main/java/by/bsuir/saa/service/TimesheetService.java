package by.bsuir.saa.service;

import by.bsuir.saa.entity.*;
import by.bsuir.saa.repository.TimesheetRepository;
import by.bsuir.saa.repository.TimesheetEntryRepository;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@Transactional
public class TimesheetService {

    private final TimesheetRepository timesheetRepository;
    private final TimesheetEntryRepository timesheetEntryRepository;
    private final MarkTypeService markTypeService;

    public TimesheetService(TimesheetRepository timesheetRepository,
                            TimesheetEntryRepository timesheetEntryRepository,
                            MarkTypeService markTypeService) {
        this.timesheetRepository = timesheetRepository;
        this.timesheetEntryRepository = timesheetEntryRepository;
        this.markTypeService = markTypeService;
    }

    public Optional<Timesheet> getTimesheet(Employee employee, Integer month, Integer year) {
        return timesheetRepository.findByEmployeeAndMonthAndYear(employee, month, year);
    }

    public Timesheet getOrCreateTimesheet(Employee employee, Integer month, Integer year) {
        return timesheetRepository.findByEmployeeAndMonthAndYear(employee, month, year)
                .orElseGet(() -> {
                    Timesheet timesheet = new Timesheet();
                    timesheet.setEmployee(employee);
                    timesheet.setMonth(month);
                    timesheet.setYear(year);
                    timesheet.setTotalHours(BigDecimal.ZERO);
                    return timesheetRepository.save(timesheet);
                });
    }

    public TimesheetEntry addTimesheetEntry(Timesheet timesheet, LocalDate date,
                                            MarkType markType, BigDecimal hours) {
        TimesheetEntry entry = new TimesheetEntry();
        entry.setTimesheet(timesheet);
        entry.setDate(date);
        entry.setMarkType(markType);
        entry.setHoursWorked(hours);

        TimesheetEntry savedEntry = timesheetEntryRepository.save(entry);

        updateTotalHours(timesheet);

        return savedEntry;
    }

    public void confirmTimesheet(Timesheet timesheet, User confirmedBy) {
        timesheet.setStatus(Timesheet.TimesheetStatus.CONFIRMED);
        timesheet.setConfirmedBy(confirmedBy);
        timesheet.setConfirmedAt(java.time.LocalDateTime.now());
        timesheetRepository.save(timesheet);
    }

    public List<TimesheetEntry> getTimesheetEntries(Timesheet timesheet) {
        List<TimesheetEntry> entries = timesheetEntryRepository.findByTimesheetWithMarkType(timesheet);
        System.out.println("Запрос записей для табеля " + timesheet.getId() + ": найдено " + entries.size());

        entries.forEach(entry -> {
            System.out.println("Запись: " + entry.getDate() + " - " +
                    entry.getMarkType().getCode() + " - " +
                    entry.getHoursWorked());
        });

        return entries;
    }

    public Map<LocalDate, TimesheetEntry> getTimesheetEntriesMap(Timesheet timesheet) {
        List<TimesheetEntry> entries = timesheetEntryRepository.findByTimesheet(timesheet);
        Map<LocalDate, TimesheetEntry> entriesMap = new HashMap<>();

        for (TimesheetEntry entry : entries) {
            if (entry.getMarkType() != null) {
                Hibernate.initialize(entry.getMarkType());
            }
            entriesMap.put(entry.getDate(), entry);
        }

        return entriesMap;
    }

    private void updateTotalHours(Timesheet timesheet) {
        List<TimesheetEntry> entries = timesheetEntryRepository.findByTimesheet(timesheet);
        BigDecimal total = entries.stream()
                .map(TimesheetEntry::getHoursWorked)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        timesheet.setTotalHours(total);
        timesheetRepository.save(timesheet);
    }

    public List<Timesheet> getTimesheetsByPeriod(Integer month, Integer year) {
        return timesheetRepository.findByMonthAndYear(month, year);
    }

    public Optional<Timesheet> getTimesheetById(Integer id) {
        return timesheetRepository.findById(id);
    }

    public void confirmTimesheet(Integer timesheetId, User confirmedBy) {
        Timesheet timesheet = timesheetRepository.findById(timesheetId)
                .orElseThrow(() -> new RuntimeException("Табель не найден"));

        timesheet.setStatus(Timesheet.TimesheetStatus.CONFIRMED);
        timesheet.setConfirmedBy(confirmedBy);
        timesheet.setConfirmedAt(LocalDateTime.now());

        timesheetRepository.save(timesheet);
    }

    public List<Timesheet> getTimesheetsByEmployee(Employee employee) {
        return timesheetRepository.findByEmployee(employee);
    }

    public List<Timesheet> getAllTimesheets() {
        return timesheetRepository.findAll();
    }

    public void deleteTimesheetEntries(Timesheet timesheet) {
        timesheetEntryRepository.deleteByTimesheet(timesheet);
    }

    public void deleteTimesheet(Integer timesheetId) {
        timesheetRepository.deleteById(timesheetId);
    }

    public Long getConfirmedTimesheetsCount(Integer month, Integer year) {
        return timesheetRepository.countByMonthAndYearAndStatus(month, year, Timesheet.TimesheetStatus.CONFIRMED);
    }

    public Long getPendingTimesheetsCount(Integer month, Integer year) {
        return timesheetRepository.countByMonthAndYearAndStatus(month, year, Timesheet.TimesheetStatus.DRAFT);
    }

    public List<Timesheet> getTimesheetsByStatus(Timesheet.TimesheetStatus status) {
        return timesheetRepository.findByStatus(status);
    }

    @Transactional
    public void saveTimesheetEntries(Integer timesheetId, Map<String, String> dayEntries) {
        System.out.println("Сохранение табеля " + timesheetId + ", записей: " + dayEntries.size());

        Timesheet timesheet = timesheetRepository.findById(timesheetId)
                .orElseThrow(() -> new RuntimeException("Табель не найден"));

        timesheetEntryRepository.deleteByTimesheet(timesheet);
        System.out.println("Старые записи удалены");

        int savedCount = 0;
        for (Map.Entry<String, String> entry : dayEntries.entrySet()) {
            if (entry.getKey().startsWith("day_")) {
                String dayStr = entry.getKey().substring(4);
                LocalDate date = LocalDate.parse(dayStr);
                String value = entry.getValue();

                System.out.println("Обработка дня " + date + ": " + value);

                if (value != null && !value.trim().isEmpty() && !value.equals("_")) {
                    try {
                        String[] parts = value.split("_");
                        if (parts.length == 2) {
                            String markTypeCode = parts[0];
                            BigDecimal hours = new BigDecimal(parts[1]);

                            MarkType markType = markTypeService.getMarkTypeByCode(markTypeCode)
                                    .orElseGet(() -> getDefaultMarkType());

                            TimesheetEntry timesheetEntry = new TimesheetEntry();
                            timesheetEntry.setTimesheet(timesheet);
                            timesheetEntry.setDate(date);
                            timesheetEntry.setMarkType(markType);
                            timesheetEntry.setHoursWorked(hours);

                            timesheetEntryRepository.save(timesheetEntry);
                            savedCount++;
                            System.out.println("Сохранена запись: " + date + " - " + markTypeCode + " - " + hours);
                        }
                    } catch (Exception e) {
                        System.err.println("Ошибка при сохранении записи для дня " + date + ": " + e.getMessage());
                    }
                }
            }
        }

        updateTotalHours(timesheet);
        System.out.println("Сохранено записей: " + savedCount + ", общее время: " + timesheet.getTotalHours());
    }

    private MarkType determineMarkType(BigDecimal hours) {
        if (hours.compareTo(BigDecimal.ZERO) == 0) {
            return markTypeService.getMarkTypeByCode("ОТ")
                    .orElseGet(this::getDefaultMarkType);
        } else if (hours.compareTo(new BigDecimal("8")) <= 0) {
            return markTypeService.getMarkTypeByCode("Я")
                    .orElseGet(this::getDefaultMarkType);
        } else {
            return markTypeService.getMarkTypeByCode("С")
                    .orElseGet(this::getDefaultMarkType);
        }
    }

    private MarkType getDefaultMarkType() {
        MarkType markType = new MarkType();
        markType.setCode("Я");
        markType.setName("Явка");
        return markType;
    }

    private MarkType createDefaultMarkType(String code, String name) {
        MarkType markType = new MarkType();
        markType.setCode(code);
        markType.setName(name);
        return markType;
    }
}