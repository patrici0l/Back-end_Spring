package com.lucero.backend.repositories;

import com.lucero.backend.models.Asesoria;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import java.util.Optional;
import java.util.Map;

public interface AsesoriaRepository extends JpaRepository<Asesoria, UUID> {

        List<Asesoria> findByProgramadorId(UUID programadorId);

        // Para saber horas ocupadas en una fecha
        List<Asesoria> findByProgramadorIdAndFecha(UUID programadorId, LocalDate fecha);

        List<Asesoria> findByProgramadorIdAndEstado(
                        UUID programadorId,
                        String estado);

        List<Asesoria> findByProgramadorIdAndFechaBetween(
                        UUID programadorId,
                        LocalDate desde,
                        LocalDate hasta);

        List<Asesoria> findByProgramadorIdAndEstadoAndFechaBetween(
                        UUID programadorId,
                        String estado,
                        LocalDate desde,
                        LocalDate hasta);

        List<Asesoria> findByUsuarioId(UUID usuarioId);

        // ESTE ES EL QUE PEDISTE (Ya estaba, lo mantenemos aquí)
        List<Asesoria> findByProgramadorIdOrderByFechaAscHoraAsc(UUID programadorId);

        List<Asesoria> findByProgramadorIdAndFechaAndEstadoNot(UUID programadorId, LocalDate fecha, String estado);

        Optional<Asesoria> findByProgramadorIdAndFechaAndHora(UUID programadorId, LocalDate fecha,
                        java.time.LocalTime hora);

        boolean existsByProgramadorIdAndFechaAndHoraAndEstadoNot(
                        UUID programadorId,
                        LocalDate fecha,
                        LocalTime hora,
                        String estado);

        long countByProgramadorIdAndEstado(UUID programadorId, String estado);

        long countByProgramadorId(UUID programadorId);

        // Serie por fecha (para gráfico)
        @Query("""
                            SELECT a.fecha as fecha, COUNT(a) as total
                            FROM Asesoria a
                            WHERE a.programador.id = :programadorId
                            GROUP BY a.fecha
                            ORDER BY a.fecha
                        """)
        List<Map<String, Object>> countPorFecha(@Param("programadorId") UUID programadorId);
}