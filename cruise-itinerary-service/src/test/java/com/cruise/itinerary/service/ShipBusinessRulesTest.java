package com.cruise.itinerary.service;

import com.cruise.itinerary.dto.CreateCruiseShipRequest;
import com.cruise.itinerary.dto.CruiseShipResponse;
import com.cruise.itinerary.dto.UpdateCruiseShipRequest;
import com.cruise.itinerary.dto.UpdateShipStatusRequest;
import com.cruise.itinerary.entity.CruiseShip;
import com.cruise.itinerary.enums.ShipStatus;
import com.cruise.itinerary.exception.DuplicateResourceException;
import com.cruise.itinerary.repository.CruiseShipRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Year;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Ship Business Rules Unit Tests (KAN-89)")
class ShipBusinessRulesTest {

    @Mock
    private CruiseShipRepository shipRepository;

    private CruiseShipService shipService;

    @BeforeEach
    void setUp() {
        ShipValidator realValidator = new ShipValidator();
        shipService = new CruiseShipService(shipRepository, realValidator);
    }

    private CreateCruiseShipRequest createValidRequest() {
        return CreateCruiseShipRequest.builder()
                .name("Sea Voyager")
                .code("VOYAGER-01")
                .operator("Oceanic Lines")
                .capacity(1500)
                .crewCapacity(450)
                .totalCabins(700)
                .totalDecks(10)
                .manufacturingYear(2022)
                .lengthMeters(280.0)
                .beamMeters(32.0)
                .draftMeters(7.5)
                .status(ShipStatus.ACTIVE)
                .build();
    }

    private CruiseShip buildExistingShip(Long id, String code, String name) {
        CruiseShip ship = CruiseShip.builder()
                .name(name)
                .code(code)
                .operator("Oceanic Lines")
                .capacity(1500)
                .crewCapacity(450)
                .totalCabins(700)
                .totalDecks(10)
                .manufacturingYear(2022)
                .lengthMeters(280.0)
                .beamMeters(32.0)
                .draftMeters(7.5)
                .status(ShipStatus.ACTIVE)
                .active(true)
                .build();
        ship.setId(id);
        return ship;
    }

    @Nested
    @DisplayName("1. Code and Identity Business Rules")
    class CodeAndIdentityRules {

        @Test
        @DisplayName("Should normalize ship code to uppercase and trim whitespaces upon creation")
        void shouldNormalizeShipCodeToUppercaseAndTrimWhitespaces() {
            CreateCruiseShipRequest req = createValidRequest();
            req.setCode("  voyager-01  ");
            when(shipRepository.existsByCode("voyager-01")).thenReturn(false);
            when(shipRepository.existsByName("Sea Voyager")).thenReturn(false);
            when(shipRepository.save(any(CruiseShip.class))).thenAnswer(inv -> inv.getArgument(0));

            CruiseShipResponse resp = shipService.createShip(req);

            assertThat(resp.getCode()).isEqualTo("VOYAGER-01");
        }

        @Test
        @DisplayName("Should reject ship with invalid characters in code")
        void shouldRejectShipWithInvalidCodePattern() {
            CreateCruiseShipRequest req = createValidRequest();
            req.setCode("SHIP#INVALID!");

            assertThatThrownBy(() -> shipService.createShip(req))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Mã tàu chỉ được chứa chữ cái");
            verify(shipRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should reject creation when ship code already exists in system")
        void shouldRejectCreationWithDuplicateCode() {
            CreateCruiseShipRequest req = createValidRequest();
            when(shipRepository.existsByCode("VOYAGER-01")).thenReturn(true);

            assertThatThrownBy(() -> shipService.createShip(req))
                    .isInstanceOf(DuplicateResourceException.class)
                    .hasMessageContaining("Mã tàu 'VOYAGER-01' đã tồn tại");
            verify(shipRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should reject creation when ship name already exists in system")
        void shouldRejectCreationWithDuplicateName() {
            CreateCruiseShipRequest req = createValidRequest();
            when(shipRepository.existsByCode("VOYAGER-01")).thenReturn(false);
            when(shipRepository.existsByName("Sea Voyager")).thenReturn(true);

            assertThatThrownBy(() -> shipService.createShip(req))
                    .isInstanceOf(DuplicateResourceException.class)
                    .hasMessageContaining("Tên tàu 'Sea Voyager' đã tồn tại");
            verify(shipRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should allow updating ship while retaining its own code and name")
        void shouldAllowUpdateWithOwnCodeAndName() {
            CruiseShip existing = buildExistingShip(1L, "VOYAGER-01", "Sea Voyager");
            when(shipRepository.findById(1L)).thenReturn(Optional.of(existing));
            when(shipRepository.existsByCodeAndIdNot("VOYAGER-01", 1L)).thenReturn(false);
            when(shipRepository.existsByNameAndIdNot("Sea Voyager", 1L)).thenReturn(false);
            when(shipRepository.save(any(CruiseShip.class))).thenAnswer(inv -> inv.getArgument(0));

            UpdateCruiseShipRequest updateReq = UpdateCruiseShipRequest.builder()
                    .name("Sea Voyager")
                    .code("VOYAGER-01")
                    .operator("Oceanic Lines")
                    .capacity(1600)
                    .crewCapacity(500)
                    .totalCabins(750)
                    .lengthMeters(280.0)
                    .beamMeters(32.0)
                    .draftMeters(7.5)
                    .manufacturingYear(2022)
                    .build();

            CruiseShipResponse updated = shipService.updateShip(1L, updateReq);
            assertThat(updated.getCapacity()).isEqualTo(1600);
        }

        @Test
        @DisplayName("Should reject update when new code is already used by another ship")
        void shouldRejectUpdateWhenCodeBelongsToAnotherShip() {
            CruiseShip existing = buildExistingShip(1L, "VOYAGER-01", "Sea Voyager");
            when(shipRepository.findById(1L)).thenReturn(Optional.of(existing));
            when(shipRepository.existsByCodeAndIdNot("OTHER-01", 1L)).thenReturn(true);

            UpdateCruiseShipRequest updateReq = UpdateCruiseShipRequest.builder()
                    .name("Sea Voyager")
                    .code("OTHER-01")
                    .operator("Oceanic Lines")
                    .capacity(1500)
                    .crewCapacity(450)
                    .totalCabins(700)
                    .lengthMeters(280.0)
                    .beamMeters(32.0)
                    .draftMeters(7.5)
                    .manufacturingYear(2022)
                    .build();

            assertThatThrownBy(() -> shipService.updateShip(1L, updateReq))
                    .isInstanceOf(DuplicateResourceException.class)
                    .hasMessageContaining("đã được sử dụng bởi tàu khác");
        }
    }

    @Nested
    @DisplayName("2. Capacity and Cabins Business Rules")
    class CapacityAndCabinRules {

        @Test
        @DisplayName("Should reject when total cabins exceed passenger capacity")
        void shouldRejectWhenTotalCabinsExceedPassengerCapacity() {
            CreateCruiseShipRequest req = createValidRequest();
            req.setCapacity(500);
            req.setTotalCabins(600);

            assertThatThrownBy(() -> shipService.createShip(req))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Số lượng cabin không thể lớn hơn sức chứa");
        }

        @Test
        @DisplayName("Should reject when crew capacity exceeds twice passenger capacity")
        void shouldRejectWhenCrewCapacityExceedsTwicePassengerCapacity() {
            CreateCruiseShipRequest req = createValidRequest();
            req.setCapacity(500);
            req.setTotalCabins(200);
            req.setCrewCapacity(1100);

            assertThatThrownBy(() -> shipService.createShip(req))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Sức chứa thủy thủ đoàn không hợp lý");
        }
    }

    @Nested
    @DisplayName("3. Nautical Dimensions Business Rules")
    class NauticalDimensionRules {

        @Test
        @DisplayName("Should reject when length is less than or equal to beam")
        void shouldRejectWhenLengthSmallerOrEqualBeam() {
            CreateCruiseShipRequest req = createValidRequest();
            req.setLengthMeters(30.0);
            req.setBeamMeters(35.0);

            assertThatThrownBy(() -> shipService.createShip(req))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Chiều dài thân tàu phải lớn hơn chiều rộng");
        }

        @Test
        @DisplayName("Should reject when draft exceeds or equals beam")
        void shouldRejectWhenDraftExceedsOrEqualsBeam() {
            CreateCruiseShipRequest req = createValidRequest();
            req.setDraftMeters(40.0);
            req.setBeamMeters(35.0);

            assertThatThrownBy(() -> shipService.createShip(req))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Mớn nước của tàu phải nhỏ hơn chiều rộng");
        }
    }

    @Nested
    @DisplayName("4. Manufacturing Year Business Rules")
    class ManufacturingYearRules {

        @Test
        @DisplayName("Should reject manufacturing year before 1900")
        void shouldRejectYearBefore1900() {
            CreateCruiseShipRequest req = createValidRequest();
            req.setManufacturingYear(1889);

            assertThatThrownBy(() -> shipService.createShip(req))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Năm đóng tàu phải từ năm 1900 trở đi");
        }

        @Test
        @DisplayName("Should reject manufacturing year more than 5 years into the future")
        void shouldRejectYearInDistantFuture() {
            int distantYear = Year.now().getValue() + 8;
            CreateCruiseShipRequest req = createValidRequest();
            req.setManufacturingYear(distantYear);

            assertThatThrownBy(() -> shipService.createShip(req))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Năm đóng tàu không hợp lệ");
        }

        @Test
        @DisplayName("Should accept commissioning year up to 5 years into future")
        void shouldAcceptAllowedFutureYear() {
            int futureYear = Year.now().getValue() + 3;
            CreateCruiseShipRequest req = createValidRequest();
            req.setManufacturingYear(futureYear);
            when(shipRepository.existsByCode("VOYAGER-01")).thenReturn(false);
            when(shipRepository.existsByName("Sea Voyager")).thenReturn(false);
            when(shipRepository.save(any(CruiseShip.class))).thenAnswer(inv -> inv.getArgument(0));

            CruiseShipResponse resp = shipService.createShip(req);
            assertThat(resp.getManufacturingYear()).isEqualTo(futureYear);
        }
    }

    @Nested
    @DisplayName("5. Lifecycle and Status Business Rules")
    class LifecycleAndStatusRules {

        @Test
        @DisplayName("Should default ship status to ACTIVE when omitted in creation request")
        void shouldDefaultStatusToActiveWhenOmitted() {
            CreateCruiseShipRequest req = createValidRequest();
            req.setStatus(null);
            when(shipRepository.existsByCode("VOYAGER-01")).thenReturn(false);
            when(shipRepository.existsByName("Sea Voyager")).thenReturn(false);

            ArgumentCaptor<CruiseShip> captor = ArgumentCaptor.forClass(CruiseShip.class);
            when(shipRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

            shipService.createShip(req);

            assertThat(captor.getValue().getStatus()).isEqualTo(ShipStatus.ACTIVE);
            assertThat(captor.getValue().getActive()).isTrue();
        }

        @Test
        @DisplayName("Should soft delete ship by marking active as false and status as INACTIVE")
        void shouldSoftDeleteShipSuccessfully() {
            CruiseShip existing = buildExistingShip(1L, "VOYAGER-01", "Sea Voyager");
            when(shipRepository.findById(1L)).thenReturn(Optional.of(existing));
            when(shipRepository.save(any(CruiseShip.class))).thenAnswer(inv -> inv.getArgument(0));

            shipService.deleteShip(1L, false);

            assertThat(existing.getActive()).isFalse();
            assertThat(existing.getStatus()).isEqualTo(ShipStatus.INACTIVE);
            verify(shipRepository, never()).delete(any());
            verify(shipRepository).save(existing);
        }

        @Test
        @DisplayName("Should hard delete ship by invoking repository delete")
        void shouldHardDeleteShipSuccessfully() {
            CruiseShip existing = buildExistingShip(1L, "VOYAGER-01", "Sea Voyager");
            when(shipRepository.findById(1L)).thenReturn(Optional.of(existing));

            shipService.deleteShip(1L, true);

            verify(shipRepository).delete(existing);
            verify(shipRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should update ship status while keeping all other properties intact")
        void shouldUpdateShipStatusSuccessfully() {
            CruiseShip existing = buildExistingShip(1L, "VOYAGER-01", "Sea Voyager");
            when(shipRepository.findById(1L)).thenReturn(Optional.of(existing));
            when(shipRepository.save(any(CruiseShip.class))).thenAnswer(inv -> inv.getArgument(0));

            UpdateShipStatusRequest statusReq = UpdateShipStatusRequest.builder()
                    .status(ShipStatus.UNDER_MAINTENANCE)
                    .build();

            CruiseShipResponse resp = shipService.updateShipStatus(1L, statusReq);

            assertThat(resp.getStatus()).isEqualTo(ShipStatus.UNDER_MAINTENANCE);
            assertThat(resp.getName()).isEqualTo("Sea Voyager");
        }
    }
}
