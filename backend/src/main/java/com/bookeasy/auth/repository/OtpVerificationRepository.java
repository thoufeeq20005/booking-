package com.bookeasy.auth.repository;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.bookeasy.auth.entity.OtpPurpose;
import com.bookeasy.auth.entity.OtpVerification;
import jakarta.persistence.LockModeType;

public interface OtpVerificationRepository extends JpaRepository<OtpVerification, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<OtpVerification> findFirstByPhoneAndPurposeOrderByCreatedAtDesc(String phone, OtpPurpose purpose);

    long countByPhoneAndPurposeAndCreatedAtAfter(String phone, OtpPurpose purpose, Instant after);

    @Modifying
    @Query("delete from OtpVerification o where o.expiresAt < :cutoff")
    int deleteExpiredBefore(@Param("cutoff") Instant cutoff);
}
