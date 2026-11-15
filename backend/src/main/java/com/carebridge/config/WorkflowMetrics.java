package com.carebridge.config;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component public class WorkflowMetrics implements MeterBinder {
  private final JdbcTemplate db;
  public WorkflowMetrics(JdbcTemplate db){this.db=db;}
  @Override public void bindTo(MeterRegistry registry){
    registry.gauge("carebridge.outbox.pending",this,x->count("select count(*) from outbox_events where status='PENDING'"));
    registry.gauge("carebridge.outbox.failed",this,x->count("select count(*) from outbox_events where status='FAILED'"));
    registry.gauge("carebridge.offers.open",this,x->count("select count(*) from appointment_offers where status='OPEN' and expires_at>now()"));
    registry.gauge("carebridge.gaps.open",this,x->count("select count(*) from care_gap_alerts where resolved_at is null"));
  }
  private double count(String sql){return db.queryForObject(sql,Long.class).doubleValue();}
}
