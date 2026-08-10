package io.github.fabricetiennette.radiofy.backend.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/// Radio Browser is a free service running on a single server, and every keystroke
/// in the app reaches it. Caching cuts the latency the user feels and the load we
/// put on a directory that is already struggling.
///
/// Two caches because the data ages very differently.
@EnableCaching
@Configuration
public class RadioCacheConfig {

    public static final String TAGS_CACHE = "radioTags";
    public static final String STATIONS_CACHE = "radioStations";

    @Bean
    public CacheManager cacheManager() {
        var cacheManager = new CaffeineCacheManager();

        // The tag vocabulary barely moves from one day to the next.
        cacheManager.registerCustomCache(TAGS_CACHE, Caffeine.newBuilder()
                .maximumSize(1_000)
                .expireAfterWrite(Duration.ofHours(6))
                .build());

        // Stations come and go, and their popularity ordering shifts, so this one
        // only needs to cover a burst of typing rather than a whole session.
        cacheManager.registerCustomCache(STATIONS_CACHE, Caffeine.newBuilder()
                .maximumSize(1_000)
                .expireAfterWrite(Duration.ofMinutes(5))
                .build());

        return cacheManager;
    }
}
