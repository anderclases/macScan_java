package macScan;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GridLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.*;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.RowFilter;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;

public class NetworkScanner extends JFrame {

    private static final String DB_URL = "jdbc:sqlite:dispositivos.db";

    private JButton btnEscanear;
    private JButton btnGestionarDb;
    private JCheckBox chkOcultarNoRegistrados;

    private JTable tabla;
    private DefaultTableModel model;
    private TableRowSorter<DefaultTableModel> sorter;

    private JLabel lblEstado;
    private JProgressBar progressBar;

    static class Dispositivo {
        String ip;
        String mac;
        String usuario = "Sin Asignar";
        String estado = "OFFLINE";

        public Dispositivo(String ip, String mac) {
            this.ip = ip;
            this.mac = normalizarMac(mac);
        }
    }

    public NetworkScanner() {
        super("Escáner de Red MAC & Usuarios (SQLite)");
        inicializarBaseDatos();
        initUI();
    }

    // --- BASE DE DATOS SQLITE ---

    private static void inicializarBaseDatos() {
        String sql = "CREATE TABLE IF NOT EXISTS mac_usuarios (" +
                     "mac TEXT PRIMARY KEY, " +
                     "usuario TEXT NOT NULL);";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        } catch (Exception e) {
            System.err.println("Error al inicializar SQLite: " + e.getMessage());
        }
    }

    private static Map<String, String> cargarListaMacsDesdeBD() {
        Map<String, String> mapa = new HashMap<>();
        String sql = "SELECT mac, usuario FROM mac_usuarios";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                mapa.put(normalizarMac(rs.getString("mac")), rs.getString("usuario"));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return mapa;
    }

    private static void guardarOActualizarMac(String mac, String usuario) {
        String sql = "INSERT INTO mac_usuarios(mac, usuario) VALUES(?, ?) " +
                     "ON CONFLICT(mac) DO UPDATE SET usuario = excluded.usuario;";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, normalizarMac(mac));
            pstmt.setString(2, usuario);
            pstmt.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void eliminarMacBD(String mac) {
        String sql = "DELETE FROM mac_usuarios WHERE mac = ?";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, normalizarMac(mac));
            pstmt.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // --- INTERFAZ PRINCIPAL ---

    private void initUI() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(900, 550);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(10, 10));

        // --- Panel Superior ---
        JPanel panelControl = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));

        chkOcultarNoRegistrados = new JCheckBox("Solo esperados (Ocultar no registrados)");
        panelControl.add(chkOcultarNoRegistrados);

        btnEscanear = new JButton("Escanear Red");
        btnEscanear.setFont(btnEscanear.getFont().deriveFont(Font.BOLD));
        panelControl.add(btnEscanear);

        btnGestionarDb = new JButton("Gestionar MACs y Usuarios...");
        panelControl.add(btnGestionarDb);

        add(panelControl, BorderLayout.NORTH);

        // --- Tabla de Resultados ---
        String[] columnas = {"DIRECCIÓN IP", "DIRECCIÓN MAC", "USUARIO", "ESTADO"};
        model = new DefaultTableModel(columnas, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        tabla = new JTable(model);
        tabla.setRowHeight(24);
        tabla.setFont(new Font("SansSerif", Font.PLAIN, 12));
        tabla.getTableHeader().setFont(new Font("SansSerif", Font.BOLD, 12));
        tabla.setCellSelectionEnabled(true);

        sorter = new TableRowSorter<>(model);
        tabla.setRowSorter(sorter);

        // Formato visual para columna ESTADO (Índice 3)
        tabla.getColumnModel().getColumn(3).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                String estado = (String) value;
                if ("ONLINE".equals(estado)) {
                    c.setForeground(new Color(0, 130, 0));
                    setFont(getFont().deriveFont(Font.BOLD));
                } else {
                    c.setForeground(Color.RED);
                    setFont(getFont().deriveFont(Font.PLAIN));
                }
                setHorizontalAlignment(SwingConstants.CENTER);
                return c;
            }
        });

        // Menú Contextual (Clic Derecho)
        JPopupMenu popupMenu = new JPopupMenu();

        JMenuItem itemCopiar = new JMenuItem("Copiar valor de celda");
        itemCopiar.addActionListener(e -> copiarCeldaSeleccionada());
        popupMenu.add(itemCopiar);

        popupMenu.addSeparator();

        JMenuItem itemAsignar = new JMenuItem("Asignar / Editar Usuario (SQLite)");
        itemAsignar.addActionListener(e -> asignarUsuarioFilaSeleccionada());
        popupMenu.add(itemAsignar);

        JMenuItem itemEliminar = new JMenuItem("Eliminar de la Base de Datos");
        itemEliminar.addActionListener(e -> eliminarRegistroFilaSeleccionada());
        popupMenu.add(itemEliminar);

        tabla.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                evaluarClicDerecho(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                evaluarClicDerecho(e);
            }

            private void evaluarClicDerecho(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    int row = tabla.rowAtPoint(e.getPoint());
                    int col = tabla.columnAtPoint(e.getPoint());
                    if (row != -1 && col != -1) {
                        tabla.changeSelection(row, col, false, false);
                        popupMenu.show(e.getComponent(), e.getX(), e.getY());
                    }
                }
            }
        });

        add(new JScrollPane(tabla), BorderLayout.CENTER);

        // --- Panel Inferior ---
        JPanel panelInferior = new JPanel(new BorderLayout(10, 5));
        panelInferior.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));

        lblEstado = new JLabel("Listo para escanear. Base de datos SQLite activa.");
        progressBar = new JProgressBar();
        progressBar.setPreferredSize(new Dimension(150, 16));

        panelInferior.add(lblEstado, BorderLayout.WEST);
        panelInferior.add(progressBar, BorderLayout.EAST);

        add(panelInferior, BorderLayout.SOUTH);

        // --- Eventos ---
        btnEscanear.addActionListener(e -> ejecutarEscaneo());
        btnGestionarDb.addActionListener(e -> abrirVentanaGestionBD());
        chkOcultarNoRegistrados.addActionListener(e -> aplicarFiltro());
    }

    private void copiarCeldaSeleccionada() {
        int row = tabla.getSelectedRow();
        int col = tabla.getSelectedColumn();
        if (row != -1 && col != -1) {
            Object valor = tabla.getValueAt(row, col);
            if (valor != null) {
                StringSelection selection = new StringSelection(valor.toString());
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, selection);
            }
        }
    }

    private void asignarUsuarioFilaSeleccionada() {
        int row = tabla.getSelectedRow();
        if (row != -1) {
            int modelRow = tabla.convertRowIndexToModel(row);
            String mac = (String) model.getValueAt(modelRow, 1);
            String usuarioActual = (String) model.getValueAt(modelRow, 2);
            if ("No Registrado".equals(usuarioActual) || "Sin Asignar".equals(usuarioActual)) {
                usuarioActual = "";
            }

            String nuevoUsuario = JOptionPane.showInputDialog(this,
                    "Introduce el nombre del usuario asignado a la MAC (" + mac + "):",
                    usuarioActual);

            if (nuevoUsuario != null && !nuevoUsuario.trim().isEmpty()) {
                guardarOActualizarMac(mac, nuevoUsuario.trim());
                model.setValueAt(nuevoUsuario.trim(), modelRow, 2);
                aplicarFiltro();
            }
        }
    }

    private void eliminarRegistroFilaSeleccionada() {
        int row = tabla.getSelectedRow();
        if (row != -1) {
            int modelRow = tabla.convertRowIndexToModel(row);
            String mac = (String) model.getValueAt(modelRow, 1);

            int confirm = JOptionPane.showConfirmDialog(this,
                    "¿Deseas eliminar la asignación de la MAC " + mac + " de la BD?",
                    "Confirmar Eliminación", JOptionPane.YES_NO_OPTION);

            if (confirm == JOptionPane.YES_OPTION) {
                eliminarMacBD(mac);
                model.setValueAt("No Registrado", modelRow, 2);
                aplicarFiltro();
            }
        }
    }

    private void abrirVentanaGestionBD() {
        GestionMacsDialog dialog = new GestionMacsDialog(this);
        dialog.setVisible(true);
    }

    private void aplicarFiltro() {
        if (chkOcultarNoRegistrados.isSelected()) {
            // Columna USUARIO es la 2
            sorter.setRowFilter(RowFilter.notFilter(RowFilter.regexFilter("^No Registrado$", 2)));
        } else {
            sorter.setRowFilter(null);
        }
    }

    private String obtenerSubredLocal() {
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.connect(InetAddress.getByName("8.8.8.8"), 10002);
            String ip = socket.getLocalAddress().getHostAddress();
            int lastDot = ip.lastIndexOf('.');
            if (lastDot != -1) {
                return ip.substring(0, lastDot + 1);
            }
        } catch (Exception e) {
            try {
                String ip = InetAddress.getLocalHost().getHostAddress();
                int lastDot = ip.lastIndexOf('.');
                if (lastDot != -1) {
                    return ip.substring(0, lastDot + 1);
                }
            } catch (Exception ignored) {}
        }
        return "192.168.1.";
    }

    private void ejecutarEscaneo() {
        final String subredFinal = obtenerSubredLocal();

        btnEscanear.setEnabled(false);
        progressBar.setIndeterminate(true);
        lblEstado.setText("Escaneando red local en " + subredFinal + "* ...");
        model.setRowCount(0);

        SwingWorker<List<Dispositivo>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<Dispositivo> doInBackground() {
                return realizarEscaneoRed(subredFinal);
            }

            @Override
            protected void done() {
                try {
                    List<Dispositivo> resultados = get();
                    for (Dispositivo dev : resultados) {
                        model.addRow(new Object[]{
                                dev.ip,
                                dev.mac,
                                dev.usuario,
                                dev.estado
                        });
                    }
                    aplicarFiltro();
                    lblEstado.setText("Escaneo finalizado. Total: " + resultados.size() + " dispositivos.");
                } catch (Exception e) {
                    lblEstado.setText("Error en el escaneo: " + e.getMessage());
                    e.printStackTrace();
                } finally {
                    btnEscanear.setEnabled(true);
                    progressBar.setIndeterminate(false);
                }
            }
        };

        worker.execute();
    }

    private List<Dispositivo> realizarEscaneoRed(String subred) {
        Map<String, String> usuariosPorMac = cargarListaMacsDesdeBD();

        // 1. Pings masivos para actualizar la tabla ARP
        ExecutorService executorPing = Executors.newFixedThreadPool(60);
        for (int i = 1; i < 255; i++) {
            final String host = subred + i;
            executorPing.submit(() -> {
                try {
                    Process p = new ProcessBuilder("ping", "-n", "1", "-w", "150", host).start();
                    p.waitFor();
                } catch (Exception ignored) {
                }
            });
        }

        executorPing.shutdown();
        try {
            executorPing.awaitTermination(3, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // 2. Leer tabla ARP de Windows
        Map<String, Dispositivo> detectadosPorMac = new HashMap<>();
        Pattern pattern = Pattern.compile(
                "^\\s*([0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3})\\s+([0-9a-fA-F:-]{17})\\s+(din[áa]mico|dynamic)",
                Pattern.CASE_INSENSITIVE
        );

        try {
            Process process = new ProcessBuilder("arp", "-a").start();

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), Charset.forName("CP850")))) {

                String linea;
                while ((linea = reader.readLine()) != null) {
                    Matcher matcher = pattern.matcher(linea);
                    if (matcher.find()) {
                        String ip = matcher.group(1);
                        String macRaw = matcher.group(2);

                        if (!ip.endsWith(".255") && !ip.equals("255.255.255.255")) {
                            Dispositivo dev = new Dispositivo(ip, macRaw);
                            dev.estado = "ONLINE";
                            detectadosPorMac.put(dev.mac, dev);
                        }
                    }
                }
            }
            process.waitFor();
        } catch (Exception e) {
            e.printStackTrace();
        }

        // 3. Consolidar lista combinando SQLite con detectados
        List<Dispositivo> resultadoFinal = new ArrayList<>();
        Set<String> macsProcesadas = new HashSet<>();

        // Registrados en SQLite
        for (Map.Entry<String, String> entry : usuariosPorMac.entrySet()) {
            String mac = entry.getKey();
            String usuario = entry.getValue();
            macsProcesadas.add(mac);

            if (detectadosPorMac.containsKey(mac)) {
                Dispositivo dev = detectadosPorMac.get(mac);
                dev.usuario = usuario;
                resultadoFinal.add(dev);
            } else {
                Dispositivo devOffline = new Dispositivo("---", mac);
                devOffline.usuario = usuario;
                devOffline.estado = "OFFLINE";
                resultadoFinal.add(devOffline);
            }
        }

        // Detectados no registrados en BD
        for (Dispositivo dev : detectadosPorMac.values()) {
            if (!macsProcesadas.contains(dev.mac)) {
                dev.usuario = "No Registrado";
                resultadoFinal.add(dev);
            }
        }

        return resultadoFinal;
    }

    private static String normalizarMac(String mac) {
        if (mac == null) return "";
        return mac.replace(":", "-").trim().toUpperCase();
    }

    // =========================================================================
    // DIÁLOGO DE GESTIÓN DE MACS, USUARIOS, IMPORTAR Y EXPORTAR
    // =========================================================================

    static class GestionMacsDialog extends JDialog {

        private JTable tablaDb;
        private DefaultTableModel modelDb;

        public GestionMacsDialog(Frame parent) {
            super(parent, "Gestión de MACs y Usuarios (Base de Datos)", true);
            initUI();
            cargarDatos();
        }

        private void initUI() {
            setSize(600, 420);
            setLocationRelativeTo(getParent());
            setLayout(new BorderLayout(10, 10));

            // Tabla de Registros
            String[] columnas = {"DIRECCIÓN MAC", "USUARIO / PROPIETARIO"};
            modelDb = new DefaultTableModel(columnas, 0) {
                @Override
                public boolean isCellEditable(int row, int column) {
                    return false;
                }
            };

            tablaDb = new JTable(modelDb);
            tablaDb.setRowHeight(22);
            tablaDb.getTableHeader().setFont(new Font("SansSerif", Font.BOLD, 12));
            add(new JScrollPane(tablaDb), BorderLayout.CENTER);

            // Panel de Botones Laterales
            JPanel panelBotones = new JPanel(new GridLayout(6, 1, 5, 8));
            panelBotones.setBorder(BorderFactory.createEmptyBorder(10, 5, 10, 10));

            JButton btnAnadir = new JButton("Añadir");
            JButton btnEditar = new JButton("Editar");
            JButton btnEliminar = new JButton("Eliminar");
            JButton btnImportar = new JButton("Importar TXT/CSV");
            JButton btnExportar = new JButton("Exportar TXT/CSV");
            JButton btnCerrar = new JButton("Cerrar");

            panelBotones.add(btnAnadir);
            panelBotones.add(btnEditar);
            panelBotones.add(btnEliminar);
            panelBotones.add(btnImportar);
            panelBotones.add(btnExportar);
            panelBotones.add(btnCerrar);

            add(panelBotones, BorderLayout.EAST);

            // Eventos
            btnAnadir.addActionListener(e -> anadirRegistro());
            btnEditar.addActionListener(e -> editarRegistro());
            btnEliminar.addActionListener(e -> eliminarRegistro());
            btnImportar.addActionListener(e -> importarArchivo());
            btnExportar.addActionListener(e -> exportarArchivo());
            btnCerrar.addActionListener(e -> dispose());
        }

        private void cargarDatos() {
            modelDb.setRowCount(0);
            Map<String, String> datos = cargarListaMacsDesdeBD();
            for (Map.Entry<String, String> entry : datos.entrySet()) {
                modelDb.addRow(new Object[]{entry.getKey(), entry.getValue()});
            }
        }

        private void anadirRegistro() {
            JTextField txtMac = new JTextField();
            JTextField txtUsuario = new JTextField();
            Object[] message = {
                    "Dirección MAC:", txtMac,
                    "Usuario / Propietario:", txtUsuario
            };

            int option = JOptionPane.showConfirmDialog(this, message, "Nuevo Registro", JOptionPane.OK_CANCEL_OPTION);
            if (option == JOptionPane.OK_OPTION) {
                String mac = normalizarMac(txtMac.getText());
                String usuario = txtUsuario.getText().trim();

                if (mac.isEmpty() || usuario.isEmpty()) {
                    JOptionPane.showMessageDialog(this, "Debe completar ambos campos.", "Error", JOptionPane.ERROR_MESSAGE);
                    return;
                }

                guardarOActualizarMac(mac, usuario);
                cargarDatos();
            }
        }

        private void editarRegistro() {
            int row = tablaDb.getSelectedRow();
            if (row == -1) {
                JOptionPane.showMessageDialog(this, "Selecciona una fila para editar.", "Aviso", JOptionPane.WARNING_MESSAGE);
                return;
            }

            String macActual = (String) modelDb.getValueAt(row, 0);
            String usuarioActual = (String) modelDb.getValueAt(row, 1);

            String nuevoUsuario = JOptionPane.showInputDialog(this, "Editar usuario para la MAC (" + macActual + "):", usuarioActual);
            if (nuevoUsuario != null && !nuevoUsuario.trim().isEmpty()) {
                guardarOActualizarMac(macActual, nuevoUsuario.trim());
                cargarDatos();
            }
        }

        private void eliminarRegistro() {
            int row = tablaDb.getSelectedRow();
            if (row == -1) {
                JOptionPane.showMessageDialog(this, "Selecciona una fila para eliminar.", "Aviso", JOptionPane.WARNING_MESSAGE);
                return;
            }

            String mac = (String) modelDb.getValueAt(row, 0);
            int confirm = JOptionPane.showConfirmDialog(this, "¿Eliminar " + mac + " de la BD?", "Confirmar", JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION) {
                eliminarMacBD(mac);
                cargarDatos();
            }
        }

        private void exportarArchivo() {
            JFileChooser chooser = new JFileChooser(".");
            chooser.setDialogTitle("Exportar lista de MACs y Usuarios");
            chooser.setSelectedFile(new File("mac_list_export.txt"));
            chooser.setFileFilter(new FileNameExtensionFilter("Archivos de Texto (*.txt, *.csv)", "txt", "csv"));

            if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
                File file = chooser.getSelectedFile();
                try (BufferedWriter writer = new BufferedWriter(new FileWriter(file, StandardCharsets.UTF_8))) {
                    Map<String, String> datos = cargarListaMacsDesdeBD();
                    for (Map.Entry<String, String> entry : datos.entrySet()) {
                        writer.write(entry.getKey() + ";" + entry.getValue());
                        writer.newLine();
                    }
                    JOptionPane.showMessageDialog(this, "Lista exportada con éxito (" + datos.size() + " registros).", "Éxito", JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(this, "Error al exportar: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        }

        private void importarArchivo() {
            JFileChooser chooser = new JFileChooser(".");
            chooser.setDialogTitle("Importar lista desde TXT/CSV");
            chooser.setFileFilter(new FileNameExtensionFilter("Archivos de Texto (*.txt, *.csv)", "txt", "csv"));

            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                File file = chooser.getSelectedFile();
                int importados = 0;

                try (BufferedReader br = new BufferedReader(new FileReader(file, StandardCharsets.UTF_8))) {
                    String linea;
                    while ((linea = br.readLine()) != null) {
                        linea = linea.trim();
                        if (linea.isEmpty() || linea.startsWith("#")) continue;

                        String[] partes = linea.contains(";") ? linea.split(";") : linea.split(",");
                        if (partes.length >= 2) {
                            String mac = normalizarMac(partes[0]);
                            String usuario = partes[1].trim();

                            if (!mac.isEmpty() && !usuario.isEmpty()) {
                                guardarOActualizarMac(mac, usuario);
                                importados++;
                            }
                        }
                    }
                    cargarDatos();
                    JOptionPane.showMessageDialog(this, "Se importaron / actualizaron " + importados + " registros en SQLite.", "Importación Finalizada", JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(this, "Error al importar: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        }
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
        }

        SwingUtilities.invokeLater(() -> {
            new NetworkScanner().setVisible(true);
        });
    }
}