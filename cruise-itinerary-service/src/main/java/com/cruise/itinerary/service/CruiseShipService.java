package com.cruise.itinerary.service;

import com.cruise.itinerary.dto.*;
import com.cruise.itinerary.entity.CruiseShip;
import com.cruise.itinerary.enums.ShipStatus;
import com.cruise.itinerary.exception.DuplicateResourceException;
import com.cruise.itinerary.exception.ResourceNotFoundException;
import com.cruise.itinerary.repository.CruiseShipRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Service xử lý nghiệp vụ quản lý tàu du lịch
 */
@Service
@Transactional
public class CruiseShipService {

    private final CruiseShipRepository shipRepository;

    public CruiseShipService(CruiseShipRepository shipRepository) {
        this.shipRepository = shipRepository;
    }

    /**
     * Tạo mới một tàu du lịch trong hệ thống
     */
    public CruiseShipResponse createShip(CreateCruiseShipRequest request) {
        if (shipRepository.existsByCode(request.getCode().trim())) {
            throw new DuplicateResourceException("Mã tàu '" + request.getCode() + "' đã tồn tại trong hệ thống");
        }
        if (shipRepository.existsByName(request.getName().trim())) {
            throw new DuplicateResourceException("Tên tàu '" + request.getName() + "' đã tồn tại trong hệ thống");
        }

        ShipStatus status = request.getStatus() != null ? request.getStatus() : ShipStatus.ACTIVE;

        CruiseShip ship = CruiseShip.builder()
                .name(request.getName().trim())
                .code(request.getCode().trim().toUpperCase())
                .operator(request.getOperator().trim())
                .capacity(request.getCapacity())
                .crewCapacity(request.getCrewCapacity())
                .totalCabins(request.getTotalCabins())
                .totalDecks(request.getTotalDecks())
                .manufacturingYear(request.getManufacturingYear())
                .lengthMeters(request.getLengthMeters())
                .beamMeters(request.getBeamMeters())
                .draftMeters(request.getDraftMeters())
                .status(status)
                .description(request.getDescription())
                .amenities(request.getAmenities())
                .imageUrl(request.getImageUrl())
                .active(true)
                .build();

        CruiseShip savedShip = shipRepository.save(ship);
        return CruiseShipResponse.fromEntity(savedShip);
    }

    /**
     * Tìm kiếm và lấy chi tiết tàu theo ID
     */
    @Transactional(readOnly = true)
    public CruiseShipResponse getShipById(Long id) {
        CruiseShip ship = shipRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tàu du lịch với ID: " + id));
        return CruiseShipResponse.fromEntity(ship);
    }

    /**
     * Tìm kiếm và lấy chi tiết tàu theo mã định danh (Code)
     */
    @Transactional(readOnly = true)
    public CruiseShipResponse getShipByCode(String code) {
        CruiseShip ship = shipRepository.findByCode(code.trim().toUpperCase())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tàu du lịch với mã: " + code));
        return CruiseShipResponse.fromEntity(ship);
    }

    /**
     * Lấy danh sách tàu có phân trang, hỗ trợ tìm kiếm theo từ khóa và lọc theo trạng thái
     */
    @Transactional(readOnly = true)
    public Page<CruiseShipResponse> getAllShips(String keyword, ShipStatus status, Boolean activeOnly, Pageable pageable) {
        String trimmedKeyword = (keyword != null && !keyword.trim().isEmpty()) ? keyword.trim() : null;
        Page<CruiseShip> page = shipRepository.searchShips(trimmedKeyword, status, activeOnly, pageable);
        return page.map(CruiseShipResponse::fromEntity);
    }

    /**
     * Lấy danh sách toàn bộ các tàu đang hoạt động (không phân trang, phục vụ dropdown)
     */
    @Transactional(readOnly = true)
    public List<CruiseShipResponse> getAllActiveShips() {
        return shipRepository.findByActiveTrue().stream()
                .map(CruiseShipResponse::fromEntity)
                .toList();
    }

    /**
     * Cập nhật thông tin chi tiết của tàu du lịch
     */
    public CruiseShipResponse updateShip(Long id, UpdateCruiseShipRequest request) {
        CruiseShip ship = shipRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tàu du lịch với ID: " + id));

        String newCode = request.getCode().trim().toUpperCase();
        String newName = request.getName().trim();

        if (shipRepository.existsByCodeAndIdNot(newCode, id)) {
            throw new DuplicateResourceException("Mã tàu '" + request.getCode() + "' đã được sử dụng bởi tàu khác");
        }
        if (shipRepository.existsByNameAndIdNot(newName, id)) {
            throw new DuplicateResourceException("Tên tàu '" + request.getName() + "' đã được sử dụng bởi tàu khác");
        }

        ship.setName(newName);
        ship.setCode(newCode);
        ship.setOperator(request.getOperator().trim());
        ship.setCapacity(request.getCapacity());
        ship.setCrewCapacity(request.getCrewCapacity());
        ship.setTotalCabins(request.getTotalCabins());
        ship.setTotalDecks(request.getTotalDecks());
        ship.setManufacturingYear(request.getManufacturingYear());
        ship.setLengthMeters(request.getLengthMeters());
        ship.setBeamMeters(request.getBeamMeters());
        ship.setDraftMeters(request.getDraftMeters());

        if (request.getStatus() != null) {
            ship.setStatus(request.getStatus());
        }
        if (request.getActive() != null) {
            ship.setActive(request.getActive());
        }

        ship.setDescription(request.getDescription());
        ship.setAmenities(request.getAmenities());
        ship.setImageUrl(request.getImageUrl());

        CruiseShip updatedShip = shipRepository.save(ship);
        return CruiseShipResponse.fromEntity(updatedShip);
    }

    /**
     * Cập nhật trạng thái hoạt động của tàu
     */
    public CruiseShipResponse updateShipStatus(Long id, UpdateShipStatusRequest request) {
        CruiseShip ship = shipRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tàu du lịch với ID: " + id));

        ship.setStatus(request.getStatus());
        CruiseShip updatedShip = shipRepository.save(ship);
        return CruiseShipResponse.fromEntity(updatedShip);
    }

    /**
     * Xóa tàu du lịch (hỗ trợ xóa mềm hoặc xóa cứng khỏi cơ sở dữ liệu)
     */
    public void deleteShip(Long id, boolean hardDelete) {
        CruiseShip ship = shipRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tàu du lịch với ID: " + id));

        if (hardDelete) {
            shipRepository.delete(ship);
        } else {
            ship.setActive(false);
            ship.setStatus(ShipStatus.INACTIVE);
            shipRepository.save(ship);
        }
    }

    /**
     * Thống kê tổng số tàu du lịch đang kích hoạt
     */
    @Transactional(readOnly = true)
    public long countActiveShips() {
        return shipRepository.countByActiveTrue();
    }
}
