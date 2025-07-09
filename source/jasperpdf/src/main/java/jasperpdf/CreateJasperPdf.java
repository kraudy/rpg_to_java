package jasperpdf;

import java.util.HashMap;
import com.ibm.as400.access.AS400;
import com.ibm.as400.access.IFSFile;
import com.ibm.as400.access.IFSFileInputStream;
import com.ibm.as400.access.IFSFileOutputStream;
import net.sf.jasperreports.engine.*;
import net.sf.jasperreports.json.data.JsonDataSource;

public class CreateJasperPdf {
    public static void main(String[] args) {
        AS400 sys = new AS400();
        IFSFile jsonFile = new IFSFile(sys, "/home/ROBKRAUDY/notif.json");
        IFSFile pdfFile = new IFSFile(sys, "/home/ROBKRAUDY/employees.pdf");
        
        try {
            JasperReport report = JasperCompileManager.compileReport(
              CreateJasperPdf.class.getResourceAsStream("/employee_report.jrxml")
            );
            JsonDataSource dataSource = new JsonDataSource(new IFSFileInputStream(jsonFile), "employees");
            JasperPrint print = JasperFillManager.fillReport(report, new HashMap<>(), dataSource);
            JasperExportManager.exportReportToPdfStream(print, new IFSFileOutputStream(pdfFile));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}