package com.example.ledgerly.domain.entity;

import com.example.ledgerly.domain.model.Role;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(nullable = false)
    private String passwordHash;

    //Wir können uns first und last name lassen und nur username verwenden?
    //Was sagt ihr dazu?
    @Column(nullable = false, length = 100)
    private String firstName;

    @Column(nullable = false, length = 100)
    private String lastName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    // Vorgesetzter -> bildet das "Team" ab. Kann null sein (z. B. Admin, oberster Manager)
    //Ist das zu viel?
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "manager_id")
    private User manager;

    // Deaktivieren statt Löschen, damit alte Ausgaben ihren Ersteller behalten
    @Column(nullable = false)
    private boolean enabled = true;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    // Für JPA
    protected User() {
    }

    public User(String username, String passwordHash, String firstName,
                String lastName, Role role, User manager) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.firstName = firstName;
        this.lastName = lastName;
        this.role = role;
        this.manager = manager;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    // --- Änderungen nur über gezielte Methoden (nur der Admin ruft diese über den Service auf) ---

    public void changeRole(Role role) {
        this.role = role;
    }

    public void assignManager(User manager) {
        this.manager = manager;
    }

    public void changePasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public void enable() {
        this.enabled = true;
    }

    public void disable() {
        this.enabled = false;
    }

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public String getPasswordHash() { return passwordHash; }
    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
    public Role getRole() { return role; }
    public User getManager() { return manager; }
    public boolean isEnabled() { return enabled; }
    public Instant getCreatedAt() { return createdAt; }
}
