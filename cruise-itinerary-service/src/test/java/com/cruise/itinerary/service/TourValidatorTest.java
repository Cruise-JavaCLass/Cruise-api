package com.cruise.itinerary.service;

import com.cruise.itinerary.dto.CreateCruiseTourRequest;
import com.cruise.itinerary.entity.CruiseShip;
import com.cruise.itinerary.enums.TourStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("TourValidator Unit Tests")
class TourValidatorTest {

    private TourValidator validator;
    private CreateCruiseTourRequest validRequest;
    private CruiseShip sampleShip;

    @BeforeEach
    void setUp() {
        validator = new TourValidator();

        sampleShip = CruiseShip.builder()
                .name("Halong Grand Harmony")
                .code("SHIP-001")
                .capacity(2000)
                .active(true)
                .build();
        sampleShip.setId(1L);

        validRequest = CreateCruiseTourRequest.builder()
                .shipId(1L)
                .code("TOUR-HL-01")
                .name("Hành trình Di sản Vịnh Hạ Long 3N2Đ")
                .description("Khám phá kỳ quan thiên nhiên thế giới")
                .durationDays(3)
                .durationNights(2)
                .startDate(LocalDate.of(2026, 11, 1))
                .endDate(LocalDate.of(2026, 11, 3))
                .maxCapacity(1500)
                .basePrice(BigDecimal.valueOf(5000000))
                .status(TourStatus.DRAFT)
                .departurePort("Cảng tàu khách quốc tế Hạ Long")
                .destinationPort("Cảng tàu khách quốc tế Hạ Long")
                .build();
    }

    @Test
    @DisplayName("Should pass validation with valid request and ship")
    void shouldPassValidRequest() {
        assertThatCode(() -> validator.validate(validRequest, sampleShip)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should pass validation when ship is null")
    void shouldPassWhenShipIsNull() {
        assertThatCode(() -> validator.validate(validRequest, null)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should pass validation when dates are null")
    void shouldPassWhenDatesAreNull() {
        validRequest.setStartDate(null);
        validRequest.setEndDate(null);
        assertThatCode(() -> validator.validate(validRequest, sampleShip)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should throw exception when request is null")
    void shouldThrowWhenRequestIsNull() {
        assertThatThrownBy(() -> validator.validate(null, sampleShip))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Thông tin tour không được để trống");
    }

    @Test
    @DisplayName("Should throw exception when tour code contains invalid characters")
    void shouldThrowWhenCodeHasInvalidCharacters() {
        validRequest.setCode("TOUR#01!");
        assertThatThrownBy(() -> validator.validate(validRequest, sampleShip))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Mã tour chỉ được chứa");
    }

    @Test
    @DisplayName("Should throw exception when duration days is less than 1")
    void shouldThrowWhenDurationDaysLessThanOne() {
        validRequest.setDurationDays(0);
        assertThatThrownBy(() -> validator.validate(validRequest, sampleShip))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Số ngày tour phải từ 1 ngày trở lên");
    }

    @Test
    @DisplayName("Should throw exception when duration nights is negative")
    void shouldThrowWhenDurationNightsNegative() {
        validRequest.setDurationNights(-1);
        assertThatThrownBy(() -> validator.validate(validRequest, sampleShip))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Số đêm tour không thể âm");
    }

    @Test
    @DisplayName("Should throw exception when duration nights exceeds duration days")
    void shouldThrowWhenNightsExceedDays() {
        validRequest.setDurationDays(3);
        validRequest.setDurationNights(4);
        assertThatThrownBy(() -> validator.validate(validRequest, sampleShip))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Số đêm tour không thể lớn hơn số ngày tour");
    }

    @Test
    @DisplayName("Should throw exception when end date is before start date")
    void shouldThrowWhenEndDateBeforeStartDate() {
        validRequest.setStartDate(LocalDate.of(2026, 11, 5));
        validRequest.setEndDate(LocalDate.of(2026, 11, 2));
        assertThatThrownBy(() -> validator.validate(validRequest, sampleShip))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Ngày kết thúc tour không thể trước ngày bắt đầu");
    }

    @Test
    @DisplayName("Should throw exception when max capacity is non-positive")
    void shouldThrowWhenMaxCapacityIsNonPositive() {
        validRequest.setMaxCapacity(0);
        assertThatThrownBy(() -> validator.validate(validRequest, sampleShip))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Sức chứa tối đa của tour phải lớn hơn 0");
    }

    @Test
    @DisplayName("Should throw exception when assigned ship is inactive")
    void shouldThrowWhenShipIsInactive() {
        sampleShip.setActive(false);
        assertThatThrownBy(() -> validator.validate(validRequest, sampleShip))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Tàu du lịch được chỉ định hiện đang ngừng hoạt động");
    }

    @Test
    @DisplayName("Should throw exception when base price is negative")
    void shouldThrowWhenBasePriceIsNegative() {
        validRequest.setBasePrice(BigDecimal.valueOf(-1000));
        assertThatThrownBy(() -> validator.validate(validRequest, sampleShip))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Giá tour cơ bản không được âm");
    }

    @Test
    @DisplayName("Should throw exception when tour max capacity exceeds ship capacity")
    void shouldThrowWhenMaxCapacityExceedsShipCapacity() {
        validRequest.setMaxCapacity(2500); // ship capacity is 2000
        assertThatThrownBy(() -> validator.validate(validRequest, sampleShip))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("không thể vượt quá sức chứa tối đa của tàu");
    }

    @Test
    @DisplayName("validateDates should validate properly standalone")
    void shouldValidateDatesStandalone() {
        assertThatCode(() -> validator.validateDates(
                LocalDate.of(2026, 12, 1),
                LocalDate.of(2026, 12, 4)
        )).doesNotThrowAnyException();

        assertThatThrownBy(() -> validator.validateDates(
                LocalDate.of(2026, 12, 5),
                LocalDate.of(2026, 12, 2)
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Ngày kết thúc tour không thể trước ngày bắt đầu");
    }
}
