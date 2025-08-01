package com.example;

import com.ibm.as400.access.*; 

import java.util.Date;
import java.util.Properties;

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

import jakarta.mail.*;
import jakarta.mail.internet.*;
import jakarta.activation.DataHandler;
import jakarta.activation.DataSource;
import jakarta.mail.util.ByteArrayDataSource;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.FileNotFoundException;

public class SendPdfMail {
  private static final Font FONT_8 = FontFactory.getFont(FontFactory.HELVETICA, 8);
  private static final float[] COLUMN_WIDTHS = {33.33F, 33.33F, 33.33F};

  private static final String SMTP_HOST = "smtp.gmail.com";
  private static final String SMTP_PORT = "587";
  private static final String SMTP_USERNAME = "your-email@gmail.com";
  private static final String SMTP_PASSWORD = "your-app-password";
  private static final String FROM_EMAIL = "your-email@gmail.com";
  private static final String TO_EMAIL = "recipient@yourdomain.com";
    
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
    ByteArrayOutputStream baos = new ByteArrayOutputStream();


    try {
      IFSFile file = ValidateFile(sys, inputFilePath);

      JsonNode rootNode = ParseJson(file);

      PdfWriter writer = CreatePdfWriter(document, baos);

      ProcessEmployees(rootNode, document, writer);

      // Send email with PDF attachment
      byte[] pdfBytes = baos.toByteArray();

      sendEmailWithAttachment(pdfBytes, "tables.pdf");

      System.out.println(pdfBytes);

    } catch (DocumentException | IOException | AS400SecurityException | MessagingException e) {
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

  private static PdfWriter CreatePdfWriter(Document document, ByteArrayOutputStream baos) throws DocumentException, IOException{
    PdfWriter writer = PdfWriter.getInstance(document, baos);
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

  private static void sendEmailWithAttachment(byte[] pdfBytes, String fileName) 
    throws MessagingException, FileNotFoundException,  IOException {
    // Set up mail server properties
    Properties props = new Properties();
    props.put("mail.smtp.auth", "true");
    props.put("mail.smtp.starttls.enable", "true");
    props.put("mail.smtp.host", SMTP_HOST);
    props.put("mail.smtp.port", SMTP_PORT);

    // Create a session with authentication
    Session session = Session.getInstance(props, new Authenticator() {
        @Override
        protected PasswordAuthentication getPasswordAuthentication() {
            return new PasswordAuthentication(SMTP_USERNAME, SMTP_PASSWORD);
        }
    });

    // Create a new email message
    Message message = new MimeMessage(session);
    message.setFrom(new InternetAddress(FROM_EMAIL));
    message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(TO_EMAIL));
    message.setSubject("Employee Transaction Report - " + new Date());
    message.setSentDate(new Date());

    // Create the message part
    BodyPart messageBodyPart = new MimeBodyPart();
    messageBodyPart.setText("Please find attached the employee transaction report.");

    // Create the attachment part
    MimeBodyPart attachmentPart = new MimeBodyPart();
    DataSource source = new ByteArrayDataSource(pdfBytes, "application/pdf");
    attachmentPart.setDataHandler(new DataHandler(source));
    attachmentPart.setFileName(fileName);

    // Create a multipart message
    Multipart multipart = new MimeMultipart();
    multipart.addBodyPart(messageBodyPart);
    multipart.addBodyPart(attachmentPart);

    // Set the multipart message to the email
    message.setContent(multipart);

    // Send the email
    //Transport.send(message);
    //x`System.out.println("Email sent successfully with PDF attachment");

    // Save email to file instead of sending for now
    //TODO: Fix this.
    String emlFilePath = "email.eml"; // Specify IFS path
    try (FileOutputStream fos = new FileOutputStream(emlFilePath)) {
      message.writeTo(fos);
      System.out.println("Email saved to " + emlFilePath);
    }
    }

}
