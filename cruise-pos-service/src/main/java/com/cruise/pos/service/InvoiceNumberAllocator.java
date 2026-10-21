package com.cruise.pos.service;

import com.cruise.pos.entity.InvoiceSequence;
import com.cruise.pos.repository.InvoiceSequenceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

/**
 * Cấp số hoá đơn đơn điệu theo từng cabin.
 * Sử dụng khoá bi quan trên dòng bộ đếm để an toàn khi chạy nhiều instance.
 */
@Service
@RequiredArgsConstructor
public class InvoiceNumberAllocator {

    private static final String PREFIX = "INV";

    private final InvoiceSequenceRepository sequenceRepository;

    @Transactional(propagation = Propagation.MANDATORY)
    public String allocate(String cabinNumber) {
        InvoiceSequence sequence = sequenceRepository.findByKeyWithLock(cabinNumber)
                .orElseGet(() -> sequenceRepository.saveAndFlush(InvoiceSequence.builder()
                        .sequenceKey(cabinNumber)
                        .nextValue(1L)
                        .build()));

        long current = sequence.getNextValue();
        sequence.setNextValue(current + 1);
        sequenceRepository.saveAndFlush(sequence);

        return String.format(Locale.ROOT, "%s-%s-%05d", PREFIX, cabinNumber, current);
    }
}
