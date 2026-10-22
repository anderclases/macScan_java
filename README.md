# macScan

Proyecto de aplicación Java con integración de base de datos relacional mediante **SQLite**.

---

## 📋 Requisitos Previos

* **Java Development Kit (JDK):** Versión 8 o superior instalada.
* **Driver JDBC para SQLite:** `sqlite-jdbc-3.53.4.0.jar`

---

## 🛠️ Configuración de la Librería (`sqlite-jdbc-3.53.4.0.jar`)

Para que Java pueda conectarse y realizar consultas a la base de datos SQLite, es obligatorio incluir el conector JDBC (`sqlite-jdbc-3.53.4.0.jar`) en el *classpath* de tu proyecto.

### 1. Obtención del JAR
Asegúrate de tener el archivo `sqlite-jdbc-3.53.4.0.jar` disponible. Si no lo posees, puedes descargarlo desde el repositorio central de Maven o directamente en tu directorio local de trabajo (por ejemplo, en una carpeta llamada `lib/`).

### 2. Configuración según el IDE o Entorno

#### 🔹 IntelliJ IDEA
1. Ve a **File** > **Project Structure** (`Ctrl + Alt + Shift + S`).
2. En el menú de la izquierda, selecciona **Libraries**.
3. Haz clic en el botón `+` (New Project Library) y selecciona **Java**.
4. Busca y selecciona el archivo `sqlite-jdbc-3.53.4.0.jar`.
5. Presiona **Apply** y luego **OK**.

#### 🔹 Eclipse
1. Haz clic derecho sobre el proyecto en el explorador de paquetes y selecciona **Properties**.
2. Ve a **Java Build Path** > pestaña **Libraries**.
3. Selecciona **Classpath** (si aplica) y haz clic en **Add External JARs...**.
4. Selecciona el archivo `sqlite-jdbc-3.53.4.0.jar` y haz clic en **Open**.
5. Presiona **Apply and Close**.

#### 🔹 VS Code
1. Abre la vista de **Java Projects** en el panel lateral.
2. Despliega la sección **Referenced Libraries**.
3. Haz clic en el botón `+` y selecciona el archivo `sqlite-jdbc-3.53.4.0.jar`.

#### 🔹 Compilación y Ejecución por Terminal
Si compilas y ejecutas directamente desde la consola, debes incluir el archivo JAR con la bandera `-cp` (classpath):

```bash
# Compilar
javac -cp "lib/sqlite-jdbc-3.53.4.0.jar:." src/Main.java -d bin/

# Ejecutar (Linux / macOS)
java -cp "lib/sqlite-jdbc-3.53.4.0.jar:bin" Main

# Ejecutar (Windows)
java -cp "lib/sqlite-jdbc-3.53.4.0.jar;bin" Main
```

---

## 💾 Conexión a la Base de Datos

En el código Java, puedes establecer la conexión utilizando la siguiente cadena de conexión JDBC pointing al archivo `.db`:

```java
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DatabaseConnection {
    private static final String URL = "jdbc:sqlite:macScan/dispositivos.db";

    public static Connection connect() {
        Connection conn = null;
        try {
            conn = DriverManager.getConnection(URL);
            System.out.println("Conexión establecida con SQLite.");
        } catch (SQLException e) {
            System.err.println("Error al conectar con la base de datos: " + e.getMessage());
        }
        return conn;
    }
}
```

Cuando ejecutes el programa en la carpeta de tu usaurio va a crear la base de datos: networkmanager.db.

---

## ⚙️ Control de Versiones (Git)

Ten en cuenta que el archivo de base de datos local (`macScan/dispositivos.db`) y los archivos compilados están ignorados en el control de versiones (`.gitignore`) para evitar subir datos de prueba o temporales al repositorio.
