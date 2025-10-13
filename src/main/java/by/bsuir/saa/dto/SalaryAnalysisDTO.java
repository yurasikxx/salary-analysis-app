package by.bsuir.saa.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.util.Map;

@Data
public class SalaryAnalysisDTO {
    private String period;
    private Map<String, BigDecimal> departmentSalaries;
    private Map<String, BigDecimal> paymentStructure;
    private BigDecimal totalFOT; // Фонд оплаты труда
    private BigDecimal averageSalary;
    private Integer employeeCount;
}