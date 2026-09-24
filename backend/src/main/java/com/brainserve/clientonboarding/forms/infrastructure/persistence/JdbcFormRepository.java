package com.brainserve.clientonboarding.forms.infrastructure.persistence;

import static com.brainserve.clientonboarding.common.infrastructure.persistence.JdbcValues.timestamp;
import com.brainserve.clientonboarding.common.domain.model.PageSlice;
import com.brainserve.clientonboarding.forms.domain.model.FormModels.*;
import com.brainserve.clientonboarding.forms.domain.model.FormField;
import com.brainserve.clientonboarding.forms.domain.repository.FormRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcFormRepository implements FormRepository {
    private final JdbcClient jdbc;
    private final ObjectMapper json;
    public void event(Response r,String type,String correlationId,Instant now) {
        jdbc.sql("INSERT INTO form_outbox_events(id,organization_id,response_id,event_type,submission_number,occurred_at,correlation_id) VALUES(?,?,?,?,?,?,?)")
                .params(UUID.randomUUID(),r.organizationId(),r.id(),type,r.submissionNumber(),timestamp(now),correlationId).update();
    }
    public JdbcFormRepository(JdbcClient jdbc, ObjectMapper json) { this.jdbc = jdbc; this.json = json; }
    public PageSlice<Template> templates(UUID org, String search, int page, int size) {
        String filter = " FROM forms WHERE organization_id=:org AND archived_at IS NULL AND lower(name) LIKE :search";
        Map<String,Object> args = Map.of("org", org, "search", "%"+search.toLowerCase(java.util.Locale.ROOT)+"%", "limit",size,"offset",(long)page*size);
        return new PageSlice<>(jdbc.sql("SELECT *"+filter+" ORDER BY created_at DESC,id LIMIT :limit OFFSET :offset").params(args).query(this::template).list(),page,size,
                jdbc.sql("SELECT count(*)"+filter).params(args).query(Long.class).single());
    }
    public Optional<Template> template(UUID org, UUID id) { return jdbc.sql("SELECT * FROM forms WHERE organization_id=? AND id=?").params(org,id).query(this::template).optional(); }
    public void insertTemplate(Template t, UUID actor) {
        jdbc.sql("INSERT INTO forms(id,organization_id,name,description,created_at,created_by,updated_at,updated_by,version) VALUES(?,?,?,?,?,?,?,?,0)")
                .params(t.id(),t.organizationId(),t.name(),t.description(),timestamp(t.createdAt()),actor,timestamp(t.updatedAt()),actor).update();
    }
    public boolean lockTemplate(UUID org, UUID id) { return jdbc.sql("SELECT id FROM forms WHERE organization_id=? AND id=? AND archived_at IS NULL FOR NO KEY UPDATE").params(org,id).query(UUID.class).optional().isPresent(); }
    public boolean archive(UUID org, UUID id, long version, UUID actor, Instant now) { return jdbc.sql("UPDATE forms SET archived_at=?,updated_at=?,updated_by=?,version=version+1 WHERE organization_id=? AND id=? AND version=? AND archived_at IS NULL").params(timestamp(now),timestamp(now),actor,org,id,version).update()==1; }
    public PageSlice<Definition> versions(UUID org, UUID id, int page, int size) {
        return new PageSlice<>(jdbc.sql("SELECT * FROM form_versions WHERE organization_id=? AND form_id=? ORDER BY version_number DESC LIMIT ? OFFSET ?").params(org,id,size,(long)page*size).query(this::definition).list(), page,size,
                jdbc.sql("SELECT count(*) FROM form_versions WHERE organization_id=? AND form_id=?").params(org,id).query(Long.class).single());
    }
    public int nextVersion(UUID org, UUID id) { return jdbc.sql("SELECT coalesce(max(version_number),0)+1 FROM form_versions WHERE organization_id=? AND form_id=?").params(org,id).query(Integer.class).single(); }
    public Optional<Definition> definition(UUID org, UUID id) { return jdbc.sql("SELECT * FROM form_versions WHERE organization_id=? AND id=?").params(org,id).query(this::definition).optional(); }
    public void insertDefinition(Definition d, UUID actor) {
        jdbc.sql("INSERT INTO form_versions(id,organization_id,form_id,version_number,status,fields_json,created_at,created_by,updated_at,updated_by,version) VALUES(?,?,?,?,'DRAFT',?,?,?,?,?,0)")
                .params(d.id(),d.organizationId(),d.formId(),d.versionNumber(),write(d.fields()),timestamp(d.createdAt()),actor,timestamp(d.updatedAt()),actor).update();
    }
    public boolean updateDefinition(UUID org, UUID id, long version, List<FormField> fields, boolean publish, UUID actor, Instant now) {
        return jdbc.sql("UPDATE form_versions SET fields_json=?,status=?,published_at=?,updated_at=?,updated_by=?,version=version+1 WHERE organization_id=? AND id=? AND version=? AND status='DRAFT'")
                .params(write(fields),publish?"PUBLISHED":"DRAFT",timestamp(publish?now:null),timestamp(now),actor,org,id,version).update()==1;
    }
    public Optional<Response> response(UUID org, UUID step) { return jdbc.sql("SELECT * FROM form_responses WHERE organization_id=? AND step_id=?").params(org,step).query(this::response).optional(); }
    public void insertResponse(Response r, UUID actor) {
        jdbc.sql("INSERT INTO form_responses(id,organization_id,step_id,form_version_id,status,answers_json,submission_number,review_note,created_at,created_by,updated_at,updated_by,version) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)")
                .params(r.id(),r.organizationId(),r.stepId(),r.formVersionId(),r.status().name(),write(r.answers()),r.submissionNumber(),r.reviewNote(),timestamp(r.updatedAt()),actor,timestamp(r.updatedAt()),actor,r.version()).update();
    }
    public boolean updateResponse(Response r, long version, UUID actor) {
        return jdbc.sql("UPDATE form_responses SET status=?,answers_json=?,submission_number=?,review_note=?,updated_at=?,updated_by=?,version=version+1 WHERE organization_id=? AND step_id=? AND version=?")
                .params(r.status().name(),write(r.answers()),r.submissionNumber(),r.reviewNote(),timestamp(r.updatedAt()),actor,r.organizationId(),r.stepId(),version).update()==1;
    }
    public void insertSubmission(UUID org, Response r, UUID actor, Instant now) {
        jdbc.sql("INSERT INTO form_submissions(id,organization_id,response_id,submission_number,answers_json,created_at,created_by) VALUES(?,?,?,?,?,?,?)")
                .params(UUID.randomUUID(),org,r.id(),r.submissionNumber(),write(r.answers()),timestamp(now),actor).update();
    }
    public void insertReview(UUID org, Response r, ReviewDecision decision, String note, UUID actor, Instant now) {
        int written = jdbc.sql("INSERT INTO form_reviews(id,organization_id,submission_id,decision,note,created_at,created_by) SELECT ?,organization_id,id,?,?,?,? FROM form_submissions WHERE organization_id=? AND response_id=? AND submission_number=?")
                .params(UUID.randomUUID(),decision.name(),note,timestamp(now),actor,org,r.id(),r.submissionNumber()).update();
        if (written != 1) throw new IllegalStateException("Current form submission is missing");
    }
    public PageSlice<Submission> submissions(UUID org, UUID id, int page, int size) {
        // Bound answer-bearing history pages to 20 in the application, with one batched review lookup.
        List<Submission> rows = jdbc.sql("SELECT * FROM form_submissions WHERE organization_id=? AND response_id=? ORDER BY submission_number DESC LIMIT ? OFFSET ?")
                .params(org,id,size,(long)page*size).query((rs,n) -> new Submission(rs.getObject("id",UUID.class),rs.getInt("submission_number"),read(rs.getString("answers_json"),new TypeReference<Map<String,Object>>(){}),instant(rs,"created_at"),rs.getObject("created_by",UUID.class),List.of())).list();
        Map<UUID,List<Review>> reviews = new java.util.HashMap<>();
        if (!rows.isEmpty()) jdbc.sql("SELECT * FROM form_reviews WHERE organization_id=:org AND submission_id IN (:ids) ORDER BY created_at,id")
                .param("org",org).param("ids",rows.stream().map(Submission::id).toList()).query((rs,n) -> {
                    reviews.computeIfAbsent(rs.getObject("submission_id",UUID.class), key -> new java.util.ArrayList<>()).add(new Review(ReviewDecision.valueOf(rs.getString("decision")),rs.getString("note"),instant(rs,"created_at"),rs.getObject("created_by",UUID.class))); return 1;
                }).list();
        return new PageSlice<>(rows.stream().map(s -> new Submission(s.id(),s.submissionNumber(),s.answers(),s.createdAt(),s.createdBy(),reviews.getOrDefault(s.id(),List.of()))).toList(),page,size,
                jdbc.sql("SELECT count(*) FROM form_submissions WHERE organization_id=? AND response_id=?").params(org,id).query(Long.class).single());
    }
    private Template template(ResultSet r,int n) throws SQLException { return new Template(r.getObject("id",UUID.class),r.getObject("organization_id",UUID.class),r.getString("name"),r.getString("description"),instant(r,"archived_at"),instant(r,"created_at"),instant(r,"updated_at"),r.getLong("version")); }
    private Definition definition(ResultSet r,int n) throws SQLException { return new Definition(r.getObject("id",UUID.class),r.getObject("organization_id",UUID.class),r.getObject("form_id",UUID.class),r.getInt("version_number"),VersionStatus.valueOf(r.getString("status")),read(r.getString("fields_json"),new TypeReference<List<FormField>>(){}),instant(r,"published_at"),instant(r,"created_at"),instant(r,"updated_at"),r.getLong("version")); }
    private Response response(ResultSet r,int n) throws SQLException { return new Response(r.getObject("id",UUID.class),r.getObject("organization_id",UUID.class),r.getObject("step_id",UUID.class),r.getObject("form_version_id",UUID.class),ResponseStatus.valueOf(r.getString("status")),read(r.getString("answers_json"),new TypeReference<Map<String,Object>>(){}),r.getInt("submission_number"),r.getString("review_note"),instant(r,"updated_at"),r.getLong("version")); }
    private Instant instant(ResultSet r,String column) throws SQLException { var t=r.getTimestamp(column); return t==null?null:t.toInstant(); }
    private String write(Object value) { try { return json.writeValueAsString(value); } catch (java.io.IOException e) { throw new IllegalStateException("Cannot serialize form data",e); } }
    private <T> T read(String value,TypeReference<T> type) { try { return json.readValue(value,type); } catch (java.io.IOException e) { throw new IllegalStateException("Cannot read form data",e); } }
}
