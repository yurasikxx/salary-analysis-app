package by.bsuir.saa.service;

import by.bsuir.saa.entity.PaymentType;
import by.bsuir.saa.repository.PaymentTypeRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class PaymentTypeService {

    private final PaymentTypeRepository paymentTypeRepository;

    public PaymentTypeService(PaymentTypeRepository paymentTypeRepository) {
        this.paymentTypeRepository = paymentTypeRepository;
    }

    public List<PaymentType> getAllPaymentTypes() {
        return paymentTypeRepository.findAll();
    }

    public Optional<PaymentType> getPaymentTypeById(Integer id) {
        return paymentTypeRepository.findById(id);
    }

    public Optional<PaymentType> getPaymentTypeByCode(String code) {
        return paymentTypeRepository.findByCode(code);
    }

    public List<PaymentType> getAccrualTypes() {
        return paymentTypeRepository.findByCategory("accrual");
    }

    public List<PaymentType> getDeductionTypes() {
        return paymentTypeRepository.findByCategory("deduction");
    }

    public List<PaymentType> getPaymentTypesByCategory(String category) {
        return paymentTypeRepository.findByCategory(category);
    }
}