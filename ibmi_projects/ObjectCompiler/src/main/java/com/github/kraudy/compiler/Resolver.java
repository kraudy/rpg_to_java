package com.github.kraudy.compiler;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;

import com.github.kraudy.compiler.ObjectCompiler;
import com.github.kraudy.compiler.ObjectCompiler.ObjectType;
import com.github.kraudy.compiler.ObjectCompiler.SourceType;
import com.github.kraudy.compiler.ObjectCompiler.ParamCmd;

public class Resolver {
  private final Map<ParamCmd, Supplier<String>> valueSuppliers = new EnumMap<>(ParamCmd.class);
  private final String library;
  private final String objectName;
  private final String sourceFile;
  private final String sourceName;
  private final ObjectType objectType;
  private final SourceType sourceType;

  public Resolver(String library, String objectName, String sourceFile, String sourceName, ObjectType objectType, SourceType sourceType) {
    this.library = library;
    this.objectName = objectName;
    this.sourceFile = sourceFile;
    this.sourceName = sourceName;
    this.objectType = objectType;
    this.sourceType = sourceType;
    initSuppliers();
  }

  private void initSuppliers() {
    // Define suppliers here, using instance fields for dynamic values
    //TODO: Generate Only necessary Suppliers based on ParamCmd.PGM.
    valueSuppliers.put(ParamCmd.OUTPUT, () -> "*" + ObjectCompiler.valueParamsMap.get(ParamCmd.OUTPUT).get(0));
    valueSuppliers.put(ParamCmd.OUTMBR, () -> "*" + ObjectCompiler.valueParamsMap.get(ParamCmd.OUTMBR).get(0));
    
    // For PGM, use the provided library and objectName (can validate if library is empty, etc.)
    valueSuppliers.put(ParamCmd.PGM, () -> (library == null ? "*" + ObjectCompiler.valueParamsMap.get(ParamCmd.PGM).get(0) : library) + "/" + objectName);

    //TODO: Add "QRPGLESRC" to its own enum with key being type: RPG, RPGLE, etc
    valueSuppliers.put(ParamCmd.SRCFILE, () -> (sourceFile == null ? ObjectCompiler.typeToDftSrc.get(sourceType).name() : sourceFile) + "/" + (sourceName == null ? objectName : sourceName));
    //valueSuppliers.put(ParamCmd.SRCFILE, () -> ParamCmd.SRCFILE.name() + (sourceFile == null ? "QRPGLESRC" : sourceFile) + "/" + objectName);
    //valueSuppliers.put(ParamCmd.PGM, () -> ParamCmd.PGM.name() + " (*" + ObjectCompiler.valueParamsMap.get(ParamCmd.PGM).get(0) + "/" + objectName + ")");

    // Similarly for other common params
    // valueSuppliers.put(ParamCmd.MODULE, () -> library + "/" + objectName);
    // valueSuppliers.put(ParamCmd.OBJ, () -> library + "/" + objectName);
    // valueSuppliers.put(ParamCmd.OBJTYPE, () -> "*" + objectType.name());


    // TODO: Add suppliers for other ParamCmd as needed, e.g., BNDSRVPGM could default to "*NONE" or require input
    // valueSuppliers.put(ParamCmd.BNDSRVPGM, () -> "*NONE");

    // TODO: Additional validation logic can be added here, e.g., check if required params have valid values
  }

  public String resolve(ParamCmd param) {
    return param.name() + " (" + valueSuppliers.get(param).get() + ")";
  }
}
