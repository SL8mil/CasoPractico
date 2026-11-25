import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;


public class ConexionBD {

    private static final String URL = "jdbc:mysql://localhost:3306/recetario?useSSL=false&serverTimezone=UTC";
    private static final String USER = "root";
    private static final String PASSWORD = "root";

    private static final String BASE_DATOS = "recetario";

    public static Connection getConexion() throws SQLException {
        Connection conexion = DriverManager.getConnection(URL, USER, PASSWORD);
        conexion.setCatalog(BASE_DATOS);
        return conexion;
    }
}