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
import java.util.EnumMap;
import java.util.EnumSet;
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

@Command
(name = "compiler", description = "OPM/ILE Object Compiler")
public class ObjectCompiler implements Runnable{
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

  enum ReqCmd { PGM, OBJ, OBJTYPE, OUTPUT, OUTMBR, MODULE, BNDSRVPGM, LIBL, SRCFILE } 

  enum OptCmd { ACTGRP, DFTACTGRP, BNDDIR, COMMIT, OBJTYPE, TEXT, TGTCCSID, CRTFRMSTMF } 

  enum ValCmd { FIRST, REPLACE, OUTFILE, LIBL, FILE, DTAARA, PGM, SRVPGM } // add * to these 

  enum PostCmpCmd { CHGOBJD } 

  @Option(names = "--obj", description = "Object name", converter = objectNameConverter.class)
  private String objectName;

  @Option(names = "--type", description = "Object type (e.g., PGM, SRVPGM)", converter = ObjectTypeConverter.class)
  private ObjectType objectType;

  @Option(names = "--source-type", description = "Source type (e.g., RPGLE, CLLE)", converter = SourceTypeConverter.class)
  private SourceType sourceType;

  private static final Map<SourceType, Map<ObjectType, ComCmd>> typeToCmdMap = new EnumMap<>(SourceType.class);  
  private static final Map<ComCmd, Set<ReqCmd>> requiredParamsMap = new EnumMap<>(ComCmd.class);  
  private static final Map<ComCmd, Set<OptCmd>> optionalParamsMap = new EnumMap<>(ComCmd.class);

  static {
    /*
     * Populate mapping from (SourceType, ObjectType) to ComCmd
     */
    // TODO: There has to be a cleaner way of doing this, maybe using :: or lambda to auto define them
    // Map RPG commands and add bind them to the type
    Map<ObjectType, ComCmd> rpgMap = new EnumMap<>(ObjectType.class);
    rpgMap.put(ObjectType.PGM, ComCmd.CRTRPGPGM);
    typeToCmdMap.put(SourceType.RPG, rpgMap);

    Map<ObjectType, ComCmd> rpgLeMap = new EnumMap<>(ObjectType.class);
    rpgLeMap.put(ObjectType.MODULE, ComCmd.CRTRPGMOD);
    rpgLeMap.put(ObjectType.PGM, ComCmd.CRTBNDRPG);
    rpgLeMap.put(ObjectType.SRVPGM, ComCmd.CRTSRVPGM); // Assuming compilation involves module creation first, but mapping to final command
    typeToCmdMap.put(SourceType.RPGLE, rpgLeMap);

    Map<ObjectType, ComCmd> sqlRpgLeMap = new EnumMap<>(ObjectType.class);
    sqlRpgLeMap.put(ObjectType.MODULE, ComCmd.CRTSQLRPGI);
    sqlRpgLeMap.put(ObjectType.PGM, ComCmd.CRTSQLRPGI);
    sqlRpgLeMap.put(ObjectType.SRVPGM, ComCmd.CRTSRVPGM);
    typeToCmdMap.put(SourceType.SQLRPGLE, sqlRpgLeMap);

    Map<ObjectType, ComCmd> clpMap = new EnumMap<>(ObjectType.class);
    clpMap.put(ObjectType.PGM, ComCmd.CRTCLPGM);
    typeToCmdMap.put(SourceType.CLP, clpMap);

    Map<ObjectType, ComCmd> clleMap = new EnumMap<>(ObjectType.class);
    clleMap.put(ObjectType.MODULE, ComCmd.CRTCLMOD);
    clleMap.put(ObjectType.PGM, ComCmd.CRTBNDCL);
    clleMap.put(ObjectType.SRVPGM, ComCmd.CRTSRVPGM);
    typeToCmdMap.put(SourceType.CLLE, clleMap);

    Map<ObjectType, ComCmd> sqlMap = new EnumMap<>(ObjectType.class);
    sqlMap.put(ObjectType.TABLE, ComCmd.RUNSQLSTM);
    sqlMap.put(ObjectType.LF, ComCmd.RUNSQLSTM);
    sqlMap.put(ObjectType.VIEW, ComCmd.RUNSQLSTM);
    sqlMap.put(ObjectType.ALIAS, ComCmd.RUNSQLSTM);
    sqlMap.put(ObjectType.PROCEDURE, ComCmd.RUNSQLSTM);
    sqlMap.put(ObjectType.FUNCTION, ComCmd.RUNSQLSTM);
    typeToCmdMap.put(SourceType.SQL, sqlMap);

    // Populate required params for each ComCmd
    requiredParamsMap.put(ComCmd.CRTRPGMOD, EnumSet.of(ReqCmd.MODULE));
    requiredParamsMap.put(ComCmd.CRTSQLRPGI, EnumSet.of(ReqCmd.OBJ, ReqCmd.OBJTYPE));
    requiredParamsMap.put(ComCmd.CRTBNDRPG, EnumSet.of(ReqCmd.PGM));
    requiredParamsMap.put(ComCmd.CRTRPGPGM, EnumSet.of(ReqCmd.PGM));
    requiredParamsMap.put(ComCmd.CRTCLMOD, EnumSet.of(ReqCmd.MODULE));
    requiredParamsMap.put(ComCmd.CRTBNDCL, EnumSet.of(ReqCmd.PGM));
    requiredParamsMap.put(ComCmd.CRTCLPGM, EnumSet.of(ReqCmd.PGM));
    requiredParamsMap.put(ComCmd.RUNSQLSTM, EnumSet.noneOf(ReqCmd.SRCFILE));
    requiredParamsMap.put(ComCmd.CRTSRVPGM, EnumSet.of(ReqCmd.OBJ, ReqCmd.MODULE, ReqCmd.BNDSRVPGM));
    requiredParamsMap.put(ComCmd.CRTDSPF, EnumSet.of(ReqCmd.OBJ));
    requiredParamsMap.put(ComCmd.CRTLF, EnumSet.of(ReqCmd.OBJ));
    requiredParamsMap.put(ComCmd.CRTPRTF, EnumSet.of(ReqCmd.OBJ));
    requiredParamsMap.put(ComCmd.CRTMNU, EnumSet.of(ReqCmd.OBJ));
    requiredParamsMap.put(ComCmd.CRTQMQRY, EnumSet.of(ReqCmd.OBJ));

    // Populate optional params for each ComCmd
    optionalParamsMap.put(ComCmd.CRTRPGMOD, EnumSet.of(OptCmd.TEXT, OptCmd.TGTCCSID, OptCmd.BNDDIR, OptCmd.DFTACTGRP));
    optionalParamsMap.put(ComCmd.CRTSQLRPGI, EnumSet.of(OptCmd.COMMIT, OptCmd.OBJTYPE, OptCmd.TEXT, OptCmd.ACTGRP, OptCmd.BNDDIR));
    optionalParamsMap.put(ComCmd.CRTBNDRPG, EnumSet.of(OptCmd.ACTGRP, OptCmd.DFTACTGRP, OptCmd.BNDDIR, OptCmd.TEXT));
    optionalParamsMap.put(ComCmd.CRTRPGPGM, EnumSet.of(OptCmd.TEXT, OptCmd.TGTCCSID));
    optionalParamsMap.put(ComCmd.CRTCLMOD, EnumSet.of(OptCmd.TEXT, OptCmd.TGTCCSID, OptCmd.ACTGRP));
    optionalParamsMap.put(ComCmd.CRTBNDCL, EnumSet.of(OptCmd.ACTGRP, OptCmd.DFTACTGRP, OptCmd.BNDDIR, OptCmd.TEXT));
    optionalParamsMap.put(ComCmd.CRTCLPGM, EnumSet.of(OptCmd.TEXT, OptCmd.TGTCCSID));
    optionalParamsMap.put(ComCmd.RUNSQLSTM, EnumSet.of(OptCmd.COMMIT, OptCmd.TEXT));
    optionalParamsMap.put(ComCmd.CRTSRVPGM, EnumSet.of(OptCmd.ACTGRP, OptCmd.BNDDIR, OptCmd.TEXT, OptCmd.DFTACTGRP));
    optionalParamsMap.put(ComCmd.CRTDSPF, EnumSet.of(OptCmd.TEXT, OptCmd.TGTCCSID));
    optionalParamsMap.put(ComCmd.CRTLF, EnumSet.of(OptCmd.TEXT, OptCmd.TGTCCSID));
    optionalParamsMap.put(ComCmd.CRTPRTF, EnumSet.of(OptCmd.TEXT, OptCmd.TGTCCSID));
    optionalParamsMap.put(ComCmd.CRTMNU, EnumSet.of(OptCmd.TEXT));
    optionalParamsMap.put(ComCmd.CRTQMQRY, EnumSet.of(OptCmd.TEXT));  
  }

  static class ObjectNameConverter implements ITypeConverter<String> {
    @Override
    public String convert(String value) throws Exception {
      value = value.trim().toUpperCase();
      if (value.length() > 10 || value.isEmpty()) {
        throw new Exception("Invalid object name: must be 1-10 characters");
      }
      return value;
    }
  }  

  static class ObjectTypeConverter implements ITypeConverter<ObjectType> {
    @Override
    public ObjectType convert(String value) throws Exception {
      try {
        return ObjectType.valueOf(value.trim().toUpperCase());
      } catch (IllegalArgumentException e) {
        throw new Exception("Invalid object type: " + value);
      }
    }
  }  

  static class SourceTypeConverter implements ITypeConverter<SourceType> {
    @Override
    public SourceType convert(String value) throws Exception {
      try {
        return SourceType.valueOf(value.trim().toUpperCase());
      } catch (IllegalArgumentException e) {
        throw new Exception("Invalid source type: " + value);
      }
    }
  }

  @Override
  public void run() {
    // For now, demonstrate the mappings
    Map<ObjectType, ComCmd> objectMap = typeToCmdMap.get(sourceType);
    if (objectMap == null) {
      System.err.println("No mapping for source type: " + sourceType);
      return;
    }
    ComCmd comCmd = objectMap.get(objectType);
    if (comCmd == null) {
      System.err.println("No compilation command for source type " + sourceType + " and object type " + objectType);
      return;
    }
    Set<ReqCmd> reqParams = requiredParamsMap.getOrDefault(comCmd, EnumSet.noneOf(ReqCmd.class));
    Set<OptCmd> optParams = optionalParamsMap.getOrDefault(comCmd, EnumSet.noneOf(OptCmd.class));System.out.println("Compilation command: " + comCmd.name());
    System.out.println("Required parameters: " + reqParams.stream().map(Enum::name).collect(Collectors.joining(", ")));
    System.out.println("Optional parameters: " + optParams.stream().map(Enum::name).collect(Collectors.joining(", ")));
    // Later: build command string, execute via CALL QCMD, etc.  
  }

  static void main( String... args ){
    CommandLine(new ObjectCompiler()).execute(args);
  }
}
