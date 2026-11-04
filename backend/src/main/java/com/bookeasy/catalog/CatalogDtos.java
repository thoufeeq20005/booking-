package com.bookeasy.catalog;

import java.math.BigDecimal;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class CatalogDtos {

    private CatalogDtos() {
    }

    public record BranchOffer(@NotNull Long branchId,
                              @DecimalMin("0.00") @DecimalMax("9999999.99") @Digits(integer = 10, fraction = 2)
                              BigDecimal priceOverride) {
    }

    public record ServiceRequest(
            @NotBlank @Size(max = 150) String name,
            @Size(max = 1000) String description,
            @Min(5) @Max(720) int durationMinutes,
            @NotNull @DecimalMin("0.00") @DecimalMax("9999999.99") @Digits(integer = 10, fraction = 2) BigDecimal price,
            ServiceOffering.ConsultationType consultationType,
            Boolean requiresStaff,
            @Min(0) @Max(240) Integer bufferMinutes,
            @Min(5) @Max(240) Integer slotIntervalMinutes,
            @Min(1) @Max(365) Integer maxAdvanceDays,
            @Min(0) @Max(20160) Integer minNoticeMinutes,
            Boolean active,
            @NotNull @Size(max = 50) List<@Valid @NotNull BranchOffer> branches) {
    }

    public record ServiceView(long id, String name, String description, int durationMinutes, BigDecimal price,
                              ServiceOffering.ConsultationType consultationType, boolean requiresStaff,
                              int bufferMinutes, int slotIntervalMinutes, int maxAdvanceDays, int minNoticeMinutes,
                              boolean active, List<BranchOffer> branches) {

        public static ServiceView of(ServiceOffering s, List<BranchOffer> branches) {
            return new ServiceView(s.getId(), s.getName(), s.getDescription(), s.getDurationMinutes(), s.getPrice(),
                    s.getConsultationType(), s.isRequiresStaff(), s.getBufferMinutes(), s.getSlotIntervalMinutes(),
                    s.getMaxAdvanceDays(), s.getMinNoticeMinutes(), s.isActive(), branches);
        }
    }
}
