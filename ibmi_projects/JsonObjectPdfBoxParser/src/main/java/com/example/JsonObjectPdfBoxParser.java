package com.example;

import com.ibm.as400.access.*;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.Date;

import org.json.JSONArray;
import org.json.JSONObject;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;


public class JsonObjectPdfBoxParser {
  public static void main(String... args){
    AS400 sys = new AS400();
    //TODO: Add listener
    IFSFile file = new IFSFile(sys, "/home/ROBKRAUDY/notif.json");
    IFSFile logFile = new IFSFile(sys, "/home/ROBKRAUDY/log.txt"); // Log file path
    IFSFile pdfFile = new IFSFile(sys, "/home/ROBKRAUDY/employees.pdf"); // PDF file path. Could be done in memory.

    Connection conn = null;

    try {
      if(!file.exists()){
        System.out.println("File does not exists: " + file.getPath());
        return;
      }
  
      if(!file.canRead()){
        System.out.println("Can't read from file: " + file.getPath());
      }

      // Establish JDBC connection using AS400JDBCConnection
      AS400JDBCDataSource dataSource = new AS400JDBCDataSource(sys);
      conn = dataSource.getConnection();
      conn.setAutoCommit(true); // We don't want transaction control

      // Prepare SQL statement for inserting logs
      String sql = "INSERT INTO ROBKRAUDY2.NOTIF_LOG (LOG_TIMESTAMP, LOG_MESSAGE) VALUES (CURRENT_TIMESTAMP, ?)";
      PreparedStatement pstmt = conn.prepareStatement(sql);
      
      IFSFileInputStream fis = new IFSFileInputStream(file);
      BufferedReader reader = new BufferedReader(new InputStreamReader(fis));

      /* 
      So, we read the entire file into a StringBuilder, generate the json object
      and extract the json array
      */
      StringBuilder jsonContent = new StringBuilder();
      String line;
      while ((line = reader.readLine()) != null) {
          jsonContent.append(line);
      }
      reader.close();

      // Parse JSON as JSONObject
      JSONObject jsonObject = new JSONObject(jsonContent.toString());
      // Extract the "employees" array
      JSONArray jsonArray = jsonObject.getJSONArray("employees");

      // Open log file for writing (append mode)
      IFSFileOutputStream fos = new IFSFileOutputStream(logFile); // creates or replace
      PrintWriter logWriter = new PrintWriter(fos);
 
      // Create PDF document
      PDDocument document = new PDDocument();
      PDPage page = new PDPage();
      document.addPage(page);
      PDPageContentStream contentStream = new PDPageContentStream(document, page);

      // Set font and starting position
      contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 12);
      float yPosition = 700; // Start near the top of the page
      float margin = 50;
      float lineHeight = 20;

      // Write header
      contentStream.beginText();
      contentStream.newLineAtOffset(margin, yPosition);
      contentStream.showText("Employee Report");
      contentStream.endText();
      yPosition -= lineHeight * 2;

      // Write table headers
      contentStream.beginText();
      contentStream.newLineAtOffset(margin, yPosition);
      contentStream.showText("ID    First Name    Last Name    Department    Salary");
      contentStream.endText();
      yPosition -= lineHeight;

      /*
        This example only uses org.json which lets you parse the whole json file directly
        It is more straigthforward than jackson but you are loading the whole file into memory
        which is something that you may or may not want.
      */
      System.out.println("Parsed JSON Array: " + jsonArray.toString());

      for (Object obj : jsonArray) {
        JSONObject employee = (JSONObject) obj;
        System.out.println("Id: " + employee.getInt("id"));
        System.out.println("First name: " + employee.getString("firstName"));
        System.out.println("Last name: " + employee.getString("lastName"));
        System.out.println("Department: " + employee.getString("department"));
        System.out.println("Salary: " + employee.getInt("salary"));

        // Write to log file with timestamp
        String logEntry = new Date() + " - Processed: " + employee.getString("firstName") + " " + employee.getString("lastName");
        logWriter.println(logEntry);

        // Insert into database
        pstmt.setString(1, logEntry);
        pstmt.executeUpdate();

        // Write to PDF
        contentStream.beginText();
        contentStream.newLineAtOffset(margin, yPosition);
        contentStream.showText(String.format("%-6d%-14s%-13s%-14s%d", 
        employee.getInt("id"), employee.getString("firstName"), employee.getString("lastName"),  employee.getString("department"), employee.getInt("salary")));
        contentStream.endText();
        yPosition -= lineHeight;

        // Check for page overflow (simple approach)
        if (yPosition < margin) {
          contentStream.close();
          page = new PDPage();
          document.addPage(page);
          contentStream = new PDPageContentStream(document, page);
          contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 12);
          yPosition = 700;
          // Rewrite headers on new page
          contentStream.beginText();
          contentStream.newLineAtOffset(margin, yPosition);
          contentStream.showText("Employee Report");
          contentStream.endText();
          yPosition -= lineHeight * 2;
          contentStream.beginText();
          contentStream.newLineAtOffset(margin, yPosition);
          contentStream.showText("ID    First Name    Last Name    Department    Salary");
          contentStream.endText();
          yPosition -= lineHeight;
      }
      }
      // Close log file
      logWriter.close();
      fos.close();

      // Close PDF content stream
      contentStream.close();

      // Save PDF to IFS
      IFSFileOutputStream pdfFos = new IFSFileOutputStream(pdfFile);
      document.save(pdfFos);
      document.close();
      pdfFos.close();

      // Close connection
      pstmt.close();
      conn.close();

      // Additional file checks
      System.out.println("File CCSID: " + file.getCCSID());
      System.out.println("Free Space: " + (file.getFreeSpace(sys)/1024) + " MG");

    } catch (Exception e){
      e.printStackTrace();
    } finally {
      try {
        conn.close();
      } catch (Exception closeEx) {
        closeEx.printStackTrace();
      }
    }

    // Check if the file exists exists()
    // Get CCSID => getCCSID()
    // Returns storage space available to the user. => getFreeSpace(AS400 system)

  }
}
