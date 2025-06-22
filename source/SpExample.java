/*

*/
import java.sql.*;
import com.ibm.db2.app.*;

public class SpExample extends StoredProc {
  
  public void returnTwoResultSets() throws Exception {
    Connection con = getConnection();

    Statement stmt1 = con.createStatement();
    String sql1 = "select * from robkraudy2.parts";
    stmt1.execute(sql1);
    Statement stmt2 = con.createStatement();
    String sql2 = "SELECT PARTDES, PARTQTY, PARTPRC FROM robkraudy2.parts";
    stmt2.execute(sql2);
  }

}
