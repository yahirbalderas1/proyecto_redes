package servidor.udp;

import datos.Mensaje;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.net.*;
import java.util.zip.CRC32;

public class ServidorEscuchaUDP extends Thread {
    protected DatagramSocket socket;
    protected final int PUERTO_SERVER;
    
    public ServidorEscuchaUDP(int puertoS) throws Exception {
        PUERTO_SERVER = puertoS;
        socket = new DatagramSocket(PUERTO_SERVER);
    }
    
    public void run() {
        try {
            byte[] buffer = new byte[1024];
            System.out.println("Servidor UDP escuchando...");
            
            while(true) {
                DatagramPacket paquete = new DatagramPacket(buffer, buffer.length);
                socket.receive(paquete);
                
                Mensaje mensaje = procesarPaquete(paquete);
                System.out.println("Mensaje UDP recibido: " + mensaje.getMensaje() + 
                                 " de " + mensaje.getAddressCliente() + ":" + 
                                 mensaje.getPuertoCliente());
            }
        } catch(Exception e) {
            System.err.println(e.getMessage());
        }
    }
    
private Mensaje procesarPaquete(DatagramPacket paquete) throws Exception {
    byte[] datos = paquete.getData();
    ByteArrayInputStream bais = new ByteArrayInputStream(datos);
    DataInputStream dis = new DataInputStream(bais);
    
    long checksumRecibido = dis.readLong();
    String mensajeStr = dis.readUTF(); // Usar readUTF para consistencia
    
    CRC32 crc = new CRC32();
    crc.update(mensajeStr.getBytes("UTF-8"));
    long checksumCalculado = crc.getValue();
    
    Mensaje mensaje = new Mensaje();
    mensaje.setMensaje(mensajeStr);
    mensaje.setAddressCliente(paquete.getAddress());
    mensaje.setPuertoCliente(paquete.getPort());
    
    if(checksumRecibido != checksumCalculado) {
        mensaje.setMensaje("[ERROR EN DATOS] " + mensajeStr);
    }
    
    return mensaje;
}
    
    private long bytesToLong(byte[] bytes, int offset) {
        long value = 0;
        for(int i=0; i<8; i++) {
            value |= ((long) bytes[offset + i] & 0xff) << (8*i);
        }
        return value;
    }
}