package com.example;

import com.ibm.as400.access.*; 

import java.util.Date;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

import java.io.FileOutputStream;
import java.io.IOException;

public class CreateOpenPDF {
  public static void main( String... args ){
    AS400 sys = new AS400();
    IFSFile file = new IFSFile(sys, "/home/ROBKRAUDY/notif2.json");

    // Prepare document
    Font font8 = FontFactory.getFont(FontFactory.HELVETICA, 8);
    Document document = new Document(PageSize.A4);

    try {
      if(!file.exists()){
      System.out.println("File does not exists: " + file.getPath());
      return;
      }
  
      if(!file.canRead()){
        System.out.println("Can't read from file: " + file.getPath());
      }
      // Initialize ObjectMapper for JSON parsing
      IFSFileInputStream fis = new IFSFileInputStream(file);
      ObjectMapper mapper = new ObjectMapper();
      JsonNode rootNode = mapper.readTree(fis);

      // step 2
      PdfWriter writer = PdfWriter.getInstance(document,
              new FileOutputStream("tables.pdf"));
      float width = document.getPageSize().getWidth();
      float height = document.getPageSize().getHeight();
      // step 3
      document.open();

      // step 4
      float[] columnDefinitionSize = {33.33F, 33.33F, 33.33F};

      float pos = height - 50; // Starting position for tables
      PdfPTable table = null;
      PdfPCell cell = null;

      // Access the "employees" array
      //JsonNode employees = rootNode.get("employees");
      if (!rootNode.isArray()){
        System.out.println("Root node is not an array");
        return;
      }
      for (JsonNode node: rootNode){
        JsonNode employee     = node.get("employee");
        JsonNode transactions = node.get("transactions");

        if (employee == null){
          System.out.println("No employee data");
          // Log on bit error
          continue;
        }

        if (transactions == null || !transactions.isArray()){
          System.out.println("No transaction data");
          // Log on bit error
          continue;
        }

        String firstName  = employee.get("firstName").asText();
        String lastName   = employee.get("lastName").asText();

        table = new PdfPTable(columnDefinitionSize);
        table.getDefaultCell().setBorder(0);
        table.setHorizontalAlignment(0);
        table.setTotalWidth(width - 72);
        table.setLockedWidth(true);

        // Add header
        cell = new PdfPCell(new Phrase("Notifications for client: " + firstName + " " + lastName));
        cell.setColspan(columnDefinitionSize.length);
        table.addCell(cell);

        // Add column headers for transactions
        table.addCell(new Phrase("Trans. Code", font8));
        table.addCell(new Phrase("Business Name", font8));
        table.addCell(new Phrase("Amount", font8));

        System.out.println("Created table for " + firstName + " " + lastName);

        for (JsonNode transaction : transactions) {
          table.addCell(new Phrase(transaction.get("transactionCode").asText(), font8));
          table.addCell(new Phrase(transaction.get("businessName").asText(), font8));
          table.addCell(new Phrase("$" + transaction.get("amount").asText(), font8));

          System.out.println("Added transacction : " +  transaction.get("amount").asText());
        }

        // Add table to document
        table.writeSelectedRows(0, -1, 36, pos, writer.getDirectContent());
        pos -= (table.getTotalHeight() + 20); // Adjust position for next table

        // Log on succes bit

        document.add(table);

      }

      // Add created table
      //document.add(table);

    } catch (DocumentException | IOException | AS400SecurityException de) {
      System.err.println(de.getMessage());
    } finally {
      // Step 5: Close document
      document.close();
    }
  }
}
