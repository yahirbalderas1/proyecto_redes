package cliente.chat;

import java.awt.*;
import java.io.*;
import java.net.*;
import java.util.zip.CRC32;
import javax.swing.*;

public class ClienteChat extends JFrame {
    private JTextArea chatArea;
    private JTextField mensajeField;
    private JButton enviarBtn, archivoBtn;
    private JLabel estadoLabel;
    
    private Socket socketTCP;
    private DatagramSocket socketUDP;
    private DataOutputStream outTCP;
    
    
    private String servidorIP;
    private final int puertoTCP;
    private final int puertoUDP;
    
    // Constructor modificado
    public ClienteChat(String ipServidor, int puertoTCP, int puertoUDP) {
        this.servidorIP = ipServidor;
        this.puertoTCP = puertoTCP;
        this.puertoUDP = puertoUDP;
        
        initComponents(); // Cambiado de configurarGUI() a initComponents()
        conectarServidor();
        iniciarEscuchaUDP();
    }

    private void iniciarEscuchaUDP() {
        new Thread(() -> {
            try {
                DatagramSocket socketEscucha = new DatagramSocket(puertoUDP + 1);
                byte[] buffer = new byte[1024];
                
                while (true) {
                    DatagramPacket paquete = new DatagramPacket(buffer, buffer.length);
                    socketEscucha.receive(paquete);
                    
                    // Procesar mensaje
                    ByteArrayInputStream bais = new ByteArrayInputStream(paquete.getData());
                    DataInputStream dis = new DataInputStream(bais);
                    
                    long checksumRecibido = dis.readLong();
                    String mensaje = dis.readUTF();
                    
                    CRC32 crc = new CRC32();
                    crc.update(mensaje.getBytes("UTF-8"));
                    
                    if(crc.getValue() == checksumRecibido) {
                        agregarMensaje("Remoto: " + mensaje);
                    } else {
                        agregarMensaje("[ERROR] Mensaje corrupto recibido");
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }
    
    private void initComponents() {
        setTitle("Cliente de Chat");
        setSize(600, 400);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        
        chatArea = new JTextArea();
        chatArea.setEditable(false);
        JScrollPane scroll = new JScrollPane(chatArea);
        
        mensajeField = new JTextField();
        enviarBtn = new JButton("Enviar");
        archivoBtn = new JButton("Enviar Archivo");
        estadoLabel = new JLabel("Conectando...");
        
        enviarBtn.addActionListener(e -> enviarMensaje());
        archivoBtn.addActionListener(e -> seleccionarArchivo());
        mensajeField.addActionListener(e -> enviarMensaje());
        
        JPanel panelInferior = new JPanel(new BorderLayout());
        panelInferior.add(mensajeField, BorderLayout.CENTER);
        
        JPanel panelBotones = new JPanel();
        panelBotones.add(enviarBtn);
        panelBotones.add(archivoBtn);
        panelInferior.add(panelBotones, BorderLayout.EAST);
        
        add(scroll, BorderLayout.CENTER);
        add(panelInferior, BorderLayout.SOUTH);
        add(estadoLabel, BorderLayout.NORTH);
    }
    
    private void conectarServidor() {
        try {
            socketTCP = new Socket(servidorIP, puertoTCP);
            outTCP = new DataOutputStream(socketTCP.getOutputStream());
            socketUDP = new DatagramSocket();
            
            estadoLabel.setText("Conectado a " + servidorIP);
            
            // Hilo para recibir mensajes TCP (archivos)
            new Thread(() -> {
                try {
                    DataInputStream inTCP = new DataInputStream(socketTCP.getInputStream());
                    while(true) {
                        String tipo = inTCP.readUTF();
                        if(tipo.equals("ARCHIVO")) {
                            recibirArchivo(inTCP);
                        } else if(tipo.equals("MENSAJE")) {
                            String mensaje = inTCP.readUTF();
                            agregarMensaje("Servidor (TCP): " + mensaje);
                        }
                    }
                } catch(Exception e) {
                    e.printStackTrace();
                }
            }).start();
            
            // Hilo para recibir mensajes UDP (chat)
            new Thread(() -> {
                byte[] buffer = new byte[1024];
                while(true) {
                    DatagramPacket paquete = new DatagramPacket(buffer, buffer.length);
                    try {
                        socketUDP.receive(paquete);
                        String mensaje = new String(paquete.getData(), 0, paquete.getLength());
                        agregarMensaje("Servidor (UDP): " + mensaje);
                    } catch(Exception e) {
                        e.printStackTrace();
                    }
                }
            }).start();
            
        } catch(Exception e) {
            JOptionPane.showMessageDialog(this, "Error al conectar: " + e.getMessage());
        }
    }
    
    private void enviarMensaje() {
        String mensaje = mensajeField.getText();
        if(mensaje.isEmpty()) return;
        
        try {
            // Enviar por UDP con checksum
            byte[] bytesMensaje = mensaje.getBytes();
            CRC32 crc = new CRC32();
            crc.update(bytesMensaje);
            long checksum = crc.getValue();
            
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);
            dos.writeLong(checksum);
            dos.write(bytesMensaje);
            
            byte[] datos = baos.toByteArray();
            DatagramPacket paquete = new DatagramPacket(
                datos, datos.length, InetAddress.getByName(servidorIP), puertoUDP);
            
            socketUDP.send(paquete);
            agregarMensaje("Yo (UDP): " + mensaje);
            mensajeField.setText("");
        } catch(Exception e) {
            e.printStackTrace();
        }
    }
    
    private void seleccionarArchivo() {
        JFileChooser fileChooser = new JFileChooser();
        if(fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File archivo = fileChooser.getSelectedFile();
            new Thread(() -> enviarArchivo(archivo)).start();
        }
    }
    
    private void enviarArchivo(File archivo) {
        try {
            long startTime = System.currentTimeMillis();
            long fileSize = archivo.length();
            
            outTCP.writeUTF("ARCHIVO");
            outTCP.writeUTF(archivo.getName());
            outTCP.writeLong(fileSize);
            
            FileInputStream fis = new FileInputStream(archivo);
            byte[] buffer = new byte[8192];
            int leidos;
            long totalLeidos = 0;
            
            while((leidos = fis.read(buffer)) > 0) {
                outTCP.write(buffer, 0, leidos);
                totalLeidos += leidos;
                
                // Calcular y mostrar progreso
                long tiempoTranscurrido = System.currentTimeMillis() - startTime;
                double tasa = (totalLeidos * 8.0) / (tiempoTranscurrido / 1000.0); // bps
                double progreso = (totalLeidos * 100.0) / fileSize;
                
                SwingUtilities.invokeLater(() -> {
                    estadoLabel.setText(String.format(
                        "Enviando %s: %.2f%% - Tasa: %.2f bps - Tiempo: %d ms",
                        archivo.getName(), progreso, tasa, tiempoTranscurrido));
                });
            }
            
            fis.close();
            SwingUtilities.invokeLater(() -> {
                estadoLabel.setText("Archivo enviado: " + archivo.getName());
                agregarMensaje("Yo (Archivo): " + archivo.getName() + " enviado");
            });
            
        } catch(Exception e) {
            e.printStackTrace();
        }
    }
    
    private void recibirArchivo(DataInputStream inTCP) throws IOException {
        String nombreArchivo = inTCP.readUTF();
        long fileSize = inTCP.readLong();
        
        long startTime = System.currentTimeMillis();
        long totalRecibidos = 0;
        
        FileOutputStream fos = new FileOutputStream("recibido_" + nombreArchivo);
        byte[] buffer = new byte[8192];
        int leidos;
        
        while(totalRecibidos < fileSize && 
              (leidos = inTCP.read(buffer, 0, (int)Math.min(buffer.length, fileSize - totalRecibidos))) > 0) {
            fos.write(buffer, 0, leidos);
            totalRecibidos += leidos;
            
            // Calcular y mostrar progreso
            long tiempoTranscurrido = System.currentTimeMillis() - startTime;
            double tasa = (totalRecibidos * 8.0) / (tiempoTranscurrido / 1000.0); // bps
            double progreso = (totalRecibidos * 100.0) / fileSize;
            
            SwingUtilities.invokeLater(() -> {
                estadoLabel.setText(String.format(
                    "Recibiendo %s: %.2f%% - Tasa: %.2f bps - Tiempo: %d ms",
                    nombreArchivo, progreso, tasa, tiempoTranscurrido));
            });
        }
        
        fos.close();
        SwingUtilities.invokeLater(() -> {
            estadoLabel.setText("Archivo recibido: " + nombreArchivo);
            agregarMensaje("Servidor (Archivo): " + nombreArchivo + " recibido");
        });
    }
    
    private void agregarMensaje(String mensaje) {
        SwingUtilities.invokeLater(() -> {
            chatArea.append(mensaje + "\n");
        });
    }
    
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            String ip = JOptionPane.showInputDialog("IP del servidor:", "localhost");
            new ClienteChat(ip, 12345, 54321).setVisible(true);
        });
    }
}