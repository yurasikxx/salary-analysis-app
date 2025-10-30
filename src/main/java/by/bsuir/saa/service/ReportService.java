package by.bsuir.saa.service;

import by.bsuir.saa.controller.AnalystController;
import by.bsuir.saa.entity.*;
import by.bsuir.saa.repository.PaymentRepository;
import by.bsuir.saa.repository.SalaryPaymentRepository;
import com.itextpdf.text.*;
import com.itextpdf.text.Font;
import com.itextpdf.text.pdf.BaseFont;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfWriter;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.*;

@Service
public class ReportService {

    private final PaymentRepository paymentRepository;
    private final SalaryPaymentRepository salaryPaymentRepository;
    private final EmployeeService employeeService;
    private final DepartmentService departmentService;
    private final AnalyticsService analyticsService;

    private BaseFont russianBaseFont;

    public ReportService(PaymentRepository paymentRepository,
                         SalaryPaymentRepository salaryPaymentRepository,
                         EmployeeService employeeService,
                         DepartmentService departmentService,
                         AnalyticsService analyticsService) {
        this.paymentRepository = paymentRepository;
        this.salaryPaymentRepository = salaryPaymentRepository;
        this.employeeService = employeeService;
        this.departmentService = departmentService;
        this.analyticsService = analyticsService;
        initializeRussianFont();
    }

    private void initializeRussianFont() {
        try {
            ClassPathResource fontResource = new ClassPathResource("fonts/arial.ttf");
            if (fontResource.exists()) {
                russianBaseFont = BaseFont.createFont(fontResource.getPath(), BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
            } else {
                russianBaseFont = BaseFont.createFont("c:/windows/fonts/arial.ttf", BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
            }
        } catch (Exception e) {
            try {
                russianBaseFont = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.WINANSI, BaseFont.EMBEDDED);
            } catch (Exception ex) {
                throw new RuntimeException("❌ Cannot initialize fonts", ex);
            }
        }
    }

    private Font createRussianFont(int size, int style) {
        return new Font(russianBaseFont, size, style);
    }

    private Font createTitleFont() {
        return createRussianFont(16, Font.BOLD);
    }

    private Font createHeaderFont() {
        return createRussianFont(12, Font.BOLD);
    }

    private Font createNormalFont() {
        return createRussianFont(10, Font.NORMAL);
    }

    private Font createBoldFont() {
        return createRussianFont(10, Font.BOLD);
    }

    public byte[] generatePayslipPdf(Integer employeeId, Integer month, Integer year) throws DocumentException {
        Employee employee = employeeService.getEmployeeById(employeeId)
                .orElseThrow(() -> new RuntimeException("Сотрудник не найден"));

        List<Payment> payments = paymentRepository.findByEmployeeAndMonthAndYear(employee, month, year);

        Document document = new Document(PageSize.A4, 50, 50, 50, 50);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PdfWriter.getInstance(document, baos);

        document.open();

        Font titleFont = createTitleFont();
        Font headerFont = createHeaderFont();
        Font normalFont = createNormalFont();
        Font boldFont = createBoldFont();

        Paragraph title = new Paragraph("Расчетный листок", titleFont);
        title.setAlignment(Element.ALIGN_CENTER);
        title.setSpacingAfter(20);
        document.add(title);

        addKeyValue(document, "Сотрудник:", employee.getFullName(), boldFont, normalFont);
        addKeyValue(document, "Должность:", employee.getPosition().getTitle(), boldFont, normalFont);
        addKeyValue(document, "Подразделение:", employee.getDepartment().getName(), boldFont, normalFont);
        addKeyValue(document, "Период:", getRussianMonthName(month) + " " + year, boldFont, normalFont);
        addKeyValue(document, "Табельный номер:", employee.getId().toString(), boldFont, normalFont);
        addKeyValue(document, "Дата формирования:",
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")), boldFont, normalFont);

        document.add(new Paragraph(" "));

        Paragraph accrualsTitle = new Paragraph("Начисления", headerFont);
        accrualsTitle.setSpacingAfter(10);
        document.add(accrualsTitle);

        PdfPTable accrualsTable = createTable(new float[]{3, 2, 3});

        accrualsTable.addCell(createCell("Вид начисления", headerFont, Element.ALIGN_CENTER));
        accrualsTable.addCell(createCell("Сумма (руб.)", headerFont, Element.ALIGN_CENTER));
        accrualsTable.addCell(createCell("Основание", headerFont, Element.ALIGN_CENTER));

        BigDecimal totalAccruals = BigDecimal.ZERO;
        List<Payment> accruals = payments.stream()
                .filter(p -> "accrual".equals(p.getPaymentType().getCategory()))
                .toList();

        for (Payment payment : accruals) {
            accrualsTable.addCell(createCell(payment.getPaymentType().getName(), normalFont, Element.ALIGN_LEFT));
            accrualsTable.addCell(createCell(formatMoney(payment.getAmount()), normalFont, Element.ALIGN_RIGHT));
            accrualsTable.addCell(createCell(
                    payment.getDescription() != null ? payment.getDescription() : "-",
                    normalFont, Element.ALIGN_LEFT
            ));
            totalAccruals = totalAccruals.add(payment.getAmount());
        }

        if (accruals.isEmpty()) {
            accrualsTable.addCell(createCell("Нет начислений", normalFont, Element.ALIGN_CENTER));
            accrualsTable.addCell(createCell("", normalFont, Element.ALIGN_CENTER));
            accrualsTable.addCell(createCell("", normalFont, Element.ALIGN_CENTER));
        }

        accrualsTable.addCell(createCell("ИТОГО начислено:", boldFont, Element.ALIGN_RIGHT));
        accrualsTable.addCell(createCell(formatMoney(totalAccruals), boldFont, Element.ALIGN_RIGHT));
        accrualsTable.addCell(createCell("", normalFont, Element.ALIGN_LEFT));

        document.add(accrualsTable);
        document.add(new Paragraph(" "));

        Paragraph deductionsTitle = new Paragraph("Удержания", headerFont);
        deductionsTitle.setSpacingAfter(10);
        document.add(deductionsTitle);

        PdfPTable deductionsTable = createTable(new float[]{3, 2, 3});

        deductionsTable.addCell(createCell("Вид удержания", headerFont, Element.ALIGN_CENTER));
        deductionsTable.addCell(createCell("Сумма (руб.)", headerFont, Element.ALIGN_CENTER));
        deductionsTable.addCell(createCell("Основание", headerFont, Element.ALIGN_CENTER));

        BigDecimal totalDeductions = BigDecimal.ZERO;
        List<Payment> deductions = payments.stream()
                .filter(p -> "deduction".equals(p.getPaymentType().getCategory()))
                .toList();

        for (Payment payment : deductions) {
            deductionsTable.addCell(createCell(payment.getPaymentType().getName(), normalFont, Element.ALIGN_LEFT));
            deductionsTable.addCell(createCell(formatMoney(payment.getAmount().abs()), normalFont, Element.ALIGN_RIGHT));
            deductionsTable.addCell(createCell(
                    payment.getDescription() != null ? payment.getDescription() : "-",
                    normalFont, Element.ALIGN_LEFT
            ));
            totalDeductions = totalDeductions.add(payment.getAmount().abs());
        }

        if (deductions.isEmpty()) {
            deductionsTable.addCell(createCell("Нет удержаний", normalFont, Element.ALIGN_CENTER));
            deductionsTable.addCell(createCell("", normalFont, Element.ALIGN_CENTER));
            deductionsTable.addCell(createCell("", normalFont, Element.ALIGN_CENTER));
        }

        deductionsTable.addCell(createCell("ИТОГО удержано:", boldFont, Element.ALIGN_RIGHT));
        deductionsTable.addCell(createCell(formatMoney(totalDeductions), boldFont, Element.ALIGN_RIGHT));
        deductionsTable.addCell(createCell("", normalFont, Element.ALIGN_LEFT));

        document.add(deductionsTable);
        document.add(new Paragraph(" "));

        BigDecimal netSalary = totalAccruals.subtract(totalDeductions);
        Paragraph result = new Paragraph("К ВЫПЛАТЕ: " + formatMoney(netSalary) + " руб.", titleFont);
        result.setAlignment(Element.ALIGN_CENTER);
        result.setSpacingBefore(20);
        document.add(result);

        document.add(new Paragraph(" "));
        PdfPTable signatureTable = createTable(new float[]{1, 1});
        signatureTable.addCell(createCell("Бухгалтер: _________________", normalFont, Element.ALIGN_LEFT));
        signatureTable.addCell(createCell("Сотрудник: _________________", normalFont, Element.ALIGN_RIGHT));
        document.add(signatureTable);

        document.close();
        return baos.toByteArray();
    }

    public byte[] generateSalaryStatementPdf(Integer departmentId, Integer month, Integer year) throws DocumentException {
        Department department = departmentId != null ?
                departmentService.getDepartmentById(departmentId)
                        .orElseThrow(() -> new RuntimeException("Подразделение не найдено")) : null;

        List<Employee> employees = departmentId != null ?
                employeeService.getActiveEmployees().stream()
                        .filter(e -> e.getDepartment().getId().equals(departmentId))
                        .toList() :
                employeeService.getActiveEmployees();

        Document document = new Document(PageSize.A4.rotate(), 50, 50, 50, 50);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PdfWriter.getInstance(document, baos);

        document.open();

        Font titleFont = createTitleFont();
        Font headerFont = createHeaderFont();
        Font normalFont = createNormalFont();
        Font boldFont = createBoldFont();

        String titleText = "Зарплатная ведомость" +
                (department != null ? " - " + department.getName() : " - Все подразделения");
        Paragraph title = new Paragraph(titleText, titleFont);
        title.setAlignment(Element.ALIGN_CENTER);
        title.setSpacingAfter(20);
        document.add(title);

        addKeyValue(document, "Период:", getRussianMonthName(month) + " " + year, boldFont, normalFont);
        addKeyValue(document, "Дата формирования:",
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")), boldFont, normalFont);
        addKeyValue(document, "Количество сотрудников:", String.valueOf(employees.size()), boldFont, normalFont);

        document.add(new Paragraph(" "));

        PdfPTable table = createTable(new float[]{4, 3, 2, 2, 2, 2});

        table.addCell(createCell("ФИО сотрудника", headerFont, Element.ALIGN_CENTER));
        table.addCell(createCell("Должность", headerFont, Element.ALIGN_CENTER));
        table.addCell(createCell("Начисления", headerFont, Element.ALIGN_CENTER));
        table.addCell(createCell("Удержания", headerFont, Element.ALIGN_CENTER));
        table.addCell(createCell("К выплате", headerFont, Element.ALIGN_CENTER));
        table.addCell(createCell("Подпись", headerFont, Element.ALIGN_CENTER));

        BigDecimal totalAccruals = BigDecimal.ZERO;
        BigDecimal totalDeductions = BigDecimal.ZERO;
        BigDecimal totalNetSalary = BigDecimal.ZERO;

        for (Employee employee : employees) {
            List<Payment> payments = paymentRepository.findByEmployeeAndMonthAndYear(employee, month, year);

            BigDecimal accruals = payments.stream()
                    .filter(p -> "accrual".equals(p.getPaymentType().getCategory()))
                    .map(Payment::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal deductions = payments.stream()
                    .filter(p -> "deduction".equals(p.getPaymentType().getCategory()))
                    .map(p -> p.getAmount().abs())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal netSalary = accruals.subtract(deductions);

            table.addCell(createCell(employee.getFullName(), normalFont, Element.ALIGN_LEFT));
            table.addCell(createCell(employee.getPosition().getTitle(), normalFont, Element.ALIGN_LEFT));
            table.addCell(createCell(formatMoney(accruals), normalFont, Element.ALIGN_RIGHT));
            table.addCell(createCell(formatMoney(deductions), normalFont, Element.ALIGN_RIGHT));
            table.addCell(createCell(formatMoney(netSalary), normalFont, Element.ALIGN_RIGHT));
            table.addCell(createCell("__________", normalFont, Element.ALIGN_CENTER));

            totalAccruals = totalAccruals.add(accruals);
            totalDeductions = totalDeductions.add(deductions);
            totalNetSalary = totalNetSalary.add(netSalary);
        }

        table.addCell(createCell("ВСЕГО:", headerFont, Element.ALIGN_RIGHT));
        table.addCell(createCell("", headerFont, Element.ALIGN_CENTER));
        table.addCell(createCell(formatMoney(totalAccruals), headerFont, Element.ALIGN_RIGHT));
        table.addCell(createCell(formatMoney(totalDeductions), headerFont, Element.ALIGN_RIGHT));
        table.addCell(createCell(formatMoney(totalNetSalary), headerFont, Element.ALIGN_RIGHT));
        table.addCell(createCell("", headerFont, Element.ALIGN_CENTER));

        document.add(table);

        document.add(new Paragraph(" "));
        PdfPTable signatureTable = createTable(new float[]{1, 1});
        signatureTable.addCell(createCell("Главный бухгалтер: _________________", normalFont, Element.ALIGN_LEFT));
        signatureTable.addCell(createCell("Руководитель: _________________", normalFont, Element.ALIGN_RIGHT));
        document.add(signatureTable);

        document.close();
        return baos.toByteArray();
    }

    public byte[] generateSalaryReportExcel(Integer month, Integer year) throws IOException {
        List<Employee> employees = employeeService.getActiveEmployees();

        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Зарплатная ведомость " + getRussianMonthName(month) + " " + year);

        CellStyle headerStyle = createEnhancedHeaderStyle(workbook);
        CellStyle moneyStyle = createEnhancedMoneyStyle(workbook);
        CellStyle normalStyle = createEnhancedNormalStyle(workbook);
        CellStyle titleStyle = createTitleStyle(workbook);
        CellStyle totalStyle = createTotalStyle(workbook);

        sheet.setColumnWidth(0, 8000);
        sheet.setColumnWidth(1, 6000);
        sheet.setColumnWidth(2, 5000);
        sheet.setColumnWidth(3, 5000);
        sheet.setColumnWidth(4, 5000);
        sheet.setColumnWidth(5, 5000);

        Row titleRow = sheet.createRow(0);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue("ЗАРПЛАТНАЯ ВЕДОМОСТЬ ЗА " + getRussianMonthName(month).toUpperCase() + " " + year);
        titleCell.setCellStyle(titleStyle);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 5));

        Row paramsRow = sheet.createRow(2);
        paramsRow.createCell(0).setCellValue("Дата формирования:");
        paramsRow.createCell(1).setCellValue(LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")));
        paramsRow.createCell(4).setCellValue("Количество сотрудников:");
        paramsRow.createCell(5).setCellValue(employees.size());

        Row headerRow = sheet.createRow(4);
        String[] headers = {"ФИО сотрудника", "Должность", "Подразделение", "Начисления", "Удержания", "К выплате"};
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        int rowNum = 5;
        BigDecimal totalAccruals = BigDecimal.ZERO;
        BigDecimal totalDeductions = BigDecimal.ZERO;
        BigDecimal totalNetSalary = BigDecimal.ZERO;

        for (Employee employee : employees) {
            List<Payment> payments = paymentRepository.findByEmployeeAndMonthAndYear(employee, month, year);

            BigDecimal accruals = payments.stream()
                    .filter(p -> "accrual".equals(p.getPaymentType().getCategory()))
                    .map(Payment::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal deductions = payments.stream()
                    .filter(p -> "deduction".equals(p.getPaymentType().getCategory()))
                    .map(p -> p.getAmount().abs())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal netSalary = accruals.subtract(deductions);

            Row row = sheet.createRow(rowNum++);

            row.createCell(0).setCellValue(employee.getFullName());
            row.getCell(0).setCellStyle(normalStyle);

            row.createCell(1).setCellValue(employee.getPosition().getTitle());
            row.getCell(1).setCellStyle(normalStyle);

            row.createCell(2).setCellValue(employee.getDepartment().getName());
            row.getCell(2).setCellStyle(normalStyle);

            Cell accrualsCell = row.createCell(3);
            accrualsCell.setCellValue(accruals.doubleValue());
            accrualsCell.setCellStyle(moneyStyle);

            Cell deductionsCell = row.createCell(4);
            deductionsCell.setCellValue(deductions.doubleValue());
            deductionsCell.setCellStyle(moneyStyle);

            Cell netSalaryCell = row.createCell(5);
            netSalaryCell.setCellValue(netSalary.doubleValue());
            netSalaryCell.setCellStyle(moneyStyle);

            totalAccruals = totalAccruals.add(accruals);
            totalDeductions = totalDeductions.add(deductions);
            totalNetSalary = totalNetSalary.add(netSalary);
        }

        Row totalRow = sheet.createRow(rowNum);
        totalRow.createCell(0).setCellValue("ВСЕГО:");
        totalRow.getCell(0).setCellStyle(totalStyle);

        totalRow.createCell(1).setCellValue("");
        totalRow.getCell(1).setCellStyle(totalStyle);

        totalRow.createCell(2).setCellValue("");
        totalRow.getCell(2).setCellStyle(totalStyle);

        Cell totalAccrualsCell = totalRow.createCell(3);
        totalAccrualsCell.setCellValue(totalAccruals.doubleValue());
        totalAccrualsCell.setCellStyle(totalStyle);

        Cell totalDeductionsCell = totalRow.createCell(4);
        totalDeductionsCell.setCellValue(totalDeductions.doubleValue());
        totalDeductionsCell.setCellStyle(totalStyle);

        Cell totalNetSalaryCell = totalRow.createCell(5);
        totalNetSalaryCell.setCellValue(totalNetSalary.doubleValue());
        totalNetSalaryCell.setCellStyle(totalStyle);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        workbook.write(baos);
        workbook.close();

        return baos.toByteArray();
    }

    private void addKeyValue(Document document, String key, String value, Font keyFont, Font valueFont) throws DocumentException {
        Paragraph p = new Paragraph();
        p.add(new Chunk(key + " ", keyFont));
        p.add(new Chunk(value, valueFont));
        p.setSpacingAfter(5);
        document.add(p);
    }

    private PdfPTable createTable(float[] relativeWidths) {
        PdfPTable table = new PdfPTable(relativeWidths);
        table.setWidthPercentage(100);
        table.setSpacingBefore(10);
        table.setSpacingAfter(10);
        return table;
    }

    private PdfPCell createCell(String content, Font font, int alignment) {
        PdfPCell cell = new PdfPCell(new Phrase(content, font));
        cell.setHorizontalAlignment(alignment);
        cell.setPadding(5);
        cell.setBorder(PdfPCell.BOTTOM | PdfPCell.TOP | PdfPCell.LEFT | PdfPCell.RIGHT);
        return cell;
    }

    private String formatMoney(BigDecimal amount) {
        return String.format("%,.2f", amount).replace(',', ' ');
    }

    private String getRussianMonthName(int month) {
        String[] months = {"Январь", "Февраль", "Март", "Апрель", "Май", "Июнь",
                "Июль", "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь"};
        return months[month - 1];
    }

    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        org.apache.poi.ss.usermodel.Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
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

    public byte[] generateSalaryTrendsReport(Integer monthsBack, Integer departmentId) throws IOException {
        List<AnalystController.SalaryTrendData> trendData = analyticsService.getSalaryTrends(monthsBack, departmentId);

        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Динамика ЗП");

        CellStyle headerStyle = createEnhancedHeaderStyle(workbook);
        CellStyle moneyStyle = createEnhancedMoneyStyle(workbook);
        CellStyle percentStyle = createEnhancedPercentStyle(workbook);
        CellStyle titleStyle = createTitleStyle(workbook);

        sheet.setColumnWidth(0, 6000);
        sheet.setColumnWidth(1, 5000);
        sheet.setColumnWidth(2, 6000);
        sheet.setColumnWidth(3, 4000);
        sheet.setColumnWidth(4, 5000);
        sheet.setColumnWidth(5, 5000);
        sheet.setColumnWidth(6, 5000);

        Row titleRow = sheet.createRow(0);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue("ОТЧЕТ ПО ДИНАМИКЕ ЗАРАБОТНОЙ ПЛАТЫ");
        titleCell.setCellStyle(titleStyle);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 6));

        Row paramsRow1 = sheet.createRow(2);
        paramsRow1.createCell(0).setCellValue("Период анализа:");
        paramsRow1.createCell(1).setCellValue(monthsBack + " месяцев");

        Row paramsRow2 = sheet.createRow(3);
        paramsRow2.createCell(0).setCellValue("Подразделение:");
        if (departmentId != null) {
            Department department = departmentService.getDepartmentById(departmentId).orElse(null);
            paramsRow2.createCell(1).setCellValue(department != null ? department.getName() : "Все подразделения");
        } else {
            paramsRow2.createCell(1).setCellValue("Все подразделения");
        }

        Row paramsRow3 = sheet.createRow(4);
        paramsRow3.createCell(0).setCellValue("Дата формирования:");
        paramsRow3.createCell(1).setCellValue(LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")));

        Row headerRow = sheet.createRow(6);
        String[] headers = {"Период", "Средняя ЗП", "ФОТ", "Сотрудников", "Мин. ЗП", "Макс. ЗП", "Изменение"};
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        int rowNum = 7;
        BigDecimal previousSalary = null;

        for (AnalystController.SalaryTrendData data : trendData) {
            Row row = sheet.createRow(rowNum++);

            row.createCell(0).setCellValue(data.getPeriod());

            Cell avgCell = row.createCell(1);
            avgCell.setCellValue(data.getAverageSalary().doubleValue());
            avgCell.setCellStyle(moneyStyle);

            Cell fotCell = row.createCell(2);
            fotCell.setCellValue(data.getTotalFOT().doubleValue());
            fotCell.setCellStyle(moneyStyle);

            row.createCell(3).setCellValue(data.getEmployeeCount());

            Cell minCell = row.createCell(4);
            minCell.setCellValue(data.getMinSalary().doubleValue());
            minCell.setCellStyle(moneyStyle);

            Cell maxCell = row.createCell(5);
            maxCell.setCellValue(data.getMaxSalary().doubleValue());
            maxCell.setCellStyle(moneyStyle);

            if (previousSalary != null && previousSalary.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal change = data.getAverageSalary().subtract(previousSalary)
                        .divide(previousSalary, 4, java.math.RoundingMode.HALF_UP)
                        .multiply(new BigDecimal("100"));
                Cell changeCell = row.createCell(6);
                changeCell.setCellValue(change.doubleValue() / 100);

                CellStyle changeStyle = workbook.createCellStyle();
                changeStyle.cloneStyleFrom(percentStyle);
                if (change.compareTo(BigDecimal.ZERO) > 0) {
                    changeStyle.setFillForegroundColor(IndexedColors.LIGHT_GREEN.getIndex());
                    changeStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
                } else if (change.compareTo(BigDecimal.ZERO) < 0) {
                    changeStyle.setFillForegroundColor(IndexedColors.LIGHT_ORANGE.getIndex());
                    changeStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
                }
                changeCell.setCellStyle(changeStyle);
            } else {
                row.createCell(6).setCellValue("-");
            }

            previousSalary = data.getAverageSalary();
        }

        if (!trendData.isEmpty()) {
            Row totalRow = sheet.createRow(rowNum++);
            totalRow.createCell(0).setCellValue("ИТОГО:");

            CellStyle totalStyle = createTotalStyle(workbook);
            totalRow.getCell(0).setCellStyle(totalStyle);

            BigDecimal totalFOT = trendData.stream()
                    .map(AnalystController.SalaryTrendData::getTotalFOT)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            Cell totalFotCell = totalRow.createCell(2);
            totalFotCell.setCellValue(totalFOT.doubleValue());
            totalFotCell.setCellStyle(totalStyle);

            int totalEmployees = trendData.stream()
                    .mapToInt(AnalystController.SalaryTrendData::getEmployeeCount)
                    .sum();
            totalRow.createCell(3).setCellValue(totalEmployees);
            totalRow.getCell(3).setCellStyle(totalStyle);

            if (trendData.size() > 1) {
                BigDecimal firstSalary = trendData.get(0).getAverageSalary();
                BigDecimal lastSalary = trendData.get(trendData.size() - 1).getAverageSalary();
                BigDecimal avgChange = lastSalary.subtract(firstSalary)
                        .divide(firstSalary, 4, java.math.RoundingMode.HALF_UP)
                        .multiply(new BigDecimal("100"));
                Cell changeCell = totalRow.createCell(6);
                changeCell.setCellValue(avgChange.doubleValue() / 100);
                changeCell.setCellStyle(totalStyle);
            }
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        workbook.write(baos);
        workbook.close();

        return baos.toByteArray();
    }

    public byte[] generatePositionAnalysisReport(Integer month, Integer year) throws IOException {
        List<AnalystController.PositionStats> positionStats = analyticsService.getPositionStats(month, year);

        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Анализ по должностям");

        CellStyle headerStyle = createEnhancedHeaderStyle(workbook);
        CellStyle moneyStyle = createEnhancedMoneyStyle(workbook);
        CellStyle titleStyle = createTitleStyle(workbook);
        CellStyle totalStyle = createTotalStyle(workbook);

        sheet.setColumnWidth(0, 8000);
        sheet.setColumnWidth(1, 5000);
        sheet.setColumnWidth(2, 5000);
        sheet.setColumnWidth(3, 5000);
        sheet.setColumnWidth(4, 4000);
        sheet.setColumnWidth(5, 6000);
        sheet.setColumnWidth(6, 5000);

        Row titleRow = sheet.createRow(0);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue("АНАЛИЗ ЗАРАБОТНОЙ ПЛАТЫ ПО ДОЛЖНОСТЯМ ЗА " + getRussianMonthName(month).toUpperCase() + " " + year);
        titleCell.setCellStyle(titleStyle);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 6));

        Row paramsRow = sheet.createRow(2);
        paramsRow.createCell(0).setCellValue("Дата формирования:");
        paramsRow.createCell(1).setCellValue(LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")));

        Row headerRow = sheet.createRow(4);
        String[] headers = {"Должность", "Средняя ЗП", "Мин. ЗП", "Макс. ЗП", "Сотрудников", "ФОТ должности", "Разброс ЗП"};
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        int rowNum = 5;
        for (AnalystController.PositionStats stat : positionStats) {
            Row row = sheet.createRow(rowNum++);

            row.createCell(0).setCellValue(stat.getPositionTitle());

            Cell avgCell = row.createCell(1);
            avgCell.setCellValue(stat.getAverageSalary().doubleValue());
            avgCell.setCellStyle(moneyStyle);

            Cell minCell = row.createCell(2);
            minCell.setCellValue(stat.getMinSalary().doubleValue());
            minCell.setCellStyle(moneyStyle);

            Cell maxCell = row.createCell(3);
            maxCell.setCellValue(stat.getMaxSalary().doubleValue());
            maxCell.setCellStyle(moneyStyle);

            row.createCell(4).setCellValue(stat.getEmployeeCount());

            Cell fotCell = row.createCell(5);
            fotCell.setCellValue(stat.getTotalFOT().doubleValue());
            fotCell.setCellStyle(moneyStyle);

            BigDecimal spread = stat.getMaxSalary().subtract(stat.getMinSalary());
            Cell spreadCell = row.createCell(6);
            spreadCell.setCellValue(spread.doubleValue());
            spreadCell.setCellStyle(moneyStyle);
        }

        if (!positionStats.isEmpty()) {
            Row totalRow = sheet.createRow(rowNum++);
            totalRow.createCell(0).setCellValue("ИТОГО:");
            totalRow.getCell(0).setCellStyle(totalStyle);

            BigDecimal totalFOT = positionStats.stream()
                    .map(AnalystController.PositionStats::getTotalFOT)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            Cell totalFotCell = totalRow.createCell(5);
            totalFotCell.setCellValue(totalFOT.doubleValue());
            totalFotCell.setCellStyle(totalStyle);

            long totalEmployees = positionStats.stream()
                    .mapToLong(AnalystController.PositionStats::getEmployeeCount)
                    .sum();
            totalRow.createCell(4).setCellValue(totalEmployees);
            totalRow.getCell(4).setCellStyle(totalStyle);
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        workbook.write(baos);
        workbook.close();

        return baos.toByteArray();
    }

    public byte[] generateDepartmentAnalysisExcel(Integer month, Integer year) throws IOException {
        List<AnalystController.DepartmentStats> departmentStats = analyticsService.calculateDepartmentStats(month, year);
        BigDecimal totalCompanyFOT = analyticsService.getTotalCompanyFOT(month, year);

        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Анализ по подразделениям");

        CellStyle headerStyle = createEnhancedHeaderStyle(workbook);
        CellStyle moneyStyle = createEnhancedMoneyStyle(workbook);
        CellStyle titleStyle = createTitleStyle(workbook);
        CellStyle totalStyle = createTotalStyle(workbook);

        sheet.setColumnWidth(0, 8000);
        sheet.setColumnWidth(1, 5000);
        sheet.setColumnWidth(2, 6000);
        sheet.setColumnWidth(3, 4000);
        sheet.setColumnWidth(4, 5000);
        sheet.setColumnWidth(5, 5000);
        sheet.setColumnWidth(6, 4000);

        Row titleRow = sheet.createRow(0);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue("АНАЛИТИЧЕСКИЙ ОТЧЕТ ПО ПОДРАЗДЕЛЕНИЯМ ЗА " + getRussianMonthName(month).toUpperCase() + " " + year);
        titleCell.setCellStyle(titleStyle);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 6));

        Row paramsRow = sheet.createRow(2);
        paramsRow.createCell(0).setCellValue("Дата формирования:");
        paramsRow.createCell(1).setCellValue(LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")));

        Row summaryRow = sheet.createRow(4);
        summaryRow.createCell(0).setCellValue("Общий ФОТ предприятия:");
        Cell totalFotCell = summaryRow.createCell(1);
        totalFotCell.setCellValue(totalCompanyFOT.doubleValue());
        totalFotCell.setCellStyle(moneyStyle);

        Row headerRow = sheet.createRow(6);
        String[] headers = {"Подразделение", "Средняя ЗП", "ФОТ подразделения", "Сотрудников", "Мин. ЗП", "Макс. ЗП", "Доля в ФОТ"};
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        int rowNum = 7;
        for (AnalystController.DepartmentStats stat : departmentStats) {
            Row row = sheet.createRow(rowNum++);

            row.createCell(0).setCellValue(stat.getDepartmentName());

            Cell avgCell = row.createCell(1);
            avgCell.setCellValue(stat.getAverageSalary().doubleValue());
            avgCell.setCellStyle(moneyStyle);

            Cell fotCell = row.createCell(2);
            fotCell.setCellValue(stat.getTotalFOT().doubleValue());
            fotCell.setCellStyle(moneyStyle);

            row.createCell(3).setCellValue(stat.getEmployeeCount());

            Cell minCell = row.createCell(4);
            minCell.setCellValue(stat.getMinSalary().doubleValue());
            minCell.setCellStyle(moneyStyle);

            Cell maxCell = row.createCell(5);
            maxCell.setCellValue(stat.getMaxSalary().doubleValue());
            maxCell.setCellStyle(moneyStyle);

            BigDecimal share = totalCompanyFOT.compareTo(BigDecimal.ZERO) > 0 ?
                    stat.getTotalFOT().divide(totalCompanyFOT, 4, java.math.RoundingMode.HALF_UP)
                            .multiply(new BigDecimal("100")) : BigDecimal.ZERO;
            Cell shareCell = row.createCell(6);
            shareCell.setCellValue(share.doubleValue() / 100);
            shareCell.setCellStyle(createEnhancedPercentStyle(workbook));
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        workbook.write(baos);
        workbook.close();

        return baos.toByteArray();
    }

    public byte[] generateDepartmentAnalysisReport(Integer month, Integer year) throws DocumentException {
        List<AnalystController.DepartmentStats> departmentStats = analyticsService.calculateDepartmentStats(month, year);
        BigDecimal totalCompanyFOT = analyticsService.getTotalCompanyFOT(month, year);

        Document document = new Document(PageSize.A4.rotate(), 50, 50, 50, 50);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PdfWriter.getInstance(document, baos);

        document.open();

        Font titleFont = createTitleFont();
        Font headerFont = createHeaderFont();
        Font normalFont = createNormalFont();
        Font boldFont = createBoldFont();

        Paragraph title = new Paragraph("Аналитический отчет по подразделениям", titleFont);
        title.setAlignment(Element.ALIGN_CENTER);
        title.setSpacingAfter(20);
        document.add(title);

        addKeyValue(document, "Период:", getRussianMonthName(month) + " " + year, boldFont, normalFont);
        addKeyValue(document, "Дата формирования:",
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")), boldFont, normalFont);

        document.add(new Paragraph(" "));

        Paragraph summary = new Paragraph("Сводная информация по предприятию", headerFont);
        summary.setSpacingAfter(10);
        document.add(summary);

        PdfPTable summaryTable = createTable(new float[]{2, 1, 1, 1});
        summaryTable.addCell(createCell("Показатель", headerFont, Element.ALIGN_CENTER));
        summaryTable.addCell(createCell("Значение", headerFont, Element.ALIGN_CENTER));
        summaryTable.addCell(createCell("Ед. изм.", headerFont, Element.ALIGN_CENTER));
        summaryTable.addCell(createCell("Примечание", headerFont, Element.ALIGN_CENTER));

        long totalEmployees = departmentStats.stream()
                .mapToLong(AnalystController.DepartmentStats::getEmployeeCount)
                .sum();
        BigDecimal avgSalary = totalEmployees > 0 ?
                totalCompanyFOT.divide(new BigDecimal(totalEmployees), 2, java.math.RoundingMode.HALF_UP) :
                BigDecimal.ZERO;

        addSummaryRow(summaryTable, "Общий ФОТ предприятия", totalCompanyFOT, "руб.", "Фонд оплаты труда", normalFont);
        addSummaryRow(summaryTable, "Средняя заработная плата", avgSalary, "руб.", "По предприятию", normalFont);
        addSummaryRow(summaryTable, "Количество сотрудников", new BigDecimal(totalEmployees), "чел.", "Всего по предприятию", normalFont);
        addSummaryRow(summaryTable, "Количество подразделений", new BigDecimal(departmentStats.size()), "ед.", "С рассчитанной ЗП", normalFont);

        document.add(summaryTable);
        document.add(new Paragraph(" "));

        Paragraph detailTitle = new Paragraph("Детальный анализ по подразделениям", headerFont);
        detailTitle.setSpacingAfter(10);
        document.add(detailTitle);

        PdfPTable detailTable = createTable(new float[]{3, 2, 2, 1, 2, 2, 2});

        detailTable.addCell(createCell("Подразделение", headerFont, Element.ALIGN_CENTER));
        detailTable.addCell(createCell("Средняя ЗП", headerFont, Element.ALIGN_CENTER));
        detailTable.addCell(createCell("ФОТ подразделения", headerFont, Element.ALIGN_CENTER));
        detailTable.addCell(createCell("Сотрудников", headerFont, Element.ALIGN_CENTER));
        detailTable.addCell(createCell("Мин. ЗП", headerFont, Element.ALIGN_CENTER));
        detailTable.addCell(createCell("Макс. ЗП", headerFont, Element.ALIGN_CENTER));
        detailTable.addCell(createCell("Доля в ФОТ", headerFont, Element.ALIGN_CENTER));

        for (AnalystController.DepartmentStats stat : departmentStats) {
            detailTable.addCell(createCell(stat.getDepartmentName(), normalFont, Element.ALIGN_LEFT));
            detailTable.addCell(createCell(formatMoney(stat.getAverageSalary()), normalFont, Element.ALIGN_RIGHT));
            detailTable.addCell(createCell(formatMoney(stat.getTotalFOT()), normalFont, Element.ALIGN_RIGHT));
            detailTable.addCell(createCell(String.valueOf(stat.getEmployeeCount()), normalFont, Element.ALIGN_CENTER));
            detailTable.addCell(createCell(formatMoney(stat.getMinSalary()), normalFont, Element.ALIGN_RIGHT));
            detailTable.addCell(createCell(formatMoney(stat.getMaxSalary()), normalFont, Element.ALIGN_RIGHT));

            BigDecimal share = totalCompanyFOT.compareTo(BigDecimal.ZERO) > 0 ?
                    stat.getTotalFOT().divide(totalCompanyFOT, 4, java.math.RoundingMode.HALF_UP)
                            .multiply(new BigDecimal("100")) : BigDecimal.ZERO;
            detailTable.addCell(createCell(String.format("%.1f%%", share), normalFont, Element.ALIGN_RIGHT));
        }

        document.add(detailTable);

        document.add(new Paragraph(" "));
        Paragraph analysis = new Paragraph("Аналитические выводы", headerFont);
        analysis.setSpacingAfter(10);
        document.add(analysis);

        if (!departmentStats.isEmpty()) {
            AnalystController.DepartmentStats maxSalaryDept = departmentStats.get(0);
            AnalystController.DepartmentStats minSalaryDept = departmentStats.get(departmentStats.size() - 1);

            List<String> conclusions = new ArrayList<>();
            conclusions.add(String.format("Наибольшая средняя заработная плата наблюдается в подразделении '%s' - %s руб.",
                    maxSalaryDept.getDepartmentName(), formatMoney(maxSalaryDept.getAverageSalary())));
            conclusions.add(String.format("Наименьшая средняя заработная плата наблюдается в подразделении '%s' - %s руб.",
                    minSalaryDept.getDepartmentName(), formatMoney(minSalaryDept.getAverageSalary())));
            conclusions.add(String.format("Разница между максимальной и минимальной средней ЗП составляет %s руб.",
                    formatMoney(maxSalaryDept.getAverageSalary().subtract(minSalaryDept.getAverageSalary()))));

            for (String conclusion : conclusions) {
                Paragraph p = new Paragraph("• " + conclusion, normalFont);
                p.setSpacingAfter(5);
                document.add(p);
            }
        }

        document.close();
        return baos.toByteArray();
    }

    public byte[] generateSalaryTrendsPdf(Integer monthsBack, Integer departmentId) throws DocumentException {
        List<AnalystController.SalaryTrendData> trendData = analyticsService.getSalaryTrends(monthsBack, departmentId);

        Document document = new Document(PageSize.A4.rotate(), 50, 50, 50, 50);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PdfWriter.getInstance(document, baos);

        document.open();

        Font titleFont = createTitleFont();
        Font headerFont = createHeaderFont();
        Font normalFont = createNormalFont();
        Font boldFont = createBoldFont();

        Paragraph title = new Paragraph("Отчет по динамике заработной платы", titleFont);
        title.setAlignment(Element.ALIGN_CENTER);
        title.setSpacingAfter(20);
        document.add(title);

        addKeyValue(document, "Период анализа:", monthsBack + " месяцев", boldFont, normalFont);
        addKeyValue(document, "Подразделение:", departmentId != null ?
                departmentService.getDepartmentById(departmentId).map(Department::getName).orElse("Все подразделения") :
                "Все подразделения", boldFont, normalFont);
        addKeyValue(document, "Дата формирования:",
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")), boldFont, normalFont);

        document.add(new Paragraph(" "));

        PdfPTable table = createTable(new float[]{3, 2, 2, 2, 2, 2, 2});

        table.addCell(createCell("Период", headerFont, Element.ALIGN_CENTER));
        table.addCell(createCell("Средняя ЗП", headerFont, Element.ALIGN_CENTER));
        table.addCell(createCell("ФОТ", headerFont, Element.ALIGN_CENTER));
        table.addCell(createCell("Сотрудников", headerFont, Element.ALIGN_CENTER));
        table.addCell(createCell("Мин. ЗП", headerFont, Element.ALIGN_CENTER));
        table.addCell(createCell("Макс. ЗП", headerFont, Element.ALIGN_CENTER));
        table.addCell(createCell("Изменение", headerFont, Element.ALIGN_CENTER));

        BigDecimal previousSalary = null;
        BigDecimal totalFOT = BigDecimal.ZERO;
        int totalEmployees = 0;

        for (AnalystController.SalaryTrendData data : trendData) {
            table.addCell(createCell(data.getPeriod(), normalFont, Element.ALIGN_LEFT));
            table.addCell(createCell(formatMoney(data.getAverageSalary()), normalFont, Element.ALIGN_RIGHT));
            table.addCell(createCell(formatMoney(data.getTotalFOT()), normalFont, Element.ALIGN_RIGHT));
            table.addCell(createCell(String.valueOf(data.getEmployeeCount()), normalFont, Element.ALIGN_CENTER));
            table.addCell(createCell(formatMoney(data.getMinSalary()), normalFont, Element.ALIGN_RIGHT));
            table.addCell(createCell(formatMoney(data.getMaxSalary()), normalFont, Element.ALIGN_RIGHT));

            String changeText = "-";
            if (previousSalary != null && previousSalary.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal change = data.getAverageSalary().subtract(previousSalary)
                        .divide(previousSalary, 4, java.math.RoundingMode.HALF_UP)
                        .multiply(new BigDecimal("100"));
                changeText = String.format("%+.1f%%", change);
            }
            table.addCell(createCell(changeText, normalFont, Element.ALIGN_RIGHT));

            previousSalary = data.getAverageSalary();
            totalFOT = totalFOT.add(data.getTotalFOT());
            totalEmployees += data.getEmployeeCount();
        }

        if (!trendData.isEmpty()) {
            table.addCell(createCell("ИТОГО:", headerFont, Element.ALIGN_RIGHT));
            table.addCell(createCell("", headerFont, Element.ALIGN_CENTER));
            table.addCell(createCell(formatMoney(totalFOT), headerFont, Element.ALIGN_RIGHT));
            table.addCell(createCell(String.valueOf(totalEmployees), headerFont, Element.ALIGN_CENTER));
            table.addCell(createCell("", headerFont, Element.ALIGN_CENTER));
            table.addCell(createCell("", headerFont, Element.ALIGN_CENTER));
            table.addCell(createCell("", headerFont, Element.ALIGN_CENTER));
        }

        document.add(table);

        if (!trendData.isEmpty() && trendData.size() > 1) {
            document.add(new Paragraph(" "));
            Paragraph analysis = new Paragraph("Аналитические выводы", headerFont);
            analysis.setSpacingAfter(10);
            document.add(analysis);

            AnalystController.SalaryTrendData firstPeriod = trendData.get(0);
            AnalystController.SalaryTrendData lastPeriod = trendData.get(trendData.size() - 1);

            BigDecimal totalChange = lastPeriod.getAverageSalary().subtract(firstPeriod.getAverageSalary())
                    .divide(firstPeriod.getAverageSalary(), 4, java.math.RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"));

            List<String> conclusions = new ArrayList<>();
            conclusions.add(String.format("Общее изменение средней заработной платы за период: %+.1f%%", totalChange));
            conclusions.add(String.format("Начальная средняя ЗП: %s руб.", formatMoney(firstPeriod.getAverageSalary())));
            conclusions.add(String.format("Конечная средняя ЗП: %s руб.", formatMoney(lastPeriod.getAverageSalary())));
            conclusions.add(String.format("Общий ФОТ за период: %s руб.", formatMoney(totalFOT)));

            for (String conclusion : conclusions) {
                Paragraph p = new Paragraph("• " + conclusion, normalFont);
                p.setSpacingAfter(5);
                document.add(p);
            }
        }

        document.close();
        return baos.toByteArray();
    }

    public byte[] generatePositionAnalysisPdf(Integer month, Integer year) throws DocumentException {
        List<AnalystController.PositionStats> positionStats = analyticsService.getPositionStats(month, year);

        Document document = new Document(PageSize.A4.rotate(), 50, 50, 50, 50);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PdfWriter.getInstance(document, baos);

        document.open();

        Font titleFont = createTitleFont();
        Font headerFont = createHeaderFont();
        Font normalFont = createNormalFont();
        Font boldFont = createBoldFont();

        Paragraph title = new Paragraph("Анализ заработной платы по должностям", titleFont);
        title.setAlignment(Element.ALIGN_CENTER);
        title.setSpacingAfter(20);
        document.add(title);

        addKeyValue(document, "Период:", getRussianMonthName(month) + " " + year, boldFont, normalFont);
        addKeyValue(document, "Дата формирования:",
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")), boldFont, normalFont);

        document.add(new Paragraph(" "));

        PdfPTable table = createTable(new float[]{3, 2, 2, 2, 2, 2, 2});

        table.addCell(createCell("Должность", headerFont, Element.ALIGN_CENTER));
        table.addCell(createCell("Средняя ЗП", headerFont, Element.ALIGN_CENTER));
        table.addCell(createCell("Мин. ЗП", headerFont, Element.ALIGN_CENTER));
        table.addCell(createCell("Макс. ЗП", headerFont, Element.ALIGN_CENTER));
        table.addCell(createCell("Сотрудников", headerFont, Element.ALIGN_CENTER));
        table.addCell(createCell("ФОТ должности", headerFont, Element.ALIGN_CENTER));
        table.addCell(createCell("Разброс ЗП", headerFont, Element.ALIGN_CENTER));

        BigDecimal totalFOT = BigDecimal.ZERO;
        long totalEmployees = 0;

        for (AnalystController.PositionStats stat : positionStats) {
            table.addCell(createCell(stat.getPositionTitle(), normalFont, Element.ALIGN_LEFT));
            table.addCell(createCell(formatMoney(stat.getAverageSalary()), normalFont, Element.ALIGN_RIGHT));
            table.addCell(createCell(formatMoney(stat.getMinSalary()), normalFont, Element.ALIGN_RIGHT));
            table.addCell(createCell(formatMoney(stat.getMaxSalary()), normalFont, Element.ALIGN_RIGHT));
            table.addCell(createCell(String.valueOf(stat.getEmployeeCount()), normalFont, Element.ALIGN_CENTER));
            table.addCell(createCell(formatMoney(stat.getTotalFOT()), normalFont, Element.ALIGN_RIGHT));

            BigDecimal spread = stat.getMaxSalary().subtract(stat.getMinSalary());
            table.addCell(createCell(formatMoney(spread), normalFont, Element.ALIGN_RIGHT));

            totalFOT = totalFOT.add(stat.getTotalFOT());
            totalEmployees += stat.getEmployeeCount();
        }

        if (!positionStats.isEmpty()) {
            table.addCell(createCell("ИТОГО:", headerFont, Element.ALIGN_RIGHT));
            table.addCell(createCell("", headerFont, Element.ALIGN_CENTER));
            table.addCell(createCell("", headerFont, Element.ALIGN_CENTER));
            table.addCell(createCell("", headerFont, Element.ALIGN_CENTER));
            table.addCell(createCell(String.valueOf(totalEmployees), headerFont, Element.ALIGN_CENTER));
            table.addCell(createCell(formatMoney(totalFOT), headerFont, Element.ALIGN_RIGHT));
            table.addCell(createCell("", headerFont, Element.ALIGN_CENTER));
        }

        document.add(table);

        if (!positionStats.isEmpty()) {
            document.add(new Paragraph(" "));
            Paragraph analysis = new Paragraph("Аналитические выводы", headerFont);
            analysis.setSpacingAfter(10);
            document.add(analysis);

            AnalystController.PositionStats maxSalaryPos = positionStats.get(0);
            AnalystController.PositionStats minSalaryPos = positionStats.get(positionStats.size() - 1);

            List<String> conclusions = new ArrayList<>();
            conclusions.add(String.format("Наибольшая средняя заработная плата у должности '%s' - %s руб.",
                    maxSalaryPos.getPositionTitle(), formatMoney(maxSalaryPos.getAverageSalary())));
            conclusions.add(String.format("Наименьшая средняя заработная плата у должности '%s' - %s руб.",
                    minSalaryPos.getPositionTitle(), formatMoney(minSalaryPos.getAverageSalary())));
            conclusions.add(String.format("Разница между максимальной и минимальной средней ЗП составляет %s руб.",
                    formatMoney(maxSalaryPos.getAverageSalary().subtract(minSalaryPos.getAverageSalary()))));

            for (String conclusion : conclusions) {
                Paragraph p = new Paragraph("• " + conclusion, normalFont);
                p.setSpacingAfter(5);
                document.add(p);
            }
        }

        document.close();
        return baos.toByteArray();
    }

    private CellStyle createEnhancedHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        org.apache.poi.ss.usermodel.Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 11);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setBorderBottom(BorderStyle.MEDIUM);
        style.setBorderTop(BorderStyle.MEDIUM);
        style.setBorderLeft(BorderStyle.MEDIUM);
        style.setBorderRight(BorderStyle.MEDIUM);
        return style;
    }

    private CellStyle createEnhancedMoneyStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setDataFormat(workbook.createDataFormat().getFormat("#,##0.00"));
        style.setAlignment(HorizontalAlignment.RIGHT);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private CellStyle createEnhancedNormalStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        org.apache.poi.ss.usermodel.Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 11);
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setBorderBottom(BorderStyle.MEDIUM);
        style.setBorderTop(BorderStyle.MEDIUM);
        style.setBorderLeft(BorderStyle.MEDIUM);
        style.setBorderRight(BorderStyle.MEDIUM);
        return style;
    }

    private CellStyle createEnhancedPercentStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setDataFormat(workbook.createDataFormat().getFormat("0.00%"));
        style.setAlignment(HorizontalAlignment.RIGHT);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private CellStyle createTitleStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        org.apache.poi.ss.usermodel.Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 14);
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        return style;
    }

    private CellStyle createTotalStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        org.apache.poi.ss.usermodel.Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 11);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.MEDIUM);
        style.setBorderTop(BorderStyle.MEDIUM);
        style.setBorderLeft(BorderStyle.MEDIUM);
        style.setBorderRight(BorderStyle.MEDIUM);
        return style;
    }

    private CellStyle createPercentStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setDataFormat(workbook.createDataFormat().getFormat("0.00%"));
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private void addSummaryRow(PdfPTable table, String indicator, BigDecimal value, String unit, String note, Font font) {
        table.addCell(createCell(indicator, font, Element.ALIGN_LEFT));
        table.addCell(createCell(formatMoney(value), font, Element.ALIGN_RIGHT));
        table.addCell(createCell(unit, font, Element.ALIGN_CENTER));
        table.addCell(createCell(note, font, Element.ALIGN_LEFT));
    }
}