package com.lucero.backend.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties; // 👈 IMPORTANTE
import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

@Data
@Entity
@Table(name = "asesorias")
public class Asesoria {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;
    @Column(name = "respondido_en")
    private LocalDateTime respondidoEn;

    @ManyToOne
    @JoinColumn(name = "programador_id", nullable = false)
    @JsonIgnoreProperties({ "asesorias", "usuario", "password", "rol" })
    private Programador programador;

    @ManyToOne
    @JoinColumn(name = "usuario_id")
    @JsonIgnoreProperties({ "asesorias", "password", "rol" }) // Opcional, por seguridad
    private Usuario usuario;

    @Column(name = "nombre_solicitante")
    private String nombreSolicitante;

    @Column(name = "email_solicitante")
    private String emailSolicitante;

    private LocalDate fecha;
    private LocalTime hora;
    private String comentario;
    private String estado;

    @Column(name = "respuesta_programador")
    private String respuestaProgramador;

    @Column(name = "creado_en")
    private LocalDateTime creadoEn;

    @PrePersist
    protected void onCreate() {
        creadoEn = LocalDateTime.now();
        if (estado == null)
            estado = "pendiente";
    }
}