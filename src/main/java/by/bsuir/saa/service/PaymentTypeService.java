package by.bsuir.saa.service;

import by.bsuir.saa.entity.Employee;
import by.bsuir.saa.entity.PaymentType;
import by.bsuir.saa.entity.Timesheet;
import by.bsuir.saa.repository.PaymentRepository;
import by.bsuir.saa.repository.PaymentTypeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class PaymentTypeService {

    private final PaymentTypeRepository paymentTypeRepository;
    private final PaymentRepository paymentRepository;
    private final EmployeeService employeeService;
    private final TimesheetService timesheetService;

    public PaymentTypeService(PaymentTypeRepository paymentTypeRepository,
                              PaymentRepository paymentRepository,
                              EmployeeService employeeService,
                              TimesheetService timesheetService) {
        this.paymentTypeRepository = paymentTypeRepository;
        this.paymentRepository = paymentRepository;
        this.employeeService = employeeService;
        this.timesheetService = timesheetService;
    }

    public List<PaymentType> getAllPaymentTypes() {
        return paymentTypeRepository.findAll();
    }

    public Optional<PaymentType> getPaymentTypeById(Integer id) {
        return paymentTypeRepository.findById(id);
    }

    public Optional<PaymentType> getPaymentTypeByCode(String code) {
        return paymentTypeRepository.findByCode(code.toUpperCase());
    }

    public List<PaymentType> getAccrualTypes() {
        return paymentTypeRepository.findByCategory("accrual");
    }

    public List<PaymentType> getDeductionTypes() {
        return paymentTypeRepository.findByCategory("deduction");
    }

    public Optional<PaymentType> getVacationPaymentType() {
        return getPaymentTypeByCode("ОТП");
    }

    public Optional<PaymentType> getSickLeavePaymentType() {
        return getPaymentTypeByCode("БОЛ");
    }

    public boolean isVacationOrSickLeavePayment(PaymentType paymentType) {
        return "ОТП".equals(paymentType.getCode()) || "БОЛ".equals(paymentType.getCode());
    }

    public List<Employee> getEmployeesWithConfirmedTimesheets(Integer month, Integer year) {
        return employeeService.getActiveEmployees().stream()
                .filter(employee -> timesheetService.getTimesheet(employee, month, year)
                        .map(t -> t.getStatus() == Timesheet.TimesheetStatus.CONFIRMED)
                        .orElse(false))
                .toList();
    }

    public boolean paymentTypeExists(String code) {
        return paymentTypeRepository.existsByCode(code.toUpperCase());
    }

    public PaymentType createPaymentType(String code, String name, String category,
                                         String description, String formula) {
        validateInput(code, name, category);
        String trimmedCode = prepareCode(code);

        validateUniqueCode(trimmedCode);

        PaymentType paymentType = buildPaymentType(trimmedCode, name, category, description, formula);
        return paymentTypeRepository.save(paymentType);
    }

    public PaymentType updatePaymentType(Integer id, String code, String name, String category,
                                         String description, String formula) {
        PaymentType paymentType = getExistingPaymentType(id);
        validateInput(code, name, category);

        String trimmedCode = prepareCode(code);
        validateUniqueCodeForUpdate(paymentType, trimmedCode);

        updatePaymentTypeFields(paymentType, trimmedCode, name, category, description, formula);
        return paymentTypeRepository.save(paymentType);
    }

    public void deletePaymentType(Integer id) {
        PaymentType paymentType = getExistingPaymentType(id);
        validateNoRelatedPayments(paymentType);
        paymentTypeRepository.delete(paymentType);
    }

    private void validateInput(String code, String name, String category) {
        if (code == null || code.trim().isEmpty()) {
            throw new RuntimeException("Код не может быть пустым");
        }
        if (name == null || name.trim().isEmpty()) {
            throw new RuntimeException("Название не может быть пустым");
        }
        if (!isValidCategory(category)) {
            throw new RuntimeException("Категория должна быть 'accrual' или 'deduction'");
        }
    }

    private boolean isValidCategory(String category) {
        return "accrual".equals(category) || "deduction".equals(category);
    }

    private String prepareCode(String code) {
        String trimmedCode = code.trim().toUpperCase();
        if (!trimmedCode.matches("^[А-ЯЁ]+$")) {
            throw new RuntimeException("Код может содержать только заглавные кириллические буквы");
        }
        return trimmedCode;
    }

    private void validateUniqueCode(String code) {
        if (paymentTypeRepository.existsByCode(code)) {
            throw new RuntimeException("Тип оплаты с кодом '" + code + "' уже существует");
        }
    }

    private void validateUniqueCodeForUpdate(PaymentType paymentType, String newCode) {
        if (!paymentType.getCode().equals(newCode) && paymentTypeRepository.existsByCode(newCode)) {
            throw new RuntimeException("Тип оплаты с кодом '" + newCode + "' уже существует");
        }
    }

    private void validateNoRelatedPayments(PaymentType paymentType) {
        if (paymentRepository.existsByPaymentType(paymentType)) {
            throw new RuntimeException("Невозможно удалить тип оплаты. Существуют связанные платежи.");
        }
    }

    private PaymentType getExistingPaymentType(Integer id) {
        return paymentTypeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Тип оплаты не найден"));
    }

    private PaymentType buildPaymentType(String code, String name, String category,
                                         String description, String formula) {
        PaymentType paymentType = new PaymentType();
        paymentType.setCode(code);
        paymentType.setName(name.trim());
        paymentType.setCategory(category);
        paymentType.setDescription(description != null ? description.trim() : null);
        paymentType.setFormula(formula != null ? formula.trim() : null);
        return paymentType;
    }

    private void updatePaymentTypeFields(PaymentType paymentType, String code, String name,
                                         String category, String description, String formula) {
        paymentType.setCode(code);
        paymentType.setName(name.trim());
        paymentType.setCategory(category);
        paymentType.setDescription(description != null ? description.trim() : null);
        paymentType.setFormula(formula != null ? formula.trim() : null);
    }
}