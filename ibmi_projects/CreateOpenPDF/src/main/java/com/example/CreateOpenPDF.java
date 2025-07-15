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
    IFSFile file = new IFSFile(sys, "/home/ROBKRAUDY/notif.json");

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

      float pos = height / 2;
      PdfPTable table = null;
      PdfPCell cell = null;

      // Access the "employees" array
      JsonNode employees = rootNode.get("employees");
      if (employees != null && employees.isArray()) {
        table = new PdfPTable(columnDefinitionSize);
        table.getDefaultCell().setBorder(0);
        table.setHorizontalAlignment(0);
        table.setTotalWidth(width - 72);
        table.setLockedWidth(true);

        cell = new PdfPCell(new Phrase("Table added with document.add()"));
        cell.setColspan(columnDefinitionSize.length);
        table.addCell(cell);

        for (JsonNode employee : employees) {
            String firstName = employee.get("firstName").asText();
            String lastName = employee.get("lastName").asText();

            table.addCell(new Phrase(employee.get("id").asText(), font8));
            table.addCell(new Phrase(firstName + " " + lastName, font8));
            table.addCell(new Phrase(employee.get("salary").asText(), font8));
                   
            String logEntry = new Date() + " JsonParser - Processed: " + firstName + " " + lastName;
            System.out.println(logEntry);
        }
      } else {
        System.out.println("No 'employees' array found in JSON");
      }

      // Add created table
      document.add(table);

      // Second table

      table = new PdfPTable(columnDefinitionSize);
      table.getDefaultCell().setBorder(0);
      table.setHorizontalAlignment(0);
      table.setTotalWidth(width - 72);
      table.setLockedWidth(true);

      cell = new PdfPCell(new Phrase("Table added with writeSelectedRows"));
      cell.setColspan(columnDefinitionSize.length);
      table.addCell(cell);
      table.addCell(new Phrase("Louis Pasteur", font8));
      table.addCell(new Phrase("Albert Einstein", font8));
      table.addCell(new Phrase("Isaac Newton", font8));
      table.addCell(new Phrase("8, Rabic street", font8));
      table.addCell(new Phrase("2 Photons Avenue", font8));
      table.addCell(new Phrase("32 Gravitation Court", font8));
      table.addCell(new Phrase("39100 Dole France", font8));
      table.addCell(new Phrase("12345 Ulm Germany", font8));
      table.addCell(new Phrase("45789 Cambridge  England", font8));

      table.writeSelectedRows(0, -1, 50, pos, writer.getDirectContent());

    } catch (DocumentException | IOException | AS400SecurityException de) {
        System.err.println(de.getMessage());
    }
    // step 5
    document.close();
  }
}
