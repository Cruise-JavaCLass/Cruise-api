package com.cruise.activity.service;

import com.cruise.activity.dto.CreateTourProviderRequest;
import com.cruise.activity.dto.TourProviderResponse;
import com.cruise.activity.dto.UpdateTourProviderRequest;
import com.cruise.activity.entity.TourProvider;
import com.cruise.activity.enums.TourProviderStatus;
import com.cruise.activity.exception.BusinessRuleException;
import com.cruise.activity.exception.DuplicateTourProviderException;
import com.cruise.activity.exception.ResourceNotFoundException;
import com.cruise.activity.repository.TourProviderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class TourProviderService {

    private final TourProviderRepository tourProviderRepository;

    public TourProviderResponse createTourProvider(CreateTourProviderRequest request) {
        String code = request.getCode() == null || request.getCode().isBlank()
                ? generateCode()
                : normalizeCode(request.getCode());
        ensureCodeAvailable(code);
        String taxCode = trimToNull(request.getTaxCode());
        String licenseNumber = trimToNull(request.getLicenseNumber());
        ensureTaxCodeAvailable(taxCode);
        ensureLicenseNumberAvailable(licenseNumber);

        TourProvider provider = TourProvider.builder()
                .code(code)
                .name(request.getName().trim())
                .taxCode(taxCode)
                .licenseNumber(licenseNumber)
                .contactPerson(trimToNull(request.getContactPerson()))
                .email(trimToNull(request.getEmail()))
                .phone(trimToNull(request.getPhone()))
                .emergencyHotline(request.getEmergencyHotline().trim())
                .portId(request.getPortId().trim())
                .status(request.getStatus() == null ? TourProviderStatus.ACTIVE : request.getStatus())
                .build();

        try {
            return TourProviderResponse.fromEntity(tourProviderRepository.saveAndFlush(provider));
        } catch (DataIntegrityViolationException ex) {
            handleDataIntegrityViolation(ex);
            throw ex;
        }
    }

    public TourProviderResponse updateTourProvider(UUID id, UpdateTourProviderRequest request) {
        TourProvider provider = tourProviderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhà cung cấp với ID: " + id));

        if (request.getCode() != null && !request.getCode().isBlank()) {
            String code = normalizeCode(request.getCode());
            if (!code.equalsIgnoreCase(provider.getCode())) {
                ensureCodeAvailable(code, id);
            }
            provider.setCode(code);
        }

        provider.setName(request.getName().trim());
        if (request.getTaxCode() != null) {
            String taxCode = trimToNull(request.getTaxCode());
            ensureTaxCodeAvailable(taxCode, id);
            provider.setTaxCode(taxCode);
        }
        if (request.getLicenseNumber() != null) {
            String licenseNumber = trimToNull(request.getLicenseNumber());
            ensureLicenseNumberAvailable(licenseNumber, id);
            provider.setLicenseNumber(licenseNumber);
        }
        if (request.getContactPerson() != null) {
            provider.setContactPerson(trimToNull(request.getContactPerson()));
        }
        if (request.getEmail() != null) {
            provider.setEmail(trimToNull(request.getEmail()));
        }
        if (request.getPhone() != null) {
            provider.setPhone(trimToNull(request.getPhone()));
        }
        provider.setEmergencyHotline(request.getEmergencyHotline().trim());
        provider.setPortId(request.getPortId().trim());
        if (request.getStatus() != null) {
            provider.setStatus(request.getStatus());
        }

        try {
            return TourProviderResponse.fromEntity(tourProviderRepository.saveAndFlush(provider));
        } catch (DataIntegrityViolationException ex) {
            handleDataIntegrityViolation(ex);
            throw ex;
        }
    }

    @Transactional(readOnly = true)
    public TourProviderResponse getTourProviderById(UUID id) {
        return tourProviderRepository.findById(id)
                .map(TourProviderResponse::fromEntity)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhà cung cấp với ID: " + id));
    }

    @Transactional(readOnly = true)
    public Page<TourProviderResponse> getTourProviders(Pageable pageable) {
        return getTourProviders(pageable, null);
    }

    @Transactional(readOnly = true)
    public Page<TourProviderResponse> getTourProviders(Pageable pageable, String portId) {
        if (portId != null && !portId.isBlank()) {
            return tourProviderRepository.findAllByPortId(portId.trim(), pageable)
                    .map(TourProviderResponse::fromEntity);
        }
        return tourProviderRepository.findAll(pageable)
                .map(TourProviderResponse::fromEntity);
    }

    private void ensureCodeAvailable(String code) {
        if (tourProviderRepository.existsByCodeIgnoreCase(code)) {
            throw new DuplicateTourProviderException("Mã nhà cung cấp '" + code + "' đã tồn tại");
        }
    }

    private void ensureCodeAvailable(String code, UUID id) {
        if (tourProviderRepository.existsByCodeIgnoreCaseAndIdNot(code, id)) {
            throw new DuplicateTourProviderException("Mã nhà cung cấp '" + code + "' đã tồn tại");
        }
    }

    private void ensureTaxCodeAvailable(String taxCode) {
        if (taxCode != null && tourProviderRepository.existsByTaxCodeIgnoreCase(taxCode)) {
            throw new DuplicateTourProviderException("Mã số thuế '" + taxCode + "' đã được sử dụng");
        }
    }

    private void ensureTaxCodeAvailable(String taxCode, UUID id) {
        if (taxCode != null && tourProviderRepository.existsByTaxCodeIgnoreCaseAndIdNot(taxCode, id)) {
            throw new DuplicateTourProviderException("Mã số thuế '" + taxCode + "' đã được sử dụng");
        }
    }

    private void ensureLicenseNumberAvailable(String licenseNumber) {
        if (licenseNumber != null && tourProviderRepository.existsByLicenseNumberIgnoreCase(licenseNumber)) {
            throw new DuplicateTourProviderException("Số giấy phép '" + licenseNumber + "' đã được sử dụng");
        }
    }

    private void ensureLicenseNumberAvailable(String licenseNumber, UUID id) {
        if (licenseNumber != null
                && tourProviderRepository.existsByLicenseNumberIgnoreCaseAndIdNot(licenseNumber, id)) {
            throw new DuplicateTourProviderException("Số giấy phép '" + licenseNumber + "' đã được sử dụng");
        }
    }

    private void handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        String msg = ex.getMessage();
        if (msg != null) {
            String lower = msg.toLowerCase(Locale.ROOT);
            if (lower.contains("uk_tour_providers_code") || lower.contains("code")) {
                throw new DuplicateTourProviderException("Mã nhà cung cấp đã tồn tại");
            }
            if (lower.contains("uk_tour_providers_tax_code") || lower.contains("tax_code")) {
                throw new DuplicateTourProviderException("Mã số thuế đã được sử dụng");
            }
            if (lower.contains("uk_tour_providers_license_number") || lower.contains("license_number")) {
                throw new DuplicateTourProviderException("Số giấy phép đã được sử dụng");
            }
        }
        throw new DuplicateTourProviderException("Thông tin nhà cung cấp bị trùng lặp trong hệ thống");
    }

    private String generateCode() {
        return "TP-" + UUID.randomUUID().toString().replace("-", "").toUpperCase(Locale.ROOT);
    }

    private String normalizeCode(String code) {
        String normalized = code.trim().toUpperCase(Locale.ROOT);
        if (normalized.length() > 40) {
            throw new BusinessRuleException("Mã nhà cung cấp không được vượt quá 40 ký tự");
        }
        return normalized;
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
