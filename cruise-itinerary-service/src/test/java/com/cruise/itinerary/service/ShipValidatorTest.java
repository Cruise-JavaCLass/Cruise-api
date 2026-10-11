package com.cruise.itinerary.service;

import com.cruise.itinerary.dto.CreateCruiseShipRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Year;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ShipValidator Unit Tests")
class ShipValidatorTest {

    private ShipValidator validator;
    private CreateCruiseShipRequest validRequest;

    @BeforeEach
    void setUp() {
        validator = new ShipValidator();
        validRequest = CreateCruiseShipRequest.builder()
                .name("Halong Princess")
                .code("SHIP-001")
                .operator("Paradise Cruises")
                .capacity(2000)
                .crewCapacity(600)
                .totalCabins(900)
                .totalDecks(12)
                .manufacturingYear(2023)
                .lengthMeters(300.0)
                .beamMeters(35.0)
                .draftMeters(8.0)
                .build();
    }

    @Test
    @DisplayName("Should pass validation with valid request")
    void shouldPassValidRequest() {
        assertThatCode(() -> validator.validate(validRequest)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should throw exception when request is null")
    void shouldThrowWhenRequestIsNull() {
        assertThatThrownBy(() -> validator.validate(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Thông tin tàu không được để trống");
    }

    @Test
    @DisplayName("Should throw exception when ship code contains invalid characters")
    void shouldThrowWhenCodeHasInvalidCharacters() {
        validRequest.setCode("SHIP#001!");
        assertThatThrownBy(() -> validator.validate(validRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Mã tàu chỉ được chứa");
    }

    @Test
    @DisplayName("Should throw exception when total cabins exceed passenger capacity")
    void shouldThrowWhenCabinsExceedCapacity() {
        validRequest.setCapacity(500);
        validRequest.setTotalCabins(600);
        assertThatThrownBy(() -> validator.validate(validRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Số lượng cabin không thể lớn hơn sức chứa");
    }

    @Test
    @DisplayName("Should throw exception when crew capacity is disproportionately large")
    void shouldThrowWhenCrewCapacityTooLarge() {
        validRequest.setCapacity(500);
        validRequest.setTotalCabins(200);
        validRequest.setCrewCapacity(1200);
        assertThatThrownBy(() -> validator.validate(validRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Sức chứa thủy thủ đoàn không hợp lý");
    }

    @Test
    @DisplayName("Should throw exception when ship length is less than or equal to beam")
    void shouldThrowWhenLengthSmallerThanBeam() {
        validRequest.setLengthMeters(30.0);
        validRequest.setBeamMeters(35.0);
        assertThatThrownBy(() -> validator.validate(validRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Chiều dài thân tàu phải lớn hơn chiều rộng");
    }

    @Test
    @DisplayName("Should throw exception when draft exceeds beam")
    void shouldThrowWhenDraftExceedsBeam() {
        validRequest.setDraftMeters(40.0);
        validRequest.setBeamMeters(35.0);
        assertThatThrownBy(() -> validator.validate(validRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Mớn nước của tàu phải nhỏ hơn chiều rộng");
    }

    @Test
    @DisplayName("Should throw exception when manufacturing year is in the distant future")
    void shouldThrowWhenYearInDistantFuture() {
        validRequest.setManufacturingYear(Year.now().getValue() + 10);
        assertThatThrownBy(() -> validator.validate(validRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Năm đóng tàu không hợp lệ");
    }

    @Test
    @DisplayName("Should throw exception when manufacturing year is before 1900")
    void shouldThrowWhenYearBefore1900() {
        validRequest.setManufacturingYear(1880);
        assertThatThrownBy(() -> validator.validate(validRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Năm đóng tàu phải từ năm 1900 trở đi");
    }
}
