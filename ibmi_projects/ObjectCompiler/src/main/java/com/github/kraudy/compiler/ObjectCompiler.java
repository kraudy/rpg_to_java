package com.github.kraudy.compiler;

import java.beans.PropertyVetoException;
import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.ibm.as400.access.AS400;
import com.ibm.as400.access.AS400SecurityException;
import com.ibm.as400.access.ErrorCompletingRequestException;
import com.ibm.as400.access.AS400JDBCDataSource;
import com.ibm.as400.access.CommandCall;
import com.ibm.as400.access.ErrorCompletingRequestException;
import com.ibm.as400.access.User;

import io.github.theprez.dotenv_ibmi.IBMiDotEnv;
import com.github.kraudy.Nodes;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Option;

public class ObjectCompiler {
  private static final String UTF8_CCSID = "1208"; // UTF-8 for stream files
  public static final String INVARIANT_CCSID = "37"; // EBCDIC
  private final AS400 system;
  private final Connection connection;
  private final User currentUser;

  @Option(names = { "-l", "--lib" }, required = true,  description = "Library to build)")
  private String libraryList = null;

  enum SysCmd { CHGLIBL, DSPPGMREF, DSPOBJD, DSPDBR } 

  enum SourceType { RPG, RPGLE, SQLRPGLE, CLP, CLLE, SQL}

  enum ObjectType { PGM, SRVPGM, MODULE, TABLE, LF, VIEW, ALIAS, PROCEDURE, FUNCTION } // Add more as needed
  
  enum ComCmd { CRTRPGMOD, CRTSQLRPGI, CRTBNDRPG, CRTRPGPGM, CRTCLMOD, CRTBNDCL, CRTCLPGM, RUNSQLSTM, CRTSRVPGM, CRTDSPF, CRTLF,
               CRTPRTF, CRTMNU, CRTQMQRY}

  enum ReqCmd { PGM, OBJ, OBJTYPE, OUTPUT, OUTMBR, MODULE, BNDSRVPGM, LIBL } 

  enum OptCmd { ACTGRP, DFTACTGRP, BNDDIR, COMMIT, OBJTYPE, TEXT, TGTCCSID, CRTFRMSTMF } 

  enum ValCmd { FIRST, REPLACE, OUTFILE, LIBL, FILE, DTAARA, PGM, SRVPGM } // add * to these 

  enum PostCmpCmd { CHGOBJD } 

  /*
   * Maybe i can add to these commands like CPYTOSTRMF or DSPPGMREF, DSPDBR, etc
   */

  @Option(names = "--obj", description = "Object name", converter = objectNameConverter.class)
  private String objectName;

  @Option(names = "--type", description = "Object type (e.g., PGM, SRVPGM)", converter = ObjectTypeConverter.class)
  private ObjectType objectType;

  // New mappings for SourceType -> ObjectType -> ComCmd
  private static final Map<SourceType, Map<ObjectType, ComCmd>> SOURCE_TO_OBJ_TO_CMD = new HashMap<>();

  // New mapping for ComCmd -> List of associated parameters (extend ReqCmd/OptCmd as needed)
  private static final Map<ComCmd, List<String>> CMD_TO_PARAMS = new HashMap<>();

  // New mapping for ComCmd -> Map of default parameter values
  private static final Map<ComCmd, Map<String, String>> CMD_TO_DEFAULTS = new HashMap<>();
  public
  
   static void main( String... args ){
    System.out.println( "Hello World!" );
  }
}
