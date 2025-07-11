package com.example;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Properties;

/*
 * 
 *  This thing doesn't works!
 * 
 */
public class AS400JDBCExample {
    public static void main(String[] args) {
        String url = "jdbc:as400://pub400.com";
        
        Properties props = new Properties();
        props.put("user", "user");
        props.put("password", "password");
        props.put("prompt", "true"); // login form if needed
        // add more properties here

        try (Connection conn = DriverManager.getConnection(url, props)) {
            System.out.println("Connected to IBM i database successfully!");

            String sql = "SELECT SERVICE_NAME, EXAMPLE FROM QSYS2.SERVICES_INFO where SERVICE_CATEGORY = 'PRODUCT'";
            try (PreparedStatement stmt = conn.prepareStatement(sql);
                 ResultSet rs = stmt.executeQuery()) {

                while (rs.next()) {
                    System.out.println("Service: " + rs.getString("SERVICE_NAME"));
                    System.out.println("Example SQL: ");
                    System.out.println(rs.getString("EXAMPLE"));
                    System.out.println();
                    System.out.println();
                }
            }

            System.out.println("Connection closed.");
        } catch (SQLException e) {
            System.err.println("SQL Exception: " + e.getMessage());
        }
    }
}