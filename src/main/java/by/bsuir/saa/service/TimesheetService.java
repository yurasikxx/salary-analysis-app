package by.bsuir.saa.service;

import by.bsuir.saa.entity.*;
import by.bsuir.saa.repository.EmployeeRepository;
import by.bsuir.saa.repository.PaymentRepository;
import by.bsuir.saa.repository.TimesheetRepository;
import by.bsuir.saa.repository.TimesheetEntryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
public class TimesheetService {

    private final TimesheetRepository timesheetRepository;
    private final TimesheetEntryRepository timesheetEntryRepository;
    private final MarkTypeService markTypeService;
    private final PaymentRepository paymentRepository;
    private final EmployeeRepository employeeRepository;

    public TimesheetService(TimesheetRepository timesheetRepository,
                            TimesheetEntryRepository timesheetEntryRepository,
                            MarkTypeService markTypeService,
                            PaymentRepository paymentRepository,
                            EmployeeRepository employeeRepository) {
        this.timesheetRepository = timesheetRepository;
        this.timesheetEntryRepository = timesheetEntryRepository;
        this.markTypeService = markTypeService;
        this.paymentRepository = paymentRepository;
        this.employeeRepository = employeeRepository;
    }

    public Optional<Timesheet> getTimesheetById(Integer id) {
        return timesheetRepository.findById(id);
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
                    timesheet.setStatus(Timesheet.TimesheetStatus.DRAFT);
                    return timesheetRepository.save(timesheet);
                });
    }

    public List<Timesheet> getTimesheetsByPeriod(Integer month, Integer year) {
        return timesheetRepository.findByMonthAndYear(month, year);
    }

    public List<Timesheet> getTimesheetsByEmployee(Employee employee) {
        return timesheetRepository.findByEmployee(employee);
    }

    public Map<LocalDate, TimesheetEntry> getTimesheetEntriesMap(Timesheet timesheet) {
        List<TimesheetEntry> entries = timesheetEntryRepository.findByTimesheetWithMarkType(timesheet);
        Map<LocalDate, TimesheetEntry> entriesMap = new HashMap<>();

        for (TimesheetEntry entry : entries) {
            entriesMap.put(entry.getDate(), entry);
        }

        return entriesMap;
    }

    @Transactional
    public void saveTimesheetEntries(Integer timesheetId, Map<String, String> dayEntries) {
        Timesheet timesheet = timesheetRepository.findById(timesheetId)
                .orElseThrow(() -> new RuntimeException("Табель не найден"));

        timesheetEntryRepository.deleteByTimesheet(timesheet);

        int savedCount = 0;
        for (Map.Entry<String, String> entry : dayEntries.entrySet()) {
            if (entry.getKey().startsWith("day_")) {
                String dayStr = entry.getKey().substring(4);
                LocalDate date = LocalDate.parse(dayStr);
                String value = entry.getValue();

                if (value != null && !value.trim().isEmpty()) {
                    try {
                        String[] parts = value.split("_");
                        if (parts.length == 2) {
                            String markTypeCode = parts[0];
                            BigDecimal hours = new BigDecimal(parts[1]);

                            MarkType markType = markTypeService.getMarkTypeByCode(markTypeCode)
                                    .orElseThrow(() -> new RuntimeException("Тип отметки не найден: " + markTypeCode));

                            TimesheetEntry timesheetEntry = new TimesheetEntry();
                            timesheetEntry.setTimesheet(timesheet);
                            timesheetEntry.setDate(date);
                            timesheetEntry.setMarkType(markType);
                            timesheetEntry.setHoursWorked(hours);

                            timesheetEntryRepository.save(timesheetEntry);
                            savedCount++;
                        }
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }

        updateTotalHours(timesheet);
    }

    @Transactional
    public void fillFullMonth(Timesheet timesheet) {
        timesheetEntryRepository.deleteByTimesheet(timesheet);

        List<LocalDate> monthDays = getDaysInMonth(timesheet.getYear(), timesheet.getMonth());

        MarkType workMarkType = markTypeService.getMarkTypeByCode("Я")
                .orElseThrow(() -> new RuntimeException("Тип отметки 'Явка' не найден"));

        for (LocalDate day : monthDays) {
            if (day.getDayOfWeek().getValue() >= 1 && day.getDayOfWeek().getValue() <= 5) {
                TimesheetEntry entry = new TimesheetEntry();
                entry.setTimesheet(timesheet);
                entry.setDate(day);
                entry.setMarkType(workMarkType);
                entry.setHoursWorked(new BigDecimal("8"));

                timesheetEntryRepository.save(entry);
            }
        }

        updateTotalHours(timesheet);
    }

    public void confirmTimesheet(Integer timesheetId, User confirmedBy) {
        Timesheet timesheet = timesheetRepository.findById(timesheetId)
                .orElseThrow(() -> new RuntimeException("Табель не найден"));

        timesheet.setStatus(Timesheet.TimesheetStatus.CONFIRMED);
        timesheet.setConfirmedBy(confirmedBy);
        timesheet.setConfirmedAt(LocalDateTime.now());
        timesheetRepository.save(timesheet);
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

    public void deleteTimesheetEntries(Timesheet timesheet) {
        timesheetEntryRepository.deleteByTimesheet(timesheet);
    }

    public void deleteTimesheet(Integer timesheetId) {
        timesheetRepository.deleteById(timesheetId);
    }

    public Map<String, Long> countDaysByMarkType(Employee employee, Integer month, Integer year) {
        Optional<Timesheet> timesheetOpt = getTimesheet(employee, month, year);
        if (timesheetOpt.isEmpty() || timesheetOpt.get().getStatus() != Timesheet.TimesheetStatus.CONFIRMED) {
            return Map.of();
        }

        Timesheet timesheet = timesheetOpt.get();
        List<TimesheetEntry> entries = timesheetEntryRepository.findByTimesheet(timesheet);

        return entries.stream()
                .collect(Collectors.groupingBy(
                        entry -> entry.getMarkType().getCode(),
                        Collectors.counting()
                ));
    }

    @Transactional
    public void unconfirmTimesheet(Integer timesheetId) {
        Timesheet timesheet = timesheetRepository.findById(timesheetId)
                .orElseThrow(() -> new RuntimeException("Табель не найден"));

        if (timesheet.getStatus() != Timesheet.TimesheetStatus.CONFIRMED) {
            throw new RuntimeException("Табель не подтвержден, отмена подтверждения невозможна");
        }

        if (hasCalculatedPayments(timesheet.getEmployee(), timesheet.getMonth(), timesheet.getYear())) {
            List<Payment> payments = paymentRepository.findByEmployeeAndMonthAndYear(
                    timesheet.getEmployee(), timesheet.getMonth(), timesheet.getYear());

            String paymentTypes = payments.stream()
                    .filter(p -> "accrual".equals(p.getPaymentType().getCategory()))
                    .map(p -> p.getPaymentType().getName())
                    .collect(Collectors.joining(", "));

            throw new RuntimeException("Невозможно отменить подтверждение табеля. " +
                    "У сотрудника уже есть рассчитанные начисления: " + paymentTypes +
                    ". Сначала удалите эти начисления.");
        }

        timesheet.setStatus(Timesheet.TimesheetStatus.DRAFT);
        timesheet.setConfirmedBy(null);
        timesheet.setConfirmedAt(null);
        timesheetRepository.save(timesheet);
    }

    @Transactional
    public void fillFullMonthForAll(Integer month, Integer year) {
        List<Employee> activeEmployees = employeeRepository.findByTerminationDateIsNull();
        int filledCount = 0;

        for (Employee employee : activeEmployees) {
            Optional<Timesheet> timesheetOpt = getTimesheet(employee, month, year);
            if (timesheetOpt.isPresent()) {
                Timesheet timesheet = timesheetOpt.get();
                if (timesheet.getTimesheetEntries().isEmpty() &&
                        timesheet.getStatus() != Timesheet.TimesheetStatus.CONFIRMED) {
                    fillFullMonth(timesheet);
                    filledCount++;
                }
            }
        }
    }

    @Transactional
    public void fillMonthWithVacation(Integer timesheetId) {
        Timesheet timesheet = timesheetRepository.findById(timesheetId)
                .orElseThrow(() -> new RuntimeException("Табель не найден"));

        if (timesheet.getStatus() == Timesheet.TimesheetStatus.CONFIRMED) {
            throw new RuntimeException("Невозможно редактировать подтвержденный табель");
        }

        timesheetEntryRepository.deleteByTimesheet(timesheet);

        List<LocalDate> monthDays = getDaysInMonth(timesheet.getYear(), timesheet.getMonth());
        MarkType vacationMarkType = markTypeService.getMarkTypeByCode("О")
                .orElseThrow(() -> new RuntimeException("Тип отметки 'Отпуск' не найден"));

        for (LocalDate day : monthDays) {
            TimesheetEntry entry = new TimesheetEntry();
            entry.setTimesheet(timesheet);
            entry.setDate(day);
            entry.setMarkType(vacationMarkType);
            entry.setHoursWorked(BigDecimal.ZERO); // В отпуске часов не отрабатывается

            timesheetEntryRepository.save(entry);
        }

        updateTotalHours(timesheet);
    }

    @Transactional
    public void fillMonthWithSickLeave(Integer timesheetId) {
        Timesheet timesheet = timesheetRepository.findById(timesheetId)
                .orElseThrow(() -> new RuntimeException("Табель не найден"));

        if (timesheet.getStatus() == Timesheet.TimesheetStatus.CONFIRMED) {
            throw new RuntimeException("Невозможно редактировать подтвержденный табель");
        }

        timesheetEntryRepository.deleteByTimesheet(timesheet);

        List<LocalDate> monthDays = getDaysInMonth(timesheet.getYear(), timesheet.getMonth());
        MarkType sickLeaveMarkType = markTypeService.getMarkTypeByCode("Б")
                .orElseThrow(() -> new RuntimeException("Тип отметки 'Больничный' не найден"));

        for (LocalDate day : monthDays) {
            TimesheetEntry entry = new TimesheetEntry();
            entry.setTimesheet(timesheet);
            entry.setDate(day);
            entry.setMarkType(sickLeaveMarkType);
            entry.setHoursWorked(BigDecimal.ZERO);

            timesheetEntryRepository.save(entry);
        }

        updateTotalHours(timesheet);
    }

    private boolean hasCalculatedPayments(Employee employee, Integer month, Integer year) {
        List<Payment> payments = paymentRepository.findByEmployeeAndMonthAndYear(employee, month, year);

        return payments.stream()
                .anyMatch(p -> "accrual".equals(p.getPaymentType().getCategory()) &&
                        ("ОКЛ".equals(p.getPaymentType().getCode()) ||
                                "ОТП".equals(p.getPaymentType().getCode()) ||
                                "БОЛ".equals(p.getPaymentType().getCode()) ||
                                "ПРЕД".equals(p.getPaymentType().getCode()) ||
                                "ИТР".equals(p.getPaymentType().getCode()) ||
                                "ПХД".equals(p.getPaymentType().getCode()) ||
                                "ПСС".equals(p.getPaymentType().getCode()) ||
                                "СТАЖ".equals(p.getPaymentType().getCode())));
    }

    private void updateTotalHours(Timesheet timesheet) {
        List<TimesheetEntry> entries = timesheetEntryRepository.findByTimesheet(timesheet);
        BigDecimal total = entries.stream()
                .map(TimesheetEntry::getHoursWorked)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        timesheet.setTotalHours(total);
        timesheetRepository.save(timesheet);
    }

    private List<LocalDate> getDaysInMonth(int year, int month) {
        List<LocalDate> days = new ArrayList<>();
        LocalDate firstDay = LocalDate.of(year, month, 1);
        LocalDate lastDay = firstDay.withDayOfMonth(firstDay.lengthOfMonth());

        LocalDate currentDay = firstDay;
        while (!currentDay.isAfter(lastDay)) {
            days.add(currentDay);
            currentDay = currentDay.plusDays(1);
        }

        return days;
    }
}