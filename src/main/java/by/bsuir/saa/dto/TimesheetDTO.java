package by.bsuir.saa.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
public class TimesheetDTO {
    private Integer id;
    private String employeeName;
    private Integer month;
    private Integer year;
    private BigDecimal totalHours;
    private String status;
    private List<TimesheetEntryDTO> entries;
}

@Data
class TimesheetEntryDTO {
    private LocalDate date;
    private String markType;
    private BigDecimal hoursWorked;
}