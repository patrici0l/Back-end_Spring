package com.lucero.backend.models;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Entity
@Table(name = "proyectos")
public class Proyecto {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;


    @ManyToOne
    @JoinColumn(name = "programador_id", nullable = false)
    private Programador programador;

    private String titulo;
    private String descripcion;
    private String tecnologias;

    @Column(name = "url_demo")
    private String urlDemo;

    @Column(name = "url_repo")
    private String urlRepo;

    private String estado;

    @Column(name = "creado_en")
    private LocalDateTime creadoEn;
}