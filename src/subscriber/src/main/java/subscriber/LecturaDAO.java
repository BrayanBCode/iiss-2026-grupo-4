package subscriber;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;

public class LecturaDAO {

    // Crea la tabla la primera vez que arranca el subscriber (si no existe)
    public void crearTablaSiNoExiste() throws SQLException {
        String sql = """
            CREATE TABLE IF NOT EXISTS lecturas (
                id SERIAL PRIMARY KEY,
                habitacion VARCHAR(50) NOT NULL,
                temperatura NUMERIC(5,2) NOT NULL,
                fecha_hora TIMESTAMP NOT NULL
            )""";
        try (Statement st = ConexionDB.get().createStatement()) {
            st.execute(sql);
        }
    }

    // Inserta una lectura recibida por MQTT
    public void guardar(String habitacion, double temperatura, Instant timestamp) throws SQLException {
        String sql = "INSERT INTO lecturas (habitacion, temperatura, fecha_hora) VALUES (?, ?, ?)";
        try (PreparedStatement ps = ConexionDB.get().prepareStatement(sql)) {
            ps.setString(1, habitacion);
            ps.setBigDecimal(2, BigDecimal.valueOf(temperatura));
            ps.setTimestamp(3, Timestamp.from(timestamp));
            ps.executeUpdate();
        }
    }
}