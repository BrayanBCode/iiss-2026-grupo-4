package subscriber;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

public class LecturaDAO {

    // Crea la tabla la primera vez que arranca el subscriber (si no existe).
    // "habitacion_id" referencia a habitaciones(id)
    public void crearTablaSiNoExiste() throws SQLException {
        String sql = """
            CREATE TABLE IF NOT EXISTS lecturas (
                id             SERIAL         PRIMARY KEY,
                habitacion_id  INTEGER        NOT NULL REFERENCES habitaciones(id),
                temperatura_c  NUMERIC(5,2)   NOT NULL,
                temperatura_f  NUMERIC(5,2)   NOT NULL,
                epoch_mili     BIGINT         NOT NULL
            )""";
        try (Statement st = ConexionDB.get().createStatement()) {
            st.execute(sql);
        }
    }

    // Inserta una lectura ya resuelta a una habitación concreta.
    // epochMili: instante de la lectura en epoch milisegundos (UTC).
    public void guardar(int habitacionId, double temperaturaC, double temperaturaF, long epochMili) throws SQLException {
        String sql = """
            INSERT INTO lecturas
                (habitacion_id, temperatura_c, temperatura_f, epoch_mili)
            VALUES
                (?, ?, ?, ?)
            """;
        try (PreparedStatement ps = ConexionDB.get().prepareStatement(sql)) {
            ps.setInt(1, habitacionId);
            ps.setBigDecimal(2, BigDecimal.valueOf(temperaturaC));
            ps.setBigDecimal(3, BigDecimal.valueOf(temperaturaF));
            ps.setLong(4, epochMili);
            ps.executeUpdate();
        }
    }
}