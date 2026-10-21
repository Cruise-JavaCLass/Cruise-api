package com.cruise.activity.service;

import com.cruise.activity.dto.*;
import com.cruise.activity.entity.EmbarkationCheckInLog;
import com.cruise.activity.entity.Passenger;
import com.cruise.activity.enums.CheckInStatus;
import com.cruise.activity.exception.DuplicateCheckInException;
import com.cruise.activity.exception.DuplicateRegistrationException;
import com.cruise.activity.exception.ResourceNotFoundException;
import com.cruise.activity.repository.EmbarkationCheckInLogRepository;
import com.cruise.activity.repository.PassengerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("EmbarkationService Unit & Business Rule Tests")
class EmbarkationServiceTest {

    @Mock
    private PassengerRepository passengerRepository;

    @Mock
    private EmbarkationCheckInLogRepository embarkationCheckInLogRepository;

    @InjectMocks
    private EmbarkationService embarkationService;

    private Passenger testPassenger;

    @BeforeEach
    void setUp() {
        testPassenger = Passenger.builder()
                .tourId(101L)
                .bookingCode("BK-998811")
                .fullName("Nguyen Van A")
                .passportNumber("B1234567")
                .nationality("Vietnam")
                .cabinNumber("Suite-801")
                .cabinCategory("Royal Suite")
                .isVip(true)
                .healthDeclarationCompleted(true)
                .rfidTag("RFID-INITIAL-01")
                .qrCode("QR-PASS-NguyenVanA")
                .checkInStatus(CheckInStatus.NOT_CHECKED_IN)
                .folioStatus("INACTIVE")
                .active(true)
                .build();
        testPassenger.setId(1L);
    }

    @Nested
    @DisplayName("Passenger Identity Lookup Tests (AC1)")
    class LookupTests {

        @Test
        @DisplayName("Should successfully return embarkation profile when keyword matches exact identifier")
        void lookupPassenger_SuccessExactIdentifier() {
            when(passengerRepository.findExactIdentifierForEmbarkation("B1234567"))
                    .thenReturn(List.of(testPassenger));

            PassengerEmbarkationProfileDto result = embarkationService.lookupPassenger("B1234567");

            assertThat(result).isNotNull();
            assertThat(result.getFullName()).isEqualTo("Nguyen Van A");
            assertThat(result.getPassportNumber()).isEqualTo("B1234567");
            assertThat(result.getCabinNumber()).isEqualTo("Suite-801");
            assertThat(result.getIsVip()).isTrue();
            assertThat(result.getCheckInStatus()).isEqualTo(CheckInStatus.NOT_CHECKED_IN);
        }

        @Test
        @DisplayName("Should fallback to partial fullName matching and escape LIKE wildcards")
        void lookupPassenger_SuccessFullNameFallback() {
            when(passengerRepository.findExactIdentifierForEmbarkation("Nguyen%_\\"))
                    .thenReturn(List.of());
            when(passengerRepository.findByFullNameLikeIgnoreCaseForEmbarkation("Nguyen\\%\\_\\\\"))
                    .thenReturn(List.of(testPassenger));

            PassengerEmbarkationProfileDto result = embarkationService.lookupPassenger("Nguyen%_\\");

            assertThat(result).isNotNull();
            assertThat(result.getFullName()).isEqualTo("Nguyen Van A");
        }

        @Test
        @DisplayName("Should throw DuplicateRegistrationException when multiple passengers match exact identifier")
        void lookupPassenger_DuplicateExactIdentifier() {
            Passenger second = Passenger.builder().fullName("Duplicate Guest").build();
            second.setId(2L);
            when(passengerRepository.findExactIdentifierForEmbarkation("DUPLICATE_CODE"))
                    .thenReturn(List.of(testPassenger, second));

            assertThatThrownBy(() -> embarkationService.lookupPassenger("DUPLICATE_CODE"))
                    .isInstanceOf(DuplicateRegistrationException.class)
                    .hasMessageContaining("nhiều hơn một hành khách");
        }

        @Test
        @DisplayName("Should throw DuplicateRegistrationException when multiple passengers match fullName")
        void lookupPassenger_DuplicateFullName() {
            Passenger second = Passenger.builder().fullName("Nguyen Van A 2").build();
            second.setId(2L);
            when(passengerRepository.findExactIdentifierForEmbarkation("Nguyen"))
                    .thenReturn(List.of());
            when(passengerRepository.findByFullNameLikeIgnoreCaseForEmbarkation("Nguyen"))
                    .thenReturn(List.of(testPassenger, second));

            assertThatThrownBy(() -> embarkationService.lookupPassenger("Nguyen"))
                    .isInstanceOf(DuplicateRegistrationException.class)
                    .hasMessageContaining("nhiều hơn một hành khách");
        }

        @Test
        @DisplayName("Should throw ResourceNotFoundException when no passenger matches keyword")
        void lookupPassenger_NotFound() {
            when(passengerRepository.findExactIdentifierForEmbarkation("UNKNOWN"))
                    .thenReturn(List.of());
            when(passengerRepository.findByFullNameLikeIgnoreCaseForEmbarkation("UNKNOWN"))
                    .thenReturn(List.of());

            assertThatThrownBy(() -> embarkationService.lookupPassenger("UNKNOWN"))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Không tìm thấy thông tin hành khách");
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException when keyword is null or blank")
        void lookupPassenger_BlankKeyword() {
            assertThatThrownBy(() -> embarkationService.lookupPassenger("   "))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> embarkationService.lookupPassenger(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("Embarkation Check-In Transition Tests (AC3, AC4, AC6)")
    class CheckInTests {

        @Test
        @DisplayName("Should transition state to BOARDED, activate Folio, and log audit entry using pessimistic lock")
        void processCheckIn_Success() {
            when(passengerRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(testPassenger));
            when(passengerRepository.saveAndFlush(any(Passenger.class))).thenAnswer(invocation -> invocation.getArgument(0));

            EmbarkationCheckInRequest req = EmbarkationCheckInRequest.builder()
                    .staffId("STAFF-TERMINAL-01")
                    .terminalDeviceId("DEVICE-GATE-A")
                    .rfidTag("RFID-NEW-88")
                    .gateLane("VIP-LANE-1")
                    .verificationMethod("FACIAL_RECOGNITION")
                    .build();

            when(passengerRepository.findByRfidTag("RFID-NEW-88")).thenReturn(Optional.empty());

            EmbarkationCheckInResponse response = embarkationService.processCheckIn(1L, req);

            assertThat(response).isNotNull();
            assertThat(response.getCheckInStatus()).isEqualTo(CheckInStatus.BOARDED);
            assertThat(response.getBoardedAt()).isNotNull();
            assertThat(response.getFolioStatus()).isEqualTo("ACTIVE");
            assertThat(response.getRfidTag()).isEqualTo("RFID-NEW-88");

            ArgumentCaptor<EmbarkationCheckInLog> logCaptor = ArgumentCaptor.forClass(EmbarkationCheckInLog.class);
            verify(embarkationCheckInLogRepository).save(logCaptor.capture());
            EmbarkationCheckInLog savedLog = logCaptor.getValue();
            assertThat(savedLog.getPassengerId()).isEqualTo(1L);
            assertThat(savedLog.getStaffId()).isEqualTo("STAFF-TERMINAL-01");
            assertThat(savedLog.getGateLane()).isEqualTo("VIP-LANE-1");
        }

        @Test
        @DisplayName("Should process check-in without request payload (null request and empty RFID tag)")
        void processCheckIn_NullRequest() {
            when(passengerRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(testPassenger));
            when(passengerRepository.saveAndFlush(any(Passenger.class))).thenAnswer(invocation -> invocation.getArgument(0));

            EmbarkationCheckInResponse response = embarkationService.processCheckIn(1L, null);

            assertThat(response).isNotNull();
            assertThat(response.getCheckInStatus()).isEqualTo(CheckInStatus.BOARDED);
            assertThat(response.getFolioStatus()).isEqualTo("ACTIVE");
            verify(embarkationCheckInLogRepository).save(any(EmbarkationCheckInLog.class));
        }

        @Test
        @DisplayName("Should throw DuplicateCheckInException when passenger is already BOARDED (AC4)")
        void processCheckIn_DuplicateBoarded() {
            testPassenger.setCheckInStatus(CheckInStatus.BOARDED);
            testPassenger.setBoardedAt(Instant.now());
            when(passengerRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(testPassenger));

            assertThatThrownBy(() -> embarkationService.processCheckIn(1L, EmbarkationCheckInRequest.builder().build()))
                    .isInstanceOf(DuplicateCheckInException.class)
                    .hasMessageContaining("đã làm thủ tục lên tàu");

            // Also test when boardedAt is null
            testPassenger.setBoardedAt(null);
            assertThatThrownBy(() -> embarkationService.processCheckIn(1L, EmbarkationCheckInRequest.builder().build()))
                    .isInstanceOf(DuplicateCheckInException.class)
                    .hasMessageContaining("không xác định");

            verify(embarkationCheckInLogRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw DuplicateRegistrationException if check-in requests an RFID tag already assigned to another passenger")
        void processCheckIn_DuplicateRfidConflict() {
            when(passengerRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(testPassenger));

            Passenger other = Passenger.builder().fullName("Tran Thi B").build();
            other.setId(2L);
            when(passengerRepository.findByRfidTag("RFID-TAKEN")).thenReturn(Optional.of(other));

            EmbarkationCheckInRequest req = EmbarkationCheckInRequest.builder()
                    .rfidTag("RFID-TAKEN")
                    .build();

            assertThatThrownBy(() -> embarkationService.processCheckIn(1L, req))
                    .isInstanceOf(DuplicateRegistrationException.class)
                    .hasMessageContaining("đã được gán cho hành khách Tran Thi B");
        }

        @Test
        @DisplayName("Should allow keeping same RFID tag if assigned to self during check-in")
        void processCheckIn_SameRfidTagToSelf() {
            when(passengerRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(testPassenger));
            when(passengerRepository.saveAndFlush(any(Passenger.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(passengerRepository.findByRfidTag("RFID-INITIAL-01")).thenReturn(Optional.of(testPassenger));

            EmbarkationCheckInRequest req = EmbarkationCheckInRequest.builder()
                    .rfidTag("RFID-INITIAL-01")
                    .build();

            EmbarkationCheckInResponse response = embarkationService.processCheckIn(1L, req);
            assertThat(response).isNotNull();
            assertThat(response.getCheckInStatus()).isEqualTo(CheckInStatus.BOARDED);
        }

        @Test
        @DisplayName("Should catch DataIntegrityViolationException on saveAndFlush during check-in and translate RFID duplicate")
        void processCheckIn_RfidUniqueConstraintViolation() {
            when(passengerRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(testPassenger));
            when(passengerRepository.findByRfidTag("RFID-CONCURRENT")).thenReturn(Optional.empty());
            when(passengerRepository.saveAndFlush(any(Passenger.class)))
                    .thenThrow(new DataIntegrityViolationException("duplicate key value violates unique constraint idx_passenger_rfid_tag"));

            EmbarkationCheckInRequest req = EmbarkationCheckInRequest.builder()
                    .rfidTag("RFID-CONCURRENT")
                    .build();

            assertThatThrownBy(() -> embarkationService.processCheckIn(1L, req))
                    .isInstanceOf(DuplicateRegistrationException.class)
                    .hasMessageContaining("Mã thẻ RFID RFID-CONCURRENT đã được gán cho hành khách khác");
        }

        @Test
        @DisplayName("Should propagate unrelated DataIntegrityViolationException during check-in")
        void processCheckIn_UnrelatedDataIntegrityViolation() {
            when(passengerRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(testPassenger));
            when(passengerRepository.findByRfidTag("RFID-OK")).thenReturn(Optional.empty());
            when(passengerRepository.saveAndFlush(any(Passenger.class)))
                    .thenThrow(new DataIntegrityViolationException("null value in column tour_id violates not-null constraint"));

            EmbarkationCheckInRequest req = EmbarkationCheckInRequest.builder()
                    .rfidTag("RFID-OK")
                    .build();

            assertThatThrownBy(() -> embarkationService.processCheckIn(1L, req))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    @Nested
    @DisplayName("RFID Tag Binding & Folio Activation Tests (AC2, AC3)")
    class RfidBindingTests {

        @Test
        @DisplayName("Should atomically bind RFID tag and activate cashless folio account using pessimistic lock")
        void bindRfid_Success() {
            when(passengerRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(testPassenger));
            when(passengerRepository.findByRfidTag("RFID-CARD-100")).thenReturn(Optional.empty());
            when(passengerRepository.saveAndFlush(any(Passenger.class))).thenAnswer(invocation -> invocation.getArgument(0));

            BindRfidRequest req = BindRfidRequest.builder()
                    .rfidTag("RFID-CARD-100")
                    .qrCode("QR-NEW-99")
                    .activateFolio(true)
                    .build();

            BindRfidResponse response = embarkationService.bindRfid(1L, req);

            assertThat(response).isNotNull();
            assertThat(response.getRfidTag()).isEqualTo("RFID-CARD-100");
            assertThat(response.getQrCode()).isEqualTo("QR-NEW-99");
            assertThat(response.getFolioStatus()).isEqualTo("ACTIVE");
        }

        @Test
        @DisplayName("Should bind RFID and activate folio if folioStatus is INACTIVE even without activateFolio flag")
        void bindRfid_InactiveFolioAutoActivation() {
            testPassenger.setFolioStatus("INACTIVE");
            when(passengerRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(testPassenger));
            when(passengerRepository.findByRfidTag("RFID-NEW-002")).thenReturn(Optional.empty());
            when(passengerRepository.saveAndFlush(any(Passenger.class))).thenAnswer(invocation -> invocation.getArgument(0));

            BindRfidRequest req = BindRfidRequest.builder()
                    .rfidTag("RFID-NEW-002")
                    .activateFolio(false)
                    .build();

            BindRfidResponse response = embarkationService.bindRfid(1L, req);
            assertThat(response.getFolioStatus()).isEqualTo("ACTIVE");
        }

        @Test
        @DisplayName("Should reject with 409 Conflict when RFID tag is already bound to another passenger (AC2)")
        void bindRfid_Conflict() {
            when(passengerRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(testPassenger));

            Passenger existing = Passenger.builder().fullName("Le Van C").build();
            existing.setId(3L);
            when(passengerRepository.findByRfidTag("RFID-DUPLICATE")).thenReturn(Optional.of(existing));

            BindRfidRequest req = BindRfidRequest.builder()
                    .rfidTag("RFID-DUPLICATE")
                    .build();

            assertThatThrownBy(() -> embarkationService.bindRfid(1L, req))
                    .isInstanceOf(DuplicateRegistrationException.class)
                    .hasMessageContaining("đã được gán cho hành khách khác: Le Van C");

            verify(passengerRepository, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("Should throw ResourceNotFoundException when passenger not found during bindRfid")
        void bindRfid_NotFound() {
            when(passengerRepository.findByIdForUpdate(999L)).thenReturn(Optional.empty());

            BindRfidRequest req = BindRfidRequest.builder().rfidTag("TAG").build();
            assertThatThrownBy(() -> embarkationService.bindRfid(999L, req))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("Should catch DataIntegrityViolationException on saveAndFlush during bindRfid and translate RFID duplicate")
        void bindRfid_RfidUniqueConstraintViolation() {
            when(passengerRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(testPassenger));
            when(passengerRepository.findByRfidTag("RFID-RACE")).thenReturn(Optional.empty());
            when(passengerRepository.saveAndFlush(any(Passenger.class)))
                    .thenThrow(new DataIntegrityViolationException("duplicate key value violates unique constraint rfid_tag"));

            BindRfidRequest req = BindRfidRequest.builder()
                    .rfidTag("RFID-RACE")
                    .build();

            assertThatThrownBy(() -> embarkationService.bindRfid(1L, req))
                    .isInstanceOf(DuplicateRegistrationException.class)
                    .hasMessageContaining("Mã thẻ RFID RFID-RACE đã được gán cho hành khách khác");
        }

        @Test
        @DisplayName("Should propagate unrelated DataIntegrityViolationException during bindRfid")
        void bindRfid_UnrelatedDataIntegrityViolation() {
            when(passengerRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(testPassenger));
            when(passengerRepository.findByRfidTag("RFID-OK")).thenReturn(Optional.empty());
            when(passengerRepository.saveAndFlush(any(Passenger.class)))
                    .thenThrow(new DataIntegrityViolationException("foreign key violation"));

            BindRfidRequest req = BindRfidRequest.builder()
                    .rfidTag("RFID-OK")
                    .build();

            assertThatThrownBy(() -> embarkationService.bindRfid(1L, req))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    @Nested
    @DisplayName("Embarkation Manifest & Summary Tests (AC5)")
    class ManifestAndSummaryTests {

        @Test
        @DisplayName("Should calculate expected, boarded, remaining counts and category breakdown")
        void getEmbarkationSummary_Success() {
            Passenger boardedGuest = Passenger.builder()
                    .tourId(101L)
                    .checkInStatus(CheckInStatus.BOARDED)
                    .cabinCategory("Royal Suite")
                    .isVip(true)
                    .build();
            Passenger pendingGuest = Passenger.builder()
                    .tourId(101L)
                    .checkInStatus(CheckInStatus.NOT_CHECKED_IN)
                    .cabinCategory("Standard Stateroom")
                    .isVip(false)
                    .build();

            when(passengerRepository.findByTourIdAndActiveTrue(101L))
                    .thenReturn(List.of(boardedGuest, pendingGuest));

            EmbarkationSummaryDto summary = embarkationService.getEmbarkationSummary(101L);

            assertThat(summary.getExpectedTotal()).isEqualTo(2);
            assertThat(summary.getBoardedCount()).isEqualTo(1);
            assertThat(summary.getRemainingCount()).isEqualTo(1);
            assertThat(summary.getBoardingProgressPercent()).isEqualTo(50.0);
            assertThat(summary.getVipTotal()).isEqualTo(1);
            assertThat(summary.getVipBoarded()).isEqualTo(1);
            assertThat(summary.getCategoryBreakdown()).containsEntry("Royal Suite", 1L);
        }

        @Test
        @DisplayName("Should handle empty passenger list in getEmbarkationSummary")
        void getEmbarkationSummary_EmptyList() {
            when(passengerRepository.findByTourIdAndActiveTrue(101L)).thenReturn(List.of());

            EmbarkationSummaryDto summary = embarkationService.getEmbarkationSummary(101L);

            assertThat(summary.getExpectedTotal()).isZero();
            assertThat(summary.getBoardingProgressPercent()).isZero();
        }

        @Test
        @DisplayName("Should export border control manifest CSV with UTF-8 BOM and escaped fields")
        void exportBorderControlManifestCsv_Success() {
            Passenger guestWithSpecialChars = Passenger.builder()
                    .tourId(101L)
                    .bookingCode("BK,101")
                    .fullName("Nguyen \"Quang\" A")
                    .passportNumber("B123\n45")
                    .nationality("Vietnam\r")
                    .cabinNumber("Suite 101")
                    .cabinCategory(null)
                    .isVip(null)
                    .healthDeclarationCompleted(null)
                    .checkInStatus(null)
                    .boardedAt(null)
                    .folioStatus("INACTIVE")
                    .build();
            guestWithSpecialChars.setId(2L);

            when(passengerRepository.findByTourIdAndActiveTrue(101L))
                    .thenReturn(List.of(testPassenger, guestWithSpecialChars));

            byte[] csvBytes = embarkationService.exportBorderControlManifestCsv(101L);

            assertThat(csvBytes).isNotEmpty();
            String csvContent = new String(csvBytes, java.nio.charset.StandardCharsets.UTF_8);
            assertThat(csvContent).startsWith("\ufeffID,BookingCode,FullName");
            assertThat(csvContent).contains("Nguyen Van A");
            assertThat(csvContent).contains("\"Nguyen \"\"Quang\"\" A\"");
            assertThat(csvContent).contains("\"BK,101\"");
        }

        @Test
        @DisplayName("Should map profile DTO with default cabin category when null")
        void toProfileDto_DefaultCategory() {
            testPassenger.setCabinCategory(null);
            PassengerEmbarkationProfileDto dto = embarkationService.toProfileDto(testPassenger);
            assertThat(dto.getCabinCategory()).isEqualTo("Standard Stateroom");
        }
    }
}
