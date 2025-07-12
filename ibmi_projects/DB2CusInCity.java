import java.sql.*;
import com.ibm.db2.app.*;

public class DB2CusInCity extends StoredProc {
  public void DB2CusInCity (String s, int i) throws Exception {
    Connection con = getConnection(); 
    PreparedStatement ps = null;
    ResultSet rs = null;
    String sql;
    sql = "SELECT Count(*) from robkraudy2.parts WHERE (PARTQTY = ?)";
    ps = con.prepareStatement( sql );
    ps.setString( 1, s );
    rs = ps.executeQuery();
    rs.next();
    set(2, rs.getInt(1)); 
    if (rs != null) rs.close();
    if (ps != null) ps.close();
    if (con != null) con.close();
  }
}
