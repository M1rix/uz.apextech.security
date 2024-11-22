package uz.mirix.aop.fields;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import uz.mirix.annotations.audience.AudienceFilter;
import uz.mirix.annotations.audience.AudienceFilterObject;

import java.lang.reflect.Field;
import java.util.Base64;
import java.util.Collection;
import java.util.stream.Collectors;

@Aspect
@Component
public class AudienceBasedFieldHidingAspect {
    private final ObjectMapper objectMapper;

    public AudienceBasedFieldHidingAspect(ObjectMapper objectMapper) {
        this.objectMapper = configureObjectMapper(objectMapper);
    }

    private ObjectMapper configureObjectMapper(ObjectMapper objectMapper) {
        return objectMapper
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .configure(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT, true);
    }

    @Around(
            "@within(uz.mirix.annotations.audience.AudienceFilterController) && " +
            "@annotation(org.springframework.web.bind.annotation.GetMapping)"
    )
    public Object hideFieldsBasedOnRole(ProceedingJoinPoint joinPoint) throws Throwable {
        Object result = joinPoint.proceed();

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return result;
        }
        String payload  = authentication.getCredentials().toString().split("\\.")[1];
        String decoded = new String(Base64.getDecoder().decode(payload));
        boolean hasAudience = decoded.contains("aud");

        if (result instanceof ResponseEntity) {
            return processedResponseEntity((ResponseEntity<?>) result, hasAudience);
        }

        return processObject(result, hasAudience);
    }

    private ResponseEntity<Object> processedResponseEntity(ResponseEntity<?> result, boolean hasAudience) throws IllegalAccessException {
        Object processedObject = processObject(result.getBody(), hasAudience);
        return new ResponseEntity<>(processedObject, result.getHeaders(), result.getStatusCode());
    }

    private Object processObject(Object object, boolean hasAudience) throws IllegalAccessException {
        if (object == null) {
            return null;
        }

        if (!(object instanceof Collection) && !object.getClass().isAnnotationPresent(AudienceFilterObject.class)) {
            return object;
        }

        if (object instanceof Collection) {
            return ((Collection<?>) object).stream()
                    .map(item -> {
                        try {
                            return processObject(item, hasAudience);
                        } catch (IllegalAccessException e) {
                            throw new RuntimeException("Error processing collection item", e);
                        }
                    })
                    .collect(Collectors.toList());
        }

        ObjectNode objectNode = objectMapper.valueToTree(object);

        for (Field field : object.getClass().getDeclaredFields()) {
            if (field.isAnnotationPresent(AudienceFilter.class)) {
                AudienceFilter audienceFilter = field.getAnnotation(AudienceFilter.class);
                if (hasAudience && audienceFilter.hideIfExists()) {
                    objectNode.remove(field.getName());
                }
            }
        }

        return objectMapper.convertValue(objectNode, object.getClass());
    }
}

