package com.bookeasy.platform.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bookeasy.platform.entity.PlatformSetting;

public interface PlatformSettingRepository extends JpaRepository<PlatformSetting, String> {

    List<PlatformSetting> findByPublicSettingTrue();
}
