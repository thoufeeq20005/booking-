package com.bookeasy.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.Locale;
import java.util.Properties;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Every language has every email key, and each body renders the name, the link and the duration. */
class MessageBundlesTest {

    private static Properties load(String suffix) throws Exception {
        Properties p = new Properties();
        try (var in = MessageBundlesTest.class.getResourceAsStream("/i18n/messages" + suffix + ".properties")) {
            assertThat(in).as("bundle %s", suffix).isNotNull();
            p.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        return p;
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "_ta", "_hi", "_ml", "_te", "_kn"})
    void bundleIsCompleteAndRendersPlaceholders(String suffix) throws Exception {
        Properties english = load("");
        Properties bundle = load(suffix);
        assertThat(bundle.stringPropertyNames()).isEqualTo(english.stringPropertyNames());

        for (String key : new String[]{"email.verify.body", "email.reset.body"}) {
            String rendered = new MessageFormat(bundle.getProperty(key), Locale.ROOT)
                    .format(new Object[]{"NAME", "https://x.test/?token=T", 24});
            assertThat(rendered).contains("NAME", "https://x.test/?token=T", "24").contains("\n");
            assertThat(rendered).doesNotContain("{0}", "{1}", "{2}");
        }
    }
}
