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
  private static final Font FONT_8 = FontFactory.getFont(FontFactory.HELVETICA, 8);
  private static final float[] COLUMN_WIDTHS = {33.33F, 33.33F, 33.33F};
  
  public static void main( String... args ){
    if (args.length < 1) {
      System.out.println("Provide the input JSON file path as argument");
      return;
    }
    String inputFilePath = args[0];

    // TODO: Validate if is needed by various methods.
    AS400 sys = new AS400();

    // Prepare document
    Document document = new Document(PageSize.A4);

    try {
      IFSFile file = ValidateFile(sys, inputFilePath);

      JsonNode rootNode = ParseJson(file);

      PdfWriter writer = CreatePdfWriter(document);

      ProcessEmployees(rootNode, document, writer);

    } catch (DocumentException | IOException | AS400SecurityException e) {
      System.err.println(e.getMessage());
    } finally {
      // Step 5: Close document
      document.close();
    }
  }

  private static IFSFile ValidateFile(AS400 sys, String filePath) throws AS400SecurityException, IOException{
    IFSFile file = new IFSFile(sys, filePath);

    if(!file.exists()){
      System.out.println("File does not exists: " + file.getPath());
      throw new IOException("File does not exist: " + file.getPath());
    }
    System.out.println("Found JSON file");

    if(!file.canRead()){
      System.out.println("Can't read from file: " + file.getPath());
      throw new IOException("Can't read from file:  " + file.getPath());
    }
    System.out.println("File can be read");

    return file;
  }

  private static JsonNode ParseJson(IFSFile JsonFile) throws AS400SecurityException, IOException, IllegalArgumentException{
    IFSFileInputStream fis = new IFSFileInputStream(JsonFile);
    ObjectMapper mapper = new ObjectMapper();

    JsonNode rootNode = mapper.readTree(fis);
    if (!rootNode.isArray()){
      System.out.println("Root node is not an array");
      throw new IllegalArgumentException("Invalid JSON structure");
    }
    
    return rootNode;
  }

  private static PdfWriter CreatePdfWriter(Document document) throws DocumentException, IOException{
    PdfWriter writer = PdfWriter.getInstance(document, new FileOutputStream("tables.pdf"));
    document.open();
    return writer;
  }

  private static void ProcessEmployees(JsonNode rootNode, Document document, PdfWriter writer)
      throws DocumentException{
    
    float width = document.getPageSize().getWidth();
    float height = document.getPageSize().getHeight();
    float pos = height - 50; // Starting position for tables
    
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

      PdfPTable table = CreateEmployeeTable(employee, transactions, width);

      pos = WriteTableToDocument(table, document, writer, pos, height);

      // Log on succes bit

    }
  }

  private static PdfPTable CreateEmployeeTable(JsonNode employee, JsonNode transactions, float pageWidth){
    String firstName  = employee.get("firstName").asText();
    String lastName   = employee.get("lastName").asText();

    PdfPTable table = new PdfPTable(COLUMN_WIDTHS);
    table.getDefaultCell().setBorder(0);
    table.setHorizontalAlignment(0);
    table.setTotalWidth(pageWidth - 72);
    table.setLockedWidth(true);

    // Add header
    PdfPCell cell = new PdfPCell(new Phrase("Notifications for client: " + firstName + " " + lastName));
    cell.setColspan(COLUMN_WIDTHS.length);
    table.addCell(cell);

    // Add column headers for transactions
    table.addCell(new Phrase("Trans. Code", FONT_8));
    table.addCell(new Phrase("Business Name", FONT_8));
    table.addCell(new Phrase("Amount", FONT_8));

    System.out.println("Created table for " + firstName + " " + lastName);

    for (JsonNode transaction : transactions) {
      if (!transaction.has("transactionCode") || !transaction.has("businessName") || !transaction.has("amount")) {
        System.out.println("Missing transaction data");
        continue;
      }
      table.addCell(new Phrase(transaction.get("transactionCode").asText(), FONT_8));
      table.addCell(new Phrase(transaction.get("businessName").asText(), FONT_8));
      table.addCell(new Phrase("$" + transaction.get("amount").asText(), FONT_8));

      System.out.println("Added transacction : " +  transaction.get("amount").asText());
    }

    return table;
  }

  private static float WriteTableToDocument(PdfPTable table, Document document, PdfWriter writer, 
            float currentPos, float pageHeight) throws DocumentException{

    if (currentPos < table.getTotalHeight() + document.bottomMargin()) {
      document.newPage();
      currentPos = pageHeight - 50;
    }
    // Add table to document
    table.writeSelectedRows(0, -1, 36, currentPos, writer.getDirectContent());
    // This lets you add the object on the next cursor position. Useful for not dealing with line numbers
    //document.add(table);

    return currentPos - (table.getTotalHeight() + 20); // Adjust position for next table

  }

}
