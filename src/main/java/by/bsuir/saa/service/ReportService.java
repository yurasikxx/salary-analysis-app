package by.bsuir.saa.service;

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

    private BaseFont russianBaseFont;

    public ReportService(PaymentRepository paymentRepository,
                         SalaryPaymentRepository salaryPaymentRepository,
                         EmployeeService employeeService,
                         DepartmentService departmentService) {
        this.paymentRepository = paymentRepository;
        this.salaryPaymentRepository = salaryPaymentRepository;
        this.employeeService = employeeService;
        this.departmentService = departmentService;
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

        // Подписи
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

        CellStyle headerStyle = workbook.createCellStyle();
        org.apache.poi.ss.usermodel.Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerStyle.setFont(headerFont);
        headerStyle.setAlignment(HorizontalAlignment.CENTER);
        headerStyle.setBorderBottom(BorderStyle.THIN);
        headerStyle.setBorderTop(BorderStyle.THIN);
        headerStyle.setBorderLeft(BorderStyle.THIN);
        headerStyle.setBorderRight(BorderStyle.THIN);

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

        Row titleRow = sheet.createRow(0);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue("Зарплатная ведомость за " + getRussianMonthName(month) + " " + year);

        Row headerRow = sheet.createRow(2);
        String[] headers = {"ФИО сотрудника", "Должность", "Подразделение", "Начисления", "Удержания", "К выплате"};
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        int rowNum = 3;
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

            Cell nameCell = row.createCell(0);
            nameCell.setCellValue(employee.getFullName());
            nameCell.setCellStyle(normalStyle);

            Cell positionCell = row.createCell(1);
            positionCell.setCellValue(employee.getPosition().getTitle());
            positionCell.setCellStyle(normalStyle);

            Cell deptCell = row.createCell(2);
            deptCell.setCellValue(employee.getDepartment().getName());
            deptCell.setCellStyle(normalStyle);

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
        Cell totalLabelCell = totalRow.createCell(2);
        totalLabelCell.setCellValue("ВСЕГО:");
        totalLabelCell.setCellStyle(headerStyle);

        Cell totalAccrualsCell = totalRow.createCell(3);
        totalAccrualsCell.setCellValue(totalAccruals.doubleValue());
        totalAccrualsCell.setCellStyle(moneyStyle);

        Cell totalDeductionsCell = totalRow.createCell(4);
        totalDeductionsCell.setCellValue(totalDeductions.doubleValue());
        totalDeductionsCell.setCellStyle(moneyStyle);

        Cell totalNetSalaryCell = totalRow.createCell(5);
        totalNetSalaryCell.setCellValue(totalNetSalary.doubleValue());
        totalNetSalaryCell.setCellStyle(moneyStyle);

        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        workbook.write(baos);
        workbook.close();

        return baos.toByteArray();
    }

    public byte[] generateDetailedSalaryReportExcel(Integer month, Integer year) throws IOException {
        List<Employee> employees = employeeService.getActiveEmployees();
        List<PaymentType> paymentTypes = getPaymentTypesForReport();

        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Детальная ведомость " + getRussianMonthName(month) + " " + year);

        CellStyle headerStyle = createHeaderStyle(workbook);
        CellStyle moneyStyle = createMoneyStyle(workbook);
        CellStyle normalStyle = createNormalStyle(workbook);

        Row headerRow = sheet.createRow(0);
        String[] headers = createDetailedHeaders(paymentTypes);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        int rowNum = 1;
        for (Employee employee : employees) {
            Row row = sheet.createRow(rowNum++);
            addEmployeeDetailedData(row, employee, month, year, paymentTypes, normalStyle, moneyStyle);
        }

        // Авто-размер колонок
        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }

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

    private List<PaymentType> getPaymentTypesForReport() {
        // Здесь должен быть метод для получения всех типов оплат
        // Временно возвращаем пустой список - нужно реализовать в PaymentTypeService
        return new ArrayList<>();
    }

    private String[] createDetailedHeaders(List<PaymentType> paymentTypes) {
        List<String> headers = new ArrayList<>();
        headers.add("ФИО сотрудника");
        headers.add("Должность");
        headers.add("Подразделение");

        for (PaymentType type : paymentTypes) {
            headers.add(type.getName());
        }

        headers.add("Всего начислено");
        headers.add("Всего удержано");
        headers.add("К выплате");

        return headers.toArray(new String[0]);
    }

    private void addEmployeeDetailedData(Row row, Employee employee, Integer month, Integer year,
                                         List<PaymentType> paymentTypes, CellStyle normalStyle, CellStyle moneyStyle) {
        List<Payment> payments = paymentRepository.findByEmployeeAndMonthAndYear(employee, month, year);

        row.createCell(0).setCellValue(employee.getFullName());
        row.createCell(1).setCellValue(employee.getPosition().getTitle());
        row.createCell(2).setCellValue(employee.getDepartment().getName());

        int colIndex = 3;
        BigDecimal totalAccruals = BigDecimal.ZERO;
        BigDecimal totalDeductions = BigDecimal.ZERO;

        for (PaymentType type : paymentTypes) {
            BigDecimal amount = payments.stream()
                    .filter(p -> p.getPaymentType().getId().equals(type.getId()))
                    .map(Payment::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            Cell cell = row.createCell(colIndex++);
            cell.setCellValue(amount.doubleValue());
            cell.setCellStyle(moneyStyle);

            if ("accrual".equals(type.getCategory())) {
                totalAccruals = totalAccruals.add(amount);
            } else if ("deduction".equals(type.getCategory())) {
                totalDeductions = totalDeductions.add(amount.abs());
            }
        }

        Cell totalAccrualsCell = row.createCell(colIndex++);
        totalAccrualsCell.setCellValue(totalAccruals.doubleValue());
        totalAccrualsCell.setCellStyle(moneyStyle);

        Cell totalDeductionsCell = row.createCell(colIndex++);
        totalDeductionsCell.setCellValue(totalDeductions.doubleValue());
        totalDeductionsCell.setCellStyle(moneyStyle);

        Cell netSalaryCell = row.createCell(colIndex);
        netSalaryCell.setCellValue(totalAccruals.subtract(totalDeductions).doubleValue());
        netSalaryCell.setCellStyle(moneyStyle);
    }
}