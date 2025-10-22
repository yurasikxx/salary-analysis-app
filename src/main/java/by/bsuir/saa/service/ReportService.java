package by.bsuir.saa.service;

import by.bsuir.saa.entity.*;
import by.bsuir.saa.repository.*;
import com.itextpdf.text.*;
import com.itextpdf.text.Font;
import com.itextpdf.text.pdf.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class ReportService {

    private final SalaryPaymentRepository salaryPaymentRepository;
    private final PaymentRepository paymentRepository;
    private final EmployeeRepository employeeRepository;
    private final DepartmentRepository departmentRepository;

    public ReportService(SalaryPaymentRepository salaryPaymentRepository,
                         PaymentRepository paymentRepository,
                         EmployeeRepository employeeRepository,
                         DepartmentRepository departmentRepository) {
        this.salaryPaymentRepository = salaryPaymentRepository;
        this.paymentRepository = paymentRepository;
        this.employeeRepository = employeeRepository;
        this.departmentRepository = departmentRepository;
    }

    public String generateSalaryReport(Integer month, Integer year) {
        List<SalaryPayment> salaryPayments = salaryPaymentRepository.findByMonthAndYear(month, year);

        StringBuilder report = new StringBuilder();
        report.append("ВЕДОМОСТЬ ПО ЗАРПЛАТЕ\n");
        report.append("За период: ").append(month).append("/").append(year).append("\n\n");

        report.append(String.format("%-30s %-15s %-15s %-15s\n",
                "Сотрудник", "Начисления", "Удержания", "К выплате"));
        report.append("-".repeat(75)).append("\n");

        BigDecimal totalAccrued = BigDecimal.ZERO;
        BigDecimal totalDeducted = BigDecimal.ZERO;
        BigDecimal totalNet = BigDecimal.ZERO;

        for (SalaryPayment sp : salaryPayments) {
            report.append(String.format("%-30s %-15.2f %-15.2f %-15.2f\n",
                    sp.getEmployee().getFullName(),
                    sp.getTotalAccrued(),
                    sp.getTotalDeducted(),
                    sp.getNetSalary()));

            totalAccrued = totalAccrued.add(sp.getTotalAccrued());
            totalDeducted = totalDeducted.add(sp.getTotalDeducted());
            totalNet = totalNet.add(sp.getNetSalary());
        }

        report.append("-".repeat(75)).append("\n");
        report.append(String.format("%-30s %-15.2f %-15.2f %-15.2f\n",
                "ИТОГО:", totalAccrued, totalDeducted, totalNet));

        return report.toString();
    }

    public String generateDeductionsReport(Integer month, Integer year) {
        List<Payment> payments = paymentRepository.findByMonthAndYear(month, year);

        StringBuilder report = new StringBuilder();
        report.append("ОТЧЕТ ПО УДЕРЖАНИЯМ ИЗ ЗАРАБОТНОЙ ПЛАТЫ\n");
        report.append("За период: ").append(getMonthName(month)).append(" ").append(year).append("\n");
        report.append("Дата формирования: ").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))).append("\n\n");

        List<Payment> deductionPayments = payments.stream()
                .filter(p -> "deduction".equals(p.getPaymentType().getCategory()))
                .toList();

        if (deductionPayments.isEmpty()) {
            report.append("Удержания за указанный период отсутствуют.\n");
            return report.toString();
        }

        Map<PaymentType, List<Payment>> paymentsByType = deductionPayments.stream()
                .collect(Collectors.groupingBy(Payment::getPaymentType));

        report.append("СТАТИСТИКА ПО ТИПАМ УДЕРЖАНИЙ:\n");
        report.append("----------------------------------------\n");

        BigDecimal totalDeductions = BigDecimal.ZERO;

        for (Map.Entry<PaymentType, List<Payment>> entry : paymentsByType.entrySet()) {
            PaymentType paymentType = entry.getKey();
            List<Payment> typePayments = entry.getValue();

            BigDecimal typeTotal = typePayments.stream()
                    .map(p -> p.getAmount().abs())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            report.append(paymentType.getName()).append(" (").append(paymentType.getCode()).append("):\n");
            report.append("  Количество операций: ").append(typePayments.size()).append("\n");
            report.append("  Общая сумма: ").append(typeTotal).append(" руб.\n");
            report.append("  Среднее удержание: ").append(typeTotal.divide(BigDecimal.valueOf(typePayments.size()), 2, RoundingMode.HALF_UP)).append(" руб.\n");
            report.append("----------------------------------------\n");

            totalDeductions = totalDeductions.add(typeTotal);
        }

        Map<Employee, List<Payment>> paymentsByEmployee = deductionPayments.stream()
                .collect(Collectors.groupingBy(Payment::getEmployee));

        report.append("\nДЕТАЛИЗАЦИЯ ПО СОТРУДНИКАМ:\n");
        report.append("========================================\n");

        for (Map.Entry<Employee, List<Payment>> entry : paymentsByEmployee.entrySet()) {
            Employee employee = entry.getKey();
            List<Payment> employeePayments = entry.getValue();

            report.append("Сотрудник: ").append(employee.getFullName()).append("\n");
            report.append("Отдел: ").append(employee.getDepartment().getName()).append("\n");
            report.append("Должность: ").append(employee.getPosition().getTitle()).append("\n");

            BigDecimal employeeTotal = BigDecimal.ZERO;

            for (Payment payment : employeePayments) {
                report.append("  - ").append(payment.getPaymentType().getName())
                        .append(": ").append(payment.getAmount().abs()).append(" руб.");

                if (payment.getDescription() != null && !payment.getDescription().isEmpty()) {
                    report.append(" (").append(payment.getDescription()).append(")");
                }
                report.append("\n");

                employeeTotal = employeeTotal.add(payment.getAmount().abs());
            }

            report.append("  ИТОГО у сотрудника: ").append(employeeTotal).append(" руб.\n");
            report.append("========================================\n");
        }

        Map<Department, List<Payment>> paymentsByDepartment = deductionPayments.stream()
                .collect(Collectors.groupingBy(p -> p.getEmployee().getDepartment()));

        report.append("\nАНАЛИТИКА ПО ОТДЕЛАМ:\n");
        report.append("----------------------------------------\n");

        for (Map.Entry<Department, List<Payment>> entry : paymentsByDepartment.entrySet()) {
            Department department = entry.getKey();
            List<Payment> departmentPayments = entry.getValue();

            BigDecimal departmentTotal = departmentPayments.stream()
                    .map(p -> p.getAmount().abs())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            long employeeCount = departmentPayments.stream()
                    .map(Payment::getEmployee)
                    .distinct()
                    .count();

            report.append(department.getName()).append(":\n");
            report.append("  Количество сотрудников с удержаниями: ").append(employeeCount).append("\n");
            report.append("  Общая сумма удержаний: ").append(departmentTotal).append(" руб.\n");
            report.append("  Среднее удержание на сотрудника: ")
                    .append(departmentTotal.divide(BigDecimal.valueOf(employeeCount), 2, RoundingMode.HALF_UP))
                    .append(" руб.\n");
            report.append("----------------------------------------\n");
        }

        report.append("\nОБЩИЕ ИТОГИ:\n");
        report.append("Всего удержаний: ").append(deductionPayments.size()).append(" операций\n");
        report.append("Общая сумма удержаний: ").append(totalDeductions).append(" руб.\n");
        report.append("Количество сотрудников с удержаниями: ")
                .append(paymentsByEmployee.size()).append("\n");
        report.append("Среднее удержание на сотрудника: ")
                .append(totalDeductions.divide(BigDecimal.valueOf(paymentsByEmployee.size()), 2, RoundingMode.HALF_UP))
                .append(" руб.\n");

        return report.toString();
    }

    private String getMonthName(Integer month) {
        String[] monthNames = {"Январь", "Февраль", "Март", "Апрель", "Май", "Июнь",
                "Июль", "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь"};
        return monthNames[month - 1];
    }

    public String generateTaxReport(Integer month, Integer year) {
        BigDecimal totalTax = paymentRepository.sumTaxesByPeriod(month, year);

        StringBuilder report = new StringBuilder();
        report.append("ОТЧЕТ ПО НАЛОГАМ И ВЗНОСАМ\n");
        report.append("За период: ").append(month).append("/").append(year).append("\n\n");

        report.append(String.format("Общая сумма налогов и взносов: %.2f руб.\n\n", totalTax));

        List<Object[]> taxDetails = paymentRepository.findTaxDetailsByPeriod(month, year);
        report.append("Детализация:\n");
        for (Object[] detail : taxDetails) {
            String taxName = (String) detail[0];
            BigDecimal amount = (BigDecimal) detail[1];
            report.append(String.format("- %s: %.2f руб.\n", taxName, amount));
        }

        return report.toString();
    }

    public String generatePayslip(Integer employeeId, Integer month, Integer year) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new RuntimeException("Сотрудник не найден"));

        SalaryPayment salaryPayment = salaryPaymentRepository
                .findByEmployeeAndMonthAndYear(employee, month, year)
                .orElseThrow(() -> new RuntimeException("Расчет зарплаты не найден"));

        List<Payment> payments = paymentRepository.findByEmployeeAndMonthAndYear(employee, month, year);

        StringBuilder payslip = new StringBuilder();

        payslip.append("РАСЧЕТНЫЙ ЛИСТОК\n");
        payslip.append("за ").append(month).append(".").append(year).append("\n\n");

        payslip.append("Сотрудник: ").append(employee.getFullName()).append("\n");
        payslip.append("Должность: ").append(employee.getPosition().getTitle()).append("\n");
        payslip.append("Отдел: ").append(employee.getDepartment().getName()).append("\n");
        payslip.append("Дата приема: ").append(employee.getHireDate()).append("\n\n");

        payslip.append("НАЧИСЛЕНИЯ:\n");
        payslip.append("----------------------------------------\n");

        BigDecimal totalAccrued = BigDecimal.ZERO;
        List<Payment> accruals = payments.stream()
                .filter(p -> p.getPaymentType().getCategory().equals("accrual"))
                .toList();

        for (Payment payment : accruals) {
            payslip.append(String.format("%-25s %10.2f руб.\n",
                    payment.getPaymentType().getName(),
                    payment.getAmount()));
            totalAccrued = totalAccrued.add(payment.getAmount());
        }

        payslip.append("----------------------------------------\n");
        payslip.append(String.format("%-25s %10.2f руб.\n", "Итого начислено:", totalAccrued));
        payslip.append("\n");

        payslip.append("УДЕРЖАНИЯ:\n");
        payslip.append("----------------------------------------\n");

        BigDecimal totalDeducted = BigDecimal.ZERO;
        List<Payment> deductions = payments.stream()
                .filter(p -> p.getPaymentType().getCategory().equals("deduction"))
                .toList();

        for (Payment payment : deductions) {
            payslip.append(String.format("%-25s %10.2f руб.\n",
                    payment.getPaymentType().getName(),
                    payment.getAmount().abs()));
            totalDeducted = totalDeducted.add(payment.getAmount().abs());
        }

        payslip.append("----------------------------------------\n");
        payslip.append(String.format("%-25s %10.2f руб.\n", "Итого удержано:", totalDeducted));
        payslip.append("\n");

        payslip.append("ИТОГИ:\n");
        payslip.append("----------------------------------------\n");
        payslip.append(String.format("%-25s %10.2f руб.\n", "Начислено всего:", totalAccrued));
        payslip.append(String.format("%-25s %10.2f руб.\n", "Удержано всего:", totalDeducted));
        payslip.append(String.format("%-25s %10.2f руб.\n", "К ВЫПЛАТЕ:", salaryPayment.getNetSalary()));
        payslip.append("----------------------------------------\n\n");

        payslip.append("Дата формирования: ").append(java.time.LocalDate.now()).append("\n");
        payslip.append("Бухгалтер: _________________\n");

        return payslip.toString();
    }

    public String generateSalaryStatement(Integer departmentId, Integer month, Integer year) {
        Department department = departmentRepository.findById(departmentId)
                .orElseThrow(() -> new RuntimeException("Отдел не найден"));

        List<SalaryPayment> salaryPayments = salaryPaymentRepository
                .findByDepartmentAndPeriod(departmentId, month, year);

        StringBuilder statement = new StringBuilder();

        statement.append("РАСЧЕТНАЯ ВЕДОМОСТЬ\n");
        statement.append("Отдел: ").append(department.getName()).append("\n");
        statement.append("за ").append(month).append(".").append(year).append("\n\n");

        statement.append(String.format("%-3s %-25s %-20s %12s %12s %12s\n",
                "№", "Сотрудник", "Должность", "Начислено", "Удержано", "К выплате"));
        statement.append("-".repeat(95)).append("\n");

        BigDecimal departmentTotalAccrued = BigDecimal.ZERO;
        BigDecimal departmentTotalDeducted = BigDecimal.ZERO;
        BigDecimal departmentTotalNet = BigDecimal.ZERO;

        int counter = 1;
        for (SalaryPayment sp : salaryPayments) {
            statement.append(String.format("%-3d %-25s %-20s %12.2f %12.2f %12.2f\n",
                    counter++,
                    sp.getEmployee().getFullName(),
                    sp.getEmployee().getPosition().getTitle(),
                    sp.getTotalAccrued(),
                    sp.getTotalDeducted(),
                    sp.getNetSalary()));

            departmentTotalAccrued = departmentTotalAccrued.add(sp.getTotalAccrued());
            departmentTotalDeducted = departmentTotalDeducted.add(sp.getTotalDeducted());
            departmentTotalNet = departmentTotalNet.add(sp.getNetSalary());
        }

        statement.append("-".repeat(95)).append("\n");
        statement.append(String.format("%-49s %12.2f %12.2f %12.2f\n",
                "ИТОГО по отделу:",
                departmentTotalAccrued,
                departmentTotalDeducted,
                departmentTotalNet));

        statement.append("\n");
        statement.append("Количество сотрудников: ").append(salaryPayments.size()).append("\n");
        statement.append("Средняя зарплата: ").append(
                departmentTotalNet.divide(BigDecimal.valueOf(salaryPayments.size()), 2, RoundingMode.HALF_UP)
        ).append(" руб.\n\n");

        statement.append("Дата формирования: ").append(java.time.LocalDate.now()).append("\n");
        statement.append("Главный бухгалтер: _________________\n");

        return statement.toString();
    }

    public byte[] generateSalaryReportPdf(Integer month, Integer year) throws DocumentException {
        List<SalaryPayment> salaryPayments = salaryPaymentRepository.findByMonthAndYear(month, year);

        Document document = new Document(PageSize.A4.rotate());
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        PdfWriter.getInstance(document, outputStream);

        document.open();

        Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, BaseColor.BLACK);
        Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, BaseColor.BLACK);
        Font normalFont = FontFactory.getFont(FontFactory.HELVETICA, 10, BaseColor.BLACK);
        Font smallFont = FontFactory.getFont(FontFactory.HELVETICA, 8, BaseColor.DARK_GRAY);

        Paragraph title = new Paragraph("ВЕДОМОСТЬ ПО ЗАРПЛАТЕ", titleFont);
        title.setAlignment(Element.ALIGN_CENTER);
        title.setSpacingAfter(20);
        document.add(title);

        Paragraph period = new Paragraph("За период: " + getMonthName(month) + " " + year, normalFont);
        period.setAlignment(Element.ALIGN_CENTER);
        period.setSpacingAfter(20);
        document.add(period);

        PdfPTable table = new PdfPTable(5);
        table.setWidthPercentage(100);
        table.setSpacingBefore(10f);
        table.setSpacingAfter(10f);

        float[] columnWidths = {1f, 4f, 2f, 2f, 2f};
        table.setWidths(columnWidths);

        table.addCell(createPdfCell("№", headerFont, Element.ALIGN_CENTER));
        table.addCell(createPdfCell("Сотрудник", headerFont, Element.ALIGN_LEFT));
        table.addCell(createPdfCell("Начисления", headerFont, Element.ALIGN_RIGHT));
        table.addCell(createPdfCell("Удержания", headerFont, Element.ALIGN_RIGHT));
        table.addCell(createPdfCell("К выплате", headerFont, Element.ALIGN_RIGHT));

        BigDecimal totalAccrued = BigDecimal.ZERO;
        BigDecimal totalDeducted = BigDecimal.ZERO;
        BigDecimal totalNet = BigDecimal.ZERO;

        int counter = 1;
        for (SalaryPayment sp : salaryPayments) {
            table.addCell(createPdfCell(String.valueOf(counter++), normalFont, Element.ALIGN_CENTER));
            table.addCell(createPdfCell(sp.getEmployee().getFullName(), normalFont, Element.ALIGN_LEFT));
            table.addCell(createPdfCell(String.format("%.2f руб.", sp.getTotalAccrued()), normalFont, Element.ALIGN_RIGHT));
            table.addCell(createPdfCell(String.format("%.2f руб.", sp.getTotalDeducted()), normalFont, Element.ALIGN_RIGHT));
            table.addCell(createPdfCell(String.format("%.2f руб.", sp.getNetSalary()), normalFont, Element.ALIGN_RIGHT));

            totalAccrued = totalAccrued.add(sp.getTotalAccrued());
            totalDeducted = totalDeducted.add(sp.getTotalDeducted());
            totalNet = totalNet.add(sp.getNetSalary());
        }

        table.addCell(createPdfCell("", headerFont, Element.ALIGN_CENTER));
        table.addCell(createPdfCell("ИТОГО:", headerFont, Element.ALIGN_RIGHT));
        table.addCell(createPdfCell(String.format("%.2f руб.", totalAccrued), headerFont, Element.ALIGN_RIGHT));
        table.addCell(createPdfCell(String.format("%.2f руб.", totalDeducted), headerFont, Element.ALIGN_RIGHT));
        table.addCell(createPdfCell(String.format("%.2f руб.", totalNet), headerFont, Element.ALIGN_RIGHT));

        document.add(table);

        Paragraph signature = new Paragraph("\n\nДата формирования: " +
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")), smallFont);
        document.add(signature);

        Paragraph footer = new Paragraph("\nБухгалтер: _________________________", normalFont);
        document.add(footer);

        document.close();
        return outputStream.toByteArray();
    }

    private PdfPCell createPdfCell(String text, Font font, int alignment) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setHorizontalAlignment(alignment);
        cell.setPadding(5);
        cell.setBorder(PdfPCell.BOTTOM | PdfPCell.TOP | PdfPCell.LEFT | PdfPCell.RIGHT);
        return cell;
    }

    public byte[] generateSalaryReportExcel(Integer month, Integer year) throws IOException {
        List<SalaryPayment> salaryPayments = salaryPaymentRepository.findByMonthAndYear(month, year);

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Ведомость по зарплате");

            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setBorderBottom(BorderStyle.THIN);
            headerStyle.setBorderTop(BorderStyle.THIN);
            headerStyle.setBorderLeft(BorderStyle.THIN);
            headerStyle.setBorderRight(BorderStyle.THIN);

            org.apache.poi.ss.usermodel.Font headerFontExcel = workbook.createFont();
            headerFontExcel.setBold(true);
            headerStyle.setFont(headerFontExcel);

            CellStyle moneyStyle = workbook.createCellStyle();
            moneyStyle.setDataFormat(workbook.createDataFormat().getFormat("#,##0.00"));
            moneyStyle.setBorderBottom(BorderStyle.THIN);
            moneyStyle.setBorderTop(BorderStyle.THIN);
            moneyStyle.setBorderLeft(BorderStyle.THIN);
            moneyStyle.setBorderRight(BorderStyle.THIN);

            CellStyle normalStyle = workbook.createCellStyle();
            normalStyle.setBorderBottom(BorderStyle.THIN);
            normalStyle.setBorderTop(BorderStyle.THIN);
            normalStyle.setBorderLeft(BorderStyle.THIN);
            normalStyle.setBorderRight(BorderStyle.THIN);

            CellStyle totalStyle = workbook.createCellStyle();
            totalStyle.setFillForegroundColor(IndexedColors.LIGHT_YELLOW.getIndex());
            totalStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            totalStyle.setBorderBottom(BorderStyle.THIN);
            totalStyle.setBorderTop(BorderStyle.THIN);
            totalStyle.setBorderLeft(BorderStyle.THIN);
            totalStyle.setBorderRight(BorderStyle.THIN);
            org.apache.poi.ss.usermodel.Font totalFont = workbook.createFont();
            totalFont.setBold(true);
            totalStyle.setFont(totalFont);

            Row titleRow = sheet.createRow(0);
            Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue("ВЕДОМОСТЬ ПО ЗАРПЛАТЕ");

            Row periodRow = sheet.createRow(1);
            Cell periodCell = periodRow.createCell(0);
            periodCell.setCellValue("За период: " + getMonthName(month) + " " + year);

            sheet.createRow(2);

            Row headerRow = sheet.createRow(3);
            String[] headers = {"№", "Сотрудник", "Должность", "Отдел", "Начисления", "Удержания", "К выплате"};
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            BigDecimal totalAccrued = BigDecimal.ZERO;
            BigDecimal totalDeducted = BigDecimal.ZERO;
            BigDecimal totalNet = BigDecimal.ZERO;

            int rowNum = 4;
            for (SalaryPayment sp : salaryPayments) {
                Row row = sheet.createRow(rowNum++);

                Cell numCell = row.createCell(0);
                numCell.setCellValue(rowNum - 4);
                numCell.setCellStyle(normalStyle);

                Cell nameCell = row.createCell(1);
                nameCell.setCellValue(sp.getEmployee().getFullName());
                nameCell.setCellStyle(normalStyle);

                Cell positionCell = row.createCell(2);
                positionCell.setCellValue(sp.getEmployee().getPosition().getTitle());
                positionCell.setCellStyle(normalStyle);

                Cell deptCell = row.createCell(3);
                deptCell.setCellValue(sp.getEmployee().getDepartment().getName());
                deptCell.setCellStyle(normalStyle);

                Cell accrualCell = row.createCell(4);
                accrualCell.setCellValue(sp.getTotalAccrued().doubleValue());
                accrualCell.setCellStyle(moneyStyle);

                Cell deductionCell = row.createCell(5);
                deductionCell.setCellValue(sp.getTotalDeducted().doubleValue());
                deductionCell.setCellStyle(moneyStyle);

                Cell netCell = row.createCell(6);
                netCell.setCellValue(sp.getNetSalary().doubleValue());
                netCell.setCellStyle(moneyStyle);

                totalAccrued = totalAccrued.add(sp.getTotalAccrued());
                totalDeducted = totalDeducted.add(sp.getTotalDeducted());
                totalNet = totalNet.add(sp.getNetSalary());
            }

            Row totalRow = sheet.createRow(rowNum);
            for (int i = 0; i < 4; i++) {
                Cell cell = totalRow.createCell(i);
                cell.setCellStyle(totalStyle);
                if (i == 3) {
                    cell.setCellValue("ИТОГО:");
                }
            }

            Cell totalAccrualCell = totalRow.createCell(4);
            totalAccrualCell.setCellValue(totalAccrued.doubleValue());
            totalAccrualCell.setCellStyle(totalStyle);

            Cell totalDeductionCell = totalRow.createCell(5);
            totalDeductionCell.setCellValue(totalDeducted.doubleValue());
            totalDeductionCell.setCellStyle(totalStyle);

            Cell totalNetCell = totalRow.createCell(6);
            totalNetCell.setCellValue(totalNet.doubleValue());
            totalNetCell.setCellStyle(totalStyle);

            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            workbook.write(outputStream);
            return outputStream.toByteArray();
        }
    }

    public byte[] generateDeductionsReportPdf(Integer month, Integer year) throws DocumentException {
        List<Payment> payments = paymentRepository.findByMonthAndYear(month, year);
        List<Payment> deductionPayments = payments.stream()
                .filter(p -> "deduction".equals(p.getPaymentType().getCategory()))
                .toList();

        Document document = new Document();
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        PdfWriter.getInstance(document, outputStream);

        document.open();

        Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, BaseColor.BLACK);
        Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, BaseColor.BLACK);
        Font normalFont = FontFactory.getFont(FontFactory.HELVETICA, 10, BaseColor.BLACK);
        Font smallFont = FontFactory.getFont(FontFactory.HELVETICA, 8, BaseColor.DARK_GRAY);

        Paragraph title = new Paragraph("ОТЧЕТ ПО УДЕРЖАНИЯМ ИЗ ЗАРАБОТНОЙ ПЛАТЫ", titleFont);
        title.setAlignment(Element.ALIGN_CENTER);
        title.setSpacingAfter(20);
        document.add(title);

        Paragraph period = new Paragraph("За период: " + getMonthName(month) + " " + year, normalFont);
        period.setAlignment(Element.ALIGN_CENTER);
        period.setSpacingAfter(20);
        document.add(period);

        if (deductionPayments.isEmpty()) {
            Paragraph noData = new Paragraph("Удержания за указанный период отсутствуют.", normalFont);
            noData.setAlignment(Element.ALIGN_CENTER);
            document.add(noData);
        } else {
            PdfPTable table = new PdfPTable(4);
            table.setWidthPercentage(100);
            table.setSpacingBefore(10f);

            float[] columnWidths = {3f, 2f, 2f, 3f};
            table.setWidths(columnWidths);

            table.addCell(createPdfCell("Сотрудник", headerFont, Element.ALIGN_LEFT));
            table.addCell(createPdfCell("Тип удержания", headerFont, Element.ALIGN_LEFT));
            table.addCell(createPdfCell("Сумма", headerFont, Element.ALIGN_RIGHT));
            table.addCell(createPdfCell("Описание", headerFont, Element.ALIGN_LEFT));

            BigDecimal totalDeductions = BigDecimal.ZERO;
            for (Payment payment : deductionPayments) {
                table.addCell(createPdfCell(payment.getEmployee().getFullName(), normalFont, Element.ALIGN_LEFT));
                table.addCell(createPdfCell(payment.getPaymentType().getName(), normalFont, Element.ALIGN_LEFT));
                table.addCell(createPdfCell(String.format("%.2f руб.", payment.getAmount().abs()), normalFont, Element.ALIGN_RIGHT));
                table.addCell(createPdfCell(payment.getDescription() != null ? payment.getDescription() : "", normalFont, Element.ALIGN_LEFT));

                totalDeductions = totalDeductions.add(payment.getAmount().abs());
            }

            table.addCell(createPdfCell("", headerFont, Element.ALIGN_LEFT));
            table.addCell(createPdfCell("ИТОГО:", headerFont, Element.ALIGN_RIGHT));
            table.addCell(createPdfCell(String.format("%.2f руб.", totalDeductions), headerFont, Element.ALIGN_RIGHT));
            table.addCell(createPdfCell("", headerFont, Element.ALIGN_LEFT));

            document.add(table);

            Paragraph stats = new Paragraph("\n\nСтатистика:\n" +
                    "Всего удержаний: " + deductionPayments.size() + " операций\n" +
                    "Общая сумма удержаний: " + String.format("%.2f", totalDeductions) + " руб.\n" +
                    "Количество сотрудников с удержаниями: " +
                    deductionPayments.stream().map(p -> p.getEmployee().getId()).distinct().count(), normalFont);
            document.add(stats);
        }

        Paragraph signature = new Paragraph("\n\nДата формирования: " +
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")), smallFont);
        document.add(signature);

        document.close();
        return outputStream.toByteArray();
    }

    public byte[] generateDeductionsReportExcel(Integer month, Integer year) throws IOException {
        List<Payment> payments = paymentRepository.findByMonthAndYear(month, year);
        List<Payment> deductionPayments = payments.stream()
                .filter(p -> "deduction".equals(p.getPaymentType().getCategory()))
                .toList();

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Удержания");

            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            org.apache.poi.ss.usermodel.Font headerFontExcel = workbook.createFont();
            headerFontExcel.setBold(true);
            headerStyle.setFont(headerFontExcel);

            CellStyle moneyStyle = workbook.createCellStyle();
            moneyStyle.setDataFormat(workbook.createDataFormat().getFormat("#,##0.00"));

            CellStyle normalStyle = workbook.createCellStyle();
            normalStyle.setBorderBottom(BorderStyle.THIN);
            normalStyle.setBorderTop(BorderStyle.THIN);
            normalStyle.setBorderLeft(BorderStyle.THIN);
            normalStyle.setBorderRight(BorderStyle.THIN);

            Row titleRow = sheet.createRow(0);
            titleRow.createCell(0).setCellValue("ОТЧЕТ ПО УДЕРЖАНИЯМ ИЗ ЗАРАБОТНОЙ ПЛАТЫ");

            Row periodRow = sheet.createRow(1);
            periodRow.createCell(0).setCellValue("За период: " + getMonthName(month) + " " + year);

            sheet.createRow(2);

            int rowNum = 3;
            String[] headers = {"Сотрудник", "Отдел", "Тип удержания", "Сумма", "Описание", "Дата создания"};
            Row headerRow = sheet.createRow(rowNum++);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            BigDecimal totalDeductions = BigDecimal.ZERO;
            for (Payment payment : deductionPayments) {
                Row row = sheet.createRow(rowNum++);

                row.createCell(0).setCellValue(payment.getEmployee().getFullName());
                row.createCell(1).setCellValue(payment.getEmployee().getDepartment().getName());
                row.createCell(2).setCellValue(payment.getPaymentType().getName());

                Cell amountCell = row.createCell(3);
                amountCell.setCellValue(payment.getAmount().abs().doubleValue());
                amountCell.setCellStyle(moneyStyle);

                row.createCell(4).setCellValue(payment.getDescription() != null ? payment.getDescription() : "");
                row.createCell(5).setCellValue(payment.getCreatedAt().toString());

                totalDeductions = totalDeductions.add(payment.getAmount().abs());
            }

            Row totalRow = sheet.createRow(rowNum);
            totalRow.createCell(2).setCellValue("ИТОГО:");
            Cell totalCell = totalRow.createCell(3);
            totalCell.setCellValue(totalDeductions.doubleValue());
            totalCell.setCellStyle(moneyStyle);

            Row statsRow1 = sheet.createRow(rowNum + 2);
            statsRow1.createCell(0).setCellValue("Статистика:");
            Row statsRow2 = sheet.createRow(rowNum + 3);
            statsRow2.createCell(0).setCellValue("Всего удержаний: " + deductionPayments.size() + " операций");
            Row statsRow3 = sheet.createRow(rowNum + 4);
            statsRow3.createCell(0).setCellValue("Общая сумма удержаний: " + String.format("%.2f", totalDeductions) + " руб.");
            Row statsRow4 = sheet.createRow(rowNum + 5);
            statsRow4.createCell(0).setCellValue("Количество сотрудников с удержаниями: " +
                    deductionPayments.stream().map(p -> p.getEmployee().getId()).distinct().count());

            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            workbook.write(outputStream);
            return outputStream.toByteArray();
        }
    }

    public byte[] generateTaxReportPdf(Integer month, Integer year) throws DocumentException {
        List<Payment> taxPayments = paymentRepository.findTaxPaymentsByPeriod(month, year);

        Document document = new Document();
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        PdfWriter.getInstance(document, outputStream);

        document.open();

        Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, BaseColor.BLACK);
        Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, BaseColor.BLACK);
        Font normalFont = FontFactory.getFont(FontFactory.HELVETICA, 10, BaseColor.BLACK);
        Font smallFont = FontFactory.getFont(FontFactory.HELVETICA, 8, BaseColor.DARK_GRAY);

        Paragraph title = new Paragraph("ОТЧЕТ ПО НАЛОГАМ И ВЗНОСАМ", titleFont);
        title.setAlignment(Element.ALIGN_CENTER);
        title.setSpacingAfter(20);
        document.add(title);

        Paragraph period = new Paragraph("За период: " + getMonthName(month) + " " + year, normalFont);
        period.setAlignment(Element.ALIGN_CENTER);
        period.setSpacingAfter(20);
        document.add(period);

        if (taxPayments.isEmpty()) {
            Paragraph noData = new Paragraph("Налоговые платежи за указанный период отсутствуют.", normalFont);
            noData.setAlignment(Element.ALIGN_CENTER);
            document.add(noData);
        } else {
            BigDecimal totalTaxes = taxPayments.stream()
                    .map(Payment::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            Paragraph summary = new Paragraph("СВОДНАЯ СТАТИСТИКА:\n", headerFont);
            summary.setSpacingAfter(10);
            document.add(summary);

            PdfPTable summaryTable = new PdfPTable(2);
            summaryTable.setWidthPercentage(50);
            summaryTable.setHorizontalAlignment(Element.ALIGN_LEFT);

            summaryTable.addCell(createPdfCell("Общая сумма налогов:", normalFont, Element.ALIGN_LEFT));
            summaryTable.addCell(createPdfCell(String.format("%.2f руб.", totalTaxes), normalFont, Element.ALIGN_RIGHT));
            summaryTable.addCell(createPdfCell("Количество операций:", normalFont, Element.ALIGN_LEFT));
            summaryTable.addCell(createPdfCell(String.valueOf(taxPayments.size()), normalFont, Element.ALIGN_RIGHT));

            document.add(summaryTable);

            Map<PaymentType, List<Payment>> taxesByType = taxPayments.stream()
                    .collect(Collectors.groupingBy(Payment::getPaymentType));

            Paragraph detailsTitle = new Paragraph("\nДЕТАЛИЗАЦИЯ ПО ВИДАМ НАЛОГОВ:", headerFont);
            detailsTitle.setSpacingAfter(10);
            document.add(detailsTitle);

            PdfPTable detailsTable = new PdfPTable(3);
            detailsTable.setWidthPercentage(100);
            float[] widths = {4f, 2f, 2f};
            detailsTable.setWidths(widths);

            detailsTable.addCell(createPdfCell("Вид налога/взноса", headerFont, Element.ALIGN_LEFT));
            detailsTable.addCell(createPdfCell("Количество", headerFont, Element.ALIGN_CENTER));
            detailsTable.addCell(createPdfCell("Сумма", headerFont, Element.ALIGN_RIGHT));

            for (Map.Entry<PaymentType, List<Payment>> entry : taxesByType.entrySet()) {
                PaymentType taxType = entry.getKey();
                List<Payment> typePayments = entry.getValue();
                BigDecimal typeTotal = typePayments.stream()
                        .map(Payment::getAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                detailsTable.addCell(createPdfCell(taxType.getName(), normalFont, Element.ALIGN_LEFT));
                detailsTable.addCell(createPdfCell(String.valueOf(typePayments.size()), normalFont, Element.ALIGN_CENTER));
                detailsTable.addCell(createPdfCell(String.format("%.2f руб.", typeTotal), normalFont, Element.ALIGN_RIGHT));
            }

            document.add(detailsTable);

            Paragraph employeesTitle = new Paragraph("\nДЕТАЛИЗАЦИЯ ПО СОТРУДНИКАМ:", headerFont);
            employeesTitle.setSpacingAfter(10);
            document.add(employeesTitle);

            PdfPTable employeesTable = new PdfPTable(4);
            employeesTable.setWidthPercentage(100);
            float[] empWidths = {3f, 2f, 2f, 1f};
            employeesTable.setWidths(empWidths);

            employeesTable.addCell(createPdfCell("Сотрудник", headerFont, Element.ALIGN_LEFT));
            employeesTable.addCell(createPdfCell("Отдел", headerFont, Element.ALIGN_LEFT));
            employeesTable.addCell(createPdfCell("Вид налога", headerFont, Element.ALIGN_LEFT));
            employeesTable.addCell(createPdfCell("Сумма", headerFont, Element.ALIGN_RIGHT));

            for (Payment payment : taxPayments) {
                employeesTable.addCell(createPdfCell(payment.getEmployee().getFullName(), normalFont, Element.ALIGN_LEFT));
                employeesTable.addCell(createPdfCell(payment.getEmployee().getDepartment().getName(), normalFont, Element.ALIGN_LEFT));
                employeesTable.addCell(createPdfCell(payment.getPaymentType().getName(), normalFont, Element.ALIGN_LEFT));
                employeesTable.addCell(createPdfCell(String.format("%.2f руб.", payment.getAmount()), normalFont, Element.ALIGN_RIGHT));
            }

            document.add(employeesTable);
        }

        Paragraph signature = new Paragraph("\n\nДата формирования: " +
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")), smallFont);
        document.add(signature);

        Paragraph footer = new Paragraph("\nГлавный бухгалтер: _________________________", normalFont);
        document.add(footer);

        document.close();
        return outputStream.toByteArray();
    }

    public byte[] generateTaxReportExcel(Integer month, Integer year) throws IOException {
        List<Payment> taxPayments = paymentRepository.findTaxPaymentsByPeriod(month, year);

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Налоговый отчет");

            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle moneyStyle = createMoneyStyle(workbook);
            CellStyle normalStyle = createNormalStyle(workbook);
            CellStyle totalStyle = createTotalStyle(workbook);

            Row titleRow = sheet.createRow(0);
            titleRow.createCell(0).setCellValue("ОТЧЕТ ПО НАЛОГАМ И ВЗНОСАМ");

            Row periodRow = sheet.createRow(1);
            periodRow.createCell(0).setCellValue("За период: " + getMonthName(month) + " " + year);

            int rowNum = 3;
            if (!taxPayments.isEmpty()) {
                BigDecimal totalTaxes = taxPayments.stream()
                        .map(Payment::getAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                Row summaryRow1 = sheet.createRow(rowNum++);
                summaryRow1.createCell(0).setCellValue("СВОДНАЯ СТАТИСТИКА:");

                Row summaryRow2 = sheet.createRow(rowNum++);
                summaryRow2.createCell(0).setCellValue("Общая сумма налогов:");
                summaryRow2.createCell(1).setCellValue(totalTaxes.doubleValue());
                summaryRow2.getCell(1).setCellStyle(moneyStyle);

                Row summaryRow3 = sheet.createRow(rowNum++);
                summaryRow3.createCell(0).setCellValue("Количество операций:");
                summaryRow3.createCell(1).setCellValue(taxPayments.size());

                rowNum++;
            }

            if (!taxPayments.isEmpty()) {
                Row detailsTitle = sheet.createRow(rowNum++);
                detailsTitle.createCell(0).setCellValue("ДЕТАЛИЗАЦИЯ ПО ВИДАМ НАЛОГОВ:");

                Row detailsHeader = sheet.createRow(rowNum++);
                String[] headers = {"Вид налога/взноса", "Количество операций", "Общая сумма"};
                for (int i = 0; i < headers.length; i++) {
                    Cell cell = detailsHeader.createCell(i);
                    cell.setCellValue(headers[i]);
                    cell.setCellStyle(headerStyle);
                }

                Map<PaymentType, List<Payment>> taxesByType = taxPayments.stream()
                        .collect(Collectors.groupingBy(Payment::getPaymentType));

                for (Map.Entry<PaymentType, List<Payment>> entry : taxesByType.entrySet()) {
                    Row row = sheet.createRow(rowNum++);
                    PaymentType taxType = entry.getKey();
                    List<Payment> typePayments = entry.getValue();
                    BigDecimal typeTotal = typePayments.stream()
                            .map(Payment::getAmount)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

                    row.createCell(0).setCellValue(taxType.getName());
                    row.createCell(1).setCellValue(typePayments.size());

                    Cell amountCell = row.createCell(2);
                    amountCell.setCellValue(typeTotal.doubleValue());
                    amountCell.setCellStyle(moneyStyle);
                }

                rowNum++;
            }

            if (!taxPayments.isEmpty()) {
                Row employeesTitle = sheet.createRow(rowNum++);
                employeesTitle.createCell(0).setCellValue("ДЕТАЛИЗАЦИЯ ПО СОТРУДНИКАМ:");

                Row employeesHeader = sheet.createRow(rowNum++);
                String[] empHeaders = {"Сотрудник", "Отдел", "Должность", "Вид налога", "Сумма", "Дата"};
                for (int i = 0; i < empHeaders.length; i++) {
                    Cell cell = employeesHeader.createCell(i);
                    cell.setCellValue(empHeaders[i]);
                    cell.setCellStyle(headerStyle);
                }

                for (Payment payment : taxPayments) {
                    Row row = sheet.createRow(rowNum++);
                    row.createCell(0).setCellValue(payment.getEmployee().getFullName());
                    row.createCell(1).setCellValue(payment.getEmployee().getDepartment().getName());
                    row.createCell(2).setCellValue(payment.getEmployee().getPosition().getTitle());
                    row.createCell(3).setCellValue(payment.getPaymentType().getName());

                    Cell amountCell = row.createCell(4);
                    amountCell.setCellValue(payment.getAmount().doubleValue());
                    amountCell.setCellStyle(moneyStyle);

                    row.createCell(5).setCellValue(
                            payment.getCreatedAt().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
                    );
                }
            } else {
                Row noData = sheet.createRow(rowNum++);
                noData.createCell(0).setCellValue("Налоговые платежи за указанный период отсутствуют.");
            }

            for (int i = 0; i < 10; i++) {
                sheet.autoSizeColumn(i);
            }

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            workbook.write(outputStream);
            return outputStream.toByteArray();
        }
    }

    public byte[] generatePayslipPdf(Integer employeeId, Integer month, Integer year) throws DocumentException {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new RuntimeException("Сотрудник не найден"));

        List<Payment> payments = paymentRepository.findByEmployeeAndMonthAndYear(employee, month, year);
        List<Payment> accruals = payments.stream()
                .filter(p -> "accrual".equals(p.getPaymentType().getCategory()))
                .toList();
        List<Payment> deductions = payments.stream()
                .filter(p -> "deduction".equals(p.getPaymentType().getCategory()))
                .toList();

        BigDecimal totalAccruals = accruals.stream()
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalDeductions = deductions.stream()
                .map(p -> p.getAmount().abs())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal netSalary = totalAccruals.subtract(totalDeductions);

        Document document = new Document();
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        PdfWriter.getInstance(document, outputStream);

        document.open();

        Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, BaseColor.BLACK);
        Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, BaseColor.BLACK);
        Font normalFont = FontFactory.getFont(FontFactory.HELVETICA, 9, BaseColor.BLACK);
        Font smallFont = FontFactory.getFont(FontFactory.HELVETICA, 8, BaseColor.DARK_GRAY);
        Font totalFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, BaseColor.BLACK);

        Paragraph title = new Paragraph("РАСЧЕТНЫЙ ЛИСТОК", titleFont);
        title.setAlignment(Element.ALIGN_CENTER);
        title.setSpacingAfter(10);
        document.add(title);

        Paragraph period = new Paragraph("за " + getMonthName(month) + " " + year, headerFont);
        period.setAlignment(Element.ALIGN_CENTER);
        period.setSpacingAfter(20);
        document.add(period);

        PdfPTable infoTable = new PdfPTable(2);
        infoTable.setWidthPercentage(100);
        infoTable.setSpacingAfter(15);

        infoTable.addCell(createPdfCell("Сотрудник:", normalFont, Element.ALIGN_LEFT));
        infoTable.addCell(createPdfCell(employee.getFullName(), normalFont, Element.ALIGN_LEFT));
        infoTable.addCell(createPdfCell("Должность:", normalFont, Element.ALIGN_LEFT));
        infoTable.addCell(createPdfCell(employee.getPosition().getTitle(), normalFont, Element.ALIGN_LEFT));
        infoTable.addCell(createPdfCell("Отдел:", normalFont, Element.ALIGN_LEFT));
        infoTable.addCell(createPdfCell(employee.getDepartment().getName(), normalFont, Element.ALIGN_LEFT));
        infoTable.addCell(createPdfCell("Табельный номер:", normalFont, Element.ALIGN_LEFT));
        infoTable.addCell(createPdfCell(String.valueOf(employee.getId()), normalFont, Element.ALIGN_LEFT));

        document.add(infoTable);

        Paragraph accrualsTitle = new Paragraph("НАЧИСЛЕНИЯ", headerFont);
        accrualsTitle.setSpacingAfter(10);
        document.add(accrualsTitle);

        PdfPTable accrualsTable = new PdfPTable(2);
        accrualsTable.setWidthPercentage(80);
        accrualsTable.setHorizontalAlignment(Element.ALIGN_LEFT);
        accrualsTable.setSpacingAfter(15);

        for (Payment payment : accruals) {
            accrualsTable.addCell(createPdfCell(payment.getPaymentType().getName(), normalFont, Element.ALIGN_LEFT));
            accrualsTable.addCell(createPdfCell(String.format("%.2f руб.", payment.getAmount()), normalFont, Element.ALIGN_RIGHT));
        }

        accrualsTable.addCell(createPdfCell("Итого начислено:", totalFont, Element.ALIGN_LEFT));
        accrualsTable.addCell(createPdfCell(String.format("%.2f руб.", totalAccruals), totalFont, Element.ALIGN_RIGHT));

        document.add(accrualsTable);

        Paragraph deductionsTitle = new Paragraph("УДЕРЖАНИЯ", headerFont);
        deductionsTitle.setSpacingAfter(10);
        document.add(deductionsTitle);

        PdfPTable deductionsTable = new PdfPTable(2);
        deductionsTable.setWidthPercentage(80);
        deductionsTable.setHorizontalAlignment(Element.ALIGN_LEFT);
        deductionsTable.setSpacingAfter(15);

        for (Payment payment : deductions) {
            deductionsTable.addCell(createPdfCell(payment.getPaymentType().getName(), normalFont, Element.ALIGN_LEFT));
            deductionsTable.addCell(createPdfCell(String.format("%.2f руб.", payment.getAmount().abs()), normalFont, Element.ALIGN_RIGHT));
        }

        deductionsTable.addCell(createPdfCell("Итого удержано:", totalFont, Element.ALIGN_LEFT));
        deductionsTable.addCell(createPdfCell(String.format("%.2f руб.", totalDeductions), totalFont, Element.ALIGN_RIGHT));

        document.add(deductionsTable);

        Paragraph finalTitle = new Paragraph("ИТОГОВЫЙ РАСЧЕТ", headerFont);
        finalTitle.setSpacingAfter(10);
        document.add(finalTitle);

        PdfPTable finalTable = new PdfPTable(2);
        finalTable.setWidthPercentage(60);
        finalTable.setHorizontalAlignment(Element.ALIGN_LEFT);

        finalTable.addCell(createPdfCell("Начислено всего:", normalFont, Element.ALIGN_LEFT));
        finalTable.addCell(createPdfCell(String.format("%.2f руб.", totalAccruals), normalFont, Element.ALIGN_RIGHT));
        finalTable.addCell(createPdfCell("Удержано всего:", normalFont, Element.ALIGN_LEFT));
        finalTable.addCell(createPdfCell(String.format("%.2f руб.", totalDeductions), normalFont, Element.ALIGN_RIGHT));

        PdfPCell lineCell = new PdfPCell(new Phrase(""));
        lineCell.setBorder(PdfPCell.TOP);
        lineCell.setColspan(2);
        lineCell.setFixedHeight(1f);
        finalTable.addCell(lineCell);

        finalTable.addCell(createPdfCell("К ВЫПЛАТЕ:", totalFont, Element.ALIGN_LEFT));
        finalTable.addCell(createPdfCell(String.format("%.2f руб.", netSalary), totalFont, Element.ALIGN_RIGHT));

        document.add(finalTable);

        Paragraph signature = new Paragraph("\n\nДата формирования: " +
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy")), smallFont);
        document.add(signature);

        Paragraph accountant = new Paragraph("\nБухгалтер: _________________________", normalFont);
        document.add(accountant);

        Paragraph received = new Paragraph("\nС расчетом ознакомлен: _________________________", normalFont);
        document.add(received);

        document.close();
        return outputStream.toByteArray();
    }

    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        org.apache.poi.ss.usermodel.Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        return style;
    }

    private CellStyle createMoneyStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setDataFormat(workbook.createDataFormat().getFormat("#,##0.00"));
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private CellStyle createNormalStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private CellStyle createTotalStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setFillForegroundColor(IndexedColors.LIGHT_YELLOW.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        org.apache.poi.ss.usermodel.Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        return style;
    }
}