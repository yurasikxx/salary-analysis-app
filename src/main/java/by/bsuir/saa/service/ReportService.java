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

        // Группируем по типам удержаний
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

        // Группируем по сотрудникам
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

        // Группируем по отделам
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

        // Создаем шрифты для PDF
        Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, BaseColor.BLACK);
        Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, BaseColor.BLACK);
        Font normalFont = FontFactory.getFont(FontFactory.HELVETICA, 10, BaseColor.BLACK);
        Font smallFont = FontFactory.getFont(FontFactory.HELVETICA, 8, BaseColor.DARK_GRAY);

        // Заголовок
        Paragraph title = new Paragraph("ВЕДОМОСТЬ ПО ЗАРПЛАТЕ", titleFont);
        title.setAlignment(Element.ALIGN_CENTER);
        title.setSpacingAfter(20);
        document.add(title);

        // Период
        Paragraph period = new Paragraph("За период: " + getMonthName(month) + " " + year, normalFont);
        period.setAlignment(Element.ALIGN_CENTER);
        period.setSpacingAfter(20);
        document.add(period);

        // Таблица
        PdfPTable table = new PdfPTable(5);
        table.setWidthPercentage(100);
        table.setSpacingBefore(10f);
        table.setSpacingAfter(10f);

        float[] columnWidths = {1f, 4f, 2f, 2f, 2f};
        table.setWidths(columnWidths);

        // Заголовки таблицы
        table.addCell(createPdfCell("№", headerFont, Element.ALIGN_CENTER));
        table.addCell(createPdfCell("Сотрудник", headerFont, Element.ALIGN_LEFT));
        table.addCell(createPdfCell("Начисления", headerFont, Element.ALIGN_RIGHT));
        table.addCell(createPdfCell("Удержания", headerFont, Element.ALIGN_RIGHT));
        table.addCell(createPdfCell("К выплате", headerFont, Element.ALIGN_RIGHT));

        // Данные
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

        // Итоговая строка
        table.addCell(createPdfCell("", headerFont, Element.ALIGN_CENTER));
        table.addCell(createPdfCell("ИТОГО:", headerFont, Element.ALIGN_RIGHT));
        table.addCell(createPdfCell(String.format("%.2f руб.", totalAccrued), headerFont, Element.ALIGN_RIGHT));
        table.addCell(createPdfCell(String.format("%.2f руб.", totalDeducted), headerFont, Element.ALIGN_RIGHT));
        table.addCell(createPdfCell(String.format("%.2f руб.", totalNet), headerFont, Element.ALIGN_RIGHT));

        document.add(table);

        // Подпись и дата
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

            // Стили для Excel
            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setBorderBottom(BorderStyle.THIN);
            headerStyle.setBorderTop(BorderStyle.THIN);
            headerStyle.setBorderLeft(BorderStyle.THIN);
            headerStyle.setBorderRight(BorderStyle.THIN);

            // Шрифт для заголовков Excel
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

            // Заголовок
            Row titleRow = sheet.createRow(0);
            Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue("ВЕДОМОСТЬ ПО ЗАРПЛАТЕ");

            Row periodRow = sheet.createRow(1);
            Cell periodCell = periodRow.createCell(0);
            periodCell.setCellValue("За период: " + getMonthName(month) + " " + year);

            // Пустая строка
            sheet.createRow(2);

            // Заголовки таблицы
            Row headerRow = sheet.createRow(3);
            String[] headers = {"№", "Сотрудник", "Должность", "Отдел", "Начисления", "Удержания", "К выплате"};
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // Данные
            BigDecimal totalAccrued = BigDecimal.ZERO;
            BigDecimal totalDeducted = BigDecimal.ZERO;
            BigDecimal totalNet = BigDecimal.ZERO;

            int rowNum = 4;
            for (SalaryPayment sp : salaryPayments) {
                Row row = sheet.createRow(rowNum++);

                // №
                Cell numCell = row.createCell(0);
                numCell.setCellValue(rowNum - 4);
                numCell.setCellStyle(normalStyle);

                // Сотрудник
                Cell nameCell = row.createCell(1);
                nameCell.setCellValue(sp.getEmployee().getFullName());
                nameCell.setCellStyle(normalStyle);

                // Должность
                Cell positionCell = row.createCell(2);
                positionCell.setCellValue(sp.getEmployee().getPosition().getTitle());
                positionCell.setCellStyle(normalStyle);

                // Отдел
                Cell deptCell = row.createCell(3);
                deptCell.setCellValue(sp.getEmployee().getDepartment().getName());
                deptCell.setCellStyle(normalStyle);

                // Начисления
                Cell accrualCell = row.createCell(4);
                accrualCell.setCellValue(sp.getTotalAccrued().doubleValue());
                accrualCell.setCellStyle(moneyStyle);

                // Удержания
                Cell deductionCell = row.createCell(5);
                deductionCell.setCellValue(sp.getTotalDeducted().doubleValue());
                deductionCell.setCellStyle(moneyStyle);

                // К выплате
                Cell netCell = row.createCell(6);
                netCell.setCellValue(sp.getNetSalary().doubleValue());
                netCell.setCellStyle(moneyStyle);

                totalAccrued = totalAccrued.add(sp.getTotalAccrued());
                totalDeducted = totalDeducted.add(sp.getTotalDeducted());
                totalNet = totalNet.add(sp.getNetSalary());
            }

            // Итоговая строка
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

            // Авто-размер колонок
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

        // Создаем шрифты для PDF
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
            // Создаем таблицу для удержаний
            PdfPTable table = new PdfPTable(4);
            table.setWidthPercentage(100);
            table.setSpacingBefore(10f);

            float[] columnWidths = {3f, 2f, 2f, 3f};
            table.setWidths(columnWidths);

            // Заголовки таблицы
            table.addCell(createPdfCell("Сотрудник", headerFont, Element.ALIGN_LEFT));
            table.addCell(createPdfCell("Тип удержания", headerFont, Element.ALIGN_LEFT));
            table.addCell(createPdfCell("Сумма", headerFont, Element.ALIGN_RIGHT));
            table.addCell(createPdfCell("Описание", headerFont, Element.ALIGN_LEFT));

            // Данные
            BigDecimal totalDeductions = BigDecimal.ZERO;
            for (Payment payment : deductionPayments) {
                table.addCell(createPdfCell(payment.getEmployee().getFullName(), normalFont, Element.ALIGN_LEFT));
                table.addCell(createPdfCell(payment.getPaymentType().getName(), normalFont, Element.ALIGN_LEFT));
                table.addCell(createPdfCell(String.format("%.2f руб.", payment.getAmount().abs()), normalFont, Element.ALIGN_RIGHT));
                table.addCell(createPdfCell(payment.getDescription() != null ? payment.getDescription() : "", normalFont, Element.ALIGN_LEFT));

                totalDeductions = totalDeductions.add(payment.getAmount().abs());
            }

            // Итог
            table.addCell(createPdfCell("", headerFont, Element.ALIGN_LEFT));
            table.addCell(createPdfCell("ИТОГО:", headerFont, Element.ALIGN_RIGHT));
            table.addCell(createPdfCell(String.format("%.2f руб.", totalDeductions), headerFont, Element.ALIGN_RIGHT));
            table.addCell(createPdfCell("", headerFont, Element.ALIGN_LEFT));

            document.add(table);

            // Статистика
            Paragraph stats = new Paragraph("\n\nСтатистика:\n" +
                    "Всего удержаний: " + deductionPayments.size() + " операций\n" +
                    "Общая сумма удержаний: " + String.format("%.2f", totalDeductions) + " руб.\n" +
                    "Количество сотрудников с удержаниями: " +
                    deductionPayments.stream().map(p -> p.getEmployee().getId()).distinct().count(), normalFont);
            document.add(stats);
        }

        // Подпись и дата
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

            // Стили для Excel
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

            // Заголовок
            Row titleRow = sheet.createRow(0);
            titleRow.createCell(0).setCellValue("ОТЧЕТ ПО УДЕРЖАНИЯМ ИЗ ЗАРАБОТНОЙ ПЛАТЫ");

            Row periodRow = sheet.createRow(1);
            periodRow.createCell(0).setCellValue("За период: " + getMonthName(month) + " " + year);

            // Пустая строка
            sheet.createRow(2);

            // Данные
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

            // Итоговая строка
            Row totalRow = sheet.createRow(rowNum);
            totalRow.createCell(2).setCellValue("ИТОГО:");
            Cell totalCell = totalRow.createCell(3);
            totalCell.setCellValue(totalDeductions.doubleValue());
            totalCell.setCellStyle(moneyStyle);

            // Статистика
            Row statsRow1 = sheet.createRow(rowNum + 2);
            statsRow1.createCell(0).setCellValue("Статистика:");
            Row statsRow2 = sheet.createRow(rowNum + 3);
            statsRow2.createCell(0).setCellValue("Всего удержаний: " + deductionPayments.size() + " операций");
            Row statsRow3 = sheet.createRow(rowNum + 4);
            statsRow3.createCell(0).setCellValue("Общая сумма удержаний: " + String.format("%.2f", totalDeductions) + " руб.");
            Row statsRow4 = sheet.createRow(rowNum + 5);
            statsRow4.createCell(0).setCellValue("Количество сотрудников с удержаниями: " +
                    deductionPayments.stream().map(p -> p.getEmployee().getId()).distinct().count());

            // Авто-размер колонок
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            workbook.write(outputStream);
            return outputStream.toByteArray();
        }
    }
}