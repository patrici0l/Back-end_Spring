package com.lucero.backend.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
public class ProgramadorPublicoDTO {

    private UUID id;
    private String nombre;
    private String foto;

    private String especialidad;
    private String descripcion;

    // ✅ campos que tu front espera y hoy NO llegan
    private String emailContacto;
    private String whatsapp;
    private String github;
    private String linkedin;
    private String portafolio;

    private String disponibilidad;
    private List<String> horasDisponibles;

    private UUID usuarioId;

    public ProgramadorPublicoDTO(
            UUID id,
            String nombre,
            String foto,
            String especialidad,
            String descripcion,
            String emailContacto,
            String whatsapp,
            String github,
            String linkedin,
            String portafolio,
            String disponibilidad,
            List<String> horasDisponibles,
            UUID usuarioId
    ) {
        this.id = id;
        this.nombre = nombre;
        this.foto = foto;
        this.especialidad = especialidad;
        this.descripcion = descripcion;

        this.emailContacto = emailContacto;
        this.whatsapp = whatsapp;
        this.github = github;
        this.linkedin = linkedin;
        this.portafolio = portafolio;

        this.disponibilidad = disponibilidad;
        this.horasDisponibles = horasDisponibles;
        this.usuarioId = usuarioId;
    }
}
