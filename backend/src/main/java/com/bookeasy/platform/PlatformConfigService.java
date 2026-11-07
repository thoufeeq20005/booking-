package com.bookeasy.platform;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bookeasy.common.exception.ApiException;
import com.bookeasy.common.exception.ErrorCode;
import com.bookeasy.platform.dto.PublicConfigResponse;
import com.bookeasy.platform.entity.PlatformSetting;
import com.bookeasy.platform.entity.SupportSettings;
import com.bookeasy.platform.repository.PlatformSettingRepository;
import com.bookeasy.platform.repository.SupportSettingsRepository;

@Service
public class PlatformConfigService {

    public static final String COMMISSION_PERCENT = "commission.percent";
    public static final String CURRENCY_CODE = "currency.code";
    public static final String LOCALE_SUPPORTED = "locale.supported";
    public static final String LOCALE_DEFAULT = "locale.default";
    public static final String TIMEZONE_DEFAULT = "timezone.default";

    private final PlatformSettingRepository settingRepository;
    private final SupportSettingsRepository supportRepository;

    public PlatformConfigService(PlatformSettingRepository settingRepository,
                                 SupportSettingsRepository supportRepository) {
        this.settingRepository = settingRepository;
        this.supportRepository = supportRepository;
    }

    @Transactional(readOnly = true)
    public PublicConfigResponse publicConfig() {
        Map<String, String> settings = settingRepository.findByPublicSettingTrue().stream()
                .collect(Collectors.toMap(PlatformSetting::getKey, PlatformSetting::getValue));
        Function<String, String> required = key -> {
            String value = settings.get(key);
            if (value == null) {
                throw new ApiException(ErrorCode.INTERNAL_ERROR, "Missing platform setting " + key);
            }
            return value;
        };
        SupportSettings support = supportRepository.findById(SupportSettings.SINGLETON_ID)
                .orElseThrow(() -> new ApiException(ErrorCode.INTERNAL_ERROR, "Support settings are not initialised"));

        return new PublicConfigResponse(
                required.apply(CURRENCY_CODE),
                new BigDecimal(required.apply(COMMISSION_PERCENT)),
                splitList(required.apply(LOCALE_SUPPORTED)),
                required.apply(LOCALE_DEFAULT),
                required.apply(TIMEZONE_DEFAULT),
                new PublicConfigResponse.Support(
                        support.getSupportEmail(),
                        support.getSupportPhone(),
                        splitList(support.getWorkingDays()),
                        support.getOpensAt(),
                        support.getClosesAt(),
                        support.getTimezone(),
                        support.isAvailable()));
    }

    /** A numeric setting; fails loudly when missing because money rules depend on it. */
    @Transactional(readOnly = true)
    public BigDecimal decimal(String key) {
        return settingRepository.findById(key).map(s -> new BigDecimal(s.getValue().trim()))
                .orElseThrow(() -> new ApiException(ErrorCode.INTERNAL_ERROR, "Missing platform setting " + key));
    }

    @Transactional(readOnly = true)
    public int integer(String key) {
        return decimal(key).intValueExact();
    }

    static List<String> splitList(String value) {
        return Arrays.stream(value.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }
}
