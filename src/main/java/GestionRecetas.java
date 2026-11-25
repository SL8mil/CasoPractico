import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;


public class GestionRecetas {


    private static final String SQL_LISTAR =
            "SELECT id, nombre, categoria, tiempo_preparacion, dificultad, raciones "
                    + "FROM recetas ORDER BY id";
    private static final String SQL_BUSCAR_ID =
            "SELECT id, nombre, categoria, tiempo_preparacion, dificultad, raciones "
                    + "FROM recetas WHERE id = ?";
    private static final String SQL_INSERTAR =
            "INSERT INTO recetas (nombre, categoria, tiempo_preparacion, dificultad, raciones) "
                    + "VALUES (?, ?, ?, ?, ?)";
    private static final String SQL_MODIFICAR =
            "UPDATE recetas SET tiempo_preparacion = ?, raciones = ? WHERE id = ?";
    private static final String SQL_ELIMINAR =
            "DELETE FROM recetas WHERE id = ?";
    private static final String SQL_ACTUALIZAR_ESTADISTICAS =
            "UPDATE estadisticas SET total_recetas = total_recetas + 1 WHERE id = ?";
    private static final String SQL_LEER_ESTADISTICAS =
            "SELECT total_recetas FROM estadisticas WHERE id = ?";
    private static final String SQL_PROCEDIMIENTO_BUSCAR =
            "{call buscarReceta(?)}";

    private static final int ID_ESTADISTICAS = 1;

    private static final Scanner teclado = new Scanner(System.in);


    public static void main(String[] args) {
        int opcion;
        do {
            mostrarMenu();
            opcion = leerEntero("Elige una opción: ", 0, 8);
            switch (opcion) {
                case 1 -> opcionMostrarTodas();
                case 2 -> opcionBuscarPorId();
                case 3 -> opcionBuscarPorNombre();
                case 4 -> opcionAgregar();
                case 5 -> opcionAgregarConTransaccion();
                case 6 -> opcionModificar();
                case 7 -> opcionEliminar();
                case 8 -> opcionMostrarEstadisticas();
                case 0 -> System.out.println("¡Hasta pronto!");
            }
        } while (opcion != 0);
    }

    private static void mostrarMenu() {
        System.out.println();
        System.out.println("GESTOR DE RECETAS");
        System.out.println("1. Mostrar todas las recetas");
        System.out.println("2. Buscar receta por id");
        System.out.println("3. Buscar receta por nombre (procedimiento almacenado)");
        System.out.println("4. Añadir receta");
        System.out.println("5. Añadir receta y actualizar estadísticas (transacción)");
        System.out.println("6. Modificar tiempo de preparación y raciones");
        System.out.println("7. Eliminar receta");
        System.out.println("8. Ver total de recetas (tabla estadisticas)");
        System.out.println("0. Salir");
    }


    private static void opcionMostrarTodas() {
        try {
            List<Receta> recetas = obtenerTodas();
            if (recetas.isEmpty()) {
                System.out.println("No hay recetas en el catálogo.");
            } else {
                imprimirCabecera();
                recetas.forEach(System.out::println);
            }
        } catch (SQLException e) {
            mostrarError("mostrar las recetas", e);
        }
    }

    private static void opcionBuscarPorId() {
        int id = leerEntero("Id de la receta: ", 1, Integer.MAX_VALUE);
        try {
            Receta receta = buscarPorId(id);
            if (receta == null) {
                System.out.println("No existe ninguna receta con id " + id + ".");
            } else {
                imprimirCabecera();
                System.out.println(receta);
            }
        } catch (SQLException e) {
            mostrarError("buscar la receta", e);
        }
    }

    private static void opcionBuscarPorNombre() {
        String nombre = leerTexto("Nombre exacto de la receta: ");
        try {
            List<Receta> recetas = buscarPorNombre(nombre);
            if (recetas.isEmpty()) {
                System.out.println("No se ha encontrado ninguna receta llamada \"" + nombre + "\".");
            } else {
                imprimirCabecera();
                recetas.forEach(System.out::println);
            }
        } catch (SQLException e) {
            mostrarError("ejecutar el procedimiento buscarReceta", e);
        }
    }

    private static void opcionAgregar() {
        Receta receta = pedirDatosReceta();
        try {
            int id = agregarReceta(receta);
            System.out.println("Receta añadida correctamente con id " + id + ".");
        } catch (SQLException e) {
            mostrarError("añadir la receta", e);
        }
    }

    private static void opcionAgregarConTransaccion() {
        Receta receta = pedirDatosReceta();
        try {
            int id = agregarRecetaConTransaccion(receta);
            System.out.println("Transacción confirmada (commit): receta añadida con id "
                    + id + " y estadísticas actualizadas.");
        } catch (SQLException e) {
            mostrarError("completar la transacción. Se han deshecho todos los cambios (rollback)", e);
        }
    }

    private static void opcionModificar() {
        int id = leerEntero("Id de la receta a modificar: ", 1, Integer.MAX_VALUE);
        int tiempo = leerEntero("Nuevo tiempo de preparación (min): ", 1, Integer.MAX_VALUE);
        int raciones = leerEntero("Nuevo número de raciones: ", 1, Integer.MAX_VALUE);
        try {
            if (modificarTiempoYRaciones(id, tiempo, raciones)) {
                System.out.println("Receta " + id + " modificada correctamente.");
            } else {
                System.out.println("No existe ninguna receta con id " + id + ".");
            }
        } catch (SQLException e) {
            mostrarError("modificar la receta", e);
        }
    }

    private static void opcionEliminar() {
        int id = leerEntero("Id de la receta a eliminar: ", 1, Integer.MAX_VALUE);
        try {
            if (eliminarReceta(id)) {
                System.out.println("Receta " + id + " eliminada del catálogo.");
            } else {
                System.out.println("No existe ninguna receta con id " + id + ".");
            }
        } catch (SQLException e) {
            mostrarError("eliminar la receta", e);
        }
    }

    private static void opcionMostrarEstadisticas() {
        try {
            System.out.println("Total de recetas registrado en estadisticas: " + obtenerTotalRecetas());
        } catch (SQLException e) {
            mostrarError("leer las estadísticas", e);
        }
    }


    public static List<Receta> obtenerTodas() throws SQLException {
        List<Receta> recetas = new ArrayList<>();
        try (Connection con = ConexionBD.getConexion();
             PreparedStatement ps = con.prepareStatement(SQL_LISTAR);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                recetas.add(leerReceta(rs));
            }
        }
        return recetas;
    }

    public static Receta buscarPorId(int id) throws SQLException {
        try (Connection con = ConexionBD.getConexion();
             PreparedStatement ps = con.prepareStatement(SQL_BUSCAR_ID)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? leerReceta(rs) : null;
            }
        }
    }

    public static List<Receta> buscarPorNombre(String nombre) throws SQLException {
        List<Receta> recetas = new ArrayList<>();
        try (Connection con = ConexionBD.getConexion();
             CallableStatement cs = con.prepareCall(SQL_PROCEDIMIENTO_BUSCAR)) {
            cs.setString(1, nombre);
            try (ResultSet rs = cs.executeQuery()) {
                while (rs.next()) {
                    recetas.add(leerReceta(rs));
                }
            }
        }
        return recetas;
    }

    public static int agregarReceta(Receta receta) throws SQLException {
        try (Connection con = ConexionBD.getConexion()) {
            return insertarReceta(con, receta);
        }
    }


    public static int agregarRecetaConTransaccion(Receta receta) throws SQLException {
        try (Connection con = ConexionBD.getConexion()) {
            con.setAutoCommit(false);
            try {
                int id = insertarReceta(con, receta);

                try (PreparedStatement ps = con.prepareStatement(SQL_ACTUALIZAR_ESTADISTICAS)) {
                    ps.setInt(1, ID_ESTADISTICAS);
                    if (ps.executeUpdate() == 0) {
                        throw new SQLException("No existe la fila de estadísticas con id "
                                + ID_ESTADISTICAS);
                    }
                }

                con.commit();
                return id;
            } catch (SQLException e) {
                con.rollback();
                throw e;
            } finally {
                con.setAutoCommit(true);
            }
        }
    }

    public static boolean modificarTiempoYRaciones(int id, int tiempo, int raciones) throws SQLException {
        try (Connection con = ConexionBD.getConexion();
             PreparedStatement ps = con.prepareStatement(SQL_MODIFICAR)) {
            ps.setInt(1, tiempo);
            ps.setInt(2, raciones);
            ps.setInt(3, id);
            return ps.executeUpdate() > 0;
        }
    }

    public static boolean eliminarReceta(int id) throws SQLException {
        try (Connection con = ConexionBD.getConexion();
             PreparedStatement ps = con.prepareStatement(SQL_ELIMINAR)) {
            ps.setInt(1, id);
            return ps.executeUpdate() > 0;
        }
    }

    public static int obtenerTotalRecetas() throws SQLException {
        try (Connection con = ConexionBD.getConexion();
             PreparedStatement ps = con.prepareStatement(SQL_LEER_ESTADISTICAS)) {
            ps.setInt(1, ID_ESTADISTICAS);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new SQLException("No existe la fila de estadísticas con id " + ID_ESTADISTICAS);
                }
                return rs.getInt("total_recetas");
            }
        }
    }


    private static int insertarReceta(Connection con, Receta receta) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(SQL_INSERTAR,
                PreparedStatement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, receta.getNombre());
            ps.setString(2, receta.getCategoria());
            ps.setInt(3, receta.getTiempoPreparacion());
            ps.setString(4, receta.getDificultad());
            ps.setInt(5, receta.getRaciones());
            ps.executeUpdate();
            try (ResultSet claves = ps.getGeneratedKeys()) {
                claves.next();
                int id = claves.getInt(1);
                receta.setId(id);
                return id;
            }
        }
    }

    private static Receta leerReceta(ResultSet rs) throws SQLException {
        return new Receta(
                rs.getInt("id"),
                rs.getString("nombre"),
                rs.getString("categoria"),
                rs.getInt("tiempo_preparacion"),
                rs.getString("dificultad"),
                rs.getInt("raciones"));
    }

    private static Receta pedirDatosReceta() {
        String nombre = leerTexto("Nombre: ");
        String categoria = leerTexto("Categoría: ");
        int tiempo = leerEntero("Tiempo de preparación (min): ", 1, Integer.MAX_VALUE);
        String dificultad = leerTexto("Dificultad (Fácil/Media/Difícil): ");
        int raciones = leerEntero("Raciones: ", 1, Integer.MAX_VALUE);
        return new Receta(nombre, categoria, tiempo, dificultad, raciones);
    }

    private static void imprimirCabecera() {
        System.out.printf("%-3s %-22s %-10s %8s  %-8s %s%n",
                "ID", "NOMBRE", "CATEGORÍA", "TIEMPO", "DIFICULT", "RACIONES");
        System.out.println("-".repeat(70));
    }

    private static void mostrarError(String accion, SQLException e) {
        System.out.println("ERROR al " + accion + ": " + e.getMessage());
    }

    private static int leerEntero(String mensaje, int min, int max) {
        while (true) {
            System.out.print(mensaje);
            if (!teclado.hasNextLine()) {
                System.exit(0);
            }
            String linea = teclado.nextLine().trim();
            try {
                int valor = Integer.parseInt(linea);
                if (valor >= min && valor <= max) {
                    return valor;
                }
                System.out.println("El valor debe estar entre " + min + " y " + max + ".");
            } catch (NumberFormatException e) {
                System.out.println("Introduce un número entero válido.");
            }
        }
    }

    private static String leerTexto(String mensaje) {
        while (true) {
            System.out.print(mensaje);
            if (!teclado.hasNextLine()) {
                System.exit(0);
            }
            String linea = teclado.nextLine().trim();
            if (!linea.isEmpty()) {
                return linea;
            }
            System.out.println("El campo no puede estar vacío.");
        }
    }
}
