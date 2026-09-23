package subscriber;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

// Metodo singletone
public class ConexionDB {

    // Variable estatica solo accesible por la clase
    private static Connection connection;

    // Función publica estática que interactúa con el resto del codigo brindado siempre la misma conexion
    public static Connection get() throws SQLException {
        if (connection == null || connection.isClosed()) {
            // Variables de entorno definidas en docker-compose: subscriber/enviroment
            String url = System.getenv("DB_URL");
            String user = System.getenv("DB_USER");
            String pass = System.getenv("DB_PASSWORD");
            connection = DriverManager.getConnection(url, user, pass);
        }
        return connection;
    }
}