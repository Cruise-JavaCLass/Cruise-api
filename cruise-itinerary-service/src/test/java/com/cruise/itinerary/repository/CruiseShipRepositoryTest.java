package com.cruise.itinerary.repository;

import com.cruise.itinerary.entity.CruiseShip;
import com.cruise.itinerary.enums.ShipStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@DisplayName("CruiseShipRepository DataJpaTest Suite")
class CruiseShipRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private CruiseShipRepository shipRepository;

    private CruiseShip sampleShip;

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
    }

    @Test
    @DisplayName("Should persist ship and auto-generate ID and audit timestamps")
    void shouldPersistShipWithAuditTimestamps() {
        CruiseShip savedShip = shipRepository.save(sampleShip);
        entityManager.flush();

        assertThat(savedShip.getId()).isNotNull();
        assertThat(savedShip.getCreatedAt()).isNotNull();
        assertThat(savedShip.getUpdatedAt()).isNotNull();
        assertThat(savedShip.getName()).isEqualTo("Halong Grand Harmony");
        assertThat(savedShip.getCode()).isEqualTo("SHIP-001");
    }

    @Test
    @DisplayName("Should find ship by code")
    void shouldFindShipByCode() {
        entityManager.persistAndFlush(sampleShip);

        Optional<CruiseShip> found = shipRepository.findByCode("SHIP-001");

        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Halong Grand Harmony");
        assertThat(found.get().getCapacity()).isEqualTo(2400);
    }

    @Test
    @DisplayName("Should find ship by name")
    void shouldFindShipByName() {
        entityManager.persistAndFlush(sampleShip);

        Optional<CruiseShip> found = shipRepository.findByName("Halong Grand Harmony");

        assertThat(found).isPresent();
        assertThat(found.get().getCode()).isEqualTo("SHIP-001");
    }

    @Test
    @DisplayName("Should check existence by code and name")
    void shouldCheckExistenceByCodeAndName() {
        entityManager.persistAndFlush(sampleShip);

        assertThat(shipRepository.existsByCode("SHIP-001")).isTrue();
        assertThat(shipRepository.existsByCode("SHIP-999")).isFalse();
        assertThat(shipRepository.existsByName("Halong Grand Harmony")).isTrue();
        assertThat(shipRepository.existsByName("Non Existent Ship")).isFalse();
    }

    @Test
    @DisplayName("Should check existence excluding specific ID")
    void shouldCheckExistenceExcludingSpecificId() {
        CruiseShip persisted = entityManager.persistAndFlush(sampleShip);

        assertThat(shipRepository.existsByCodeAndIdNot("SHIP-001", persisted.getId())).isFalse();
        assertThat(shipRepository.existsByCodeAndIdNot("SHIP-001", 9999L)).isTrue();
        assertThat(shipRepository.existsByNameAndIdNot("Halong Grand Harmony", persisted.getId())).isFalse();
        assertThat(shipRepository.existsByNameAndIdNot("Halong Grand Harmony", 9999L)).isTrue();
    }

    @Test
    @DisplayName("Should find ships by status and active flag")
    void shouldFindShipsByStatusAndActive() {
        entityManager.persist(sampleShip);

        CruiseShip ship2 = CruiseShip.builder()
                .name("Ocean Majestic")
                .code("SHIP-002")
                .operator("Royal Caribbean")
                .capacity(3200)
                .crewCapacity(900)
                .totalCabins(1500)
                .totalDecks(16)
                .status(ShipStatus.UNDER_MAINTENANCE)
                .active(true)
                .build();
        entityManager.persist(ship2);

        CruiseShip ship3 = CruiseShip.builder()
                .name("Old Cruiser")
                .code("SHIP-003")
                .operator("Heritage Line")
                .capacity(800)
                .crewCapacity(200)
                .totalCabins(400)
                .totalDecks(8)
                .status(ShipStatus.INACTIVE)
                .active(false)
                .build();
        entityManager.persist(ship3);
        entityManager.flush();

        List<CruiseShip> activeStatusShips = shipRepository.findByStatus(ShipStatus.ACTIVE);
        List<CruiseShip> activeShips = shipRepository.findByActiveTrue();
        List<CruiseShip> maintenanceShips = shipRepository.findByStatusAndActiveTrue(ShipStatus.UNDER_MAINTENANCE);

        assertThat(activeStatusShips).hasSize(1);
        assertThat(activeShips).hasSize(2);
        assertThat(maintenanceShips).hasSize(1);
        assertThat(shipRepository.countByActiveTrue()).isEqualTo(2);
        assertThat(shipRepository.countByStatusAndActiveTrue(ShipStatus.ACTIVE)).isEqualTo(1);
    }

    @Test
    @DisplayName("Should search ships with keyword filter and pagination")
    void shouldSearchShipsWithKeywordFilterAndPagination() {
        entityManager.persist(sampleShip);

        CruiseShip ship2 = CruiseShip.builder()
                .name("Ocean Majestic")
                .code("SHIP-002")
                .operator("Royal Caribbean")
                .capacity(3200)
                .crewCapacity(900)
                .totalCabins(1500)
                .totalDecks(16)
                .status(ShipStatus.ACTIVE)
                .active(true)
                .build();
        entityManager.persist(ship2);
        entityManager.flush();

        Page<CruiseShip> searchResult = shipRepository.searchShips(
                "Harmony",
                null,
                true,
                PageRequest.of(0, 10)
        );

        assertThat(searchResult.getTotalElements()).isEqualTo(1);
        assertThat(searchResult.getContent().get(0).getCode()).isEqualTo("SHIP-001");

        Page<CruiseShip> searchByOperator = shipRepository.searchShips(
                "Royal",
                null,
                true,
                PageRequest.of(0, 10)
        );
        assertThat(searchByOperator.getTotalElements()).isEqualTo(1);
        assertThat(searchByOperator.getContent().get(0).getName()).isEqualTo("Ocean Majestic");
    }
}
