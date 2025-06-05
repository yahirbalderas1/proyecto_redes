package servidor.principal;

import servidor.tcp.ServidorTCP;
import servidor.udp.ServidorEscuchaUDP;

public class ServidorPrincipal {
    public static void main(String[] args) {
        try {
            // Iniciar servidor TCP (puerto 12345)
            ServidorTCP servidorTCP = new ServidorTCP(12345);
            servidorTCP.inicia();
            
            // Iniciar servidor UDP (puerto 54321)
            ServidorEscuchaUDP servidorUDP = new ServidorEscuchaUDP(54321);
            servidorUDP.start();
            
            System.out.println("Servidores iniciados correctamente");
        } catch(Exception e) {
            System.err.println("Error al iniciar servidores: " + e.getMessage());
        }
    }
}