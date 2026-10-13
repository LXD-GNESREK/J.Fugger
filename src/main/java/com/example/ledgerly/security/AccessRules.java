package com.example.ledgerly.security;

/**
 * Zentrale SpEL-Ausdrücke für {@code @PreAuthorize} (Issue 2.4).
 * Verwendung: {@code @PreAuthorize(AccessRules.MANAGER)}
 *
 * Vorteil gegenüber Strings direkt in der Annotation: Tippfehler wie "hasRole('MANGER')"
 * würden sonst still jeden Zugriff verweigern und erst zur Laufzeit auffallen.
 *
 * Wichtig: Das sind nur grobe Rollen-Checks. Objektbezogene Regeln
 * ("nur EIGENE Ausgaben", "nur Ausgaben des EIGENEN Teams") gehören zusätzlich
 * in domain/policy (ExpensePolicy / ApprovalPolicy), sonst droht IDOR.
 */

public class AccessRules {
    /** Öffentlich, z. B. Login. Bewusst explizit, damit es kein Versehen ist. */
    public static final String PUBLIC = "permitAll()";

    /** Irgendein eingeloggter User, egal welche Rolle. */
    public static final String AUTHENTICATED = "isAuthenticated()";

    public static final String EMPLOYEE = "hasRole('EMPLOYEE')";
    public static final String MANAGER = "hasRole('MANAGER')";
    public static final String FINANCE_ADMIN = "hasRole('FINANCE_ADMIN')";

    public static final String ANY_ROLE = "hasAnyRole('EMPLOYEE','MANAGER','FINANCE_ADMIN')";
    public static final String MANAGER_OR_FINANCE_ADMIN = "hasAnyRole('MANAGER','FINANCE_ADMIN')";

    private AccessRules()
    {
    }
}
