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

        for (String action : new String[]{"submitted", "approved", "rejected", "suspended", "reinstated"}) {
            String subject = new MessageFormat(bundle.getProperty("email.org." + action + ".subject"), Locale.ROOT)
                    .format(new Object[]{"BIZ"});
            assertThat(subject).as("%s subject %s", suffix, action).contains("BIZ");
            String body = new MessageFormat(bundle.getProperty("email.org." + action + ".body"), Locale.ROOT)
                    .format(new Object[]{"NAME", "BIZ", "WHY", "https://x.test/owner/organizations/1"});
            assertThat(body).as("%s body %s", suffix, action)
                    .contains("NAME", "BIZ", "https://x.test/owner/organizations/1").doesNotContain("{");
            if (action.equals("rejected") || action.equals("suspended")) {
                assertThat(body).contains("WHY");
            }
        }
    }
}
