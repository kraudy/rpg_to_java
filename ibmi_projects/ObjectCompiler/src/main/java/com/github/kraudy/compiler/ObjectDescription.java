package com.github.kraudy.compiler;

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.kraudy.compiler.CompilationPattern.ParamCmd;
import com.ibm.as400.access.AS400;
import com.ibm.as400.access.AS400Bin4;
import com.ibm.as400.access.AS400Message;
import com.ibm.as400.access.AS400Text;
import com.ibm.as400.access.ProgramCall;
import com.ibm.as400.access.ProgramParameter;
import com.ibm.as400.access.UserSpace;

// Core struct for capturing compilation specs (JSON-friendly via Jackson)
public class ObjectDescription {
  private final AS400 system;
  private final boolean debug;
  //TODO: Make this private, add set method and move to another file
  public String targetLibrary;
  public String objectName;
  public ObjectType objectType;
  public String sourceLibrary;
  public String sourceFile;
  public String sourceName;
  public SourceType sourceType;

  public Map<CompilationPattern.ParamCmd, String> ParamCmdSequence = new HashMap<>();
  public Map<String, Object> objInfo = new HashMap<>();

  public String text;
  public String actGrp;//TODO: Remove this

  public enum SysCmd { CHGLIBL, DSPPGMREF, DSPOBJD, DSPDBR }

  public enum SourceType { 
    RPG, RPGLE, SQLRPGLE, CLP, CLLE, SQL;

    public static SourceType fromString(String value) {
      try {
        return SourceType.valueOf(value);
      } catch (IllegalArgumentException e) {
        throw new IllegalArgumentException("Could not get source type from object attribute '" + value + "'");
      }
    } 

    public static String defaultSourcePf (SourceType sourceType){
      switch (sourceType){
        case RPG:
          return DftSrc.QRPGSRC.name(); //TODO: Add .name()? and return string?
        case RPGLE:
        case SQLRPGLE:
          return DftSrc.QRPGLESRC.name();
        case CLP:
        case CLLE:
          return DftSrc.QCLSRC.name();
        case SQL:
          return DftSrc.QSQLSRC.name();
        default:
          throw new IllegalArgumentException("Could not get default sourcePf for '" + sourceType + "'");
      }
    }
  }

  public enum ObjectType { PGM, SRVPGM, MODULE, TABLE, LF, VIEW, ALIAS, PROCEDURE, FUNCTION;
    public static String toParam(ObjectType objectType){
      return "*" + objectType.name();
    }
  } // Add more as needed

  public enum PostCmpCmd { CHGOBJD }

  public enum DftSrc { QRPGLESRC, QRPGSRC, QCLSRC, QSQLSRC } // TODO: Expand

  //TODO: Move this class to its own file and remove static
  // Constructor for Jackson deserialization
  @JsonCreator
  public ObjectDescription(
        AS400 system,
        boolean debug,
        @JsonProperty("targetLibrary") String targetLibrary,
        @JsonProperty("objectName") String objectName,
        @JsonProperty("objectType") ObjectType objectType,
        @JsonProperty("sourceLibrary") String sourceLibrary,
        @JsonProperty("sourceFile") String sourceFile,
        @JsonProperty("sourceName") String sourceName,
        @JsonProperty("sourceType") SourceType sourceType,
        //@JsonProperty("text") String text,
        //@JsonProperty("actGrp") String actGrp ,//) {//,
        @JsonProperty("ParamCmdSequence") Map<CompilationPattern.ParamCmd, String> ParamCmdSequence) {
    // @JsonProperty("ParamCmdSequence") Map<CompilationPattern.ParamCmd, String> ParamCmdSequence
    // String json = objectDescription.writeValueAsString(example);
    //TODO: If validtion like toUpperCase().trim() is needed, add it when passing the params to keep this clean

    this.system = system;
    this.debug = debug;

    if (objectName == null || objectName.isEmpty()) throw new IllegalArgumentException("Object name is required.");

    this.targetLibrary = targetLibrary;
    this.objectName = objectName;
    this.objectType = objectType;
    this.sourceLibrary = sourceLibrary;
    this.sourceFile = (sourceFile.isEmpty()) ? SourceType.defaultSourcePf(sourceType) : sourceFile; // TODO: Add logic for sourcePF or directory
    this.sourceName = (sourceName.isEmpty() ? objectName : sourceName); //TODO: Add logic for stream files / members / default
    this.sourceType = sourceType;

    //TODO: Maybe this should be done in the object compiler.
    /* Add compilation params values */
    //this.ParamCmdSequence.put(ParamCmd.TEXT, (text.isEmpty()) ? "" : text);
    //this.ParamCmdSequence.put(ParamCmd.ACTGRP, (actGrp.isEmpty()) ? "" : actGrp);
    this.ParamCmdSequence = ParamCmdSequence;

  }

  // Getters for Jackson serialization
  public String getTargetLibrary() { return targetLibrary; }
  public String getObjectName() { return objectName; }
  public ObjectType getObjectType() { return objectType; }
  public String getSourceLibrary() { return sourceLibrary; }
  public String getSourceFile() { return sourceFile; }
  public String getSourceName() { return sourceName; }
  public SourceType getSourceType() { return sourceType; }
  public String getText() { return text; }
  public String getActGrp() { return actGrp; }
  public Map<CompilationPattern.ParamCmd, String> getParamCmdSequence() { return ParamCmdSequence; }

  // TODO: This logic encapsulation is nice. It will be helpfull in the future
  // Key method for use in graphs (matches ObjectDependency format)
  public String toGraphKey() {
    return targetLibrary + "/" + objectName + "/" + objectType.name();
  }

  public boolean isPGM(){
    return (this.objectType == ObjectType.PGM) ? true: false;
  }

  public boolean isSRVPGM(){
    return (this.objectType == ObjectType.SRVPGM) ? true: false;
  }

  public boolean isMODULE(){
    return (this.objectType == ObjectType.MODULE) ? true: false;
  }

  public void retrieveObjectInfo() throws Exception {
    String apiPgm;
    String format;
    switch (this.objectType) {
      //TODO: Add these to maps
      case PGM:
        /* QBNRPGMI => Gives error: "Could not retrieve compilation params from object: /QSYS.LIB/QBNRPGMI.PGM: Object does not exist"
        if (sourceType == SourceType.RPG || sourceType == SourceType.RPGLE || sourceType == SourceType.SQLRPGLE) {
          apiPgm = "QBNRPGMI";
        } else if (sourceType == SourceType.CLP || sourceType == SourceType.CLLE) {
          apiPgm = "QCLRPGMI";
        } else {
          throw new Exception("Unsupported source type " + (sourceType != null ? sourceType.name() : "null") + " for retrieving compilation params.");
        }
        */
        apiPgm = "QCLRPGMI";
        format = "PGMI0100";
        break;
      case SRVPGM:
        apiPgm = "QBNRSPGM";
        format = "SPGI0100";
        break;
      case MODULE:
        apiPgm = "QBNRMODI";
        format = "MODI0100";
        break;
      default:
        throw new Exception("Object type " + this.objectType.name() + " not supported for retrieving compilation params.");
    }

    ProgramCall pc = new ProgramCall(system);
    pc.setProgram("/QSYS.LIB/" + apiPgm + ".PGM");

    int recvLen = 2048; // Sufficient for PGMI0100/SPGI0100/MODI0100
    ProgramParameter[] parms = new ProgramParameter[5];
    parms[0] = new ProgramParameter(recvLen); // Receiver
    parms[1] = new ProgramParameter(new AS400Bin4().toBytes(recvLen)); // Length of receiver
    parms[2] = new ProgramParameter(new AS400Text(8, system).toBytes(format)); // Format
    String qualName = String.format("%-10s%-10s", this.objectName, this.targetLibrary);
    parms[3] = new ProgramParameter(new AS400Text(20, this.system).toBytes(qualName)); // Qualified object name
    byte[] errorCode = new byte[16];
    new AS400Bin4().toBytes(0, errorCode, 0); // Bytes provided = 0 to throw exceptions
    parms[4] = new ProgramParameter(errorCode); // Error code

    pc.setParameterList(parms);

    if (!pc.run()) {
      AS400Message[] msgs = pc.getMessageList();
      throw new Exception("API call failed: " + (msgs.length > 0 ? msgs[0].getText() : "Unknown error"));
    }

    byte[] data = parms[0].getOutputData();
    //Map<String, Object> info = new HashMap<>();

    AS400Bin4 bin4 = new AS400Bin4();
    AS400Text text10 = new AS400Text(10, system);
    AS400Text text13 = new AS400Text(13, system);
    AS400Text text1 = new AS400Text(1, system);
    AS400Text text50 = new AS400Text(50, system);
    AS400Text text30 = new AS400Text(30, system);

    int offset = 0;
    this.objInfo.put("bytesReturned", bin4.toInt(data, offset)); offset += 4;
    this.objInfo.put("bytesAvailable", bin4.toInt(data, offset)); offset += 4;
    this.objInfo.put("programName", text10.toObject(data, offset).toString().trim()); offset += 10;
    this.objInfo.put("programLibrary", text10.toObject(data, offset).toString().trim()); offset += 10;
    this.objInfo.put("owner", text10.toObject(data, offset).toString().trim()); offset += 10;
    this.objInfo.put("attribute", text10.toObject(data, offset).toString().trim()); offset += 10;
    this.objInfo.put("creationDateTime", text13.toObject(data, offset).toString().trim()); offset += 13;
    this.objInfo.put("sourceFile", text10.toObject(data, offset).toString().trim()); offset += 10;
    this.objInfo.put("sourceLibrary", text10.toObject(data, offset).toString().trim()); offset += 10;
    this.objInfo.put("sourceName", text10.toObject(data, offset).toString().trim()); offset += 10;
    this.objInfo.put("sourceUpdatedDateTime", text13.toObject(data, offset).toString().trim()); offset += 13;
    this.objInfo.put("observable", text1.toObject(data, offset).toString().trim()); offset += 1;
    this.objInfo.put("userProfileOption", text1.toObject(data, offset).toString().trim()); offset += 1;
    this.objInfo.put("useAdoptedAuthority", text1.toObject(data, offset).toString().trim()); offset += 1;
    this.objInfo.put("logCommands", text1.toObject(data, offset).toString().trim()); offset += 1;
    this.objInfo.put("allowRTVCLSRC", text1.toObject(data, offset).toString().trim()); offset += 1;
    this.objInfo.put("fixDecimalData", text1.toObject(data, offset).toString().trim()); offset += 1;


    this.objInfo.put("textDescription", text50.toObject(data, offset).toString().trim()); offset += 50;
    this.objInfo.put("typeOfProgram", text1.toObject(data, offset).toString().trim()); offset += 1;
    this.objInfo.put("teraspaceEnabled", text1.toObject(data, offset).toString().trim()); offset += 1;
    offset += 58; // Reserved
    this.objInfo.put("minParameters", bin4.toInt(data, offset)); offset += 4;
    this.objInfo.put("maxParameters", bin4.toInt(data, offset)); offset += 4;
    offset = 368; // Jump to activation group (adjust if needed for SPGI/MODI differences)
    if (this.objectType != ObjectDescription.ObjectType.MODULE) { // Modules don't have ACTGRP
      this.objInfo.put("activationGroupAttribute", text30.toObject(data, offset).toString().trim());
    }
    // TODO: Parse additional fields as needed

    // If this is an ILE program ('B'), retrieve module information for source details
    String typeOfProgram = (String) this.objInfo.get("typeOfProgram");
    System.out.println("typeOfProgram: " + typeOfProgram);

    if ("B".equals(typeOfProgram) && this.objectType == ObjectType.PGM) {
      System.out.println("Doing retrieveModuleInfo");
      retrieveModuleInfo(qualName);
    }

    System.out.println("All data: ");
    for (String ob : this.objInfo.keySet()){
      System.out.println(ob + ": " + this.objInfo.get(ob));
    }

  }

  //TODO:  handle/select from multiple entries and choose the main module
  private void retrieveModuleInfo(String qualName) throws Exception {
    // Import required: import com.ibm.as400.access.UserSpace;
    String usName = "PGMLIST";
    String usLib = "QTEMP";
    UserSpace us = new UserSpace(system, "/QSYS.LIB/" + usLib + ".LIB/" + usName + ".USRSPC");
    
    int initialSize = 2048 * 10; // Sufficient buffer, adjust if needed for multi-module
    //us.create(initialSize, true, "", (byte) 0x00, "Temp user space for QBNLPGMI", "*USE");
    us.create(initialSize, true, " ", (byte) 0x00, "Temp user space for QBNLPGMI", "*USE");  // Added space for extended attribute to enable native read support

    String qualUs = String.format("%-10s%-10s", usName, usLib);

    ProgramCall pc = new ProgramCall(system);
    pc.setProgram("/QSYS.LIB/QBNLPGMI.PGM");

    byte[] errorCode = new byte[32];
    new AS400Bin4().toBytes(0, errorCode, 0);

    System.out.println("Inside retrieveModuleInfo");

    ProgramParameter[] parms = new ProgramParameter[4];
    parms[0] = new ProgramParameter(new AS400Text(20, system).toBytes(qualUs)); // Qualified user space name
    parms[1] = new ProgramParameter(new AS400Text(8, system).toBytes("PGML0100")); // Format
    parms[2] = new ProgramParameter(new AS400Text(20, system).toBytes(qualName)); // Qualified program name
    parms[3] = new ProgramParameter(errorCode); // Error code

    pc.setParameterList(parms);

    System.out.println("Before pc.run()");

    if (!pc.run()) {
      AS400Message[] msgs = pc.getMessageList();
      throw new Exception("QBNLPGMI API call failed: " + (msgs.length > 0 ? msgs[0].getText() : "Unknown error"));
    }

    System.out.println("After pc.run()");

    // Read the entire user space
    byte[] data = new byte[initialSize];
    us.read(data, 0);

    System.out.println("us.read()");

    AS400Bin4 bin4 = new AS400Bin4();
    int listOffset = bin4.toInt(data, 122); // Offset to list data section
    int numEntries = bin4.toInt(data, 126); // Number of list entries
    int entrySize = bin4.toInt(data, 130); // Size of each entry

    System.out.println("entrySize: " + entrySize);

    if (numEntries > 0) {
      // For simplicity, take the first module (common for single-module bound programs)
      int entryOffset = listOffset;
      AS400Text text10 = new AS400Text(10, system);

      // Update objInfo with module source info
      this.objInfo.put("sourceFile", text10.toObject(data, entryOffset + 56).toString().trim());
      this.objInfo.put("sourceLibrary", text10.toObject(data, entryOffset + 66).toString().trim());
      this.objInfo.put("sourceName", text10.toObject(data, entryOffset + 76).toString().trim());

      // TODO: If multi-module, you may need logic to select the appropriate one or aggregate
    } else {
      throw new Exception("No modules found in ILE program.");
    }

    // Optional: Delete the user space after use
    us.delete();    
  }




  public void fillSpecFromObjInfo() {
    if (this.objInfo == null) return;

    if (debug) System.out.println("Found object info");

    /* Check for filed with default value to subsitute */
    if (this.sourceType == null) {
      String attr = (String) objInfo.get("attribute");
      if (debug) System.out.println("attr: " + attr);
      if (attr != null && !attr.trim().isEmpty()) {
        this.sourceType = SourceType.fromString(attr.trim().toUpperCase());
      }
    }

    String retrievedLib = (String) objInfo.get("sourceLibrary");
    if (debug) System.out.println("retrievedLib: " + retrievedLib);
    if (retrievedLib != null && !retrievedLib.trim().isEmpty()) {
      this.sourceLibrary = retrievedLib.trim().toUpperCase();
    }

    String retrievedFile = (String) objInfo.get("sourceFile");
    if (debug) System.out.println("retrievedFile: " + retrievedFile);
    if (retrievedFile != null && !retrievedFile.trim().isEmpty()) {
      this.sourceFile = retrievedFile.trim().toUpperCase();
    } 

    String retrievedMbr = (String) objInfo.get("sourceName");
    if (debug) System.out.println("retrievedMbr: " + retrievedMbr);
    if (retrievedMbr != null && !retrievedMbr.trim().isEmpty()) {
      this.sourceName = retrievedMbr.trim().toUpperCase();
    }

    String retrievedText = (String) objInfo.get("textDescription");
    if (debug) System.out.println("retrievedText: " + retrievedText);
    if (retrievedText != null && !retrievedText.trim().isEmpty()) {
      this.ParamCmdSequence.put(ParamCmd.TEXT, retrievedText.trim());
    }

    if (this.actGrp == null && objInfo.containsKey("activationGroupAttribute")) {
      String retrievedActGrp = ((String) objInfo.get("activationGroupAttribute")).trim();
      if (debug) System.out.println("retrievedActGrp: " + retrievedActGrp);
      if (!retrievedActGrp.isEmpty()) {
        this.ParamCmdSequence.put(ParamCmd.ACTGRP, retrievedActGrp);
      }
    }
      
      // TODO: Add more params like --usrprf, --useadpaut, etc., and map from objInfo
    // Similarly fill other fields (sourceLib, sourceFile, etc.)
    // To make odes mutable or use a builder for this.
  }

}