package com.example.baidu_map;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class MySqlHelp {
    private static final String CLS = "com.mysql.jdbc.Driver";
    private static final String URL = "jdbc:mysql://113.44.137.131:3306/demo";
    private static final String USER = "root";
    private static final String PWD = "Lww827717@";

    public static List<EnvData> getEnvironmentalData(String startTime, String endTime) {
        List<EnvData> dataList = new ArrayList<>();

        try {
            Class.forName(CLS);
            Connection conn = DriverManager.getConnection(URL, USER, PWD);
            String sql = "SELECT id, recorded_time, PM2.5, PM10, SO2, NO2, O3 " +
                    "FROM environmental_data " +
                    "WHERE recorded_time BETWEEN ? AND ? " +
                    "ORDER BY id";

            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.setString(1, startTime);
            stmt.setString(2, endTime);

            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                EnvData data = new EnvData();
                data.setId(rs.getInt("id"));
                data.setCreatedAt(rs.getString("recorded_time"));
                data.setCO2(rs.getDouble("CO2"));
                data.setPM25(rs.getDouble("PM2.5"));
                data.setPM10(rs.getDouble("PM10"));
                dataList.add(data);
            }

            rs.close();
            stmt.close();
            conn.close();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return dataList;
    }
}