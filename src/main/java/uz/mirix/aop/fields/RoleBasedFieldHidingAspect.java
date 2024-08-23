package uz.mirix.aop.fields;

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

    @Around("@annotation(org.springframework.web.bind.annotation.GetMapping) && @annotation(uz.mirix.annotations.RoleVisibilityController)")
    public Object hideFieldsBasedOnRole(ProceedingJoinPoint joinPoint) throws Throwable {
         Object result = joinPoint.proceed();
        System.out.println("aspect is working: " + result.toString());

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

        if (!(object instanceof Collection) && !object.getClass().isAnnotationPresent(RoleVisibility.class)) {
            return object;
        }

        if (object instanceof Collection) {
            for (Object item : (Collection<?>) object) {
                processObject(item, userRoles);
            }
            return object;
        }

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
                    field.setAccessible(true);
                    field.set(object, null);
                }
            }
        }

        return object;
    }
}

