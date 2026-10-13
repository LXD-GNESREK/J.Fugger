package com.example.ledgerly.security;
import org.springframework.aop.Advisor;
import org.springframework.aop.support.AopUtils;
import org.springframework.aop.support.StaticMethodMatcherPointcut;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Role;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.method.AuthorizationInterceptorsOrder;
import org.springframework.security.authorization.method.AuthorizationManagerBeforeMethodInterceptor;
import org.springframework.util.ClassUtils;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * Default-Deny für Method Security (Issue 2.4).
 *
 * Jede public Methode eines @RestController oder eines Service in application.service
 * MUSS ein @PreAuthorize haben (an der Methode oder an der Klasse). Fehlt es,
 * wird der Aufruf zur Laufzeit IMMER verweigert (403) - auch für FINANCE_ADMIN.
 * Vergessene Annotationen führen also zu "kein Zugriff" statt zu "Zugriff für alle".
 *
 * Zusätzlich prüft RbacCoverageTest das schon beim Build, damit man es nicht erst
 * im Browser merkt.
 */
@Configuration
public class MethodSecurityConfig {
    static final String SERVICE_PACKAGE = "com.example.ledgerly.application.service";

    // static + ROLE_INFRASTRUCTURE: so empfiehlt es Spring Security für eigene Method-Interceptors
    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    static Advisor denyMethodsWithoutPreAuthorize()
    {
        AuthorizationManagerBeforeMethodInterceptor interceptor =
                new AuthorizationManagerBeforeMethodInterceptor(
                        new MissingPreAuthorizePointcut(),
                        (authentication, invocation) -> new AuthorizationDecision(false));
        // gleiche Stelle wie @PreAuthorize -> greift vor @Transactional
        interceptor.setOrder(AuthorizationInterceptorsOrder.PRE_AUTHORIZE.getOrder());
        return interceptor;
    }

    /** Trifft genau die Methoden, die abgesichert sein müssten, es aber nicht sind. */
    static final class MissingPreAuthorizePointcut extends StaticMethodMatcherPointcut
    {
        @Override
        public boolean matches(Method method, Class<?> targetClass)
        {
            Class<?> userClass = ClassUtils.getUserClass(
                    targetClass != null ? targetClass : method.getDeclaringClass());   // CGLIB-Proxy -> echte Klasse
            if (!isInScope(userClass))
            {
                return false;
            }
            Method specific = AopUtils.getMostSpecificMethod(method, userClass);
            if (!Modifier.isPublic(specific.getModifiers())
                    || Modifier.isStatic(specific.getModifiers())
                    || specific.getDeclaringClass() == Object.class)
            {
                return false;
            }
            return !AnnotatedElementUtils.hasAnnotation(specific, PreAuthorize.class)
                    && !AnnotatedElementUtils.hasAnnotation(userClass, PreAuthorize.class);
        }

        static boolean isInScope(Class<?> type)
        {
            return AnnotatedElementUtils.hasAnnotation(type, RestController.class)
                    || SERVICE_PACKAGE.equals(type.getPackageName());
        }
    }
}
