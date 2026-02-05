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

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/programador/asesorias")
@CrossOrigin(origins = "*")
public class AsesoriaProgramadorController {

    @Autowired
    private AsesoriaRepository asesoriaRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private ProgramadorRepository programadorRepository;

    @Autowired
    private EmailService emailService;

    // Método auxiliar para obtener el programador logueado
    private Programador obtenerProgramadorActual() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = auth.getName();

        Usuario usuario = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        return programadorRepository.findByUsuarioId(usuario.getId())
                .orElseThrow(() -> new RuntimeException("No tienes perfil de programador creado."));
    }

    // 1) LISTAR ASESORÍAS DEL PROGRAMADOR LOGUEADO
    @GetMapping
    public List<Asesoria> listarMias() {
        Programador p = obtenerProgramadorActual();
        return asesoriaRepository.findByProgramadorId(p.getId());
    }

    // 2) APROBAR / RECHAZAR + RESPUESTA (CORREGIDO)
    @PutMapping("/{id}")
    public ResponseEntity<?> actualizarEstado(
            @PathVariable UUID id,
            @RequestBody Map<String, String> body) {

        System.out.println("--- INICIO DEBUG ACTUALIZACIÓN ---");

        Programador actual = obtenerProgramadorActual();
        Asesoria asesoria = asesoriaRepository.findById(id).orElse(null);

        if (asesoria == null)
            return ResponseEntity.notFound().build();

        // Seguridad: Verificar que la asesoría pertenece a este programador
        if (!asesoria.getProgramador().getId().equals(actual.getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("No autorizado");
        }

        String estado = body.get("estado");
        String respuesta = body.get("respuestaProgramador");

        // 1. Guardamos en Base de Datos
        asesoria.setEstado(estado);
        asesoria.setRespuestaProgramador(respuesta);
        Asesoria guardada = asesoriaRepository.save(asesoria);
        System.out.println(" BD Actualizada. Estado: " + estado);

        // 2. Lógica de Correo Electrónico
        if (estado != null && (estado.equals("aprobada") || estado.equals("rechazada"))) {

            String emailDestino = guardada.getEmailSolicitante();

            if (emailDestino == null || emailDestino.isBlank()) {
                System.out.println("⚠️ ALERTA: Email nulo o vacío. Cancelando envío.");
            } else {
                try {
                    // Definir Asunto
                    String asunto = estado.equals("aprobada") ? "✅ Asesoría Aprobada" : "❌ Asesoría Rechazada";

                    //  FIX: Construir mensaje con fallback si 'respuesta' es null o vacía
                    String mensaje = (respuesta != null && !respuesta.isBlank())
                            ? respuesta
                            : "Hola, tu solicitud de asesoría para el día " + guardada.getFecha()
                                    + " a las " + guardada.getHora() + " ha sido: " + estado + ".";

                    System.out.println("🚀 Enviando correo a: " + emailDestino);

                    emailService.enviarCorreo(emailDestino, asunto, mensaje);

                    System.out.println("✨ ÉXITO: Correo enviado correctamente.");
                } catch (Exception e) {
                    // Logueamos el error pero no bloqueamos la respuesta al cliente
                    System.err.println("❌ ERROR enviando correo: " + e.getMessage());
                }
            }
        } else {
            System.out.println("ℹ️ No se requiere envío de correo para el estado: " + estado);
        }

        System.out.println("--- FIN DEBUG ---");
        return ResponseEntity.ok(guardada);
    }
}