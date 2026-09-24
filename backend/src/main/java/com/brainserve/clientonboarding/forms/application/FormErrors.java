package com.brainserve.clientonboarding.forms.application;
import com.brainserve.clientonboarding.common.api.FieldViolation;
import com.brainserve.clientonboarding.common.error.DomainException;
import com.brainserve.clientonboarding.forms.domain.model.FormPolicy;
import java.util.List;
import org.springframework.http.HttpStatus;
final class FormErrors {
    private FormErrors() { }
    static DomainException missing() { return new DomainException("RESOURCE_NOT_FOUND","Requested resource was not found.",HttpStatus.NOT_FOUND); }
    static DomainException conflict() { return state("OPTIMISTIC_LOCK_CONFLICT","The form changed. Reload it before trying again; your unsaved answers have been kept on this page."); }
    static DomainException state(String code,String message) { return new DomainException(code,message,HttpStatus.CONFLICT); }
    static DomainException invalid(String message) { return new DomainException("VALIDATION_FAILED",message,HttpStatus.BAD_REQUEST); }
    static DomainException invalid(FormPolicy.InvalidAnswer e) { return new DomainException("VALIDATION_FAILED",e.getMessage(),HttpStatus.BAD_REQUEST,List.of(new FieldViolation(e.field(),e.getMessage()))); }
    static void page(int page,int size,int max) { if(page<0||size<1||size>max) throw invalid("Page must be non-negative and size must be 1 to "+max+"."); }
}
