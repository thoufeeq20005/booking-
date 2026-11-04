package com.bookeasy.platform;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PlatformConfigServiceTest {

    @Test
    void splitListTrimsAndDropsEmptyEntries() {
        assertThat(PlatformConfigService.splitList(" en, ta ,,hi ")).containsExactly("en", "ta", "hi");
    }
}
