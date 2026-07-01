package com.testingai.chat.repository;

import com.testingai.chat.model.ConversationOutcome;
import com.testingai.chat.model.SessionState;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@JdbcTest
@Import(OutcomeRepository.class)
@TestPropertySource(properties = "spring.liquibase.enabled=false")
class OutcomeRepositoryTest {

  @Autowired private OutcomeRepository outcomeRepository;
  @Autowired private JdbcTemplate jdbc;

  @Test
  void save_insertsRowWithCorrectValues() {
    SessionState session = new SessionState("sess-abc");
    session.incrementTurnCount();
    session.incrementTurnCount();
    session.setOutcome(ConversationOutcome.RESOLVED);

    Instant endedAt = Instant.now();
    outcomeRepository.save(session, endedAt);

    Map<String, Object> row = jdbc.queryForMap(
            "SELECT * FROM conversations WHERE session_id = ?", "sess-abc");

    assertThat(row.get("session_id")).isEqualTo("sess-abc");
    assertThat(row.get("outcome")).isEqualTo("RESOLVED");
    assertThat(((Number) row.get("turn_count")).intValue()).isEqualTo(2);
    assertThat(row.get("started_at")).isNotNull();
    assertThat(row.get("ended_at")).isNotNull();
  }
}
