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

import com.github.kraudy.compiler.ObjectCompiler.CompCmd;
import com.github.kraudy.compiler.ObjectCompiler.DftSrc;
import com.github.kraudy.compiler.ObjectCompiler.ObjectType;
import com.github.kraudy.compiler.ObjectCompiler.SourceType;
import com.ibm.as400.access.AS400;
import com.ibm.as400.access.AS400Message;
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

  enum ParamCmd { PGM, OBJ, OBJTYPE, OUTPUT, OUTMBR, MODULE, BNDSRVPGM, LIBL, SRCFILE, SRCMBR,
    ACTGRP, DFTACTGRP, BNDDIR, COMMIT, TEXT, TGTCCSID, CRTFRMSTMF } 

  enum ValCmd { FIRST, REPLACE, OUTFILE, LIBL, FILE, DTAARA, PGM, SRVPGM, CURLIB } // add * to these 

  enum PostCmpCmd { CHGOBJD } 

  enum DftSrc { QRPGLESRC, QRPGSRC, QCLSRC, QSQLSRC } // TODO: Expand

  static class LibraryConverter implements CommandLine.ITypeConverter<String> {
    @Override
    public String convert(String value) throws Exception {
      try{
        value = value.trim().toUpperCase();
        if (value.length() > 10 || value.isEmpty()) {
          throw new Exception("Invalid library name: must be 1-10 characters");
        }
        return value;

      } catch (IllegalArgumentException e) {
        throw new Exception("Invalid library name: " + value);
      }
    }
  }  

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

  @Option(names = { "-l", "--lib" }, required = true, description = "Target library for object", converter = LibraryConverter.class)
  private String library;

  @Option(names = "--obj", required = true, description = "Object name", converter = ObjectNameConverter.class)
  private String objectName;

  @Option(names = {"-t","--type"}, required = true, description = "Object type (e.g., PGM, SRVPGM)", converter = ObjectTypeConverter.class)
  private ObjectType objectType;

  @Option(names = { "-sl", "--source-lib" }, description = "Source library (defaults to *LIBL)", converter = LibraryConverter.class)
  private String sourceLib = "*LIBL";

  @Option(names = { "-sf", "--source-file" }, description = "Source physical file (defaults based on source type)")
  private String sourceFile;

  @Option(names = { "-sn", "--source-name" }, description = "Source member name (defaults to object name or command-specific *SPECIAL)")
  private String sourceName;

  @Option(names = {"-st","--source-type"}, required = true, description = "Source type (e.g., RPGLE, CLLE)", converter = SourceTypeConverter.class)
  private SourceType sourceType;

  @Option(names = { "--text" }, description = "Object text description (optional)")
  private String text;

  /* Maps source type to its compilation command */
  private static final Map<SourceType, Map<ObjectType, CompCmd>> typeToCmdMap = new EnumMap<>(SourceType.class);  

  /* Maps params to values */
  public static final Map<ParamCmd, List<ValCmd>> valueParamsMap = new EnumMap<>(ParamCmd.class); 

  /* Maps source type to its default source pf */
  public static final Map<SourceType, DftSrc> typeToDftSrc = new EnumMap<>(SourceType.class);  

  /* Maps source type to module creation command (for multi-step) */
  private static final Map<SourceType, CompCmd> typeToModuleCmdMap = new EnumMap<>(SourceType.class);


  static {
        
    // Populate typeToDftSrc
    typeToDftSrc.put(SourceType.RPG, DftSrc.QRPGSRC);
    typeToDftSrc.put(SourceType.RPGLE, DftSrc.QRPGLESRC);
    typeToDftSrc.put(SourceType.SQLRPGLE, DftSrc.QRPGLESRC); // Often same as RPGLE
    typeToDftSrc.put(SourceType.CLP, DftSrc.QCLSRC);
    typeToDftSrc.put(SourceType.CLLE, DftSrc.QCLSRC);
    typeToDftSrc.put(SourceType.SQL, DftSrc.QSQLSRC);

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

    // Populate typeToModuleCmdMap (for SRVPGM pre-step)
    typeToModuleCmdMap.put(SourceType.RPGLE, CompCmd.CRTRPGMOD);
    typeToModuleCmdMap.put(SourceType.SQLRPGLE, CompCmd.CRTSQLRPGI);
    typeToModuleCmdMap.put(SourceType.CLLE, CompCmd.CRTCLMOD);

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
    CompCmd mainCmd = objectMap.get(objectType);
    if (mainCmd == null) {
      System.err.println("No compilation command for source type " + sourceType + " and object type " + objectType);
      return;
    }

    System.out.println("Compilation command: " + mainCmd.name());

    List<String> commandStrs = new ArrayList<>();

    boolean isMultiStep = (objectType == ObjectType.SRVPGM);
    CompCmd moduleCmd = null;
    if (isMultiStep) {
      moduleCmd = typeToModuleCmdMap.get(sourceType);
      if (moduleCmd != null) {
        commandStrs.add(buildCommand(moduleCmd, true)); // true = isModuleCreation
      }
    }

    commandStrs.add(buildCommand(mainCmd, false));
    
    System.out.println("Full command: " + commandStrs);

    //TODO: Try to compile everthing from the IFS, is the source is inside a member
    // try to migrate it and compile it in the IFS. For OPM objects, create a temporary source member

    compile(commandStrs);
  }

  private String buildCommand(CompCmd cmd, boolean isModuleCreation) {
    StringBuilder sb = new StringBuilder(cmd.name());

    String target = library + "/" + objectName;
    String srcFile = getSourceFile();
    String srcMbr = getSourceMember(cmd);

    switch (cmd) {
      case CRTRPGMOD:
      case CRTCLMOD:
        sb.append(" MODULE(").append(target).append(")");
        sb.append(" SRCFILE(").append(srcFile).append(")");
        sb.append(" SRCMBR(").append(srcMbr).append(")");
        break;
      case CRTBNDRPG:
      case CRTBNDCL:
      case CRTRPGPGM:
      case CRTCLPGM:
        sb.append(" PGM(").append(target).append(")");
        sb.append(" SRCFILE(").append(srcFile).append(")");
        sb.append(" SRCMBR(").append(srcMbr).append(")");
        break;
      case CRTSQLRPGI:
        sb.append(" OBJ(").append(target).append(")");
        sb.append(" OBJTYPE(*").append(isModuleCreation ? "MODULE" : "PGM").append(")");
        sb.append(" SRCFILE(").append(srcFile).append(")");
        sb.append(" SRCMBR(").append(srcMbr).append(")");
        break;
      case CRTSRVPGM:
        sb.append(" SRVPGM(").append(target).append(")");
        sb.append(" MODULE(").append(target).append(")"); // Assume single module with same name
        sb.append(" BNDSRVPGM(*NONE)");
        break;
      case RUNSQLSTM:
        sb.append(" SRCFILE(").append(srcFile).append(")");
        sb.append(" SRCMBR(").append(srcMbr).append(")");
        sb.append(" COMMIT(*NONE)"); // Default; add option if needed
        break;
      // TODO: Add cases for CRTDSPF, CRTLF, etc., similar to above
      default:
        throw new IllegalArgumentException("Unsupported command: " + cmd);
    }

    // Add common optional params if provided/supported
    if (text != null) {
      sb.append(" TEXT('").append(text).append("')");
    }
    // TODO: Add more like ACTGRP(*CALLER), BNDDIR, etc., with options and defaults

    System.out.println("Built command: " + sb);
    return sb.toString();
  }

  private String getSourceFile() {
    String file = (sourceFile != null) ? sourceFile : typeToDftSrc.getOrDefault(sourceType, DftSrc.QRPGLESRC).name();
    return sourceLib + "/" + file;
  }

  private String getSourceMember(CompCmd cmd) {
    if (sourceName != null) {
      return sourceName;
    }
    // Command-specific default special value
    switch (cmd) {
      case CRTBNDRPG:
      case CRTBNDCL:
      case CRTRPGPGM:
      case CRTCLPGM:
        return "*PGM";
      case CRTRPGMOD:
      case CRTCLMOD:
        return "*MODULE";
      case CRTSQLRPGI:
        return "*OBJ";
      default:
        return objectName; // Fallback for SQL, etc.
    }
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

  private void compile(List<String> commandStrs) {
    CommandCall cc = new CommandCall(system);
    for (String commandStr : commandStrs) {
      try {
        System.out.println("Executing: " + commandStr);
        boolean success = cc.run(commandStr);
        AS400Message[] messages = cc.getMessageList();
        if (success) {
          System.out.println("Compilation successful.");
        } else {
          System.out.println("Compilation failed.");
        }
        for (AS400Message msg : messages) {
          System.out.println(msg.getID() + ": " + msg.getText());
        }
      } catch (AS400SecurityException | ErrorCompletingRequestException | IOException | InterruptedException | PropertyVetoException e) {
        e.printStackTrace();
      }
    }
    cleanup();
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
