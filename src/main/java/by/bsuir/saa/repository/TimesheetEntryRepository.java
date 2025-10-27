package by.bsuir.saa.repository;

import by.bsuir.saa.entity.Timesheet;
import by.bsuir.saa.entity.TimesheetEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface TimesheetEntryRepository extends JpaRepository<TimesheetEntry, Integer> {

    List<TimesheetEntry> findByTimesheet(Timesheet timesheet);


    Optional<TimesheetEntry> findByTimesheetAndDate(Timesheet timesheet, LocalDate date);

    void deleteByTimesheet(Timesheet timesheet);

    @Query("SELECT te FROM TimesheetEntry te JOIN FETCH te.markType WHERE te.timesheet = :timesheet")
    List<TimesheetEntry> findByTimesheetWithMarkType(@Param("timesheet") Timesheet timesheet);
}