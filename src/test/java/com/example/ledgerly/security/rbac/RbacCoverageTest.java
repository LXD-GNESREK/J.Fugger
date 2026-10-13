package com.example.ledgerly.security.rbac;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Build-Zeit-Check für Issue 2.4: Jede public Methode in Controllern und Services
 * braucht ein @PreAuthorize. Zur Laufzeit würde MethodSecurityConfig solche Methoden
 * zwar ohnehin blockieren (Default-Deny), aber so fällt es schon beim Build auf.
 */
class RbacCoverageTest
{
    private static final String BASE_PACKAGE = "com.example.ledgerly";
    private static final String SERVICE_PACKAGE = "com.example.ledgerly.application.service";

    @Test
    void everyPublicControllerAndServiceMethod_hasPreAuthorize() throws Exception
    {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        scanner.addIncludeFilter(new AnnotationTypeFilter(Service.class));
        // verschachtelte Klassen (z. B. Test-Controller in Tests) ignorieren
        scanner.addExcludeFilter((reader, factory) -> reader.getClassMetadata().hasEnclosingClass());

        List<String> missing = new ArrayList<>();
        for (BeanDefinition candidate : scanner.findCandidateComponents(BASE_PACKAGE))
        {
            Class<?> type = Class.forName(candidate.getBeanClassName());
            boolean controller = AnnotatedElementUtils.hasAnnotation(type, RestController.class);
            boolean appService = SERVICE_PACKAGE.equals(type.getPackageName());
            if (!controller && !appService)
            {
                continue;   // z. B. JwtService, AppUserDetailsService: Infrastruktur, kein Use-Case
            }
            boolean classLevel = AnnotatedElementUtils.hasAnnotation(type, PreAuthorize.class);

            for (Method m : type.getDeclaredMethods())
            {
                if (!Modifier.isPublic(m.getModifiers()) || Modifier.isStatic(m.getModifiers()) || m.isSynthetic())
                {
                    continue;
                }
                if (!classLevel && !AnnotatedElementUtils.hasAnnotation(m, PreAuthorize.class))
                {
                    missing.add(type.getSimpleName() + "#" + m.getName());
                }
            }
        }

        assertThat(missing)
                .as("Diese Methoden brauchen ein @PreAuthorize (z. B. @PreAuthorize(AccessRules.EMPLOYEE)) "
                        + "- sonst werden sie zur Laufzeit immer mit 403 abgelehnt")
                .isEmpty();
    }
}