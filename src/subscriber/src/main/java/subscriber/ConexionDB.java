package subscriber;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class ConexionDB {

    private static Connection connection;

    public static Connection get() throws SQLException {
        if (connection == null || connection.isClosed()) {
            String url = System.getenv("DB_URL");
            String user = System.getenv("DB_USER");
            String pass = System.getenv("DB_PASSWORD");
            connection = DriverManager.getConnection(url, user, pass);
        }
        return connection;
    }
}