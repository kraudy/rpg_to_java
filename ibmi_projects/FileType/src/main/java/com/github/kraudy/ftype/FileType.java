package com.github.kraudy.ftype;

import java.io.InputStream;
import java.util.Scanner;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

import java.sql.Connection;

import com.ibm.as400.access.AS400;
import com.ibm.as400.access.AS400JDBCDataSource;
import com.ibm.as400.access.User;
import com.ibm.as400.access.IFSFile;
import com.ibm.as400.access.IFSFileInputStream;

import org.apache.tika.Tika;

import io.github.theprez.dotenv_ibmi.IBMiDotEnv;

public class FileType {
  public static final String INVARIANT_CCSID = "37"; // EBCDIC
  private final AS400 system;
  private final Connection connection;
  private final User currentUser;

  public FileType(AS400 system) throws Exception {
    this(system, new AS400JDBCDataSource(system).getConnection());
  }

  public FileType(AS400 system, Connection connection) throws Exception {
    this.system = system;

    // Database
    this.connection = connection;
    this.connection.setAutoCommit(true);

    // User
    this.currentUser = new User(system, system.getUserId());
    this.currentUser.loadUserInformation();

  }

  public void run() throws Exception{
    Scanner scanner = new Scanner(System.in);

    System.out.print("Enter IFS directory path (e.g., /home/user/Dir): ");
    String path = scanner.nextLine().trim();

    if (path.isEmpty()) {
        System.out.println("No path entered. Exiting.");
        return;
    }

    IFSFile dir = new IFSFile(this.system, path);
    if (!dir.isDirectory()) {
      System.out.println("Path is not a directory!");
      return;
    }

    IFSFile[] files = dir.listFiles();  // This lists only direct children (non-recursive)

    Tika tika = new Tika();

    for (IFSFile file : files) {
      if (file.isFile()) {  // Skip directories
        String fileName = file.getName();
        System.out.println("Processing: " + fileName);

        // Get an InputStream to the IFS file
        try (IFSFileInputStream is = new IFSFileInputStream(file)) {
          // Detect MIME type (e.g., "text/yaml", "application/json", "text/plain")
          String mimeType = tika.detect(is, fileName);
          System.out.println(fileName + " -> " + mimeType);
        }
      }
    }
  }

  public static void main( String[] args ){
    AS400 system = null;
    FileType ftype = null;
    try {
      system = IBMiDotEnv.getNewSystemConnection(true); // Get system
      ftype = new FileType(system);
      ftype.run();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}
