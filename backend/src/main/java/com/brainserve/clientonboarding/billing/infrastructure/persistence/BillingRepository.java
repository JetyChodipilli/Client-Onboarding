package com.brainserve.clientonboarding.billing.infrastructure.persistence;

import static com.brainserve.clientonboarding.billing.domain.model.BillingModels.*;
import static com.brainserve.clientonboarding.common.infrastructure.persistence.JdbcValues.timestamp;
import com.brainserve.clientonboarding.common.domain.model.PageSlice;
import java.sql.*;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class BillingRepository {
    private final JdbcClient jdbc;
    public BillingRepository(JdbcClient jdbc) { this.jdbc=jdbc; }
    public void lockCommands(UUID org) { jdbc.sql("SELECT id FROM organizations WHERE id=? FOR NO KEY UPDATE").param(org).query(UUID.class).single(); }
    public Optional<Invoice> find(UUID org,UUID id) { return jdbc.sql("SELECT * FROM invoices WHERE organization_id=? AND id=?").params(org,id).query(this::invoice).optional(); }
    public Optional<Invoice> lock(UUID org,UUID id) { return jdbc.sql("SELECT * FROM invoices WHERE organization_id=? AND id=? FOR UPDATE").params(org,id).query(this::invoice).optional(); }
    public Optional<Invoice> byStep(UUID org,UUID step) { return jdbc.sql("SELECT * FROM invoices WHERE organization_id=? AND step_id=? AND status NOT IN ('VOID','CANCELLED')").params(org,step).query(this::invoice).optional(); }
    public Optional<Invoice> byKey(UUID org,String key,String hash) {
        var found=jdbc.sql("SELECT * FROM invoices WHERE organization_id=? AND idempotency_key=?").params(org,key).query(this::invoice).optional();
        if(found.isPresent() && !jdbc.sql("SELECT request_hash FROM invoices WHERE organization_id=? AND id=?").params(org,found.get().id()).query(String.class).single().equals(hash))
            throw com.brainserve.clientonboarding.billing.application.BillingErrors.state("This idempotency key was already used for a different request.");
        return found;
    }
    public PageSlice<Invoice> page(UUID org,UUID project,String search,Status status,int page,int size,LocalDate today) {
        String filter=" FROM invoices WHERE organization_id=:org AND (:project IS NULL OR project_id=CAST(:project AS uuid)) AND lower(invoice_number) LIKE :search AND (:status IS NULL OR (CASE WHEN due_date<:today AND total_minor>captured_minor-refunded_minor AND status NOT IN ('DRAFT','VOID','CANCELLED') THEN 'OVERDUE' ELSE status END)=:status)";
        var args=new HashMap<String,Object>();args.put("org",org);args.put("project",project==null?null:project.toString());args.put("search","%"+search.toLowerCase(Locale.ROOT)+"%");args.put("status",status==null?null:status.name());args.put("today",today);args.put("limit",size);args.put("offset",(long)page*size);
        return new PageSlice<>(jdbc.sql("SELECT *"+filter+" ORDER BY created_at DESC,id LIMIT :limit OFFSET :offset").params(args).query(this::invoice).list(),page,size,jdbc.sql("SELECT count(*)"+filter).params(args).query(Long.class).single());
    }
    public List<Item> items(UUID org,UUID id) { return jdbc.sql("SELECT * FROM invoice_items WHERE organization_id=? AND invoice_id=? ORDER BY position").params(org,id).query((r,n)->new Item(r.getInt("position"),r.getString("description"),r.getInt("quantity"),r.getLong("unit_amount_minor"),r.getInt("tax_basis_points"),r.getLong("subtotal_minor"),r.getLong("tax_minor"),r.getLong("total_minor"))).list(); }
    public void insert(Invoice i,List<Item> items,String key,String hash,UUID actor) {
        jdbc.sql("INSERT INTO invoices(id,organization_id,project_id,step_id,invoice_number,currency,policy,subtotal_minor,tax_minor,total_minor,threshold_minor,status,due_date,note,idempotency_key,request_hash,created_at,created_by,updated_at,updated_by) VALUES(?,?,?,?,?,?,?,?,?,?,?,'DRAFT',?,?,?,?,?,?,?,?)")
            .params(i.id(),i.organizationId(),i.projectId(),i.stepId(),i.invoiceNumber(),i.currency(),i.policy().name(),i.subtotalMinor(),i.taxMinor(),i.totalMinor(),i.thresholdMinor(),i.dueDate(),i.note(),key,hash,timestamp(i.createdAt()),actor,timestamp(i.createdAt()),actor).update();
        for(var line:items) jdbc.sql("INSERT INTO invoice_items(id,organization_id,invoice_id,position,description,quantity,unit_amount_minor,tax_basis_points,subtotal_minor,tax_minor,total_minor) VALUES(?,?,?,?,?,?,?,?,?,?,?)")
            .params(UUID.randomUUID(),i.organizationId(),i.id(),line.position(),line.description(),line.quantity(),line.unitAmountMinor(),line.taxBasisPoints(),line.subtotalMinor(),line.taxMinor(),line.totalMinor()).update();
    }
    public boolean update(Invoice i,Status status,long captured,long refunded,long reserved,UUID actor,Instant now) {
        return jdbc.sql("UPDATE invoices SET status=?,captured_minor=?,refunded_minor=?,reserved_minor=?,sent_at=CASE WHEN ?='SENT' THEN coalesce(sent_at,?) ELSE sent_at END,viewed_at=CASE WHEN ?='VIEWED' THEN coalesce(viewed_at,?) ELSE viewed_at END,updated_at=?,updated_by=?,version=version+1 WHERE organization_id=? AND id=? AND version=?")
            .params(status.name(),captured,refunded,reserved,status.name(),timestamp(now),status.name(),timestamp(now),timestamp(now),actor,i.organizationId(),i.id(),i.version()).update()==1;
    }
    public void event(Invoice i,String type,String correlation,Instant now) { jdbc.sql("INSERT INTO billing_outbox_events(id,organization_id,invoice_id,event_type,occurred_at,correlation_id) VALUES(?,?,?,?,?,?)").params(UUID.randomUUID(),i.organizationId(),i.id(),type,timestamp(now),correlation).update(); }
    private Invoice invoice(ResultSet r,int n)throws SQLException { return new Invoice(r.getObject("id",UUID.class),r.getObject("organization_id",UUID.class),r.getObject("project_id",UUID.class),r.getObject("step_id",UUID.class),r.getString("invoice_number"),r.getString("currency"),Policy.valueOf(r.getString("policy")),r.getLong("subtotal_minor"),r.getLong("tax_minor"),r.getLong("total_minor"),r.getLong("threshold_minor"),r.getLong("captured_minor"),r.getLong("refunded_minor"),r.getLong("reserved_minor"),Status.valueOf(r.getString("status")),r.getObject("due_date",LocalDate.class),r.getString("note"),instant(r,"sent_at"),instant(r,"viewed_at"),instant(r,"created_at"),r.getLong("version")); }
    private Instant instant(ResultSet r,String name)throws SQLException { var t=r.getTimestamp(name);return t==null?null:t.toInstant(); }
}
