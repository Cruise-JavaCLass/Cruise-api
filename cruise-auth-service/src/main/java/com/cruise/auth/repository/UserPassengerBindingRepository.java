package com.cruise.auth.repository;

import com.cruise.auth.entity.UserPassengerBinding;
import com.cruise.auth.enums.VerificationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA Repository quản lý các thao tác truy vấn cho thực thể UserPassengerBinding.
 */
@Repository
public interface UserPassengerBindingRepository extends JpaRepository<UserPassengerBinding, Long> {

    Optional<UserPassengerBinding> findByUserIdAndBookingReference(Long userId, String bookingReference);

    Optional<UserPassengerBinding> findByUserIdAndBookingReferenceAndActiveTrue(Long userId, String bookingReference);

    List<UserPassengerBinding> findByUserId(Long userId);

    List<UserPassengerBinding> findByUserIdAndActiveTrue(Long userId);

    Optional<UserPassengerBinding> findByPassengerId(Long passengerId);

    Optional<UserPassengerBinding> findByPassengerIdAndActiveTrue(Long passengerId);

    Optional<UserPassengerBinding> findByUserIdAndPassengerId(Long userId, Long passengerId);

    Optional<UserPassengerBinding> findByUserIdAndPassengerIdAndActiveTrue(Long userId, Long passengerId);

    List<UserPassengerBinding> findByCabinNumber(String cabinNumber);

    List<UserPassengerBinding> findByVerificationStatus(VerificationStatus verificationStatus);

    boolean existsByUserIdAndBookingReference(Long userId, String bookingReference);

    boolean existsByPassengerId(Long passengerId);
}
