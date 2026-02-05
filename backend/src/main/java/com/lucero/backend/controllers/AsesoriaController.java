package com.lucero.backend.controllers;

import com.lucero.backend.models.Asesoria;
import com.lucero.backend.models.Programador;
import com.lucero.backend.models.Usuario;
import com.lucero.backend.repositories.AsesoriaRepository;
import com.lucero.backend.repositories.ProgramadorRepository;
import com.lucero.backend.repositories.UsuarioRepository;
import com.lucero.backend.services.EmailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;

@RestController
@RequestMapping("/api/asesorias")
@CrossOrigin(origins = "*")
public class AsesoriaController {

    @Autowired
    private AsesoriaRepository asesoriaRepository;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private ProgramadorRepository programadorRepository;
    @Autowired
    private EmailService emailService;

    // --- MÉTODOS DE APOYO ---

    private Usuario obtenerUsuarioActual() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = auth.getName();
        return usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
    }

    private Programador obtenerProgramadorActual() {
        Usuario u = obtenerUsuarioActual();
        return programadorRepository.findByUsuarioId(u.getId())
                .orElseThrow(() -> new RuntimeException("No tienes perfil de programador"));
    }

    // --- ENDPOINTS PÚBLICOS ---

    @PostMapping("/publica")
    public ResponseEntity<?> crearPublica(@RequestBody Map<String, Object> body) {
        try {
            // 1. Validar Programador
            UUID idProgramador = UUID.fromString((String) body.get("idProgramador"));
            Programador prog = programadorRepository.findById(idProgramador)
                    .orElseThrow(() -> new RuntimeException("Programador no existe"));

            Asesoria a = new Asesoria();
            a.setProgramador(prog);
            a.setNombreSolicitante((String) body.get("nombreSolicitante"));

            // 2. Email y Teléfono (Vinculación automática)
            String email = (String) body.get("emailSolicitante");
            a.setEmailSolicitante(email);

            // ✅ Captura de teléfono (Nueva funcionalidad)
            String telefono = body.get("telefonoSolicitante") != null
                    ? body.get("telefonoSolicitante").toString()
                    : null;
            a.setTelefonoSolicitante(telefono);

            // Vinculación con usuario existente si el email coincide
            usuarioRepository.findByEmail(email).ifPresent(a::setUsuario);

            // 3. Datos de la cita
            a.setComentario((String) body.getOrDefault("comentario", ""));
            a.setFecha(LocalDate.parse((String) body.get("fecha")));
            a.setHora(LocalTime.parse((String) body.get("hora")));
            a.setEstado("pendiente");

            return ResponseEntity.ok(asesoriaRepository.save(a));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }

    @GetMapping("/ocupadas/{idProgramador}/{fecha}")
    public List<Asesoria> getOcupadas(@PathVariable UUID idProgramador, @PathVariable String fecha) {
        LocalDate ld = LocalDate.parse(fecha);
        return asesoriaRepository.findByProgramadorIdAndFechaAndEstadoNot(idProgramador, ld, "rechazada");
    }

    // --- ENDPOINTS PRIVADOS (GESTIÓN) ---

    @GetMapping("/programador")
    public List<Asesoria> asesoriasDelProgramador() {
        Programador p = obtenerProgramadorActual();
        return asesoriaRepository.findByProgramadorId(p.getId());
    }

    @GetMapping("/mis")
    public List<Asesoria> misAsesoriasComoUsuario() {
        Usuario u = obtenerUsuarioActual();
        return asesoriaRepository.findByUsuarioId(u.getId());
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> actualizarAsesoria(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        Asesoria asesoria = asesoriaRepository.findById(id).orElse(null);
        if (asesoria == null)
            return ResponseEntity.notFound().build();

        Programador programadorActual = obtenerProgramadorActual();

        // Validaciones de seguridad y lógica de negocio
        if (!asesoria.getProgramador().getId().equals(programadorActual.getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("No autorizado");
        }

        if (!asesoria.getEstado().equalsIgnoreCase("pendiente")) {
            return ResponseEntity.badRequest()
                    .body("Esta asesoría ya fue procesada (" + asesoria.getEstado() + ") y no puede modificarse.");
        }

        if (asesoria.getFecha().isBefore(LocalDate.now())) {
            return ResponseEntity.badRequest()
                    .body("No se puede gestionar una asesoría de una fecha pasada.");
        }

        // Actualización de campos
        String estadoRaw = (String) body.get("estado");
        String estado = (estadoRaw != null) ? estadoRaw.toLowerCase().trim() : null;
        String respuesta = (String) body.get("respuestaProgramador");

        if (estado != null)
            asesoria.setEstado(estado);
        if (respuesta != null)
            asesoria.setRespuestaProgramador(respuesta);

        Asesoria guardada = asesoriaRepository.save(asesoria);

        // Envío de Email de notificación
        if (estado != null && (estado.equals("aprobada") || estado.equals("rechazada"))) {
            if (guardada.getEmailSolicitante() == null || guardada.getEmailSolicitante().isBlank()) {
                return ResponseEntity.ok(Map.of(
                        "asesoria", guardada,
                        "warning", "Estado actualizado, pero no hay email de contacto."));
            }

            try {
                String asunto = estado.equals("aprobada") ? "✅ Tu asesoría fue aprobada"
                        : "❌ Tu asesoría fue rechazada";
                String mensaje = (respuesta != null && !respuesta.isBlank()) ? respuesta
                        : "Tu asesoría para el día " + guardada.getFecha() + " ha sido: " + estado;

                emailService.enviarCorreo(guardada.getEmailSolicitante(), asunto, mensaje);
            } catch (Exception e) {
                return ResponseEntity.ok(Map.of(
                        "asesoria", guardada,
                        "warning", "Estado guardado, pero falló el correo: " + e.getMessage()));
            }
        }

        return ResponseEntity.ok(guardada);
    }

    // --- FILTROS AVANZADOS ---

    @GetMapping("/programador/filtradas")
    public ResponseEntity<?> asesoriasFiltradas(
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) String desde,
            @RequestParam(required = false) String hasta) {
        try {
            Programador p = obtenerProgramadorActual();

            // Caso 1: Sin filtros
            if (estado == null && desde == null && hasta == null) {
                return ResponseEntity.ok(asesoriaRepository.findByProgramadorId(p.getId()));
            }

            // Caso 2: Solo estado
            if (estado != null && desde == null && hasta == null) {
                return ResponseEntity.ok(asesoriaRepository.findByProgramadorIdAndEstado(p.getId(), estado));
            }

            // Caso 3: Rango de fechas
            if (estado == null && desde != null && hasta != null) {
                return ResponseEntity.ok(asesoriaRepository.findByProgramadorIdAndFechaBetween(
                        p.getId(), LocalDate.parse(desde), LocalDate.parse(hasta)));
            }

            // Caso 4: Estado + Rango de fechas
            if (estado != null && desde != null && hasta != null) {
                return ResponseEntity.ok(asesoriaRepository.findByProgramadorIdAndEstadoAndFechaBetween(
                        p.getId(), estado, LocalDate.parse(desde), LocalDate.parse(hasta)));
            }

            return ResponseEntity.badRequest().body("Combinación de filtros no válida.");

        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error al filtrar: " + e.getMessage());
        }
    }
}