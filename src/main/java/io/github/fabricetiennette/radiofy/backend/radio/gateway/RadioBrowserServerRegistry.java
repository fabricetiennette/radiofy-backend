package io.github.fabricetiennette.radiofy.backend.radio.gateway;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.InitialDirContext;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Hashtable;
import java.util.List;

/// Discovers which Radio Browser servers are live.
///
/// Their documentation is explicit: "Never use a direct link to a single new server.
/// It is much better to get a list of the servers", then randomise it and move to the
/// next entry when a request fails.
///
/// The SRV record is used rather than the A records of `all.api.radio-browser.info`,
/// because it yields host names. Connecting to a bare IP over HTTPS would fail
/// certificate validation, the certificate being issued for the host name.
@Slf4j
@Component
public class RadioBrowserServerRegistry {

    private static final String SRV_RECORD = "_api._tcp.radio-browser.info";
    private static final Duration REFRESH_AFTER = Duration.ofHours(1);

    /// Used when DNS gives nothing, so a resolver problem never takes the app down.
    private final String fallbackHost;

    private List<String> hosts = List.of();
    private Instant resolvedAt = Instant.EPOCH;

    public RadioBrowserServerRegistry(@Value("${radio-browser.base-url}") String baseUrl) {
        this.fallbackHost = hostOf(baseUrl);
    }

    /// The configured URL is only a safety net, so a malformed or absent value must
    /// not stop the application from starting. Discovery is the real source here.
    private static String hostOf(String baseUrl) {
        try {
            String host = URI.create(baseUrl).getHost();
            return host == null || host.isBlank() ? null : host;
        } catch (IllegalArgumentException e) {
            log.warn("radio-browser.base-url is not a usable URL ({}), relying on SRV discovery alone", baseUrl);
            return null;
        }
    }

    /// Shuffled on every call, so load spreads across the fleet instead of everyone
    /// hammering whichever server DNS happened to list first.
    public synchronized List<String> servers() {
        if (hosts.isEmpty() || resolvedAt.plus(REFRESH_AFTER).isBefore(Instant.now())) {
            refresh();
        }

        List<String> shuffled = new ArrayList<>(hosts);
        Collections.shuffle(shuffled);
        return shuffled;
    }

    private void refresh() {
        List<String> resolved = lookupSrv();

        if (resolved.isEmpty() && fallbackHost != null) {
            log.warn("Radio Browser SRV lookup returned nothing, falling back to {}", fallbackHost);
            resolved = List.of(fallbackHost);
        }

        hosts = resolved;
        resolvedAt = Instant.now();

        if (hosts.isEmpty()) {
            // Callers turn an empty fleet into a 503, which is the honest answer.
            log.warn("No Radio Browser server could be discovered.");
        } else {
            log.info("Radio Browser servers discovered: {}", hosts);
        }
    }

    private List<String> lookupSrv() {
        var environment = new Hashtable<String, String>();
        environment.put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory");

        try {
            var context = new InitialDirContext(environment);
            Attribute records = context.getAttributes(SRV_RECORD, new String[]{"SRV"}).get("SRV");

            if (records == null) {
                return List.of();
            }

            List<String> found = new ArrayList<>();
            for (int i = 0; i < records.size(); i++) {
                // Each record reads "priority weight port target."
                String[] parts = records.get(i).toString().trim().split("\\s+");
                if (parts.length == 4) {
                    found.add(parts[3].replaceAll("\\.$", ""));
                }
            }

            return found;
        } catch (NamingException e) {
            log.warn("Could not resolve {}: {}", SRV_RECORD, e.getMessage());
            return List.of();
        }
    }
}
