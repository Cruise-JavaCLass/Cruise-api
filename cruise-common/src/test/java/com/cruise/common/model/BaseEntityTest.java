package com.cruise.common.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("BaseEntity Unit Tests")
class BaseEntityTest {

    static class TestEntity extends BaseEntity {
        public void triggerPrePersist() {
            onCreate();
        }

        public void triggerPreUpdate() {
            onUpdate();
        }
    }

    @Test
    @DisplayName("Should set timestamps on PrePersist")
    void shouldSetTimestampsOnPrePersist() {
        TestEntity entity = new TestEntity();
        entity.setId(100L);
        entity.triggerPrePersist();

        assertThat(entity.getId()).isEqualTo(100L);
        assertThat(entity.getCreatedAt()).isNotNull().isBeforeOrEqualTo(Instant.now());
        assertThat(entity.getUpdatedAt()).isNotNull().isBeforeOrEqualTo(Instant.now());
    }

    @Test
    @DisplayName("Should update timestamp on PreUpdate")
    void shouldUpdateTimestampOnPreUpdate() {
        TestEntity entity = new TestEntity();
        entity.triggerPrePersist();
        Instant initialUpdate = entity.getUpdatedAt();

        entity.triggerPreUpdate();

        assertThat(entity.getUpdatedAt()).isNotNull();
        assertThat(entity.getUpdatedAt()).isAfterOrEqualTo(initialUpdate);
    }
}
