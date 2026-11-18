package com.cruise.auth.repository;

import com.cruise.auth.entity.UserPassengerBinding;
import com.cruise.auth.enums.VerificationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@DisplayName("UserPassengerBindingRepository DataJpaTest Suite (KAN-719)")
class UserPassengerBindingRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private UserPassengerBindingRepository bindingRepository;

    private UserPassengerBinding sampleBinding;

    @BeforeEach
    void setUp() {
        sampleBinding = UserPassengerBinding.builder()
                .userId(1001L)
                .passengerId(2001L)
                .bookingReference("CRUISE-2026-HALONG-001")
                .cabinNumber("CAB-402")
                .verificationStatus(VerificationStatus.PENDING)
                .active(true)
                .build();
    }

    @Test
    @DisplayName("Tiêu chí 1: Lưu thành công bản ghi liên kết, kiểm tra cabinNumber và verificationStatus")
    void shouldSaveUserPassengerBindingSuccessfully() {
        // Given & When: Lưu bản ghi liên kết vào cơ sở dữ liệu
        UserPassengerBinding savedBinding = bindingRepository.save(sampleBinding);
        entityManager.flush();
        entityManager.clear();

        // Then: Kiểm tra thông tin được lưu đầy đủ, sinh ID, audit timestamps và các trường đặc tả KAN-719
        assertThat(savedBinding.getId()).isNotNull();
        assertThat(savedBinding.getCreatedAt()).isNotNull();
        assertThat(savedBinding.getUpdatedAt()).isNotNull();
        assertThat(savedBinding.getUserId()).isEqualTo(1001L);
        assertThat(savedBinding.getPassengerId()).isEqualTo(2001L);
        assertThat(savedBinding.getBookingReference()).isEqualTo("CRUISE-2026-HALONG-001");
        assertThat(savedBinding.getCabinNumber()).isEqualTo("CAB-402");
        assertThat(savedBinding.getVerificationStatus()).isEqualTo(VerificationStatus.PENDING);
        assertThat(savedBinding.getActive()).isTrue();

        // Kiểm tra truy vấn lại từ repository
        Optional<UserPassengerBinding> found = bindingRepository.findById(savedBinding.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getUserId()).isEqualTo(1001L);
        assertThat(found.get().getPassengerId()).isEqualTo(2001L);
        assertThat(found.get().getBookingReference()).isEqualTo("CRUISE-2026-HALONG-001");
        assertThat(found.get().getCabinNumber()).isEqualTo("CAB-402");
        assertThat(found.get().getVerificationStatus()).isEqualTo(VerificationStatus.PENDING);

        // Kiểm tra cập nhật trạng thái sang VERIFIED
        UserPassengerBinding bindingToVerify = found.get();
        bindingToVerify.setVerificationStatus(VerificationStatus.VERIFIED);
        bindingRepository.saveAndFlush(bindingToVerify);

        Optional<UserPassengerBinding> verified = bindingRepository.findById(savedBinding.getId());
        assertThat(verified).isPresent();
        assertThat(verified.get().getVerificationStatus()).isEqualTo(VerificationStatus.VERIFIED);

        // Kiểm tra cập nhật trạng thái sang REVOKED
        UserPassengerBinding bindingToRevoke = verified.get();
        bindingToRevoke.setVerificationStatus(VerificationStatus.REVOKED);
        bindingRepository.saveAndFlush(bindingToRevoke);

        Optional<UserPassengerBinding> revoked = bindingRepository.findById(savedBinding.getId());
        assertThat(revoked).isPresent();
        assertThat(revoked.get().getVerificationStatus()).isEqualTo(VerificationStatus.REVOKED);
    }

    @Test
    @DisplayName("Tiêu chí 2: Ném ngoại lệ DataIntegrityViolationException khi cố tình gắn 1 userId với 2 hồ sơ hành khách trong cùng một chuyến đi (bookingReference)")
    void shouldThrowDataIntegrityViolationExceptionWhenBindingSameUserToMultiplePassengersInSameTrip() {
        // Given: Đã lưu bản ghi liên kết đầu tiên (userId=1001 gắn với passengerId=2001 trong chuyến đi CRUISE-2026-HALONG-001)
        bindingRepository.saveAndFlush(sampleBinding);

        // When: Cố tình tạo bản ghi thứ hai gắn cùng userId=1001 với hồ sơ hành khách khác (passengerId=2002) trong cùng chuyến đi
        UserPassengerBinding duplicateUserBinding = UserPassengerBinding.builder()
                .userId(1001L)
                .passengerId(2002L)
                .bookingReference("CRUISE-2026-HALONG-001")
                .cabinNumber("CAB-402")
                .verificationStatus(VerificationStatus.PENDING)
                .active(true)
                .build();

        // Then: Hệ thống vi phạm ràng buộc uk_user_passenger_binding_user_booking và ném DataIntegrityViolationException
        assertThatThrownBy(() -> bindingRepository.saveAndFlush(duplicateUserBinding))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Ném ngoại lệ DataIntegrityViolationException khi gắn cùng 1 passengerId với nhiều bản ghi")
    void shouldThrowDataIntegrityViolationExceptionWhenPassengerIdAlreadyBound() {
        // Given: Đã lưu bản ghi liên kết cho passengerId=2001
        bindingRepository.saveAndFlush(sampleBinding);

        // When: Cố tình tạo bản ghi liên kết khác nhưng dùng lại passengerId=2001 cho userId=1002 ở chuyến đi khác
        UserPassengerBinding duplicatePassengerBinding = UserPassengerBinding.builder()
                .userId(1002L)
                .passengerId(2001L)
                .bookingReference("CRUISE-2026-CATBA-002")
                .cabinNumber("CAB-402")
                .verificationStatus(VerificationStatus.PENDING)
                .active(true)
                .build();

        // Then: Hệ thống vi phạm ràng buộc duy nhất uk_user_passenger_binding_passenger và ném DataIntegrityViolationException
        assertThatThrownBy(() -> bindingRepository.saveAndFlush(duplicatePassengerBinding))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Cho phép 1 userId liên kết với hồ sơ hành khách ở các chuyến đi (bookingReference) khác nhau")
    void shouldAllowSameUserToBindPassengersInDifferentTrips() {
        // Given: Đã lưu liên kết cho chuyến đi 1
        bindingRepository.saveAndFlush(sampleBinding);

        // When: Cùng userId=1001 nhưng liên kết với hồ sơ hành khách khác (passengerId=2002) ở chuyến đi khác
        UserPassengerBinding differentTripBinding = UserPassengerBinding.builder()
                .userId(1001L)
                .passengerId(2002L)
                .bookingReference("CRUISE-2026-CATBA-002")
                .cabinNumber("CAB-402")
                .verificationStatus(VerificationStatus.VERIFIED)
                .active(true)
                .build();
        UserPassengerBinding savedSecond = bindingRepository.saveAndFlush(differentTripBinding);

        // Then: Lưu thành công cả 2 chuyến đi khác nhau
        assertThat(savedSecond.getId()).isNotNull();
        assertThat(savedSecond.getBookingReference()).isEqualTo("CRUISE-2026-CATBA-002");
        assertThat(savedSecond.getCabinNumber()).isEqualTo("CAB-402");
        assertThat(savedSecond.getVerificationStatus()).isEqualTo(VerificationStatus.VERIFIED);
        assertThat(bindingRepository.findByUserId(1001L)).hasSize(2);
    }

    @Test
    @DisplayName("Cho phép nhiều userId khác nhau gắn với các hồ sơ hành khách khác nhau trong cùng một chuyến đi")
    void shouldAllowDifferentUsersInSameTrip() {
        // Given: Đã lưu liên kết cho userId=1001
        bindingRepository.saveAndFlush(sampleBinding);

        // When: userId=1002 liên kết với passengerId=2002 trong cùng chuyến đi CRUISE-2026-HALONG-001
        UserPassengerBinding anotherUserBinding = UserPassengerBinding.builder()
                .userId(1002L)
                .passengerId(2002L)
                .bookingReference("CRUISE-2026-HALONG-001")
                .cabinNumber("CAB-402")
                .verificationStatus(VerificationStatus.REVOKED)
                .active(true)
                .build();
        UserPassengerBinding saved = bindingRepository.saveAndFlush(anotherUserBinding);

        // Then: Lưu thành công
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getUserId()).isEqualTo(1002L);
        assertThat(saved.getCabinNumber()).isEqualTo("CAB-402");
        assertThat(saved.getVerificationStatus()).isEqualTo(VerificationStatus.REVOKED);
        assertThat(bindingRepository.findAll()).hasSize(2);
    }

    @Test
    @DisplayName("Truy vấn các liên kết theo trạng thái verificationStatus (PENDING, VERIFIED, REVOKED)")
    void shouldQueryBindingsByVerificationStatus() {
        // Given: Lưu 3 bản ghi với 3 trạng thái xác minh khác nhau
        UserPassengerBinding pending = sampleBinding; // PENDING

        UserPassengerBinding verified = UserPassengerBinding.builder()
                .userId(1002L)
                .passengerId(2002L)
                .bookingReference("CRUISE-2026-HALONG-001")
                .cabinNumber("CAB-402")
                .verificationStatus(VerificationStatus.VERIFIED)
                .build();

        UserPassengerBinding revoked = UserPassengerBinding.builder()
                .userId(1003L)
                .passengerId(2003L)
                .bookingReference("CRUISE-2026-HALONG-001")
                .cabinNumber("CAB-402")
                .verificationStatus(VerificationStatus.REVOKED)
                .build();

        bindingRepository.save(pending);
        bindingRepository.save(verified);
        bindingRepository.save(revoked);
        entityManager.flush();

        // When & Then: Kiểm tra truy vấn danh sách theo từng trạng thái
        List<UserPassengerBinding> pendingList = bindingRepository.findByVerificationStatus(VerificationStatus.PENDING);
        List<UserPassengerBinding> verifiedList = bindingRepository.findByVerificationStatus(VerificationStatus.VERIFIED);
        List<UserPassengerBinding> revokedList = bindingRepository.findByVerificationStatus(VerificationStatus.REVOKED);

        assertThat(pendingList).hasSize(1);
        assertThat(pendingList.get(0).getVerificationStatus()).isEqualTo(VerificationStatus.PENDING);
        assertThat(pendingList.get(0).getCabinNumber()).isEqualTo("CAB-402");

        assertThat(verifiedList).hasSize(1);
        assertThat(verifiedList.get(0).getVerificationStatus()).isEqualTo(VerificationStatus.VERIFIED);

        assertThat(revokedList).hasSize(1);
        assertThat(revokedList.get(0).getVerificationStatus()).isEqualTo(VerificationStatus.REVOKED);
    }
}
