package com.dpri.droneproject.simplemjpeg.socketPackage;

import android.util.Log;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

/**
 * Created by alan on 03.10.15.
 */
public class DroneSocketClient {
    private static final String TAG = "DroneSocketClient";
    private static final boolean DEBUG = true;

    private String versionSocketClient = "v21/01/2016";
    private String serverAddress;
    private int serverPort;
    private int pingPort;
    private int clientPort;
    private InetAddress srvAddr;

    private static final int packetTIMEOUT = 3000; //ms

    private DatagramSocket pingAliveSocket;
    private DatagramSocket clientSocket;
    private DatagramPacket inPacket, pingInPacket;
    private DatagramPacket outPacket, pingOutPacket;

    byte[] inData;
    byte[] outData;
    byte[] pingData;

    public DroneSocketClient(String serverAddress,int clientPort, int serverPort, int pingPort) throws DroneSocketClientException {
        this.serverAddress = serverAddress;
        this.clientPort = clientPort;
        this.pingPort = pingPort;
        this.serverPort = serverPort;
        try {
            srvAddr = InetAddress.getByName(serverAddress);
        } catch (UnknownHostException e) {
            if (DEBUG){
                e.printStackTrace();
                Log.d(TAG, "ERROR: UNKNOWN HOST!");
                throw new DroneSocketClientException("");
            }
        }
        try {
            clientSocket = new DatagramSocket(clientPort);
            clientSocket.setSoTimeout(packetTIMEOUT);
        } catch (SocketException e) {
            if (DEBUG){
                e.printStackTrace();
                Log.d(TAG, "CLIENTSOCKET BIND ERROR!");
                throw new DroneSocketClientException("");
            }
        }
        inData = new byte[1024];
        outData = new byte[1024];
        outPacket = new DatagramPacket(outData, outData.length, srvAddr, serverPort);
        inPacket = new DatagramPacket(inData, inData.length);
    }

    public boolean sendValues(String valuesString){
        try {
            outData = valuesString.getBytes("UTF-8");
            outPacket = new DatagramPacket(outData, outData.length, srvAddr, serverPort);

            if(DEBUG) Log.d("Czas_klient: ", Long.toString(System.currentTimeMillis()));
            clientSocket.send(outPacket);

            clientSocket.receive(inPacket);
            String response = new String(inPacket.getData(), 0, inPacket.getLength(), "UTF-8");
            if(response.equals("RECV_OK")){
                if (DEBUG) Log.d(TAG, "SENT: " + valuesString);
                return true;
            }
            else{
                if (DEBUG) Log.d(TAG, "Unexptected server values confimation!");
                return false;
            }
        } catch (Exception e) {
            if (DEBUG){
                e.printStackTrace();
                Log.d(TAG, "Error sending values to server!");
            }
            return false;
        }
    }

    public boolean confirmVersionCompability(){
        try {
            outData = versionSocketClient.getBytes("UTF-8");
            outPacket = new DatagramPacket(outData, outData.length, srvAddr, serverPort);
            clientSocket.send(outPacket);
            if (DEBUG) Log.d(TAG, "Sent compability string");

            clientSocket.receive(inPacket);
            String response = new String(inPacket.getData(), 0, inPacket.getLength(), "UTF-8");
            if(response.equals("VERSION MATCH")){
                if (DEBUG) Log.d(TAG, "VERSION MATCH");
                return initPingListening();
            } else{
                if (DEBUG) Log.d(TAG, "VERSION MISMATCH!:" + response);
                return false;
            }
        } catch (Exception e) {
            if (DEBUG){
                e.printStackTrace();
                Log.d(TAG, "Error during exchange of compability strings");
            }
            return false;
        }
    }

    private boolean initPingListening(){
        try{
            pingAliveSocket = new DatagramSocket(pingPort);
            pingData = new byte[1024];
            pingOutPacket = new DatagramPacket(pingData, pingData.length, srvAddr, pingPort);
            pingInPacket = new DatagramPacket(pingData, pingData.length);
            return true;
        } catch (SocketException e){
            if (DEBUG) {
                e.printStackTrace();
                Log.d(TAG, "PING-SOCKET BIND ERROR!");
            }
            return false;
        }
    }

    public boolean pingAwaitAndReply(){
        try{
            pingAliveSocket.receive(pingInPacket);
            String response = new String(pingInPacket.getData(), 0, pingInPacket.getLength(), "UTF-8");
            if(response.equals("PING?")){
                if (DEBUG) Log.d(TAG, "PING PACKET RECEIVED");
                pingData = "PONG!".getBytes("UTF-8");
                pingOutPacket = new DatagramPacket(pingData, pingData.length, srvAddr, pingPort);
                pingAliveSocket.send(pingOutPacket);
                return true;
            } else{
                if (DEBUG) Log.d(TAG, "UNKNOWN PING MESSAGE:" + response);
                return false;
            }
        } catch(SocketTimeoutException e){
            if (DEBUG) Log.d(TAG, "PING SOCKET TIMEOUT!");
            return false;
        } catch(SocketException e){
            // Zamknięcie socketu podczas gdy pętla oczekuje na wiadomość PING - nieszkodzliwy wyjątek.
            return true;
        } catch(IOException e){
            if (DEBUG) Log.d(TAG, "PING SOCKET IOEXCEPTION!");
            return false;
        }
    }

    public void closeConnection(){
        try {
            if(pingAliveSocket != null){
                if(!pingAliveSocket.isClosed()) { pingAliveSocket.close(); }
            }
            if(clientSocket != null) {
                if (!clientSocket.isClosed()) {
                    outData = "CONN_CLOSE".getBytes("UTF-8");
                    outPacket = new DatagramPacket(outData, outData.length, srvAddr, serverPort);
                    clientSocket.send(outPacket);
                    clientSocket.close();
                }
            }
        } catch (Exception e) {
            if (DEBUG){
                e.printStackTrace();
                Log.d(TAG, "Closure of connection failed! // SERVER MIGHT BE ALREADY UNREACHABLE!");
            }
        }
    }

    public class DroneSocketClientException extends Exception {

        public DroneSocketClientException(String message) {
            super(message);
        }

        public DroneSocketClientException(String message, Throwable throwable) {
            super(message, throwable);
        }

    }
}
