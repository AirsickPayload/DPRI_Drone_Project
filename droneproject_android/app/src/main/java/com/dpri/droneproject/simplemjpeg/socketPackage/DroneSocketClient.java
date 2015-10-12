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

    private String versionSocketClient = "v05/10/2015";
    private String serverAddress;
    private int serverPort;
    private InetAddress srvAddr;

    int packetTIMEOUT = 3000; //ms

    private DatagramSocket clientSocket;
    private DatagramPacket inPacket;
    private DatagramPacket outPacket;

    byte[] inData;
    byte[] outData;

    public DroneSocketClient(String serverAddress, int serverPort) {
        this.serverAddress = serverAddress;
        this.serverPort = serverPort;
        try {
            srvAddr = InetAddress.getByName(serverAddress);
        } catch (UnknownHostException e) {
            e.printStackTrace();
            if (DEBUG) Log.d(TAG, "ERROR: UNKNOWN HOST!");
        }
        try {
            clientSocket = new DatagramSocket(8888);
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
            String response = new String(inPacket.getData(),0 , inPacket.getLength(), "UTF-8");
            if(response.equals("RECV_OK")){
                if (DEBUG) Log.d(TAG, "SENT:" + valuesString);
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
                return true;
            } else{
                if (DEBUG) Log.d(TAG, "VERSION MISMATCH!:" + response);
                clientSocket.close();
                return false;
            }
        } catch (Exception e) {
            e.printStackTrace();
            if (DEBUG) Log.d(TAG, "Error during exchange of compability strings");
            return false;
        }
    }

    public void closeConnection(){
        try {
            outData = "CONN_CLOSE".getBytes("UTF-8");
            outPacket = new DatagramPacket(outData, outData.length, srvAddr, serverPort);
            clientSocket.send(outPacket);
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
