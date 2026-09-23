package subscriber;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;

/**
 * Administra la tabla "habitaciones": la asignación de qué termostato
 * (Shelly H&T Gen3) y qué switch (Shelly Pro 1PM) corresponde a cada
 * habitación. Esta asignación la hace el cliente UNA VEZ, al configurar su
 * sitio — el subscriber no la inventa ni la deduce de los mensajes MQTT que
 * recibe, solo la consulta para saber a qué habitación pertenece cada
 * lectura que llega.
 */
public class HabitacionDAO {
    public void crearTablaSiNoExiste() throws SQLException {
        String sql = """
            CREATE TABLE IF NOT EXISTS habitaciones (
                id                    SERIAL       PRIMARY KEY,
                nombre                VARCHAR(50)  NOT NULL UNIQUE,
                termostato_id         VARCHAR(50)  NOT NULL UNIQUE,
                switch_id             VARCHAR(50)  NOT NULL UNIQUE,
                temperatura_objetivo  NUMERIC(4,1)
            )""";
        try (Statement st = ConexionDB.get().createStatement()) {
            st.execute(sql);
        }
    }

    /**
     * Carga la asignación inicial de habitaciones, solo si la tabla está
     * vacía. Simula la configuración que el cliente ya hizo al instalar
     * cada dispositivo.
     */
    public void seedearSiVacia() throws SQLException {
        String contarSql = "SELECT COUNT(*) FROM habitaciones";
        try (Statement st = ConexionDB.get().createStatement();
            ResultSet rs = st.executeQuery(contarSql)) {
            rs.next();
            if (rs.getInt(1) > 0) {
                return; // ya hay datos, no pisamos la asignación del cliente
            }
        }

        String insertSql = """
            INSERT INTO habitaciones
                (nombre, termostato_id, switch_id, temperatura_objetivo)
            VALUES
                (?, ?, ?, ?)""";
        try (PreparedStatement ps = ConexionDB.get().prepareStatement(insertSql)) {
            insertar(ps, "living",     "shellyhtg3-a1b2c3d4e5f6", "shellypro1pm-30c6f780e918", 21.0);
            insertar(ps, "dormitorio", "shellyhtg3-b2c3d4e5f6a1", "shellypro1pm-30c6f781e6bc", 20.0);
            insertar(ps, "cocina",     "shellyhtg3-c3d4e5f6a1b2", "shellypro1pm-30c6f780c14c", 19.0);
        }
    }

    private void insertar(PreparedStatement ps, String nombre, String termostatoId,
                          String switchId, double temperaturaObjetivo) throws SQLException {
        ps.setString(1, nombre);
        ps.setString(2, termostatoId);
        ps.setString(3, switchId);
        ps.setDouble(4, temperaturaObjetivo);
        ps.executeUpdate();
    }

    /**
     * Busca a qué habitación pertenece un termostato, a partir del deviceId
     * que viene en el topic MQTT (ej. "shellyhtg3-a1b2c3d4e5f6").
     * Devuelve Optional.empty() si el dispositivo no fue asignado a ninguna
     * habitación por el cliente — un mensaje de un dispositivo desconocido
     * no se inventa a qué habitación pertenece, se descarta explícitamente.
     */
    public Optional<Habitacion> buscarPorTermostatoId(String termostatoId) throws SQLException {
        String sql = """
            SELECT id, nombre, temperatura_objetivo
            FROM habitaciones
            WHERE termostato_id = ?
            """;
        try (PreparedStatement ps = ConexionDB.get().prepareStatement(sql)) {
            ps.setString(1, termostatoId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new Habitacion(
                        rs.getInt("id"),
                        rs.getString("nombre"),
                        rs.getDouble("temperatura_objetivo")
                ));
            }
        }
    }
}