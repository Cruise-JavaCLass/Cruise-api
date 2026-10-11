package com.cruise.itinerary.service;

import com.cruise.itinerary.dto.*;
import com.cruise.itinerary.entity.CruiseShip;
import com.cruise.itinerary.enums.ShipStatus;
import com.cruise.itinerary.exception.DuplicateResourceException;
import com.cruise.itinerary.exception.ResourceNotFoundException;
import com.cruise.itinerary.repository.CruiseShipRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CruiseShipService Unit Tests with Mockito")
class CruiseShipServiceTest {

    @Mock
    private CruiseShipRepository shipRepository;

    @InjectMocks
    private CruiseShipService shipService;

    private CruiseShip sampleShip;
    private CreateCruiseShipRequest createRequest;
    private UpdateCruiseShipRequest updateRequest;

    @BeforeEach
    void setUp() {
        sampleShip = CruiseShip.builder()
                .name("Halong Grand Harmony")
                .code("SHIP-001")
                .operator("Paradise Vietnam Cruises")
                .capacity(2400)
                .crewCapacity(750)
                .totalCabins(1100)
                .totalDecks(14)
                .manufacturingYear(2023)
                .lengthMeters(315.5)
                .beamMeters(38.0)
                .draftMeters(8.5)
                .status(ShipStatus.ACTIVE)
                .description("Du thuyền cao cấp 5 sao")
                .amenities("Sky Pool, Luxury Spa, Grand Ballroom")
                .imageUrl("https://cruise.com/ships/ship-001.jpg")
                .active(true)
                .build();
        sampleShip.setId(1L);

        createRequest = CreateCruiseShipRequest.builder()
                .name("Halong Grand Harmony")
                .code("SHIP-001")
                .operator("Paradise Vietnam Cruises")
                .capacity(2400)
                .crewCapacity(750)
                .totalCabins(1100)
                .totalDecks(14)
                .manufacturingYear(2023)
                .lengthMeters(315.5)
                .beamMeters(38.0)
                .draftMeters(8.5)
                .status(ShipStatus.ACTIVE)
                .description("Du thuyền cao cấp 5 sao")
                .amenities("Sky Pool, Luxury Spa, Grand Ballroom")
                .imageUrl("https://cruise.com/ships/ship-001.jpg")
                .build();

        updateRequest = UpdateCruiseShipRequest.builder()
                .name("Halong Grand Harmony Luxury")
                .code("SHIP-001-MOD")
                .operator("Paradise Vietnam Cruises Group")
                .capacity(2500)
                .crewCapacity(800)
                .totalCabins(1150)
                .totalDecks(15)
                .manufacturingYear(2024)
                .lengthMeters(320.0)
                .beamMeters(39.0)
                .draftMeters(8.8)
                .status(ShipStatus.DOCKED)
                .description("Đã nâng cấp phiên bản 2024")
                .amenities("Infinity Pool, Helipad, Luxury Spa")
                .imageUrl("https://cruise.com/ships/ship-001-v2.jpg")
                .active(true)
                .build();
    }

    @Test
    @DisplayName("Should create ship successfully when code and name are unique")
    void shouldCreateShipSuccessfully() {
        when(shipRepository.existsByCode("SHIP-001")).thenReturn(false);
        when(shipRepository.existsByName("Halong Grand Harmony")).thenReturn(false);
        when(shipRepository.save(any(CruiseShip.class))).thenReturn(sampleShip);

        CruiseShipResponse response = shipService.createShip(createRequest);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getName()).isEqualTo("Halong Grand Harmony");
        assertThat(response.getCode()).isEqualTo("SHIP-001");
        assertThat(response.getStatus()).isEqualTo(ShipStatus.ACTIVE);

        verify(shipRepository).existsByCode("SHIP-001");
        verify(shipRepository).existsByName("Halong Grand Harmony");
        verify(shipRepository).save(any(CruiseShip.class));
    }

    @Test
    @DisplayName("Should throw DuplicateResourceException when ship code already exists")
    void shouldThrowDuplicateResourceExceptionWhenCodeExists() {
        when(shipRepository.existsByCode("SHIP-001")).thenReturn(true);

        assertThatThrownBy(() -> shipService.createShip(createRequest))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("Mã tàu 'SHIP-001' đã tồn tại");

        verify(shipRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw DuplicateResourceException when ship name already exists")
    void shouldThrowDuplicateResourceExceptionWhenNameExists() {
        when(shipRepository.existsByCode("SHIP-001")).thenReturn(false);
        when(shipRepository.existsByName("Halong Grand Harmony")).thenReturn(true);

        assertThatThrownBy(() -> shipService.createShip(createRequest))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("Tên tàu 'Halong Grand Harmony' đã tồn tại");

        verify(shipRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should return ship details when found by ID")
    void shouldGetShipByIdSuccessfully() {
        when(shipRepository.findById(1L)).thenReturn(Optional.of(sampleShip));

        CruiseShipResponse response = shipService.getShipById(1L);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getName()).isEqualTo("Halong Grand Harmony");
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when ship ID does not exist")
    void shouldThrowResourceNotFoundExceptionWhenIdNotFound() {
        when(shipRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> shipService.getShipById(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Không tìm thấy tàu du lịch với ID: 999");
    }

    @Test
    @DisplayName("Should return ship details when found by code")
    void shouldGetShipByCodeSuccessfully() {
        when(shipRepository.findByCode("SHIP-001")).thenReturn(Optional.of(sampleShip));

        CruiseShipResponse response = shipService.getShipByCode("ship-001");

        assertThat(response).isNotNull();
        assertThat(response.getCode()).isEqualTo("SHIP-001");
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when code does not exist")
    void shouldThrowResourceNotFoundExceptionWhenCodeNotFound() {
        when(shipRepository.findByCode("NON-EXISTENT")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> shipService.getShipByCode("NON-EXISTENT"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Không tìm thấy tàu du lịch với mã: NON-EXISTENT");
    }

    @Test
    @DisplayName("Should search and return paginated ships")
    void shouldSearchShipsWithPagination() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<CruiseShip> page = new PageImpl<>(List.of(sampleShip), pageable, 1);

        when(shipRepository.searchShips(eq("Halong"), eq(ShipStatus.ACTIVE), eq(true), eq(pageable)))
                .thenReturn(page);

        Page<CruiseShipResponse> result = shipService.getAllShips("Halong", ShipStatus.ACTIVE, true, pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).getName()).isEqualTo("Halong Grand Harmony");
    }

    @Test
    @DisplayName("Should return list of active ships")
    void shouldGetActiveShips() {
        when(shipRepository.findByActiveTrue()).thenReturn(List.of(sampleShip));

        List<CruiseShipResponse> result = shipService.getAllActiveShips();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCode()).isEqualTo("SHIP-001");
    }

    @Test
    @DisplayName("Should update ship details successfully")
    void shouldUpdateShipSuccessfully() {
        when(shipRepository.findById(1L)).thenReturn(Optional.of(sampleShip));
        when(shipRepository.existsByCodeAndIdNot("SHIP-001-MOD", 1L)).thenReturn(false);
        when(shipRepository.existsByNameAndIdNot("Halong Grand Harmony Luxury", 1L)).thenReturn(false);
        when(shipRepository.save(any(CruiseShip.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CruiseShipResponse updated = shipService.updateShip(1L, updateRequest);

        assertThat(updated).isNotNull();
        assertThat(updated.getName()).isEqualTo("Halong Grand Harmony Luxury");
        assertThat(updated.getCode()).isEqualTo("SHIP-001-MOD");
        assertThat(updated.getStatus()).isEqualTo(ShipStatus.DOCKED);
        assertThat(updated.getCapacity()).isEqualTo(2500);
    }

    @Test
    @DisplayName("Should throw DuplicateResourceException when updating with duplicate code on another ship")
    void shouldThrowDuplicateWhenUpdatingDuplicateCode() {
        when(shipRepository.findById(1L)).thenReturn(Optional.of(sampleShip));
        when(shipRepository.existsByCodeAndIdNot("SHIP-001-MOD", 1L)).thenReturn(true);

        assertThatThrownBy(() -> shipService.updateShip(1L, updateRequest))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("đã được sử dụng bởi tàu khác");
    }

    @Test
    @DisplayName("Should throw DuplicateResourceException when updating with duplicate name on another ship")
    void shouldThrowDuplicateWhenUpdatingDuplicateName() {
        when(shipRepository.findById(1L)).thenReturn(Optional.of(sampleShip));
        when(shipRepository.existsByCodeAndIdNot("SHIP-001-MOD", 1L)).thenReturn(false);
        when(shipRepository.existsByNameAndIdNot("Halong Grand Harmony Luxury", 1L)).thenReturn(true);

        assertThatThrownBy(() -> shipService.updateShip(1L, updateRequest))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("đã được sử dụng bởi tàu khác");
    }

    @Test
    @DisplayName("Should update ship status successfully")
    void shouldUpdateShipStatusSuccessfully() {
        when(shipRepository.findById(1L)).thenReturn(Optional.of(sampleShip));
        when(shipRepository.save(any(CruiseShip.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UpdateShipStatusRequest statusReq = UpdateShipStatusRequest.builder()
                .status(ShipStatus.ON_VOYAGE)
                .build();

        CruiseShipResponse response = shipService.updateShipStatus(1L, statusReq);

        assertThat(response.getStatus()).isEqualTo(ShipStatus.ON_VOYAGE);
    }

    @Test
    @DisplayName("Should soft delete ship by setting active false and status INACTIVE")
    void shouldSoftDeleteShip() {
        when(shipRepository.findById(1L)).thenReturn(Optional.of(sampleShip));
        when(shipRepository.save(any(CruiseShip.class))).thenAnswer(invocation -> invocation.getArgument(0));

        shipService.deleteShip(1L, false);

        assertThat(sampleShip.getActive()).isFalse();
        assertThat(sampleShip.getStatus()).isEqualTo(ShipStatus.INACTIVE);
        verify(shipRepository, never()).delete(any());
        verify(shipRepository).save(sampleShip);
    }

    @Test
    @DisplayName("Should hard delete ship from repository when requested")
    void shouldHardDeleteShip() {
        when(shipRepository.findById(1L)).thenReturn(Optional.of(sampleShip));

        shipService.deleteShip(1L, true);

        verify(shipRepository).delete(sampleShip);
    }

    @Test
    @DisplayName("Should return count of active ships")
    void shouldCountActiveShips() {
        when(shipRepository.countByActiveTrue()).thenReturn(5L);

        long count = shipService.countActiveShips();

        assertThat(count).isEqualTo(5L);
    }
}
