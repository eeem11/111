package com.example.baidu_map;

public class EnvData {
    private int id;
    private double temperature;
    private String createdAt;
    private double humidity;
    private double latitude;
    private double longitude;
    private double CO2;
    private double methanal;
    private double PM25;
    private double TVOC;
    private double PM10;
    private double SO2;
    private double NO2;
    private double O3;
    private double CO;

    // Getters and Setters
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    public double getCO2() { return CO2; }
    public void setCO2(double CO2) { this.CO2 = CO2; }
    public double getPM25() { return PM25; }
    public void setPM25(double PM25) { this.PM25 = PM25; }
    public double getPM10() { return PM10; }
    public void setPM10(double PM10) { this.PM10 = PM10; }

    public double getSO2() { return SO2; }
    public void setSO2(double SO2) { this.SO2 = SO2; }

    public double getNO2() { return NO2; }
    public void setNO2(double NO2) { this.NO2 = NO2; }

    public double getO3() { return O3; }
    public void setO3(double O3) { this.O3 = O3; }

    public double getCO() { return CO; }
    public void setCO(double CO) { this.CO = CO; }

    public double getLatitude() { return latitude; }
    public void setLatitude(double latitude) { this.latitude = latitude; }

    public double getLongitude() { return longitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }

}
