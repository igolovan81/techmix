package com.testingai.chat.repository;

import com.testingai.chat.model.SessionState;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;

@Repository
public class OutcomeRepository {

  private final JdbcTemplate jdbc;

  public OutcomeRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public void save(SessionState session, Instant endedAt) {
    jdbc.update(
            "INSERT INTO conversations (session_id, outcome, turn_count, started_at, ended_at)"
                    + " VALUES (?, ?, ?, ?, ?)",
            session.getSessionId(),
            session.getOutcome().name(),
            session.getTurnCount(),
            Timestamp.from(session.getStartedAt()),
            Timestamp.from(endedAt));
  }
}
