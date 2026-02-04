package com.lucero.backend.models;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Entity
@Table(name = "usuarios")
public class Usuario {

    @Id
@Column(columnDefinition = "uuid")
private UUID id;


    @Column(name = "firebase_uid", unique = true)
    private String firebaseUid;

    @Column(nullable = false)
    private String nombre;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "foto_url")
    private String fotoUrl;

    @Column(nullable = false)
    private String rol; 

    @Column(name = "password_hash")
    private String passwordHash;

    @Column(nullable = false)
    private Boolean activo = true;

    // JPA llenará esto automáticamente gracias a @PrePersist
    @Column(name = "creado_en", nullable = false, updatable = false)
    private LocalDateTime creadoEn;

    @PrePersist
    protected void onCreate() {
        this.creadoEn = LocalDateTime.now();
    }
}