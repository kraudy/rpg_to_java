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
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import com.ibm.as400.access.AS400;
import com.ibm.as400.access.AS400SecurityException;
import com.ibm.as400.access.ErrorCompletingRequestException;
import com.ibm.as400.access.AS400JDBCDataSource;
import com.ibm.as400.access.CommandCall;
import com.ibm.as400.access.ErrorCompletingRequestException;
import com.ibm.as400.access.User;

import io.github.theprez.dotenv_ibmi.IBMiDotEnv;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Option;

@Command (name = "compiler", description = "OPM/ILE Object Compiler", mixinStandardHelpOptions = true, version = "ObjectCompiler 0.0.1")
public class ObjectCompiler implements Runnable{
  private static final String UTF8_CCSID = "1208"; // UTF-8 for stream files
  public static final String INVARIANT_CCSID = "37"; // EBCDIC
  private final AS400 system;
  private final Connection connection;
  private final User currentUser;

  enum SysCmd { CHGLIBL, DSPPGMREF, DSPOBJD, DSPDBR } 

  enum SourceType { RPG, RPGLE, SQLRPGLE, CLP, CLLE, SQL}

  enum ObjectType { PGM, SRVPGM, MODULE, TABLE, LF, VIEW, ALIAS, PROCEDURE, FUNCTION } // Add more as needed
  
  enum CompCmd { CRTRPGMOD, CRTSQLRPGI, CRTBNDRPG, CRTRPGPGM, CRTCLMOD, CRTBNDCL, CRTCLPGM, RUNSQLSTM, CRTSRVPGM, CRTDSPF, CRTLF,
               CRTPRTF, CRTMNU, CRTQMQRY}

  enum ParamCmd { PGM, OBJ, OBJTYPE, OUTPUT, OUTMBR, MODULE, BNDSRVPGM, LIBL, SRCFILE, 
    ACTGRP, DFTACTGRP, BNDDIR, COMMIT, TEXT, TGTCCSID, CRTFRMSTMF } 

  enum ValCmd { FIRST, REPLACE, OUTFILE, LIBL, FILE, DTAARA, PGM, SRVPGM, CURLIB } // add * to these 

  enum PostCmpCmd { CHGOBJD } 

  static class ObjectNameConverter implements CommandLine.ITypeConverter<String> {
    @Override
    public String convert(String value) throws Exception {
      try{
        value = value.trim().toUpperCase();
        if (value.length() > 10 || value.isEmpty()) {
          throw new Exception("Invalid object name: must be 1-10 characters");
        }
        return value;

      } catch (IllegalArgumentException e) {
        throw new Exception("Invalid object name: " + value);
      }
    }
  }  

  static class ObjectTypeConverter implements CommandLine.ITypeConverter<ObjectType> {
    @Override
    public ObjectType convert(String value) throws Exception {
      try {
        return ObjectType.valueOf(value.trim().toUpperCase());
      } catch (IllegalArgumentException e) {
        throw new Exception("Invalid object type: " + value);
      }
    }
  }  

  static class SourceTypeConverter implements CommandLine.ITypeConverter<SourceType> {
    @Override
    public SourceType convert(String value) throws Exception {
      try {
        return SourceType.valueOf(value.trim().toUpperCase());
      } catch (IllegalArgumentException e) {
        throw new Exception("Invalid source type: " + value);
      }
    }
  }

  @Option(names = { "-l", "--lib" }, required = true,  description = "Library to build") //TODO: Change to library list
  private String library;

  @Option(names = "--obj", required = true, description = "Object name", converter = ObjectNameConverter.class)
  private String objectName;

  @Option(names = {"-t","--type"}, required = true, description = "Object type (e.g., PGM, SRVPGM)", converter = ObjectTypeConverter.class)
  private ObjectType objectType;

  @Option(names = { "-sf", "--source-file" },  description = "Source Phisical File")
  private String sourceFile;

  @Option(names = { "-sn", "--source-name" },  description = "Source member name")
  private String sourceName;

  @Option(names = {"-st","--source-type"}, required = true, description = "Source type (e.g., RPGLE, CLLE)", converter = SourceTypeConverter.class)
  private SourceType sourceType;

  /* Maps source type to its compilation command */
  private static final Map<SourceType, Map<ObjectType, CompCmd>> typeToCmdMap = new EnumMap<>(SourceType.class);  

  /* Maps compilation commands to required and optional params */
  private static final Map<CompCmd, List<ParamCmd>> requiredParamsMap = new EnumMap<>(CompCmd.class);  
  private static final Map<CompCmd, Set<ParamCmd>> optionalParamsMap = new EnumMap<>(CompCmd.class);

  /* Maps params to values */
  public static final Map<ParamCmd, List<ValCmd>> valueParamsMap = new EnumMap<>(ParamCmd.class); 

  static {
    /*
     * Populate mapping from (SourceType, ObjectType) to CompCmd
     */
    // TODO: There has to be a cleaner way of doing this, maybe using :: or lambda to auto define them
    // Map RPG commands and add bind them to the type
    Map<ObjectType, CompCmd> rpgMap = new EnumMap<>(ObjectType.class);
    rpgMap.put(ObjectType.PGM, CompCmd.CRTRPGPGM);
    typeToCmdMap.put(SourceType.RPG, rpgMap);

    Map<ObjectType, CompCmd> rpgLeMap = new EnumMap<>(ObjectType.class);
    rpgLeMap.put(ObjectType.MODULE, CompCmd.CRTRPGMOD);
    rpgLeMap.put(ObjectType.PGM, CompCmd.CRTBNDRPG);
    rpgLeMap.put(ObjectType.SRVPGM, CompCmd.CRTSRVPGM); // Assuming compilation involves module creation first, but mapping to final command
    typeToCmdMap.put(SourceType.RPGLE, rpgLeMap);

    Map<ObjectType, CompCmd> sqlRpgLeMap = new EnumMap<>(ObjectType.class);
    sqlRpgLeMap.put(ObjectType.MODULE, CompCmd.CRTSQLRPGI);
    sqlRpgLeMap.put(ObjectType.PGM, CompCmd.CRTSQLRPGI);
    sqlRpgLeMap.put(ObjectType.SRVPGM, CompCmd.CRTSRVPGM);
    typeToCmdMap.put(SourceType.SQLRPGLE, sqlRpgLeMap);

    Map<ObjectType, CompCmd> clpMap = new EnumMap<>(ObjectType.class);
    clpMap.put(ObjectType.PGM, CompCmd.CRTCLPGM);
    typeToCmdMap.put(SourceType.CLP, clpMap);

    Map<ObjectType, CompCmd> clleMap = new EnumMap<>(ObjectType.class);
    clleMap.put(ObjectType.MODULE, CompCmd.CRTCLMOD);
    clleMap.put(ObjectType.PGM, CompCmd.CRTBNDCL);
    clleMap.put(ObjectType.SRVPGM, CompCmd.CRTSRVPGM);
    typeToCmdMap.put(SourceType.CLLE, clleMap);

    Map<ObjectType, CompCmd> sqlMap = new EnumMap<>(ObjectType.class);
    sqlMap.put(ObjectType.TABLE, CompCmd.RUNSQLSTM);
    sqlMap.put(ObjectType.LF, CompCmd.RUNSQLSTM);
    sqlMap.put(ObjectType.VIEW, CompCmd.RUNSQLSTM);
    sqlMap.put(ObjectType.ALIAS, CompCmd.RUNSQLSTM);
    sqlMap.put(ObjectType.PROCEDURE, CompCmd.RUNSQLSTM);
    sqlMap.put(ObjectType.FUNCTION, CompCmd.RUNSQLSTM);
    typeToCmdMap.put(SourceType.SQL, sqlMap);

    // Populate required params for each CompCmd
    requiredParamsMap.put(CompCmd.CRTRPGMOD, Arrays.asList(ParamCmd.MODULE));
    requiredParamsMap.put(CompCmd.CRTSQLRPGI, Arrays.asList(ParamCmd.OBJ, ParamCmd.OBJTYPE));
    requiredParamsMap.put(CompCmd.CRTBNDRPG, Arrays.asList(ParamCmd.PGM, ParamCmd.SRCFILE));
    requiredParamsMap.put(CompCmd.CRTRPGPGM, Arrays.asList(ParamCmd.PGM));
    requiredParamsMap.put(CompCmd.CRTCLMOD, Arrays.asList(ParamCmd.MODULE));
    requiredParamsMap.put(CompCmd.CRTBNDCL, Arrays.asList(ParamCmd.PGM));
    requiredParamsMap.put(CompCmd.CRTCLPGM, Arrays.asList(ParamCmd.PGM));
    requiredParamsMap.put(CompCmd.RUNSQLSTM, Arrays.asList(ParamCmd.SRCFILE));
    requiredParamsMap.put(CompCmd.CRTSRVPGM, Arrays.asList(ParamCmd.OBJ, ParamCmd.MODULE, ParamCmd.BNDSRVPGM));
    requiredParamsMap.put(CompCmd.CRTDSPF, Arrays.asList(ParamCmd.OBJ));
    requiredParamsMap.put(CompCmd.CRTLF, Arrays.asList(ParamCmd.OBJ));
    requiredParamsMap.put(CompCmd.CRTPRTF, Arrays.asList(ParamCmd.OBJ));
    requiredParamsMap.put(CompCmd.CRTMNU, Arrays.asList(ParamCmd.OBJ));
    requiredParamsMap.put(CompCmd.CRTQMQRY, Arrays.asList(ParamCmd.OBJ));

    // Populate optional params for each CompCmd
    optionalParamsMap.put(CompCmd.CRTRPGMOD, EnumSet.of(ParamCmd.TEXT, ParamCmd.TGTCCSID, ParamCmd.BNDDIR, ParamCmd.DFTACTGRP));
    optionalParamsMap.put(CompCmd.CRTSQLRPGI, EnumSet.of(ParamCmd.COMMIT, ParamCmd.TEXT, ParamCmd.ACTGRP, ParamCmd.BNDDIR));
    optionalParamsMap.put(CompCmd.CRTBNDRPG, EnumSet.of(ParamCmd.ACTGRP, ParamCmd.DFTACTGRP, ParamCmd.BNDDIR, ParamCmd.TEXT));
    optionalParamsMap.put(CompCmd.CRTRPGPGM, EnumSet.of(ParamCmd.TEXT, ParamCmd.TGTCCSID));
    optionalParamsMap.put(CompCmd.CRTCLMOD, EnumSet.of(ParamCmd.TEXT, ParamCmd.TGTCCSID, ParamCmd.ACTGRP));
    optionalParamsMap.put(CompCmd.CRTBNDCL, EnumSet.of(ParamCmd.ACTGRP, ParamCmd.DFTACTGRP, ParamCmd.BNDDIR, ParamCmd.TEXT));
    optionalParamsMap.put(CompCmd.CRTCLPGM, EnumSet.of(ParamCmd.TEXT, ParamCmd.TGTCCSID));
    optionalParamsMap.put(CompCmd.RUNSQLSTM, EnumSet.of(ParamCmd.COMMIT, ParamCmd.TEXT));
    optionalParamsMap.put(CompCmd.CRTSRVPGM, EnumSet.of(ParamCmd.ACTGRP, ParamCmd.BNDDIR, ParamCmd.TEXT, ParamCmd.DFTACTGRP));
    optionalParamsMap.put(CompCmd.CRTDSPF, EnumSet.of(ParamCmd.TEXT, ParamCmd.TGTCCSID));
    optionalParamsMap.put(CompCmd.CRTLF, EnumSet.of(ParamCmd.TEXT, ParamCmd.TGTCCSID));
    optionalParamsMap.put(CompCmd.CRTPRTF, EnumSet.of(ParamCmd.TEXT, ParamCmd.TGTCCSID));
    optionalParamsMap.put(CompCmd.CRTMNU, EnumSet.of(ParamCmd.TEXT));
    optionalParamsMap.put(CompCmd.CRTQMQRY, EnumSet.of(ParamCmd.TEXT));

    // Populate valueParamsMap with special values for each parameter (add * when using in commands)
    valueParamsMap.put(ParamCmd.OUTPUT, Arrays.asList(ValCmd.OUTFILE));
    valueParamsMap.put(ParamCmd.OUTMBR, Arrays.asList(ValCmd.FIRST, ValCmd.REPLACE)); // FIRST is now reliably first
    valueParamsMap.put(ParamCmd.OBJTYPE, Arrays.asList(ValCmd.PGM, ValCmd.SRVPGM));
    valueParamsMap.put(ParamCmd.MODULE, Arrays.asList(ValCmd.PGM));
    valueParamsMap.put(ParamCmd.BNDSRVPGM, Arrays.asList(ValCmd.SRVPGM));
    valueParamsMap.put(ParamCmd.LIBL, Arrays.asList(ValCmd.LIBL));
    valueParamsMap.put(ParamCmd.SRCFILE, Arrays.asList(ValCmd.FILE, ValCmd.LIBL));
    valueParamsMap.put(ParamCmd.PGM, Arrays.asList(ValCmd.CURLIB, ValCmd.LIBL)); // CURLIB is now first; swap if you want LIBL first
    valueParamsMap.put(ParamCmd.OBJ, Arrays.asList(ValCmd.LIBL, ValCmd.FILE, ValCmd.DTAARA));
    // TODO: for parms with no defined value: EnumSet.noneOf(ValCmd.class)

    // TODO: I think this Supliers is what i really need
    // Maybe i can send enums as parameters too

    //TODO: These suppliers could be instances and not static to add param validation
    //TODO: If there is not a supplier, then an input param is needed
    //TODO: I can also return the lambda function... that would be nice and would allow a higher abstraction function to get it
    

  }

  public void run() {
    // For now, demonstrate the mappings
    Map<ObjectType, CompCmd> objectMap = typeToCmdMap.get(sourceType);
    if (objectMap == null) {
      System.err.println("No mapping for source type: " + sourceType);
      return;
    }
    CompCmd CompCmd = objectMap.get(objectType);
    if (CompCmd == null) {
      System.err.println("No compilation command for source type " + sourceType + " and object type " + objectType);
      return;
    }
    List<ParamCmd> reqParams = requiredParamsMap.getOrDefault(CompCmd, Collections.emptyList());
    Set<ParamCmd> optParams = optionalParamsMap.getOrDefault(CompCmd, EnumSet.noneOf(ParamCmd.class));

    System.out.println("Compilation command: " + CompCmd.name());
    System.out.println("Required parameters: " + reqParams.stream().map(Enum::name).collect(Collectors.joining(", ")));
    System.out.println("Optional parameters: " + optParams.stream().map(Enum::name).collect(Collectors.joining(", ")));

    System.out.println("Compilation command: " + CompCmd.name());

    //TODO: This could be done cleaner
    //TODO: Add valueParamsMap validation for empty values or non existing default values
    //String commandStr = CompCmd.name() + " " + reqParams.stream().map(Enum::name).map(p -> p + " (*" + valueParamsMap.get(p) + ")").collect(Collectors.joining(" "));
    
    Resolver resolver = new Resolver(library, objectName, sourceFile, sourceName, objectType, sourceType);
    
    String commandStr = CompCmd.name() + " " + reqParams.stream()
      .map(param -> resolver.resolve(param))
      .collect(Collectors.joining(" "));

    //TODO: Try to compile everthing from the IFS, is the source is inside a member
    // try to migrate it and compile it in the IFS. For OPM objects, create a temporary source member

    //String commandStr = CompCmd.name() + " " + reqParams.stream()
    //  .map(param -> valueSuppliers.get(param).get())
    //  .collect(Collectors.joining(" "));

    //String commandStr = CompCmd.name() + " " + reqParams.stream().map(Enum::name).map(p -> p + " (" + valueSuppliers.get(p).get() + ")").collect(Collectors.joining(" "));
    // Later: build command string, execute via CALL QCMD, etc.  

    System.out.println("Full command: " + commandStr);

    compile();
  }

  public ObjectCompiler(AS400 system) throws Exception {
    this(system, new AS400JDBCDataSource(system).getConnection());
  }

  public ObjectCompiler(AS400 system, Connection connection) throws Exception {
    this.system = system;

    // Database
    this.connection = connection;
    this.connection.setAutoCommit(true);

    // User
    this.currentUser = new User(system, system.getUserId());
    this.currentUser.loadUserInformation();
  }

  private void compile(){
    try {
      System.out.println("Compilation... ");
    } catch (Exception e) {
      e.printStackTrace();
    } finally{
      cleanup();
    }
  }

  private void cleanup(){
    try {
      if (connection != null && !connection.isClosed()) {
        connection.close();
      }
      if (system != null) {
        system.disconnectAllServices();
      }

    } catch (SQLException e) {
      e.printStackTrace();
    }
  }

  public static void main( String... args ){
    AS400 system = null;
    ObjectCompiler compiler = null;
    try {
      system = IBMiDotEnv.getNewSystemConnection(true); // Get system
      compiler = new ObjectCompiler(system);
      new CommandLine(compiler).execute(args);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}
