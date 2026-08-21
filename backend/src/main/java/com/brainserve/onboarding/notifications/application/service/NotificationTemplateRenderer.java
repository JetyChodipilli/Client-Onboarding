package com.brainserve.onboarding.notifications.application.service;
import com.brainserve.onboarding.common.error.ApiException;import java.util.*;import java.util.regex.*;import org.springframework.http.HttpStatus;import org.springframework.stereotype.Component;
@Component public class NotificationTemplateRenderer {
    private static final Pattern TOKEN=Pattern.compile("\\{\\{([A-Za-z0-9_]+)}}");
    private static final Set<String> ALLOWED=Set.of("event_type","project_id","source_id","source_type","status","task_title","organization_name","recipient_name");
    public void validate(String subject,String body,String action){validateText(subject);validateText(body);if(action!=null&&!action.isBlank()){validateText(action);String t=action.trim();if(!(t.startsWith("/app")||t.startsWith("/portal")))throw bad("Notification action paths must be relative /app or /portal routes.");if(t.contains("\\")||t.contains("//"))throw bad("Notification action path is invalid.");}}
    public String render(String template,Map<String,?> values){if(template==null)return null;Matcher m=TOKEN.matcher(template);StringBuffer out=new StringBuffer();while(m.find()){String key=m.group(1);if(!ALLOWED.contains(key))throw bad("Notification template contains an unsupported variable: "+key);Object raw=values.get(key);String value=raw==null?"":String.valueOf(raw);value=value.replaceAll("[\\r\\n]"," ");m.appendReplacement(out,Matcher.quoteReplacement(value));}m.appendTail(out);return out.toString();}
    private void validateText(String value){Matcher m=TOKEN.matcher(value);while(m.find())if(!ALLOWED.contains(m.group(1)))throw bad("Notification template contains an unsupported variable: "+m.group(1));}
    private static ApiException bad(String m){return new ApiException(HttpStatus.BAD_REQUEST,"NOTIFICATION_TEMPLATE_INVALID",m);}
}
