package servidor.tcp;

import datos.EntradaSalida;
import datos.Mensaje;

import java.net.*;
import java.io.*;
import java.text.DecimalFormat;

public class ServidorEscuchaTCP extends Thread {
    protected ServerSocket socket;
    protected Socket socket_cli;
    protected final int PUERTO_SERVER;
    
    public ServidorEscuchaTCP(int puertoS) throws Exception {
        PUERTO_SERVER = puertoS;
        socket = new ServerSocket(PUERTO_SERVER);
    }

    public void run() {
        try {
            EntradaSalida.mostrarMensaje("Servidor escuchando...\n");
            socket_cli = socket.accept();
            
            EntradaSalida.mostrarMensaje("Servidor conectado con cliente " +
                    socket_cli.getInetAddress() + ":" + socket_cli.getPort() + "...\n");
            
            DataInputStream in = new DataInputStream(socket_cli.getInputStream());
            
            while (true) {
                String tipo = in.readUTF(); // Primero leer el tipo de mensaje
                
                if ("ARCHIVO".equals(tipo)) {
                    recibirArchivo(in);
                } else if ("MENSAJE".equals(tipo)) {
                    Mensaje mensajeObj = new Mensaje();
                    mensajeObj.setMensaje(in.readUTF());
                    mensajeObj.setAddressCliente(socket_cli.getInetAddress());
                    mensajeObj.setPuertoCliente(socket_cli.getPort());
                    
                    EntradaSalida.mostrarMensaje("Mensaje recibido \"" + mensajeObj.getMensaje() + 
                            "\" del cliente " + mensajeObj.getAddressCliente() + 
                            ":" + mensajeObj.getPuertoCliente() + "\n");
                }
            }
        } catch (Exception e) {
            System.err.println("Error en ServidorEscuchaTCP: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private void recibirArchivo(DataInputStream in) throws IOException {
        DecimalFormat df = new DecimalFormat("0.00");
        String nombreArchivo = in.readUTF();
        long tamañoArchivo = in.readLong();
        
        EntradaSalida.mostrarMensaje("Recibiendo archivo: " + nombreArchivo + 
                " (" + tamañoArchivo + " bytes)\n");
        
        // Crear directorio "archivos_recibidos" si no existe
        new File("archivos_recibidos").mkdirs();
        FileOutputStream fos = new FileOutputStream("archivos_recibidos/" + nombreArchivo);
        
        byte[] buffer = new byte[8192];
        int bytesLeidos;
        long totalRecibido = 0;
        long inicio = System.currentTimeMillis();
        
        while (totalRecibido < tamañoArchivo && 
               (bytesLeidos = in.read(buffer, 0, (int) Math.min(buffer.length, tamañoArchivo - totalRecibido))) != -1) {
            fos.write(buffer, 0, bytesLeidos);
            totalRecibido += bytesLeidos;
            
            // Mostrar progreso
            if (tamañoArchivo > 0) {
                double porcentaje = (totalRecibido * 100.0) / tamañoArchivo;
                long tiempoTranscurrido = System.currentTimeMillis() - inicio;
                double velocidad = (totalRecibido * 8.0) / (tiempoTranscurrido / 1000.0); // en bits/segundo
                
                if (tiempoTranscurrido > 0) {
                    EntradaSalida.mostrarMensaje("\rProgreso: " + df.format(porcentaje) + "% | " +
                            "Recibidos: " + totalRecibido + "/" + tamañoArchivo + " bytes | " +
                            "Velocidad: " + df.format(velocidad/1024) + " Kbps");
                }
            }
        }
        
        fos.close();
        EntradaSalida.mostrarMensaje("\nArchivo recibido completamente: " + nombreArchivo + "\n");
        
        // Mostrar estadísticas finales
        long tiempoTotal = System.currentTimeMillis() - inicio;
        if (tiempoTotal > 0) {
            double velocidadPromedio = (totalRecibido * 8.0) / (tiempoTotal / 1000.0);
            EntradaSalida.mostrarMensaje("Tiempo total: " + (tiempoTotal/1000.0) + " segundos | " +
                    "Velocidad promedio: " + df.format(velocidadPromedio/1024) + " Kbps\n");
        }
    }
}