package com.hb.flagmesh.control_plane.service;

import com.hb.flagmesh.control_plane.model.FlagUpdateEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import tools.jackson.databind.ObjectMapper;

/**
 * Sends every committed flag change to the Redis channel the Go sidecars subscribe to.
 *
 * AFTER_COMMIT: Spring calls this only once PostgreSQL has committed, so sidecars
 * never hear about a change that was rolled back. If the transaction rolls back,
 * the event is dropped.
 *
 * A failed publish is logged, not rethrown: the change is already saved, and the
 * sidecar's 60-second snapshot reconciler picks it up.
 */
@Component
public class FlagUpdatePublisher {

    static final String CHANNEL = "flags:updates";
    private static final Logger log = LoggerFactory.getLogger(FlagUpdatePublisher.class);

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public FlagUpdatePublisher(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onFlagUpdated(FlagUpdateEvent event) {
        // TODO(datadog): log.info("flag change committed", flagKey, version). This is the one
        //  success log per create/update: this method runs only after the DB commit.
        try {
            redis.convertAndSend(CHANNEL, objectMapper.writeValueAsString(event));
            // TODO(datadog): log.debug("published to " + CHANNEL, flagKey, version).
        } catch (RuntimeException e) {
            // TODO(datadog): no extra call needed. This SLF4J log.warn reaches Datadog once the
            //  phase 3.2 log shipper is wired; add tags service=flag-control-plane, flagKey.
            log.warn("Redis publish failed for {} v{}; sidecar reconciler will repair",
                    event.flagKey(), event.version(), e);
        }
    }
}
