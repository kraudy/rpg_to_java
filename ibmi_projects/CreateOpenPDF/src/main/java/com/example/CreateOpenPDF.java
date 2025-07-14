package com.example;

import com.ibm.as400.access.*; 

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfName;
import com.lowagie.text.pdf.PdfString;
import com.lowagie.text.pdf.PdfWriter;

import java.io.FileOutputStream;
import java.io.IOException;

public class CreateOpenPDF {
  public static void main( String... args ){
    System.out.println("Hello World");

    // step 1: creation of a document-object
    Document document = new Document();
    try {
      // step 2:
      // we create a writer that listens to the document
      // and directs a PDF-stream to a file
      final PdfWriter instance = PdfWriter.getInstance(document, new FileOutputStream("HelloWorld.pdf"));

      // step 3: we open the document
      document.open();
      instance.getInfo().put(PdfName.CREATOR, new PdfString(Document.getVersion()));
      // step 4: we add a paragraph to the document
      document.add(new Paragraph("Hello World"));
    } catch (DocumentException | IOException de) {
      System.err.println(de.getMessage());
    }

    // step 5: we close the document
    document.close();
  }
}
