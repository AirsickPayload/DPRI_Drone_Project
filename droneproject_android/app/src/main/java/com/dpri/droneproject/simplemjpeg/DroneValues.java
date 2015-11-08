package com.dpri.droneproject.simplemjpeg;

/**
 * Created by alan on 26.09.15.
 */
public class DroneValues {

    private float throttle ,yaw, pitch, roll;
    private int throttlePin, yawPin, pitchPin, rollPin;

    public DroneValues(float throttle, float pitch, float yaw, float roll) {
        this.throttle = throttle;
        this.pitch = pitch;
        this.yaw = yaw;
        this.roll = roll;
    }

    public DroneValues() {
        this.throttle = 0;
        this.yaw = 50;
        this.pitch = 50;
        this.roll = 50;
    }

    public void setInitializationValues(){
        this.throttle = 0;
        this.yaw = 100;
        this.pitch = 50;
        this.roll = 50;
    }

    public String getValuesSocketString(){
        // Tworzenie stringu postaci '1=0%,1=15%,2=55%,6=90%'
        StringBuilder sb = new StringBuilder();
        sb.append(getThrottlePin() + "=" + getThrottle() + "%,");
        sb.append(getYawPin() + "=" + getYaw() + "%,");
        sb.append(getPitchPin() + "=" + getPitch() + "%,");
        sb.append(getRollPin() + "=" + getRoll() + "%");
        return sb.toString();
    }

    public float getThrottle() {
        return throttle;
    }

    public void setThrottle(float throttle) {
        this.throttle = getCalibratedThrottleValue(throttle);
    }

    public float getYaw() {
        return yaw;
    }

    public void setYaw(float yaw) {
        this.yaw = getCalibratedHorizontalGenericValue(yaw);
    }

    public float getPitch() {
        return pitch;
    }

    public void setPitch(float pitch) {
        this.pitch = getCalibratedVerticalGenericValue(pitch);
    }

    public float getRoll() {
        return roll;
    }

    public void setRoll(float roll) {
        this.roll = getCalibratedHorizontalGenericValue(roll);
    }

    public int getThrottlePin() { return throttlePin; }

    public void setThrottlePin(int throttlePin) { this.throttlePin = throttlePin; }

    public int getYawPin() { return yawPin; }

    public void setYawPin(int yawPin) { this.yawPin = yawPin; }

    public int getPitchPin() { return pitchPin; }

    public void setPitchPin(int pitchPin) { this.pitchPin = pitchPin; }

    public int getRollPin() { return rollPin; }

    public void setRollPin(int rollPin) { this.rollPin = rollPin; }

    public float getCalibratedThrottleValue(float throttle){
        if(throttle >= 0){
            return 0;
        } else if(throttle <= -70){
            return 70;
        } else {
            return Math.abs(throttle);
        }
    }

    public float getCalibratedVerticalGenericValue(float value){
        if(value == 0){
            return 50;
        } else if(value > 0){
            return Math.abs(50+(value/2));
        } else if(value < 0){
            return Math.abs(50-(-value/2));
        }else{
            return 50;
        }
    }

    public float getCalibratedHorizontalGenericValue(float value){
        if(value == 0){
            return 50;
        } else if(value > 0){
            return Math.abs(50+(value/2));
        } else if(value < 0){
            return Math.abs(50-(-value/2));
        }else{
            return 50;
        }
    }
}
