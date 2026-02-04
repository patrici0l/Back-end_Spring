package com.lucero.backend.controllers;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lucero.backend.dto.ProgramadorPublicoDTO;
import com.lucero.backend.models.Programador;
import com.lucero.backend.models.Usuario;
import com.lucero.backend.repositories.AsesoriaRepository;
import com.lucero.backend.repositories.ProgramadorRepository;
import com.lucero.backend.repositories.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder; // ✅ IMPORT NECESARIO
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/programadores")
@CrossOrigin(origins = "*")
public class ProgramadorController {

    @Autowired
    private ProgramadorRepository programadorRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private AsesoriaRepository asesoriaRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PasswordEncoder passwordEncoder; // ✅ INYECCIÓN DE DEPENDENCIA

    // --- GET METHODS ---
    @GetMapping
    public List<ProgramadorPublicoDTO> obtenerTodos() {
        return programadorRepository.findAll().stream().map(this::convertirADTO).toList();
    }

    @GetMapping("/{id}")
    public ProgramadorPublicoDTO obtenerUno(@PathVariable UUID id) {
        Programador p = programadorRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("No encontrado"));
        return convertirADTO(p);
    }

    // ✅ Calcular Slots Libres por Fecha
    @GetMapping("/{id}/slots")
    public ResponseEntity<List<String>> obtenerSlotsDisponibles(
            @PathVariable UUID id,
            @RequestParam("fecha") String fechaStr // YYYY-MM-DD
    ) {
        try {
            // 1. Buscar al programador
            Programador p = programadorRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Programador no encontrado"));

            // 2. Obtener sus horas base (ej: ["09:00", "10:00", "11:00"])
            List<String> horasConfiguradas = p.getHorasDisponibles();
            if (horasConfiguradas == null || horasConfiguradas.isEmpty()) {
                return ResponseEntity.ok(Collections.emptyList());
            }

            // 3. Obtener las citas que YA tiene agendadas ese día
            LocalDate fecha = LocalDate.parse(fechaStr);
            var citasAgendadas = asesoriaRepository.findByProgramadorIdAndFecha(id, fecha);

            // 4. Sacar la lista de horas ocupadas (cortando los segundos si vienen
            // 09:00:00)
            List<String> horasOcupadas = citasAgendadas.stream()
                    .map(cita -> {
                        String h = cita.getHora().toString();
                        return h.length() > 5 ? h.substring(0, 5) : h;
                    })
                    .collect(Collectors.toList());

            // 5. RESTAR: Disponibles = Configuradas - Ocupadas
            List<String> slotsLibres = new ArrayList<>();
            for (String hora : horasConfiguradas) {
                // Aseguramos formato HH:mm
                String horaSimple = hora.length() > 5 ? hora.substring(0, 5) : hora;

                if (!horasOcupadas.contains(horaSimple)) {
                    slotsLibres.add(horaSimple);
                }
            }

            Collections.sort(slotsLibres); // Ordenar para que salgan bonitas (09:00, 10:00...)

            return ResponseEntity.ok(slotsLibres);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().build();
        }
    }

    // --- CREAR (POST) ---
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> crearProgramador(
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam("nombre") String nombre,
            @RequestParam("descripcion") String descripcion,
            @RequestParam("especialidad") String especialidad,
            @RequestParam(value = "emailContacto", required = false) String emailContacto,
            @RequestParam(value = "github", required = false) String github,
            @RequestParam(value = "linkedin", required = false) String linkedin,
            @RequestParam(value = "portafolio", required = false) String portafolio,
            @RequestParam(value = "whatsapp", required = false) String whatsapp,
            @RequestParam(value = "disponibilidad", required = false) String disponibilidad,
            @RequestParam(value = "horasDisponibles", required = false) String horasJson) {
        try {
            // Validación Email
            String emailReal = (emailContacto != null && !emailContacto.isBlank())
                    ? emailContacto
                    : "temp_" + UUID.randomUUID() + "@sistema.com";

            if (usuarioRepository.findByEmail(emailReal).isPresent()) {
                return ResponseEntity.badRequest().body("Error: El email " + emailReal + " ya está registrado.");
            }

            // Foto
            String urlFoto = null;
            if (file != null && !file.isEmpty()) {
                urlFoto = "https://ui-avatars.com/api/?name=" + nombre.replace(" ", "+");
            }

            // Usuario
            Usuario usuario = new Usuario();
            usuario.setNombre(nombre);
            usuario.setEmail(emailReal);

            // ✅ APLICANDO EL FIX DE PASSWORD ENCODER AQUÍ
            usuario.setPasswordHash(passwordEncoder.encode("123456"));

            usuario.setRol("programador");
            usuario.setActivo(true);
            usuario.setFotoUrl(urlFoto);
            usuario = usuarioRepository.save(usuario);

            // Programador
            Programador p = new Programador();
            p.setUsuario(usuario);
            p.setEspecialidad(especialidad);
            p.setDescripcion(descripcion);
            p.setEmailContacto(emailContacto);
            p.setGithub(github);
            p.setLinkedin(linkedin);
            p.setPortafolio(portafolio);
            p.setWhatsapp(whatsapp);
            p.setDisponibilidadTexto(disponibilidad);

            if (horasJson != null && !horasJson.isEmpty() && !horasJson.equals("undefined")) {
                List<String> horas = objectMapper.readValue(horasJson, new TypeReference<List<String>>() {
                });
                p.setHorasDisponibles(horas);
            } else {
                p.setHorasDisponibles(Collections.emptyList());
            }

            return ResponseEntity.ok(programadorRepository.save(p));

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body("Error al crear: " + e.getMessage());
        }
    }

    // --- ACTUALIZAR (PUT) ---
    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> actualizarProgramador(
            @PathVariable UUID id,
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam("nombre") String nombre,
            @RequestParam("descripcion") String descripcion,
            @RequestParam("especialidad") String especialidad,
            @RequestParam(value = "emailContacto", required = false) String emailContacto,
            @RequestParam(value = "github", required = false) String github,
            @RequestParam(value = "linkedin", required = false) String linkedin,
            @RequestParam(value = "portafolio", required = false) String portafolio,
            @RequestParam(value = "whatsapp", required = false) String whatsapp,
            @RequestParam(value = "disponibilidad", required = false) String disponibilidad,
            @RequestParam(value = "horasDisponibles", required = false) String horasJson) {
        try {
            Programador p = programadorRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("No existe"));

            p.setDescripcion(descripcion);
            p.setEspecialidad(especialidad);
            p.setEmailContacto(emailContacto);
            p.setGithub(github);
            p.setLinkedin(linkedin);
            p.setPortafolio(portafolio);
            p.setWhatsapp(whatsapp);
            p.setDisponibilidadTexto(disponibilidad);

            if (horasJson != null && !horasJson.isEmpty() && !horasJson.equals("undefined")) {
                List<String> horas = objectMapper.readValue(horasJson, new TypeReference<List<String>>() {
                });
                p.setHorasDisponibles(horas);
            }

            Usuario u = p.getUsuario();
            if (u != null) {
                u.setNombre(nombre);
                if (file != null && !file.isEmpty()) {
                    String nuevaUrl = "https://ui-avatars.com/api/?name=" + nombre;
                    u.setFotoUrl(nuevaUrl);
                }
                usuarioRepository.save(u);
            }

            return ResponseEntity.ok(programadorRepository.save(p));

        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error al editar: " + e.getMessage());
        }
    }

    // --- ELIMINAR (DELETE) ---
    @DeleteMapping("/{id}")
    public ResponseEntity<?> eliminarProgramador(@PathVariable UUID id) {
        try {
            Programador p = programadorRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("No existe el programador"));

            Usuario u = p.getUsuario();

            // La BD con CASCADE se encarga de los hijos
            programadorRepository.delete(p);

            if (u != null) {
                usuarioRepository.delete(u);
            }

            Map<String, String> response = new HashMap<>();
            response.put("mensaje", "Programador eliminado correctamente");
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", "Error al eliminar: " + e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    // --- CONVERSOR DTO ---
    private ProgramadorPublicoDTO convertirADTO(Programador p) {
        String nombre = (p.getUsuario() != null) ? p.getUsuario().getNombre() : "Sin Nombre";
        String foto = (p.getUsuario() != null) ? p.getUsuario().getFotoUrl() : null;

        return new ProgramadorPublicoDTO(
                p.getId(),
                nombre,
                foto,
                p.getEspecialidad(),
                p.getDescripcion(),
                p.getDisponibilidadTexto(),
                p.getHorasDisponibles(),
                p.getUsuario().getId());
    }
}