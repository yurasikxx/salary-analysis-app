package by.bsuir.saa.service;

import by.bsuir.saa.entity.MarkType;
import by.bsuir.saa.repository.MarkTypeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class MarkTypeService {

    private final MarkTypeRepository markTypeRepository;

    public MarkTypeService(MarkTypeRepository markTypeRepository) {
        this.markTypeRepository = markTypeRepository;
    }

    public List<MarkType> getAllMarkTypes() {
        return markTypeRepository.findAll();
    }

    public Optional<MarkType> getMarkTypeById(Integer id) {
        return markTypeRepository.findById(id);
    }

    public Optional<MarkType> getMarkTypeByCode(String code) {
        return markTypeRepository.findByCode(code);
    }
}