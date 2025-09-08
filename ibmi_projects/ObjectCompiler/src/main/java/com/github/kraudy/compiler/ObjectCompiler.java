package com.github.kraudy.compiler;

import java.beans.PropertyVetoException;
import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Timestamp;
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
import com.ibm.as400.access.AS400Message;
import com.ibm.as400.access.AS400Bin4;
import com.ibm.as400.access.AS400Text;
import com.ibm.as400.access.AS400SecurityException;
import com.ibm.as400.access.ErrorCompletingRequestException;
import com.ibm.as400.access.AS400JDBCDataSource;
import com.ibm.as400.access.CommandCall;
import com.ibm.as400.access.ProgramCall;
import com.ibm.as400.access.ProgramParameter;
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

  @Option(names = { "-sl", "--source-lib" }, description = "Source library (defaults to *LIBL or retrieved from object)", converter = LibraryConverter.class)
  private String sourceLib = "*LIBL";

  @Option(names = { "-sf", "--source-file" }, description = "Source physical file (defaults based on source type or retrieved from object)")
  private String sourceFile;

  @Option(names = { "-sn", "--source-name" }, description = "Source member name (defaults to object name, command-specific *SPECIAL, or retrieved from object)")
  private String sourceName;

  @Option(names = {"-st","--source-type"}, description = "Source type (e.g., RPGLE, CLLE) (defaults to retrieved from object if possible)", converter = SourceTypeConverter.class)
  private SourceType sourceType;

  @Option(names = { "--text" }, description = "Object text description (defaults to retrieved from object if possible)")
  private String text;

  //TODO: Is this needed?
  @Option(names = { "--actgrp" }, description = "Activation group (defaults to retrieved from object if possible)")
  private String actGrp;

  @Option(names = "-x", description = "Debug")
  private boolean debug = false;

  @Option(names = "-v", description = "Verbose output")
  private boolean verbose = false;

  /* Maps source type to its compilation command */
  private static final Map<SourceType, Map<ObjectType, CompCmd>> typeToCmdMap = new EnumMap<>(SourceType.class);  

  /* Maps params to values */
  public static final Map<ParamCmd, List<ValCmd>> valueParamsMap = new EnumMap<>(ParamCmd.class); 

  /* Maps source type to its default source pf */
  public static final Map<SourceType, DftSrc> typeToDftSrc = new EnumMap<>(SourceType.class);  

  /* Maps source type to module creation command (for multi-step) */
  private static final Map<SourceType, CompCmd> typeToModuleCmdMap = new EnumMap<>(SourceType.class);

  /* Maps object attribute to source type (for inference) */
  private static final Map<String, SourceType> attrToSourceType = new HashMap<>();


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

    // Populate attrToSourceType (basic mapping, expand as needed)
    attrToSourceType.put("RPG", SourceType.RPG);
    attrToSourceType.put("RPGLE", SourceType.RPGLE);
    attrToSourceType.put("SQLRPGLE", SourceType.SQLRPGLE);
    attrToSourceType.put("CLP", SourceType.CLP);
    attrToSourceType.put("CLLE", SourceType.CLLE);

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
    // Retrieve object info if exists to fill in defaults
    Map<String, Object> objInfo = null;
    try {
      objInfo = retrieveObjectInfo();
    } catch (Exception e) {
      System.err.println("Warning: Could not retrieve compilation params from object: " + e.getMessage() + ". Using defaults.");
    }

     // Fill in missing params from object info
    if (objInfo != null) {
      if (debug) System.out.println("Found object info");
      if (sourceType == null) {
        String attr = (String) objInfo.get("attribute");
        if (debug) System.out.println("attr: " + attr);
        if (attr != null && !attr.isEmpty()) {
          sourceType = attrToSourceType.get(attr);
          if (sourceType == null) {
            System.err.println("Could not infer source type from object attribute '" + attr + "'. Source type is required.");
            return;
          }
        }
      }
      if (sourceLib.equals("*LIBL")) {
        String retrievedLib = (String) objInfo.get("sourceLibrary");
        if (debug) System.out.println("retrievedLib: " + retrievedLib);
        if (retrievedLib != null && !retrievedLib.isEmpty()) {
          sourceLib = retrievedLib;
        }
      }
      if (sourceFile == null) {
        String retrievedFile = (String) objInfo.get("sourceFile");
        if (debug) System.out.println("retrievedFile: " + retrievedFile);
        if (retrievedFile != null && !retrievedFile.isEmpty()) {
          sourceFile = retrievedFile;
        }
      }
      if (sourceName == null) {
        String retrievedMbr = (String) objInfo.get("sourceMember");
        if (debug) System.out.println("retrievedMbr: " + retrievedMbr);
        if (retrievedMbr != null && !retrievedMbr.isEmpty()) {
          sourceName = retrievedMbr;
        }
      }
      if (text == null) {
        String retrievedText = (String) objInfo.get("textDescription");
        if (debug) System.out.println("retrievedText: " + retrievedText);
        if (retrievedText != null && !retrievedText.isEmpty()) {
          text = retrievedText;
        }
      }
      if (actGrp == null && objInfo.containsKey("activationGroupAttribute")) {
        String retrievedActGrp = ((String) objInfo.get("activationGroupAttribute")).trim();
        if (debug) System.out.println("retrievedActGrp: " + retrievedActGrp);
        if (!retrievedActGrp.isEmpty()) {
          actGrp = retrievedActGrp;
        }
      }
      
      // TODO: Add more params like --usrprf, --useadpaut, etc., and map from objInfo
    }

    if (sourceType == null) {
      System.err.println("Source type is required if not retrievable from object.");
      return;
    }

    // Mappings
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

  private Map<String, Object> retrieveObjectInfo() throws Exception {
    String apiPgm;
    String format = "PGMI0100"; // Default for PGM
    if (objectType == ObjectType.PGM) {
      apiPgm = "QCLRPGMI";
    } else if (objectType == ObjectType.SRVPGM) {
      apiPgm = "QBNRSPGM";
      format = "SPGI0100";
    } else if (objectType == ObjectType.MODULE) {
      apiPgm = "QBNRMODI";
      format = "MODI0100";
    } else {
      throw new Exception("Object type " + objectType + " not supported for retrieving compilation params.");
    }

    ProgramCall pc = new ProgramCall(system);
    pc.setProgram("/QSYS.LIB/" + apiPgm + ".PGM");

    int recvLen = 2048; // Sufficient for PGMI0100/SPGI0100/MODI0100
    ProgramParameter[] parms = new ProgramParameter[5];
    parms[0] = new ProgramParameter(recvLen); // Receiver
    parms[1] = new ProgramParameter(new AS400Bin4().toBytes(recvLen)); // Length of receiver
    parms[2] = new ProgramParameter(new AS400Text(8, system).toBytes(format)); // Format
    String qualName = String.format("%-10s%-10s", objectName, library);
    parms[3] = new ProgramParameter(new AS400Text(20, system).toBytes(qualName)); // Qualified object name
    byte[] errorCode = new byte[16];
    new AS400Bin4().toBytes(0, errorCode, 0); // Bytes provided = 0 to throw exceptions
    parms[4] = new ProgramParameter(errorCode); // Error code

    pc.setParameterList(parms);

    if (!pc.run()) {
      AS400Message[] msgs = pc.getMessageList();
      throw new Exception("API call failed: " + (msgs.length > 0 ? msgs[0].getText() : "Unknown error"));
    }

    byte[] data = parms[0].getOutputData();
    Map<String, Object> info = new HashMap<>();
    AS400Bin4 bin4 = new AS400Bin4();
    AS400Text text10 = new AS400Text(10, system);
    AS400Text text13 = new AS400Text(13, system);
    AS400Text text1 = new AS400Text(1, system);
    AS400Text text50 = new AS400Text(50, system);
    AS400Text text30 = new AS400Text(30, system);

    int offset = 0;
    info.put("bytesReturned", bin4.toInt(data, offset)); offset += 4;
    info.put("bytesAvailable", bin4.toInt(data, offset)); offset += 4;
    info.put("programName", text10.toObject(data, offset).toString().trim()); offset += 10;
    info.put("programLibrary", text10.toObject(data, offset).toString().trim()); offset += 10;
    info.put("owner", text10.toObject(data, offset).toString().trim()); offset += 10;
    info.put("attribute", text10.toObject(data, offset).toString().trim()); offset += 10;
    info.put("creationDateTime", text13.toObject(data, offset).toString().trim()); offset += 13;
    info.put("sourceFile", text10.toObject(data, offset).toString().trim()); offset += 10;
    info.put("sourceLibrary", text10.toObject(data, offset).toString().trim()); offset += 10;
    info.put("sourceMember", text10.toObject(data, offset).toString().trim()); offset += 10;
    info.put("sourceUpdatedDateTime", text13.toObject(data, offset).toString().trim()); offset += 13;
    info.put("observable", text1.toObject(data, offset).toString().trim()); offset += 1;
    info.put("userProfileOption", text1.toObject(data, offset).toString().trim()); offset += 1;
    info.put("useAdoptedAuthority", text1.toObject(data, offset).toString().trim()); offset += 1;
    info.put("logCommands", text1.toObject(data, offset).toString().trim()); offset += 1;
    info.put("allowRTVCLSRC", text1.toObject(data, offset).toString().trim()); offset += 1;
    info.put("fixDecimalData", text1.toObject(data, offset).toString().trim()); offset += 1;
    info.put("textDescription", text50.toObject(data, offset).toString().trim()); offset += 50;
    info.put("typeOfProgram", text1.toObject(data, offset).toString().trim()); offset += 1;
    info.put("teraspaceEnabled", text1.toObject(data, offset).toString().trim()); offset += 1;
    offset += 58; // Reserved
    info.put("minParameters", bin4.toInt(data, offset)); offset += 4;
    info.put("maxParameters", bin4.toInt(data, offset)); offset += 4;
    // Skip other fields for brevity; add as needed (e.g., optimization at ~325 if char1)
    offset = 368; // Jump to activation group (adjust if needed for SPGI/MODI differences)
    if (objectType != ObjectType.MODULE) { // Modules don't have ACTGRP
      info.put("activationGroupAttribute", text30.toObject(data, offset).toString().trim());
    }
    // TODO: For more params, parse additional fields or use other formats like PGMI0200 + QBNRMODI for module-specific (e.g., OPTLEVEL binary at module offset 164)

    return info;
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
    /* TODO: Validate when to add this
    if (actGrp != null && (cmd == CompCmd.CRTBNDRPG || cmd == CompCmd.CRTBNDCL || cmd == CompCmd.CRTSQLRPGI || cmd == CompCmd.CRTSRVPGM)) {
      sb.append(" ACTGRP(").append(actGrp).append(")");
    }
      */
    // TODO: Add more like DFTACTGRP(*NO), BNDDIR, USRPRF, etc., with options and defaults/retrieved values


    System.out.println("Built command: " + sb);
    return sb.toString();
  }

  private String getSourceFile() {
    String file = (sourceFile != null) ? sourceFile : typeToDftSrc.getOrDefault(sourceType, DftSrc.QRPGLESRC).name();
    //TODO: Validate if it should use library when library = null o *LIBL
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
        //TODO: Get current_timestamp, maybe use only java
        Timestamp compilationTime = null;
        try(Statement stmt = connection.createStatement();
          ResultSet rsTime = stmt.executeQuery(
            "Select CURRENT_TIMESTAMP As Compilation_Time FROM sysibm.sysdummy1" 
          )){
            while (rsTime.next()) {
              compilationTime = rsTime.getTimestamp("Compilation_Time");
            }
        }
        boolean success = cc.run(commandStr);
        AS400Message[] messages = cc.getMessageList();
        if (success) {
          System.out.println("Compilation successful.");
        } else {
          System.out.println("Compilation failed.");
          //TODO: Show spool data
          showComilationSpool(compilationTime, system.getUserId().trim().toUpperCase());
        }
        for (AS400Message msg : messages) {
          System.out.println(msg.getID() + ": " + msg.getText());
        }
      } catch (AS400SecurityException | ErrorCompletingRequestException | IOException | InterruptedException | PropertyVetoException | SQLException e) {
        e.printStackTrace();
      }
    }
    cleanup();
  }

  /*  https://gist.github.com/BirgittaHauser/f28e3527f1cc4c422a05eea865b455bb */
  private void showComilationSpool(Timestamp compilationTime, String user) throws SQLException{
    System.out.println("Compilation time: " + compilationTime);
    
    try(Statement stmt = connection.createStatement();
      ResultSet rsCompilationSpool = stmt.executeQuery(
        "Select Cast(Spooled_Data As Varchar(100) CCSID " + INVARIANT_CCSID + ") As  Spooled_Data " + 
        "from  qsys2.OutPut_Queue_Entries a Cross Join " +
            "Lateral(Select * " +
                      "From Table(SysTools.Spooled_File_Data( " +
                                              "Job_Name            => a.Job_Name, " +
                                              "Spooled_File_Name   => a.Spooled_File_Name, " +
                                              "Spooled_File_Number => File_Number))) b " +
        "Where     Output_Queue_Name = '" + user + "' " +
              "and USER_NAME = '" + user + "' " + 
              "and SPOOLED_FILE_NAME = '" + objectName + "' " +
              "and OUTPUT_QUEUE_LIBRARY_NAME = 'QGPL'" +
              "and CREATE_TIMESTAMP > '" + compilationTime + "'"
      )){
        while (rsCompilationSpool.next()) {
          System.out.println(rsCompilationSpool.getTimestamp("Spooled_Data"));
        }
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
