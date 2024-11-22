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
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import uz.mirix.annotations.RoleVisibility;
import uz.mirix.annotations.VisibleForRoles;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

@Aspect
@Component
public class RoleBasedFieldHidingAspect {
    private final ObjectMapper objectMapper;

    public RoleBasedFieldHidingAspect(ObjectMapper objectMapper) {
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
            "@within(uz.mirix.annotations.RoleVisibilityController) && " +
            "@annotation(org.springframework.web.bind.annotation.GetMapping)"
    )
    public Object hideFieldsBasedOnRole(ProceedingJoinPoint joinPoint) throws Throwable {
        Object result = joinPoint.proceed();

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return result;
        }

        Set<String> userRoles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());

        if (result instanceof ResponseEntity) {
            return processedResponseEntity((ResponseEntity<?>) result, userRoles);
        }

        return processObject(result, userRoles);
    }

    private ResponseEntity<Object> processedResponseEntity(ResponseEntity<?> result, Set<String> userRoles) throws IllegalAccessException {
        Object processedObject = processObject(result.getBody(), userRoles);
        return new ResponseEntity<>(processedObject, result.getHeaders(), result.getStatusCode());
    }

    private Object processObject(Object object, Set<String> userRoles) throws IllegalAccessException {
        if (object == null) {
            return null;
        }

        if (object instanceof String || object.getClass().isPrimitive() || !object.getClass().isAnnotationPresent(RoleVisibility.class)) {
            return object;
        }

        if (object instanceof Collection) {
            return ((Collection<?>) object).stream()
                    .map(item -> {
                        try {
                            return processObject(item, userRoles);
                        } catch (IllegalAccessException e) {
                            throw new RuntimeException("Error processing collection item", e);
                        }
                    })
                    .collect(Collectors.toList());
        }

        ObjectNode objectNode = objectMapper.valueToTree(object);

        for (Field field : object.getClass().getDeclaredFields()) {
            if (field.isAnnotationPresent(VisibleForRoles.class)) {
                boolean isVisible = false;
                VisibleForRoles visibleForRoles = field.getAnnotation(VisibleForRoles.class);

                for (String role : visibleForRoles.roles()) {
                    if (userRoles.contains(role)) {
                        isVisible = true;
                        break;
                    }
                }

                if (!isVisible) {
                    objectNode.remove(field.getName());
                } else if (field.getType().isAnnotationPresent(RoleVisibility.class)) {
                    try {
                        field.setAccessible(true);
                        Object nestedObject = field.get(object);
                        Object processedNestedObject = processObject(nestedObject, userRoles);
                        objectNode.set(field.getName(), objectMapper.valueToTree(processedNestedObject));
                    } catch (Exception e) {
                        throw new RuntimeException("Error processing nested object", e);
                    }
                }
            }
        }

        return objectMapper.convertValue(objectNode, object.getClass());
    }
}

