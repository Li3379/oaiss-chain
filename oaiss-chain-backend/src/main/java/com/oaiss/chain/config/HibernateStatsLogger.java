package com.oaiss.chain.config;

import jakarta.annotation.PostConstruct;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Hibernate slow query statistics logger.
 * Collects and logs Hibernate statistics every 5 minutes.
 * Highlights slow queries (> 1s average) and the single slowest query.
 *
 * @author OAISS Team
 */
@Component
@EnableScheduling
public class HibernateStatsLogger {

    private static final Logger log = LoggerFactory.getLogger(HibernateStatsLogger.class);

    private final SessionFactory sessionFactory;
    private Statistics statistics;

    public HibernateStatsLogger(SessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
    }

    @PostConstruct
    public void init() {
        this.statistics = sessionFactory.getStatistics();
        statistics.setStatisticsEnabled(true);
        log.info("Hibernate statistics enabled");
    }

    @Scheduled(fixedDelay = 300_000, initialDelay = 60_000)
    public void logStats() {
        if (statistics == null) return;
        long queryCount = statistics.getQueryExecutionCount();
        if (queryCount == 0) return;

        log.info(
                "Hibernate stats: queries={}, entityLoads={}, collections={}, " +
                "cacheHits={}, cacheMisses={}, maxQueryTime={}ms",
                queryCount,
                statistics.getEntityLoadCount(),
                statistics.getCollectionLoadCount(),
                statistics.getSecondLevelCacheHitCount(),
                statistics.getSecondLevelCacheMissCount(),
                statistics.getQueryExecutionMaxTime()
        );

        String slowestQuery = statistics.getQueryExecutionMaxTimeQueryString();
        if (slowestQuery != null && !slowestQuery.isEmpty()) {
            log.info("Slowest query ({}ms): {}", statistics.getQueryExecutionMaxTime(), slowestQuery);
        }

        // Log queries averaging more than 1 second
        String[] queries = statistics.getQueries();
        if (queries != null) {
            for (String query : queries) {
                var qs = statistics.getQueryStatistics(query);
                long execCount = qs.getExecutionCount();
                long totalTime = qs.getExecutionTotalTime();
                long avgTime = execCount > 0 ? totalTime / execCount : 0;
                if (avgTime > 1000) {
                    log.warn("Slow query avg={}ms count={} query={}", avgTime, execCount, query);
                }
            }
        }

        statistics.clear();
    }
}