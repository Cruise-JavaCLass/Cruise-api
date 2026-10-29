package com.cruise.activity.repository;

import com.cruise.activity.entity.TourProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface TourProviderRepository extends JpaRepository<TourProvider, UUID> {

    boolean existsByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCaseAndIdNot(String code, UUID id);

    boolean existsByTaxCodeIgnoreCase(String taxCode);

    boolean existsByTaxCodeIgnoreCaseAndIdNot(String taxCode, UUID id);

    boolean existsByLicenseNumberIgnoreCase(String licenseNumber);

    boolean existsByLicenseNumberIgnoreCaseAndIdNot(String licenseNumber, UUID id);

    Page<TourProvider> findAllByPortId(String portId, Pageable pageable);
}
