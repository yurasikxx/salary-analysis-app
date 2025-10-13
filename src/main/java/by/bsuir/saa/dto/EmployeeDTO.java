package by.bsuir.saa.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class EmployeeDTO {
    private Integer id;
    private String fullName;
    private LocalDate hireDate;
    private LocalDate terminationDate;
    private String positionTitle;
    private String departmentName;
    private BigDecimal baseSalary;
    private boolean active;
}