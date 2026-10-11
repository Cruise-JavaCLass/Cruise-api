package com.cruise.itinerary.service;

import com.cruise.itinerary.dto.PortRequest;
import com.cruise.itinerary.dto.PortResponse;
import com.cruise.itinerary.entity.Port;
import com.cruise.itinerary.exception.DuplicateResourceException;
import com.cruise.itinerary.exception.ResourceNotFoundException;
import com.cruise.itinerary.repository.PortRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class PortService {

    private final PortRepository portRepository;

    public PortService(PortRepository portRepository) {
        this.portRepository = portRepository;
    }

    public PortResponse createPort(PortRequest request) {
        if (portRepository.existsByCodeIgnoreCase(request.getCode().trim())) {
            throw new DuplicateResourceException("Mã cảng '" + request.getCode() + "' đã tồn tại trên hệ thống");
        }
        if (portRepository.existsByNameIgnoreCase(request.getName().trim())) {
            throw new DuplicateResourceException("Tên cảng '" + request.getName() + "' đã tồn tại trên hệ thống");
        }

        Port port = Port.builder()
                .name(request.getName().trim())
                .code(request.getCode().trim().toUpperCase())
                .country(request.getCountry() != null ? request.getCountry().trim() : "Việt Nam")
                .city(request.getCity().trim())
                .timezone(request.getTimezone() != null ? request.getTimezone().trim() : "Asia/Ho_Chi_Minh")
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .description(request.getDescription())
                .active(request.getActive() != null ? request.getActive() : true)
                .build();

        return PortResponse.fromEntity(portRepository.save(port));
    }

    public PortResponse updatePort(Long id, PortRequest request) {
        Port port = portRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy cảng với ID: " + id));

        if (portRepository.existsByCodeIgnoreCaseAndIdNot(request.getCode().trim(), id)) {
            throw new DuplicateResourceException("Mã cảng '" + request.getCode() + "' đã tồn tại trên hệ thống");
        }
        if (portRepository.existsByNameIgnoreCaseAndIdNot(request.getName().trim(), id)) {
            throw new DuplicateResourceException("Tên cảng '" + request.getName() + "' đã tồn tại trên hệ thống");
        }

        port.setName(request.getName().trim());
        port.setCode(request.getCode().trim().toUpperCase());
        port.setCountry(request.getCountry() != null ? request.getCountry().trim() : port.getCountry());
        port.setCity(request.getCity().trim());
        if (request.getTimezone() != null) port.setTimezone(request.getTimezone().trim());
        port.setLatitude(request.getLatitude());
        port.setLongitude(request.getLongitude());
        port.setDescription(request.getDescription());
        if (request.getActive() != null) port.setActive(request.getActive());

        return PortResponse.fromEntity(portRepository.save(port));
    }

    @Transactional(readOnly = true)
    public PortResponse getPortById(Long id) {
        return portRepository.findById(id)
                .map(PortResponse::fromEntity)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy cảng với ID: " + id));
    }

    @Transactional(readOnly = true)
    public Page<PortResponse> getAllPorts(String keyword, Boolean activeOnly, Pageable pageable) {
        return portRepository.searchPorts(keyword, activeOnly, pageable)
                .map(PortResponse::fromEntity);
    }

    @Transactional(readOnly = true)
    public List<PortResponse> getActivePorts() {
        return portRepository.findByActiveTrue().stream()
                .map(PortResponse::fromEntity)
                .toList();
    }

    public void deletePort(Long id) {
        Port port = portRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy cảng với ID: " + id));
        port.setActive(false);
        portRepository.save(port);
    }
}
