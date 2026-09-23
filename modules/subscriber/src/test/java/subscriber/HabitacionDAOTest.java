package subscriber;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Pruebas de HabitacionDAO. Como ConexionDB.get() es estático, se mockea con
 * Mockito.mockStatic (Mockito 5, mock maker "inline" incluido por defecto).
 * No hace falta una base de datos real: todo el JDBC (Connection,
 * Statement, PreparedStatement, ResultSet) se mockea.
 */
@ExtendWith(MockitoExtension.class)
class HabitacionDAOTest {

    @Mock private Connection connection;
    @Mock private Statement statement;
    @Mock private PreparedStatement preparedStatement;
    @Mock private ResultSet resultSet;

    private HabitacionDAO habitacionDAO;

    @BeforeEach
    void setUp() {
        habitacionDAO = new HabitacionDAO();
    }

    @Test
    void crearTablaSiNoExiste_ejecutaElCreateTable() throws SQLException {
        try (MockedStatic<ConexionDB> conexionMock = mockStatic(ConexionDB.class)) {
            conexionMock.when(ConexionDB::get).thenReturn(connection);
            when(connection.createStatement()).thenReturn(statement);

            habitacionDAO.crearTablaSiNoExiste();

            verify(statement).execute(contains("CREATE TABLE IF NOT EXISTS habitaciones"));
        }
    }

    @Test
    void seedearSiVacia_conTablaVacia_insertaLasTresHabitaciones() throws SQLException {
        try (MockedStatic<ConexionDB> conexionMock = mockStatic(ConexionDB.class)) {
            conexionMock.when(ConexionDB::get).thenReturn(connection);
            when(connection.createStatement()).thenReturn(statement);
            when(statement.executeQuery(anyString())).thenReturn(resultSet);
            when(resultSet.next()).thenReturn(true);
            when(resultSet.getInt(1)).thenReturn(0); // tabla vacía
            when(connection.prepareStatement(contains("INSERT"))).thenReturn(preparedStatement);

            habitacionDAO.seedearSiVacia();

            // Se insertan las 3 habitaciones predefinidas
            verify(preparedStatement, times(3)).executeUpdate();
            verify(preparedStatement).setString(1, "living");
            verify(preparedStatement).setString(1, "dormitorio");
            verify(preparedStatement).setString(1, "cocina");
        }
    }

    @Test
    void seedearSiVacia_conTablaConDatos_noInsertaNada() throws SQLException {
        try (MockedStatic<ConexionDB> conexionMock = mockStatic(ConexionDB.class)) {
            conexionMock.when(ConexionDB::get).thenReturn(connection);
            when(connection.createStatement()).thenReturn(statement);
            when(statement.executeQuery(anyString())).thenReturn(resultSet);
            when(resultSet.next()).thenReturn(true);
            when(resultSet.getInt(1)).thenReturn(3); // ya hay datos

            habitacionDAO.seedearSiVacia();

            verify(connection, never()).prepareStatement(contains("INSERT"));
        }
    }

    @Test
    void buscarPorTermostatoId_devuelveLaHabitacion_siElTermostatoEstaAsignado() throws SQLException {
        try (MockedStatic<ConexionDB> conexionMock = mockStatic(ConexionDB.class)) {
            conexionMock.when(ConexionDB::get).thenReturn(connection);
            when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
            when(preparedStatement.executeQuery()).thenReturn(resultSet);
            when(resultSet.next()).thenReturn(true);
            when(resultSet.getInt("id")).thenReturn(1);
            when(resultSet.getString("nombre")).thenReturn("living");
            when(resultSet.getDouble("temperatura_objetivo")).thenReturn(21.0);

            Optional<Habitacion> resultado = habitacionDAO.buscarPorTermostatoId("shellyhtg3-a1b2c3d4e5f6");

            verify(preparedStatement).setString(1, "shellyhtg3-a1b2c3d4e5f6");
            assertTrue(resultado.isPresent());
            assertEquals(1, resultado.get().id());
            assertEquals("living", resultado.get().nombre());
            assertEquals(21.0, resultado.get().temperaturaObjetivo());
        }
    }

    @Test
    void buscarPorTermostatoId_devuelveEmpty_siElTermostatoNoEstaAsignado() throws SQLException {
        try (MockedStatic<ConexionDB> conexionMock = mockStatic(ConexionDB.class)) {
            conexionMock.when(ConexionDB::get).thenReturn(connection);
            when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
            when(preparedStatement.executeQuery()).thenReturn(resultSet);
            when(resultSet.next()).thenReturn(false); // no hay fila para ese termostato

            Optional<Habitacion> resultado = habitacionDAO.buscarPorTermostatoId("dispositivo-desconocido");

            assertTrue(resultado.isEmpty());
        }
    }
}
