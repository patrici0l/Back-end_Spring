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
import org.springframework.security.crypto.password.PasswordEncoder;
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
    private PasswordEncoder passwordEncoder;

    // -------------------------
    // GET
    // -------------------------
    @GetMapping
    public List<ProgramadorPublicoDTO> obtenerTodos() {
        return programadorRepository.findAll()
                .stream()
                .map(this::convertirADTO)
                .toList();
    }

    @GetMapping("/{id}")
    public ProgramadorPublicoDTO obtenerUno(@PathVariable UUID id) {
        Programador p = programadorRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("No encontrado"));
        return convertirADTO(p);
    }

    // -------------------------
    // Slots por fecha
    // -------------------------
    @GetMapping("/{id}/slots")
    public ResponseEntity<List<String>> obtenerSlotsDisponibles(
            @PathVariable UUID id,
            @RequestParam("fecha") String fechaStr
    ) {
        try {
            Programador p = programadorRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Programador no encontrado"));

            List<String> horasConfiguradas = p.getHorasDisponibles();
            if (horasConfiguradas == null || horasConfiguradas.isEmpty()) {
                return ResponseEntity.ok(Collections.emptyList());
            }

            LocalDate fecha = LocalDate.parse(fechaStr);
            var citasAgendadas = asesoriaRepository.findByProgramadorIdAndFecha(id, fecha);

            List<String> horasOcupadas = citasAgendadas.stream()
                    .map(cita -> {
                        String h = cita.getHora().toString();
                        return h.length() > 5 ? h.substring(0, 5) : h;
                    })
                    .collect(Collectors.toList());

            List<String> slotsLibres = new ArrayList<>();
            for (String hora : horasConfiguradas) {
                String horaSimple = hora.length() > 5 ? hora.substring(0, 5) : hora;
                if (!horasOcupadas.contains(horaSimple)) {
                    slotsLibres.add(horaSimple);
                }
            }

            Collections.sort(slotsLibres);
            return ResponseEntity.ok(slotsLibres);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().build();
        }
    }

    // -------------------------
    // POST (crear)
    // -------------------------
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
            @RequestParam(value = "horasDisponibles", required = false) String horasJson
    ) {
        try {
            // Email "real" para login (si no mandan email, se crea uno temporal)
            String emailReal = (emailContacto != null && !emailContacto.isBlank())
                    ? emailContacto
                    : "temp_" + UUID.randomUUID() + "@sistema.com";

            if (usuarioRepository.findByEmail(emailReal).isPresent()) {
                return ResponseEntity.badRequest()
                        .body("Error: El email " + emailReal + " ya está registrado.");
            }

            // Foto (OJO: aquí NO guardas el archivo real, solo generas URL)
            String urlFoto = null;
            if (file != null && !file.isEmpty()) {
                urlFoto = "https://ui-avatars.com/api/?name=" + nombre.replace(" ", "+");
            }

            // Usuario
            Usuario usuario = new Usuario();
            usuario.setNombre(nombre);
            usuario.setEmail(emailReal);
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

            // horasDisponibles
            if (horasJson != null && !horasJson.isBlank() && !"undefined".equalsIgnoreCase(horasJson)) {
                List<String> horas = objectMapper.readValue(horasJson, new TypeReference<List<String>>() {});
                p.setHorasDisponibles(horas);
            } else {
                p.setHorasDisponibles(Collections.emptyList());
            }

            Programador guardado = programadorRepository.save(p);
            return ResponseEntity.ok(convertirADTO(guardado)); // ✅ devuelve DTO

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body("Error al crear: " + e.getMessage());
        }
    }

    // -------------------------
    // PUT (actualizar)
    // -------------------------
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
            @RequestParam(value = "horasDisponibles", required = false) String horasJson
    ) {
        try {
            Programador p = programadorRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("No existe"));

            // Programador fields
            p.setDescripcion(descripcion);
            p.setEspecialidad(especialidad);
            p.setEmailContacto(emailContacto);
            p.setGithub(github);
            p.setLinkedin(linkedin);
            p.setPortafolio(portafolio);
            p.setWhatsapp(whatsapp);
            p.setDisponibilidadTexto(disponibilidad);

            if (horasJson != null && !horasJson.isBlank() && !"undefined".equalsIgnoreCase(horasJson)) {
                List<String> horas = objectMapper.readValue(horasJson, new TypeReference<List<String>>() {});
                p.setHorasDisponibles(horas);
            } else if (horasJson != null) {
                // si mandan explícitamente vacío, puedes decidir limpiar
                p.setHorasDisponibles(Collections.emptyList());
            }

            // Usuario
            Usuario u = p.getUsuario();
            if (u != null) {
                u.setNombre(nombre);

                // OJO: si quieres permitir cambiar email/login, aquí NO lo estás haciendo.
                // u.setEmail(...)

                if (file != null && !file.isEmpty()) {
                    String nuevaUrl = "https://ui-avatars.com/api/?name=" + nombre.replace(" ", "+");
                    u.setFotoUrl(nuevaUrl);
                }
                usuarioRepository.save(u);
            }

            Programador guardado = programadorRepository.save(p);
            return ResponseEntity.ok(convertirADTO(guardado)); // ✅ devuelve DTO

        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error al editar: " + e.getMessage());
        }
    }

    // -------------------------
    // DELETE
    // -------------------------
    @DeleteMapping("/{id}")
    public ResponseEntity<?> eliminarProgramador(@PathVariable UUID id) {
        try {
            Programador p = programadorRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("No existe el programador"));

            Usuario u = p.getUsuario();

            // Si tienes cascade bien configurado, esto borra hijos
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

    // -------------------------
    // DTO CONVERTER (COMPLETO)
    // -------------------------
    private ProgramadorPublicoDTO convertirADTO(Programador p) {
        Usuario u = p.getUsuario();

        String nombre = (u != null && u.getNombre() != null) ? u.getNombre() : "Sin Nombre";
        String foto = (u != null) ? u.getFotoUrl() : null;
        UUID usuarioId = (u != null) ? u.getId() : null;

        return new ProgramadorPublicoDTO(
                p.getId(),
                nombre,
                foto,
                p.getEspecialidad(),
                p.getDescripcion(),

                // ✅ campos extra que tu front usa
                p.getEmailContacto(),
                p.getWhatsapp(),
                p.getGithub(),
                p.getLinkedin(),
                p.getPortafolio(),

                p.getDisponibilidadTexto(),
                p.getHorasDisponibles(),
                usuarioId
        );
    }
}
