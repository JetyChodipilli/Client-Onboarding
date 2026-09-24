package com.brainserve.clientonboarding.forms.application;

import com.brainserve.clientonboarding.audit.application.AuditService;
import com.brainserve.clientonboarding.common.domain.model.PageSlice;
import com.brainserve.clientonboarding.common.observability.RequestMetadata;
import com.brainserve.clientonboarding.common.security.TenantPrincipal;
import com.brainserve.clientonboarding.forms.domain.model.*;
import com.brainserve.clientonboarding.forms.domain.model.FormModels.*;
import com.brainserve.clientonboarding.forms.domain.repository.FormRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FormTemplateService {
    private final FormRepository repository;
    private final AuditService audit;
    private final Clock clock;
    private final ObjectMapper json;
    public FormTemplateService(FormRepository repository,AuditService audit,Clock clock,ObjectMapper json) { this.repository=repository; this.audit=audit; this.clock=clock; this.json=json; }
    @PreAuthorize("hasAnyAuthority('FORM_READ','FORM_MANAGE')")
    public PageSlice<Template> list(TenantPrincipal p,String search,int page,int size) {
        FormErrors.page(page,size,100);
        if(search.length()>180) throw FormErrors.invalid("Search must be at most 180 characters.");
        return repository.templates(p.organizationId(),search.trim(),page,size);
    }
    @PreAuthorize("hasAnyAuthority('FORM_READ','FORM_MANAGE')")
    public Template get(TenantPrincipal p,UUID id) { return repository.template(p.organizationId(),id).orElseThrow(FormErrors::missing); }
    @PreAuthorize("hasAnyAuthority('FORM_READ','FORM_MANAGE')")
    public Definition definition(TenantPrincipal p,UUID id) { return repository.definition(p.organizationId(),id).orElseThrow(FormErrors::missing); }
    @PreAuthorize("hasAnyAuthority('FORM_READ','FORM_MANAGE')")
    public PageSlice<Definition> versions(TenantPrincipal p,UUID id,int page,int size) { get(p,id); FormErrors.page(page,size,100); return repository.versions(p.organizationId(),id,page,size); }
    @PreAuthorize("hasAuthority('FORM_MANAGE')")
    @Transactional
    public Bundle create(TenantPrincipal p,String name,String description,RequestMetadata metadata) {
        var now=clock.instant();
        var template=new Template(UUID.randomUUID(),p.organizationId(),name.trim(),description,null,now,now,0);
        repository.insertTemplate(template,p.userId());
        var definition=new Definition(UUID.randomUUID(),p.organizationId(),template.id(),1,VersionStatus.DRAFT,List.of(),null,now,now,0);
        repository.insertDefinition(definition,p.userId());
        log(p,"FORM_CREATED",template.id(),Map.of("formVersionId",definition.id()),metadata);
        return new Bundle(template,definition);
    }
    @PreAuthorize("hasAuthority('FORM_MANAGE')")
    @Transactional
    public Definition newVersion(TenantPrincipal p,UUID formId,UUID source,RequestMetadata metadata) {
        if(!repository.lockTemplate(p.organizationId(),formId)) throw FormErrors.missing();
        List<FormField> fields=List.of();
        if(source!=null) {
            var previous=repository.definition(p.organizationId(),source).orElseThrow(FormErrors::missing);
            if(!previous.formId().equals(formId)||previous.status()!=VersionStatus.PUBLISHED) throw FormErrors.invalid("Copy a published version of this form.");
            fields=previous.fields();
        }
        var now=clock.instant();
        var definition=new Definition(UUID.randomUUID(),p.organizationId(),formId,repository.nextVersion(p.organizationId(),formId),VersionStatus.DRAFT,fields,null,now,now,0);
        repository.insertDefinition(definition,p.userId());
        log(p,"FORM_VERSION_CREATED",definition.id(),Map.of("formId",formId),metadata);
        return definition;
    }
    @PreAuthorize("hasAuthority('FORM_MANAGE')")
    @Transactional
    public Definition update(TenantPrincipal p,UUID id,long version,List<FormField> fields,boolean publish,RequestMetadata metadata) {
        var old=repository.definition(p.organizationId(),id).orElseThrow(FormErrors::missing);
        if(!repository.lockTemplate(p.organizationId(),old.formId())) throw FormErrors.missing();
        if(old.status()!=VersionStatus.DRAFT) throw FormErrors.state("FORM_VERSION_IMMUTABLE","Published forms cannot be edited. Create a new version.");
        var values=publish?old.fields():fields;
        try { FormPolicy.validateDefinition(values); } catch(FormPolicy.InvalidAnswer e) { throw FormErrors.invalid(e); }
        try { if(json.writeValueAsString(values).length()>100000) throw FormErrors.invalid("The form definition exceeds 100000 characters."); }
        catch(java.io.IOException e) { throw new IllegalStateException(e); }
        if(!repository.updateDefinition(p.organizationId(),id,version,values,publish,p.userId(),clock.instant())) throw FormErrors.conflict();
        log(p,publish?"FORM_VERSION_PUBLISHED":"FORM_FIELDS_UPDATED",id,Map.of("fieldCount",values.size()),metadata);
        return repository.definition(p.organizationId(),id).orElseThrow();
    }
    @PreAuthorize("hasAuthority('FORM_MANAGE')")
    @Transactional
    public void archive(TenantPrincipal p,UUID id,long version,RequestMetadata metadata) {
        if(!repository.lockTemplate(p.organizationId(),id)) throw FormErrors.missing();
        if(!repository.archive(p.organizationId(),id,version,p.userId(),clock.instant())) throw FormErrors.conflict();
        log(p,"FORM_ARCHIVED",id,Map.of(),metadata);
    }
    private void log(TenantPrincipal p,String action,UUID id,Map<String,Object> after,RequestMetadata m) { audit.append(p.organizationId(),p.userId(),action,"FORM",id,Map.of(),after,"API",m.ipHash()); }
    public record Bundle(Template template,Definition definition) { }
}
