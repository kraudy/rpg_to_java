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
import java.util.function.Function;
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

/*
 *import com.fasterxml.jackson.databind.JsonNode;
 *import com.fasterxml.jackson.databind.ObjectMapper;
*/
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

@Command (name = "compiler", description = "OPM/ILE Object Compiler", mixinStandardHelpOptions = true, version = "ObjectCompiler 0.0.1")
public class ObjectCompiler implements Runnable{
  private static final String UTF8_CCSID = "1208"; // UTF-8 for stream files
  public static final String INVARIANT_CCSID = "37"; // EBCDIC
  private final AS400 system;
  private final Connection connection;
  private final User currentUser;

  public enum SysCmd { CHGLIBL, DSPPGMREF, DSPOBJD, DSPDBR }

  public enum SourceType { RPG, RPGLE, SQLRPGLE, CLP, CLLE, SQL }

  public enum ObjectType { PGM, SRVPGM, MODULE, TABLE, LF, VIEW, ALIAS, PROCEDURE, FUNCTION } // Add more as needed

  public enum CompCmd { CRTRPGMOD, CRTSQLRPGI, CRTBNDRPG, CRTRPGPGM, CRTCLMOD, CRTBNDCL, CRTCLPGM, RUNSQLSTM, CRTSRVPGM, CRTDSPF, CRTLF, CRTPRTF, CRTMNU, CRTQMQRY }

  public enum ParamCmd { PGM, OBJ, OBJTYPE, OUTPUT, OUTMBR, MODULE, BNDSRVPGM, LIBL, SRCFILE, SRCMBR, ACTGRP, DFTACTGRP, BNDDIR, COMMIT, TEXT, TGTCCSID, CRTFRMSTMF }

  //TODO: Maybe is more practical to make these strings
  public enum ValCmd { FIRST, REPLACE, OUTFILE, LIBL, FILE, DTAARA, PGM, SRVPGM, CURLIB } // add * to these

  public enum PostCmpCmd { CHGOBJD }

  public enum DftSrc { QRPGLESRC, QRPGSRC, QCLSRC, QSQLSRC } // TODO: Expand

  // Core struct for capturing compilation specs (JSON-friendly via Jackson)
  public static class CompilationSpec {
    //TODO: Make this private, add set method and move to another file
    public String targetLibrary;
    public String objectName;
    public ObjectType objectType;
    public String sourceLibrary;
    public String sourceFile;
    public String sourceMember;
    public SourceType sourceType;
    public String text;
    public String actGrp;//TODO: Remove this

    //TODO: Move this class to its own file and remove static
    //TODO: Change this name to IbmObject, to be more broader
    // Constructor for Jackson deserialization
    @JsonCreator
    public CompilationSpec(
          @JsonProperty("targetLibrary") String targetLibrary,
          @JsonProperty("objectName") String objectName,
          @JsonProperty("objectType") ObjectType objectType,
          @JsonProperty("sourceLibrary") String sourceLibrary,
          @JsonProperty("sourceFile") String sourceFile,
          @JsonProperty("sourceMember") String sourceMember,
          @JsonProperty("sourceType") SourceType sourceType,
          @JsonProperty("text") String text,
          @JsonProperty("actGrp") String actGrp) {
      //TODO: If validtion like toUpperCase().trim() is needed, add it when passing the params to keep this clean
      this.targetLibrary = targetLibrary;
      this.objectName = objectName;
      this.objectType = objectType;
      this.sourceLibrary = sourceLibrary;
      this.sourceFile = sourceFile;
      this.sourceMember = sourceMember;
      this.sourceType = sourceType;
      this.text = text;
      this.actGrp = actGrp; //TODO: Remove this, maybe add it to another struct with the compilation command params
    }

    // Getters for Jackson serialization
    public String getTargetLibrary() { return targetLibrary; }
    public String getObjectName() { return objectName; }
    public ObjectType getObjectType() { return objectType; }
    public String getSourceLibrary() { return sourceLibrary; }
    public String getSourceFile() { return sourceFile; }
    public String getSourceMember() { return sourceMember; }
    public SourceType getSourceType() { return sourceType; }
    public String getText() { return text; }
    public String getActGrp() { return actGrp; }

    // TODO: This logic encapsulation is nice. It will be helpfull in the future
    // Key method for use in graphs (matches ObjectDependency format)
    public String toGraphKey() {
      return targetLibrary + "/" + objectName + "/" + objectType.name();
    }

  }

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
  private String sourceFile = "";

  @Option(names = { "-sn", "--source-name" }, description = "Source member name (defaults to object name, command-specific *SPECIAL, or retrieved from object)")
  private String sourceName = "";

  @Option(names = {"-st","--source-type"}, description = "Source type (e.g., RPGLE, CLLE) (defaults to retrieved from object if possible)", converter = SourceTypeConverter.class)
  private SourceType sourceType;

  @Option(names = { "--text" }, description = "Object text description (defaults to retrieved from object if possible)")
  private String text = "";

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

  // Resolver map for command builders (functions that build command strings based on spec)
  private final Map<CompCmd, Function<CompilationSpec, String>> cmdBuilders = new EnumMap<>(CompCmd.class);


  static {
        
    /* From source member type to default source file default name */
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
    /* Maps sources and object type to compilation command */
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

    //TODO: Make these strings
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

    initResolvers();
  }

  private void initResolvers() {

    // TODO: Move this to the constructor or the iObject class?
    // Command builders as functions (pattern matching via enums)
    cmdBuilders.put(CompCmd.CRTRPGMOD, this::buildModuleCmd);
    cmdBuilders.put(CompCmd.CRTCLMOD, this::buildModuleCmd);
    cmdBuilders.put(CompCmd.CRTBNDRPG, this::buildBoundCmd);
    cmdBuilders.put(CompCmd.CRTBNDCL, this::buildBoundCmd);
    cmdBuilders.put(CompCmd.CRTRPGPGM, this::buildBoundCmd);
    cmdBuilders.put(CompCmd.CRTCLPGM, this::buildBoundCmd);
    cmdBuilders.put(CompCmd.CRTSQLRPGI, this::buildSqlRpgCmd);
    cmdBuilders.put(CompCmd.CRTSRVPGM, this::buildSrvPgmCmd);
    cmdBuilders.put(CompCmd.RUNSQLSTM, this::buildSqlCmd);
    // Add more builders for other commands
  }

  public void run() {
    CompilationSpec spec = buildSpecFromCli();

    // Retrieve and fill in defaults from existing object if possible
    Map<String, Object> objInfo = null;
    try {
      objInfo = retrieveObjectInfo(spec);
      fillSpecFromObjInfo(spec, objInfo);
    } catch (Exception e) {
      if (verbose) System.err.println("Warning: Could not retrieve compilation params from object: " + e.getMessage() + ". Using defaults.");
    }

    if (spec.getSourceType() == null) {
      System.err.println("Source type is required if not retrievable from object.");
      return;
    }
    if (debug) System.err.println("Source type: " + spec.getSourceType());

    // TODO: This could be encapsulated on the Iobject class
    CompCmd mainCmd = typeToCmdMap.get(spec.getSourceType()).get(spec.getObjectType());
    if (mainCmd == null) {
      System.err.println("No compilation command for source type " + spec.getSourceType() + " and object type " + spec.getObjectType());
      return;
    }
    if (debug) System.out.println("Compilation command: " + mainCmd.name());

    String commandStr = buildCommand(spec, mainCmd);
    
    if (debug) System.out.println("Full command: " + commandStr);

    // TODO: Integrate with SourceMigrator if source is in member; migrate to IFS and compile from there
    // For OPM, create temp member if needed

    compile(commandStr);
  }

  private CompilationSpec buildSpecFromCli() {
    // TODO: If any validation of the specs is needed, do it here.
    return new CompilationSpec(
          library,
          objectName,
          objectType,
          sourceLib, // Default to *LIBL
          (sourceFile.isEmpty()) ? typeToDftSrc.get(sourceType).name() : sourceFile,
          (sourceName.isEmpty() ? objectName : sourceName),
          sourceType, // Specified or inferred
          text,
          actGrp
    );
  }

  private void fillSpecFromObjInfo(CompilationSpec spec, Map<String, Object> objInfo) {
    if (objInfo == null) return;

    if (debug) System.out.println("Found object info");

    if (spec.getSourceType() == null) {
      String attr = (String) objInfo.get("attribute");
      if (debug) System.out.println("attr: " + attr);
      if (attr != null && !attr.trim().isEmpty()) {
        spec.sourceType = attrToSourceType.get(attr.trim().toUpperCase());
        if (spec.getSourceType() == null) {
          throw new IllegalArgumentException("Could not infer source type from object attribute '" + attr + "'. Source type is required.");
        } 
      }
    }

    String retrievedLib = (String) objInfo.get("sourceLibrary");
    if (debug) System.out.println("retrievedLib: " + retrievedLib);
    if (retrievedLib != null && !retrievedLib.trim().isEmpty()) {
      spec.sourceLibrary = retrievedLib.trim().toUpperCase();
    }

    String retrievedFile = (String) objInfo.get("sourceFile");
    if (debug) System.out.println("retrievedFile: " + retrievedFile);
    if (retrievedFile != null && !retrievedFile.trim().isEmpty()) {
      spec.sourceFile = retrievedFile.trim().toUpperCase();
    } 

    String retrievedMbr = (String) objInfo.get("sourceMember");
    if (debug) System.out.println("retrievedMbr: " + retrievedMbr);
    if (retrievedMbr != null && !retrievedMbr.trim().isEmpty()) {
      spec.sourceMember = retrievedMbr.trim().toUpperCase();
    }

    String retrievedText = (String) objInfo.get("textDescription");
    if (debug) System.out.println("retrievedText: " + retrievedText);
    if (retrievedText != null && !retrievedText.trim().isEmpty()) {
      spec.text = retrievedText.trim();
    }

    if (spec.getActGrp() == null && objInfo.containsKey("activationGroupAttribute")) {
      String retrievedActGrp = ((String) objInfo.get("activationGroupAttribute")).trim();
      if (debug) System.out.println("retrievedActGrp: " + retrievedActGrp);
      if (!retrievedActGrp.isEmpty()) {
        spec.actGrp = retrievedActGrp;
      }
    }
      
      // TODO: Add more params like --usrprf, --useadpaut, etc., and map from objInfo
    // Similarly fill other fields (sourceLib, sourceFile, etc.)
    // To make spec mutable or use a builder for this.
  }

  private Map<String, Object> retrieveObjectInfo(CompilationSpec spec) throws Exception {
    String apiPgm;
    String format = "PGMI0100"; // Default for PGM
    if (spec.getObjectType() == ObjectType.PGM) {
      apiPgm = "QCLRPGMI";
    } else if (spec.getObjectType() == ObjectType.SRVPGM) {
      apiPgm = "QBNRSPGM";
      format = "SPGI0100";
    } else if (spec.getObjectType() == ObjectType.MODULE) {
      apiPgm = "QBNRMODI";
      format = "MODI0100";
    } else {
      throw new Exception("Object type " + spec.getObjectType() + " not supported for retrieving compilation params.");
    }

    ProgramCall pc = new ProgramCall(system);
    pc.setProgram("/QSYS.LIB/" + apiPgm + ".PGM");

    int recvLen = 2048; // Sufficient for PGMI0100/SPGI0100/MODI0100
    ProgramParameter[] parms = new ProgramParameter[5];
    parms[0] = new ProgramParameter(recvLen); // Receiver
    parms[1] = new ProgramParameter(new AS400Bin4().toBytes(recvLen)); // Length of receiver
    parms[2] = new ProgramParameter(new AS400Text(8, system).toBytes(format)); // Format
    String qualName = String.format("%-10s%-10s", spec.getObjectName(), spec.getTargetLibrary());
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
    offset = 368; // Jump to activation group (adjust if needed for SPGI/MODI differences)
    if (spec.getObjectType() != ObjectType.MODULE) { // Modules don't have ACTGRP
      info.put("activationGroupAttribute", text30.toObject(data, offset).toString().trim());
    }
    // TODO: Parse additional fields as needed

    return info;
  }

  private String buildCommand(CompilationSpec spec, CompCmd cmd) {
    Function<CompilationSpec, String> builder = cmdBuilders.getOrDefault(cmd, s -> {
      throw new IllegalArgumentException("Unsupported command: " + cmd);
    });
    String params = builder.apply(spec);
    // Prepend the command name
    return cmd.name() + params;
  }

  // Example builder function for module commands
  private String buildModuleCmd(CompilationSpec spec) {
    StringBuilder sb = new StringBuilder();
    sb.append(" MODULE(").append(spec.getTargetLibrary()).append("/").append(spec.getObjectName()).append(")");
    sb.append(" SRCFILE(").append(spec.getSourceLibrary()).append("/").append(spec.getSourceFile()).append(")");
    sb.append(" SRCMBR(").append(getSourceMember(spec, CompCmd.CRTRPGMOD)).append(")");
    appendCommonParams(sb, spec);
    return sb.toString();
  }

  // Similar for bound commands
  private String buildBoundCmd(CompilationSpec spec) {
    StringBuilder sb = new StringBuilder();
    sb.append(" PGM(").append(spec.getTargetLibrary()).append("/").append(spec.getObjectName()).append(")");
    sb.append(" SRCFILE(").append(spec.getSourceLibrary()).append("/").append(spec.getSourceFile()).append(")");
    sb.append(" SRCMBR(").append(getSourceMember(spec, CompCmd.CRTBNDRPG)).append(")");
    appendCommonParams(sb, spec);
    return sb.toString();
  }

  // For CRTSQLRPGI
  private String buildSqlRpgCmd(CompilationSpec spec) {
    StringBuilder sb = new StringBuilder();
    sb.append(" OBJ(").append(spec.getTargetLibrary()).append("/").append(spec.getObjectName()).append(")");
    sb.append(" OBJTYPE(*").append(spec.getObjectType().name()).append(")");
    sb.append(" SRCFILE(").append(spec.getSourceLibrary()).append("/").append(spec.getSourceFile()).append(")");
    sb.append(" SRCMBR(").append(getSourceMember(spec, CompCmd.CRTSQLRPGI)).append(")");
    appendCommonParams(sb, spec);
    return sb.toString();
  }

  // For CRTSRVPGM
  private String buildSrvPgmCmd(CompilationSpec spec) {
    StringBuilder sb = new StringBuilder();
    sb.append(" SRVPGM(").append(spec.getTargetLibrary()).append("/").append(spec.getObjectName()).append(")");
    sb.append(" MODULE(").append(spec.getTargetLibrary()).append("/").append(spec.getObjectName()).append(")"); // Assume single module
    sb.append(" BNDSRVPGM(*NONE)");
    appendCommonParams(sb, spec);
    return sb.toString();
  }

  // For RUNSQLSTM
  private String buildSqlCmd(CompilationSpec spec) {
    StringBuilder sb = new StringBuilder();
    sb.append(" SRCFILE(").append(spec.getSourceLibrary()).append("/").append(spec.getSourceFile()).append(")");
    sb.append(" SRCMBR(").append(getSourceMember(spec, CompCmd.RUNSQLSTM)).append(")");
    sb.append(" COMMIT(*NONE)");
    appendCommonParams(sb, spec);
    return sb.toString();
  }

  private void appendCommonParams(StringBuilder sb, CompilationSpec spec) {
    if (spec.getText() != null && !spec.getText().isEmpty()) {
      sb.append(" TEXT('").append(spec.getText()).append("')");
    }
    // if (spec.getActGrp() != null) && !spec.getActGrp().isEmpty(){
    //   sb.append(" ACTGRP(").append(spec.getActGrp()).append(")");
    // }
    // Add more common params (e.g., DFTACTGRP(*NO), BNDDIR, etc.)
  }



  private String getSourceFile() {
    String file = (sourceFile != null) ? sourceFile : typeToDftSrc.getOrDefault(sourceType, DftSrc.QRPGLESRC).name();
    //TODO: Validate if it should use library when library = null o *LIBL
    return sourceLib + "/" + file;
  }

  private String getSourceMember(CompilationSpec spec, CompCmd cmd) {
    if (spec.sourceMember != null && !spec.sourceMember.isEmpty()) {
      return spec.sourceMember;
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
        return spec.objectName; // Fallback for SQL, etc.
    }
  }

  private void compile(String commandStr) {
    CommandCall cc = new CommandCall(system);
    try {
      if (debug) System.out.println("Executing: " + commandStr);
      Timestamp compilationTime = null;
      try (Statement stmt = connection.createStatement();
          ResultSet rsTime = stmt.executeQuery("SELECT CURRENT_TIMESTAMP AS Compilation_Time FROM sysibm.sysdummy1")) {
        if (rsTime.next()) {
          compilationTime = rsTime.getTimestamp("Compilation_Time");
        }
      }
      boolean success = cc.run(commandStr);
      AS400Message[] messages = cc.getMessageList();
      if (success) {
        System.out.println("Compilation successful.");
      } else {
        System.out.println("Compilation failed.");
        showCompilationSpool(compilationTime, system.getUserId().trim().toUpperCase(), objectName);
      }
      for (AS400Message msg : messages) {
        System.out.println(msg.getID() + ": " + msg.getText());
      }
    } catch (AS400SecurityException | ErrorCompletingRequestException | IOException | InterruptedException | PropertyVetoException | SQLException e) {
      e.printStackTrace();
    }
    cleanup();
    }

  //TODO: This is kinda slow.
  /*  https://gist.github.com/BirgittaHauser/f28e3527f1cc4c422a05eea865b455bb */
  private void showCompilationSpool(Timestamp compilationTime, String user, String objectName) throws SQLException{

    System.out.println("Compiler error messages: \n");

    try(Statement stmt = connection.createStatement();
      ResultSet rsCompilationSpool = stmt.executeQuery(
        "With " +
        "Spool as ( " +
          "Select b.ordinal_position, Spooled_Data " + 
          "from  qsys2.OutPut_Queue_Entries a Cross Join " +
              "Lateral(Select * " +
                        "From Table(SysTools.Spooled_File_Data( " +
                                                "Job_Name            => a.Job_Name, " +
                                                "Spooled_File_Name   => a.Spooled_File_Name, " +
                                                "Spooled_File_Number => File_Number))) b " +
          "Where     Output_Queue_Name = '" + user + "' " +
                "and USER_NAME = '" + user + "' " + 
                "and SPOOLED_FILE_NAME = '" + objectName + "' " +
                "and OUTPUT_QUEUE_LIBRARY_NAME = 'QGPL' " +
                "and CREATE_TIMESTAMP > '" + compilationTime + "' " +
        "), " +
        "Message As ( " +
          "Select ordinal_position From Spool Where Spooled_Data like '%M e s s a g e   S u m m a r y%' " +
        ") " +
        "Select RTrim(Cast(Spooled_Data As Varchar(132) CCSID " + INVARIANT_CCSID +" )) As  Spooled_Data " + 
        "from Spool Where ordinal_position >= (Select ordinal_position From Message) "
      )){
        while (rsCompilationSpool.next()) {
          System.out.println(rsCompilationSpool.getString("Spooled_Data"));
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
