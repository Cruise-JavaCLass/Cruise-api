package com.cruise.itinerary.service;

import com.cruise.itinerary.dto.CreateCruiseTourRequest;
import com.cruise.itinerary.dto.CruiseTourResponse;
import com.cruise.itinerary.dto.UpdateCruiseTourRequest;
import com.cruise.itinerary.dto.UpdateTourStatusRequest;
import com.cruise.itinerary.entity.CruiseShip;
import com.cruise.itinerary.entity.CruiseTour;
import com.cruise.itinerary.enums.ShipStatus;
import com.cruise.itinerary.enums.TourStatus;
import com.cruise.itinerary.exception.DuplicateResourceException;
import com.cruise.itinerary.exception.ResourceNotFoundException;
import com.cruise.itinerary.repository.CruiseShipRepository;
import com.cruise.itinerary.repository.CruiseTourRepository;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CruiseTourService Unit Tests with Mockito")
class CruiseTourServiceTest {

    @Mock
    private CruiseTourRepository tourRepository;

    @Mock
    private CruiseShipRepository shipRepository;

    @Mock
    private TourValidator tourValidator;

    @InjectMocks
    private CruiseTourService tourService;

    private CruiseShip sampleShip;
    private CruiseTour sampleTour;
    private CreateCruiseTourRequest createRequest;
    private UpdateCruiseTourRequest updateRequest;

    @BeforeEach
    void setUp() {
        sampleShip = CruiseShip.builder()
                .name("Halong Grand Harmony")
                .code("SHIP-001")
                .capacity(2000)
                .status(ShipStatus.ACTIVE)
                .active(true)
                .build();
        sampleShip.setId(1L);

        sampleTour = CruiseTour.builder()
                .ship(sampleShip)
                .code("TOUR-HL-01")
                .name("Hành trình Di sản Vịnh Hạ Long 3N2Đ")
                .description("Tour khám phá vẻ đẹp Vịnh Hạ Long")
                .durationDays(3)
                .durationNights(2)
                .startDate(LocalDate.of(2026, 11, 1))
                .endDate(LocalDate.of(2026, 11, 3))
                .departurePort("Cảng Hạ Long")
                .destinationPort("Cảng Hạ Long")
                .maxCapacity(1500)
                .basePrice(BigDecimal.valueOf(5000000))
                .currency("VND")
                .status(TourStatus.DRAFT)
                .active(true)
                .build();
        sampleTour.setId(10L);

        createRequest = CreateCruiseTourRequest.builder()
                .shipId(1L)
                .code("TOUR-HL-01")
                .name("Hành trình Di sản Vịnh Hạ Long 3N2Đ")
                .description("Tour khám phá vẻ đẹp Vịnh Hạ Long")
                .durationDays(3)
                .durationNights(2)
                .startDate(LocalDate.of(2026, 11, 1))
                .endDate(LocalDate.of(2026, 11, 3))
                .departurePort("Cảng Hạ Long")
                .destinationPort("Cảng Hạ Long")
                .maxCapacity(1500)
                .basePrice(BigDecimal.valueOf(5000000))
                .currency("VND")
                .status(TourStatus.DRAFT)
                .build();

        updateRequest = UpdateCruiseTourRequest.builder()
                .shipId(1L)
                .code("TOUR-HL-01-V2")
                .name("Hành trình Di sản Vịnh Hạ Long Cao Cấp 3N2Đ")
                .description("Tour nâng cấp tiêu chuẩn 5 sao")
                .durationDays(3)
                .durationNights(2)
                .startDate(LocalDate.of(2026, 11, 1))
                .endDate(LocalDate.of(2026, 11, 3))
                .departurePort("Cảng Hạ Long")
                .destinationPort("Cảng Hạ Long")
                .maxCapacity(1600)
                .basePrice(BigDecimal.valueOf(6000000))
                .currency("VND")
                .status(TourStatus.PUBLISHED)
                .build();
    }

    // =========================================================================
    // CREATE TOUR TESTS
    // =========================================================================

    @Test
    @DisplayName("createTour should create tour successfully when data is valid")
    void shouldCreateTourSuccessfully() {
        when(shipRepository.findById(1L)).thenReturn(Optional.of(sampleShip));
        doNothing().when(tourValidator).validate(createRequest, sampleShip);
        when(tourRepository.existsByCode("TOUR-HL-01")).thenReturn(false);
        when(tourRepository.existsByName("Hành trình Di sản Vịnh Hạ Long 3N2Đ")).thenReturn(false);
        when(tourRepository.save(any(CruiseTour.class))).thenAnswer(invocation -> {
            CruiseTour saved = invocation.getArgument(0);
            saved.setId(10L);
            return saved;
        });

        CruiseTourResponse response = tourService.createTour(createRequest);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(10L);
        assertThat(response.getCode()).isEqualTo("TOUR-HL-01");
        assertThat(response.getShipId()).isEqualTo(1L);
        assertThat(response.getShipName()).isEqualTo("Halong Grand Harmony");
        assertThat(response.getStatus()).isEqualTo(TourStatus.DRAFT);

        verify(shipRepository).findById(1L);
        verify(tourValidator).validate(createRequest, sampleShip);
        verify(tourRepository).existsByCode("TOUR-HL-01");
        verify(tourRepository).save(any(CruiseTour.class));
    }

    @Test
    @DisplayName("createTour should throw ResourceNotFoundException when ship does not exist")
    void shouldThrowWhenShipNotFoundOnCreate() {
        when(shipRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tourService.createTour(createRequest))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Không tìm thấy tàu du lịch với ID: 1");

        verify(shipRepository).findById(1L);
        verifyNoInteractions(tourValidator);
        verify(tourRepository, never()).save(any());
    }

    @Test
    @DisplayName("createTour should throw DuplicateResourceException when tour code already exists")
    void shouldThrowWhenCodeExistsOnCreate() {
        when(tourRepository.existsByCode("TOUR-HL-01")).thenReturn(true);

        assertThatThrownBy(() -> tourService.createTour(createRequest))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("Mã tour 'TOUR-HL-01' đã tồn tại");

        verify(tourRepository, never()).save(any());
    }

    // =========================================================================
    // UPDATE TOUR TESTS
    // =========================================================================

    @Test
    @DisplayName("updateTour should update tour successfully when data is valid")
    void shouldUpdateTourSuccessfully() {
        when(tourRepository.findById(10L)).thenReturn(Optional.of(sampleTour));
        when(shipRepository.findById(1L)).thenReturn(Optional.of(sampleShip));
        doNothing().when(tourValidator).validate(updateRequest, sampleShip);
        when(tourRepository.existsByCodeAndIdNot("TOUR-HL-01-V2", 10L)).thenReturn(false);
        when(tourRepository.existsByNameAndIdNot("Hành trình Di sản Vịnh Hạ Long Cao Cấp 3N2Đ", 10L)).thenReturn(false);
        when(tourRepository.save(any(CruiseTour.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CruiseTourResponse response = tourService.updateTour(10L, updateRequest);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(10L);
        assertThat(response.getCode()).isEqualTo("TOUR-HL-01-V2");
        assertThat(response.getName()).isEqualTo("Hành trình Di sản Vịnh Hạ Long Cao Cấp 3N2Đ");
        assertThat(response.getStatus()).isEqualTo(TourStatus.PUBLISHED);

        verify(tourRepository).findById(10L);
        verify(shipRepository).findById(1L);
        verify(tourValidator).validate(updateRequest, sampleShip);
        verify(tourRepository).save(any(CruiseTour.class));
    }

    @Test
    @DisplayName("updateTour should throw ResourceNotFoundException when tour does not exist")
    void shouldThrowWhenTourNotFoundOnUpdate() {
        when(tourRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tourService.updateTour(999L, updateRequest))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Không tìm thấy tour du lịch với ID: 999");

        verify(tourRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateTour should throw ResourceNotFoundException when ship does not exist")
    void shouldThrowWhenShipNotFoundOnUpdate() {
        when(tourRepository.findById(10L)).thenReturn(Optional.of(sampleTour));
        when(shipRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tourService.updateTour(10L, updateRequest))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Không tìm thấy tàu du lịch với ID: 1");

        verify(tourRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateTour should throw DuplicateResourceException when new code belongs to another tour")
    void shouldThrowWhenNewCodeExistsOnUpdate() {
        when(tourRepository.findById(10L)).thenReturn(Optional.of(sampleTour));
        when(tourRepository.existsByCodeAndIdNot("TOUR-HL-01-V2", 10L)).thenReturn(true);

        assertThatThrownBy(() -> tourService.updateTour(10L, updateRequest))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("đã được sử dụng bởi tour khác");

        verify(tourRepository, never()).save(any());
    }

    // =========================================================================
    // UPDATE STATUS TESTS
    // =========================================================================

    @Test
    @DisplayName("updateTourStatus should change tour status successfully")
    void shouldUpdateTourStatusSuccessfully() {
        when(tourRepository.findById(10L)).thenReturn(Optional.of(sampleTour));
        when(tourRepository.save(any(CruiseTour.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UpdateTourStatusRequest statusRequest = new UpdateTourStatusRequest(TourStatus.IN_PROGRESS);
        CruiseTourResponse response = tourService.updateTourStatus(10L, statusRequest);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(TourStatus.IN_PROGRESS);
        verify(tourRepository).save(sampleTour);
    }

    // =========================================================================
    // GET TOUR TESTS
    // =========================================================================

    @Test
    @DisplayName("getTourById should return tour response when found")
    void shouldGetTourByIdSuccessfully() {
        when(tourRepository.findById(10L)).thenReturn(Optional.of(sampleTour));

        CruiseTourResponse response = tourService.getTourById(10L);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(10L);
        assertThat(response.getCode()).isEqualTo("TOUR-HL-01");
    }

    @Test
    @DisplayName("getTourById should throw ResourceNotFoundException when not found")
    void shouldThrowWhenTourNotFoundById() {
        when(tourRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tourService.getTourById(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Không tìm thấy tour du lịch với ID: 99");
    }

    @Test
    @DisplayName("getTourByCode should return tour response when found")
    void shouldGetTourByCodeSuccessfully() {
        when(tourRepository.findByCode("TOUR-HL-01")).thenReturn(Optional.of(sampleTour));

        CruiseTourResponse response = tourService.getTourByCode("TOUR-HL-01");

        assertThat(response).isNotNull();
        assertThat(response.getCode()).isEqualTo("TOUR-HL-01");
    }

    @Test
    @DisplayName("getTourByCode should throw ResourceNotFoundException when not found")
    void shouldThrowWhenTourNotFoundByCode() {
        when(tourRepository.findByCode("INVALID")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tourService.getTourByCode("INVALID"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Không tìm thấy tour du lịch với mã: INVALID");
    }

    @Test
    @DisplayName("getAllTours should return paged responses")
    void shouldGetAllToursPaged() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<CruiseTour> pagedTours = new PageImpl<>(List.of(sampleTour), pageable, 1);

        when(tourRepository.searchTours(eq("Hạ Long"), eq(TourStatus.DRAFT), eq(1L), eq(true), eq(pageable)))
                .thenReturn(pagedTours);

        Page<CruiseTourResponse> result = tourService.getAllTours("Hạ Long", TourStatus.DRAFT, 1L, true, pageable);

        assertThat(result).isNotNull();
        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).getCode()).isEqualTo("TOUR-HL-01");
    }

    @Test
    @DisplayName("getAllActiveTours should return list of active tours")
    void shouldGetActiveTours() {
        when(tourRepository.findByActiveTrue()).thenReturn(List.of(sampleTour));

        List<CruiseTourResponse> activeTours = tourService.getAllActiveTours();

        assertThat(activeTours).hasSize(1);
        assertThat(activeTours.get(0).getCode()).isEqualTo("TOUR-HL-01");
    }

    // =========================================================================
    // DELETE TOUR TESTS
    // =========================================================================

    @Test
    @DisplayName("deleteTour should perform soft delete when hardDelete is false")
    void shouldSoftDeleteTour() {
        when(tourRepository.findById(10L)).thenReturn(Optional.of(sampleTour));
        when(tourRepository.save(any(CruiseTour.class))).thenAnswer(invocation -> invocation.getArgument(0));

        tourService.deleteTour(10L, false);

        assertThat(sampleTour.getActive()).isFalse();
        assertThat(sampleTour.getStatus()).isEqualTo(TourStatus.CANCELLED);
        verify(tourRepository).save(sampleTour);
        verify(tourRepository, never()).delete(any());
    }

    @Test
    @DisplayName("deleteTour should perform hard delete when hardDelete is true")
    void shouldHardDeleteTour() {
        when(tourRepository.findById(10L)).thenReturn(Optional.of(sampleTour));

        tourService.deleteTour(10L, true);

        verify(tourRepository).delete(sampleTour);
        verify(tourRepository, never()).save(any());
    }
}
