package com.bookeasy.platform.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bookeasy.platform.entity.SupportSettings;

public interface SupportSettingsRepository extends JpaRepository<SupportSettings, Short> {
}
