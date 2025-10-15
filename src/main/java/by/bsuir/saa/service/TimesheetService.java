package by.bsuir.saa.service;

import by.bsuir.saa.entity.*;
import by.bsuir.saa.repository.TimesheetRepository;
import by.bsuir.saa.repository.TimesheetEntryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class TimesheetService {

    private final TimesheetRepository timesheetRepository;
    private final TimesheetEntryRepository timesheetEntryRepository;

    public TimesheetService(TimesheetRepository timesheetRepository,
                            TimesheetEntryRepository timesheetEntryRepository) {
        this.timesheetRepository = timesheetRepository;
        this.timesheetEntryRepository = timesheetEntryRepository;
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
        return timesheetEntryRepository.findByTimesheet(timesheet);
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
}