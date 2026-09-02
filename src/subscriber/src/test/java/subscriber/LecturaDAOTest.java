package subscriber;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Pruebas de LecturaDAO. Igual que en HabitacionDAOTest, se mockea
 * ConexionDB.get() de forma estática para no necesitar Postgres real.
 */
@ExtendWith(MockitoExtension.class)
class LecturaDAOTest {

    @Mock private Connection connection;
    @Mock private Statement statement;
    @Mock private PreparedStatement preparedStatement;

    private LecturaDAO lecturaDAO;

    @BeforeEach
    void setUp() {
        lecturaDAO = new LecturaDAO();
    }

    @Test
    void crearTablaSiNoExiste_ejecutaElCreateTable() throws SQLException {
        try (MockedStatic<ConexionDB> conexionMock = mockStatic(ConexionDB.class)) {
            conexionMock.when(ConexionDB::get).thenReturn(connection);
            when(connection.createStatement()).thenReturn(statement);

            lecturaDAO.crearTablaSiNoExiste();

            verify(statement).execute(contains("CREATE TABLE IF NOT EXISTS lecturas"));
        }
    }

    @Test
    void guardar_insertaLaLecturaConLosValoresCorrectos() throws SQLException {
        try (MockedStatic<ConexionDB> conexionMock = mockStatic(ConexionDB.class)) {
            conexionMock.when(ConexionDB::get).thenReturn(connection);
            when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);

            Instant timestamp = Instant.parse("2026-09-01T10:00:00Z");

            lecturaDAO.guardar(7, 21.5, 70.7, timestamp);

            verify(preparedStatement).setInt(1, 7);
            verify(preparedStatement).setBigDecimal(2, BigDecimal.valueOf(21.5));
            verify(preparedStatement).setBigDecimal(3, BigDecimal.valueOf(70.7));
            verify(preparedStatement).setTimestamp(4, Timestamp.from(timestamp));
            verify(preparedStatement).executeUpdate();
        }
    }

    @Test
    void guardar_propagaLaSqlExceptionSiFallaElInsert() throws SQLException {
        try (MockedStatic<ConexionDB> conexionMock = mockStatic(ConexionDB.class)) {
            conexionMock.when(ConexionDB::get).thenReturn(connection);
            when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
            doThrow(new SQLException("fallo simulado")).when(preparedStatement).executeUpdate();

            assertThrows(SQLException.class, () ->
                    lecturaDAO.guardar(1, 20.0, 68.0, Instant.now()));
        }
    }
}
