package com.cruise.activity.dto;

import com.cruise.activity.entity.ExcursionGuide;
import com.cruise.activity.entity.ExcursionVehicle;
import com.cruise.activity.enums.GuideRole;
import com.cruise.activity.enums.GuideStatus;
import com.cruise.activity.enums.VehicleStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Excursion Logistics DTO and Model Tests (KAN-177)")
class ExcursionLogisticsDtoAndModelTest {

    @Test
    @DisplayName("ExcursionVehicleResponse: fromEntity with full entity, null entity, and fallback branches")
    void vehicleResponse_fromEntity_Coverage() {
        // null entity
        assertThat(ExcursionVehicleResponse.fromEntity(null)).isNull();

        // full entity
        Instant now = Instant.now();
        ExcursionVehicle fullEntity = ExcursionVehicle.builder()
                .excursionId(200L)
                .vehicleCode("VEH-01")
                .licensePlate("14B-012.34")
                .vehicleType("Tàu 60 Chỗ")
                .driverName("Trần Văn Mạnh")
                .driverPhone("0903456789")
                .capacity(60)
                .assignedPassengers(45)
                .status(VehicleStatus.READY)
                .operatorName("Halong Travel")
                .notes("Ghi chú")
                .active(true)
                .build();
        fullEntity.setId(10L);
        fullEntity.setCreatedAt(now);
        fullEntity.setUpdatedAt(now);

        ExcursionVehicleResponse fullResp = ExcursionVehicleResponse.fromEntity(fullEntity);
        assertThat(fullResp).isNotNull();
        assertThat(fullResp.getId()).isEqualTo("VEH-01");
        assertThat(fullResp.getDbId()).isEqualTo(10L);
        assertThat(fullResp.getExcursionId()).isEqualTo(200L);
        assertThat(fullResp.getLicensePlate()).isEqualTo("14B-012.34");
        assertThat(fullResp.getVehicleType()).isEqualTo("Tàu 60 Chỗ");
        assertThat(fullResp.getDriverName()).isEqualTo("Trần Văn Mạnh");
        assertThat(fullResp.getDriverPhone()).isEqualTo("0903456789");
        assertThat(fullResp.getCapacity()).isEqualTo(60);
        assertThat(fullResp.getAssignedPassengers()).isEqualTo(45);
        assertThat(fullResp.getStatus()).isEqualTo(VehicleStatus.READY);
        assertThat(fullResp.getOperatorName()).isEqualTo("Halong Travel");
        assertThat(fullResp.getNotes()).isEqualTo("Ghi chú");
        assertThat(fullResp.getActive()).isTrue();
        assertThat(fullResp.getCreatedAt()).isEqualTo(now);
        assertThat(fullResp.getUpdatedAt()).isEqualTo(now);

        // fallback entity with vehicleCode=null, id=null, assignedPassengers=null, status=null
        ExcursionVehicle fallback1 = ExcursionVehicle.builder().build();
        ExcursionVehicleResponse resp1 = ExcursionVehicleResponse.fromEntity(fallback1);
        assertThat(resp1.getId()).isEqualTo("VEH-01");
        assertThat(resp1.getAssignedPassengers()).isZero();
        assertThat(resp1.getStatus()).isEqualTo(VehicleStatus.READY);

        // fallback entity with vehicleCode=" ", id=5L
        ExcursionVehicle fallback2 = ExcursionVehicle.builder()
                .vehicleCode("  ")
                .build();
        fallback2.setId(5L);
        ExcursionVehicleResponse resp2 = ExcursionVehicleResponse.fromEntity(fallback2);
        assertThat(resp2.getId()).isEqualTo("VEH-05");

        // Setters & NoArgsConstructor
        ExcursionVehicleResponse manualResp = new ExcursionVehicleResponse();
        manualResp.setId("VEH-99");
        manualResp.setDbId(99L);
        manualResp.setExcursionId(1L);
        manualResp.setLicensePlate("29B-999.99");
        manualResp.setVehicleType("Bus");
        manualResp.setDriverName("Driver");
        manualResp.setDriverPhone("0123");
        manualResp.setCapacity(40);
        manualResp.setAssignedPassengers(10);
        manualResp.setStatus(VehicleStatus.ON_TRIP);
        manualResp.setOperatorName("Operator");
        manualResp.setNotes("Notes");
        manualResp.setActive(true);
        manualResp.setCreatedAt(now);
        manualResp.setUpdatedAt(now);
        assertThat(manualResp.getId()).isEqualTo("VEH-99");
    }

    @Test
    @DisplayName("ExcursionGuideResponse: fromEntity with full entity, null entity, and fallback branches")
    void guideResponse_fromEntity_Coverage() {
        // null entity
        assertThat(ExcursionGuideResponse.fromEntity(null)).isNull();

        // full entity
        Instant now = Instant.now();
        ExcursionGuide fullGuide = ExcursionGuide.builder()
                .excursionId(200L)
                .guideCode("GUD-01")
                .fullName("Hoàng Hải Đăng")
                .phoneNumber("0912888999")
                .role(GuideRole.LEAD)
                .languages("Tiếng Việt, English, French")
                .licenseNumber("HDV-QG-88219")
                .status(GuideStatus.ACTIVE)
                .rating(4.9)
                .notes("Ghi chú HDV")
                .active(true)
                .build();
        fullGuide.setId(20L);
        fullGuide.setCreatedAt(now);
        fullGuide.setUpdatedAt(now);

        ExcursionGuideResponse fullResp = ExcursionGuideResponse.fromEntity(fullGuide);
        assertThat(fullResp).isNotNull();
        assertThat(fullResp.getId()).isEqualTo("GUD-01");
        assertThat(fullResp.getDbId()).isEqualTo(20L);
        assertThat(fullResp.getExcursionId()).isEqualTo(200L);
        assertThat(fullResp.getFullName()).isEqualTo("Hoàng Hải Đăng");
        assertThat(fullResp.getPhoneNumber()).isEqualTo("0912888999");
        assertThat(fullResp.getRole()).isEqualTo(GuideRole.LEAD);
        assertThat(fullResp.getLanguages()).containsExactly("Tiếng Việt", "English", "French");
        assertThat(fullResp.getLicenseNumber()).isEqualTo("HDV-QG-88219");
        assertThat(fullResp.getStatus()).isEqualTo(GuideStatus.ACTIVE);
        assertThat(fullResp.getRating()).isEqualTo(4.9);
        assertThat(fullResp.getNotes()).isEqualTo("Ghi chú HDV");
        assertThat(fullResp.getActive()).isTrue();
        assertThat(fullResp.getCreatedAt()).isEqualTo(now);
        assertThat(fullResp.getUpdatedAt()).isEqualTo(now);

        // fallback entity with guideCode=null, id=null, languages=null, role=null, status=null, rating=null
        ExcursionGuide fallback1 = ExcursionGuide.builder().build();
        ExcursionGuideResponse resp1 = ExcursionGuideResponse.fromEntity(fallback1);
        assertThat(resp1.getId()).isEqualTo("GUD-01");
        assertThat(resp1.getLanguages()).isEmpty();
        assertThat(resp1.getRole()).isEqualTo(GuideRole.LEAD);
        assertThat(resp1.getStatus()).isEqualTo(GuideStatus.ACTIVE);
        assertThat(resp1.getRating()).isEqualTo(5.0);

        // fallback entity with guideCode=" ", id=8L, languages="  "
        ExcursionGuide fallback2 = ExcursionGuide.builder()
                .guideCode("   ")
                .languages("   ")
                .build();
        fallback2.setId(8L);
        ExcursionGuideResponse resp2 = ExcursionGuideResponse.fromEntity(fallback2);
        assertThat(resp2.getId()).isEqualTo("GUD-08");
        assertThat(resp2.getLanguages()).isEmpty();

        // Setters & NoArgsConstructor
        ExcursionGuideResponse manualResp = new ExcursionGuideResponse();
        manualResp.setId("GUD-88");
        manualResp.setDbId(88L);
        manualResp.setExcursionId(2L);
        manualResp.setFullName("Name");
        manualResp.setPhoneNumber("0987");
        manualResp.setRole(GuideRole.ASSISTANT);
        manualResp.setLanguages(List.of("English"));
        manualResp.setLicenseNumber("LIC-123");
        manualResp.setStatus(GuideStatus.STANDBY);
        manualResp.setRating(4.5);
        manualResp.setNotes("Note");
        manualResp.setActive(false);
        manualResp.setCreatedAt(now);
        manualResp.setUpdatedAt(now);
        assertThat(manualResp.getId()).isEqualTo("GUD-88");
    }

    @Test
    @DisplayName("DTOs & Enums Coverage (Requests, Base classes, Enums)")
    void dtoAndEnums_Coverage() {
        // AddExcursionVehicleRequest
        AddExcursionVehicleRequest addVeh = AddExcursionVehicleRequest.builder()
                .licensePlate("14B-111.11")
                .vehicleType("Xe 16 chỗ")
                .driverName("Tài xế")
                .driverPhone("0912")
                .capacity(16)
                .assignedPassengers(0)
                .status(VehicleStatus.READY)
                .operatorName("Halong Express")
                .notes("Notes")
                .build();
        assertThat(addVeh.getLicensePlate()).isEqualTo("14B-111.11");

        // UpdateExcursionVehicleRequest
        UpdateExcursionVehicleRequest updVeh = UpdateExcursionVehicleRequest.builder()
                .licensePlate("14B-222.22")
                .vehicleType("Xe 29 chỗ")
                .driverName("Tài xế mới")
                .driverPhone("0988")
                .capacity(29)
                .assignedPassengers(5)
                .status(VehicleStatus.MAINTENANCE)
                .operatorName("Halong New")
                .notes("Notes new")
                .build();
        assertThat(updVeh.getCapacity()).isEqualTo(29);

        // AddExcursionGuideRequest
        AddExcursionGuideRequest addGuide = AddExcursionGuideRequest.builder()
                .fullName("Lê Văn Nam")
                .phoneNumber("0977")
                .role(GuideRole.SPECIALIST)
                .languages(List.of("Japanese", "English"))
                .licenseNumber("LIC-JPN")
                .status(GuideStatus.STANDBY)
                .rating(4.7)
                .notes("Notes guide")
                .build();
        assertThat(addGuide.getFullName()).isEqualTo("Lê Văn Nam");

        // UpdateExcursionGuideRequest
        UpdateExcursionGuideRequest updGuide = UpdateExcursionGuideRequest.builder()
                .fullName("Lê Văn Nam Updated")
                .phoneNumber("0966")
                .role(GuideRole.SPECIALIST)
                .languages(List.of("Korean"))
                .licenseNumber("LIC-KOR")
                .status(GuideStatus.ASSIGNED)
                .rating(4.9)
                .notes("Notes guide updated")
                .build();
        assertThat(updGuide.getRole()).isEqualTo(GuideRole.SPECIALIST);

        // Enums coverage
        for (VehicleStatus s : VehicleStatus.values()) {
            assertThat(VehicleStatus.valueOf(s.name())).isEqualTo(s);
        }
        for (GuideRole r : GuideRole.values()) {
            assertThat(GuideRole.valueOf(r.name())).isEqualTo(r);
        }
        for (GuideStatus gs : GuideStatus.values()) {
            assertThat(GuideStatus.valueOf(gs.name())).isEqualTo(gs);
        }

        // Entity models
        ExcursionVehicle vehicle = ExcursionVehicle.builder()
                .vehicleCode("VEH-01")
                .build();
        vehicle.setId(1L);
        assertThat(vehicle.getVehicleCode()).isEqualTo("VEH-01");

        ExcursionGuide guide = ExcursionGuide.builder()
                .guideCode("GUD-01")
                .build();
        guide.setId(1L);
        assertThat(guide.getGuideCode()).isEqualTo("GUD-01");
    }
}
