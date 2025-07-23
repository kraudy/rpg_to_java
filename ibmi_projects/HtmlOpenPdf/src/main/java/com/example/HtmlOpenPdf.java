package com.example;

import com.ibm.as400.access.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.xhtmlrenderer.pdf.ITextRenderer;

import java.io.FileOutputStream;
import java.io.IOException;

public class HtmlOpenPdf {
  public static void main( String... args ){
    if (args.length < 1) {
      System.out.println("Provide the input JSON file path as argument");
      return;
    }
    String inputFilePath = args[0];

    // TODO: Validate if is needed by various methods.
    AS400 sys = new AS400();

    try {
      IFSFile file = ValidateFile(sys, inputFilePath);

      JsonNode rootNode = ParseJson(file);

      String htmlContent = GenerateHtml(rootNode);

      createPdfFromHtml(htmlContent);
      System.out.println("PDF created: employee-tables.pdf");

    } catch (IOException | AS400SecurityException e) {
      System.err.println(e.getMessage());
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

  private static String GenerateHtml(JsonNode rootNode){
   StringBuilder html = new StringBuilder();
        html.append("<html><head>")
            .append("<style>")
            .append("@page { size: A4; margin: 2cm; }")
            .append("body { font-family: Arial, sans-serif; font-size: 10pt; color: #333; }")
            .append("h2 { color: navy; border-bottom: 1px solid #ccc; padding-bottom: 5px; }")
            .append("table { width: 100%; border-collapse: collapse; margin: 20px 0; }")
            .append("th, td { border: 1px solid #aaa; padding: 8px; text-align: left; }")
            .append("th { background-color: #f0f0f0; font-weight: bold; }")
            .append("footer { font-size: 8pt; text-align: center; margin-top: 50px; color: #777; }")
            .append("</style>")
            .append("</head><body>");
    
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

      html.append("<h2>Notifications for client: ").append(firstName).append(" ").append(lastName).append("</h2>")
              .append("<table>")
              .append("<thead><tr>")
              .append("<th>Trans. Code</th><th>Business Name</th><th>Amount</th>")
              .append("</tr></thead><tbody>");

      System.out.println("Created table for " + firstName + " " + lastName);

      for (JsonNode transaction : transactions) {
        if (!transaction.has("transactionCode") || !transaction.has("businessName") || !transaction.has("amount")) {
          System.out.println("Missing transaction data");
          continue;
        }
        html.append("<tr>")
              .append("<td>").append(transaction.get("transactionCode").asText()).append("</td>")
              .append("<td>").append(transaction.get("businessName").asText()).append("</td>")
              .append("<td>$").append(transaction.get("amount").asText()).append("</td>")
              .append("</tr>");

        System.out.println("Added transacction : " +  transaction.get("amount").asText());
      }
      // Close table
      html.append("</tbody></table>");
      // Log on succes bit
    }

    html.append("<footer>Page rendered with ♥ by OpenPDF-html.</footer>")
            .append("</body></html>");
    return html.toString();
  }

  
}
