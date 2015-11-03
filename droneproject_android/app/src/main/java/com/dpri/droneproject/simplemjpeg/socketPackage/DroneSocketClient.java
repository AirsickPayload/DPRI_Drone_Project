package com.dpri.droneproject.simplemjpeg.socketPackage;

import android.util.Log;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;

/**
 * Created by alan on 03.10.15.
 */
public class DroneSocketClient {
    private static final String TAG = "DroneSocketClient";
    private static final boolean DEBUG = true;

    private String versionSocketClient = "v28/10/2015";
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

    public DroneSocketClient(String serverAddress,int clientPort, int serverPort, int pingPort) {
        this.serverAddress = serverAddress;
        this.clientPort = clientPort;
        this.pingPort = pingPort;
        this.serverPort = serverPort;
        try {
            srvAddr = InetAddress.getByName(serverAddress);
        } catch (UnknownHostException e) {
            e.printStackTrace();
            if (DEBUG) Log.d(TAG, "ERROR: UNKNOWN HOST!");
        }
        try {
            clientSocket = new DatagramSocket(clientPort);
            clientSocket.setSoTimeout(packetTIMEOUT);
        } catch (SocketException e) {
            e.printStackTrace();
            if (DEBUG) Log.d(TAG, "SOCKET BIND ERROR!");
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
            e.printStackTrace();
            if (DEBUG) Log.d(TAG, "Error sending values to server!");
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
            e.printStackTrace();
            if (DEBUG) Log.d(TAG, "Error during exchange of compability strings");
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
            e.printStackTrace();
            if (DEBUG) Log.d(TAG, "PING-SOCKET BIND ERROR!");
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
                pingAliveSocket.close();
                return false;
            }
        } catch(Exception e){
            e.printStackTrace();
            pingAliveSocket.close();
            if (DEBUG) Log.d(TAG, "PING SOCKET ERROR / MAYBE IT'S ALREADY CLOSED?");
            return false;
        }
    }

    public void closeConnection(){
        try {
            outData = "CONN_CLOSE".getBytes("UTF-8");
            outPacket = new DatagramPacket(outData, outData.length, srvAddr, serverPort);
            clientSocket.send(outPacket);
            pingAliveSocket.close();
            clientSocket.close();
        } catch (Exception e) {
            e.printStackTrace();
            if (DEBUG) Log.d(TAG, "Closure of connection failed!");
        }
    }

    public void closeSocket(){
        clientSocket.close();
    }
}
