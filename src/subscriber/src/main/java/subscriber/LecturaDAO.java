package subscriber;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;

public class LecturaDAO {

    // Crea la tabla la primera vez que arranca el subscriber (si no existe).
    // "habitacion_id" referencia a habitaciones(id) -- ya no guardamos el
    // nombre de la habitación suelto, sino el vínculo real con la
    // asignación que hizo el cliente.
    public void crearTablaSiNoExiste() throws SQLException {
        String sql = """
            CREATE TABLE IF NOT EXISTS lecturas (
                id             SERIAL         PRIMARY KEY,
                habitacion_id  INTEGER        NOT NULL REFERENCES habitaciones(id),
                temperatura_c  NUMERIC(5,2)   NOT NULL,
                temperatura_f  NUMERIC(5,2)   NOT NULL,
                fecha_hora     TIMESTAMP      NOT NULL
            )""";
        try (Statement st = ConexionDB.get().createStatement()) {
            st.execute(sql);
        }
    }

    // Inserta una lectura ya resuelta a una habitación concreta
    public void guardar(int habitacionId, double temperaturaC, double temperaturaF, Instant timestamp) throws SQLException {
        String sql = """
            INSERT INTO lecturas
                (habitacion_id, temperatura_c, temperatura_f, fecha_hora)
            VALUES
                (?, ?, ?, ?)
            """;
        try (PreparedStatement ps = ConexionDB.get().prepareStatement(sql)) {
            ps.setInt(1, habitacionId);
            ps.setBigDecimal(2, BigDecimal.valueOf(temperaturaC));
            ps.setBigDecimal(3, BigDecimal.valueOf(temperaturaF));
            ps.setTimestamp(4, Timestamp.from(timestamp));
            ps.executeUpdate();
        }
    }
}