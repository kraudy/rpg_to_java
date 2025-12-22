package com.github.kraudy.compiler;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.github.kraudy.compiler.CompilationPattern.CompCmd;
import com.github.kraudy.compiler.CompilationPattern.ParamCmd;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;

public class ParamMapTest {
  @Test
  void testPutAndGetSingleParam() {
      ParamMap map = new ParamMap();
      map.put(CompCmd.CRTRPGMOD, ParamCmd.TEXT, "Test Module");

      assertEquals("''Test Module''", map.get(ParamCmd.TEXT));
  }

  @Test
  void testPutAllWithValidation() {
    ParamMap map = new ParamMap();
    Map<ParamCmd, String> params = Map.of(
      ParamCmd.TEXT, "Test",
      ParamCmd.OPTIMIZE, "40",  // Valid for CRTRPGMOD
      ParamCmd.CMD, "invalid"
    );
    map.putAll(CompCmd.CRTRPGMOD, params);

    // Invalid param (e.g., for CRTRPGMOD) should be rejected silently in putAll
    assertEquals("", map.get(ParamCmd.CMD)); // Not added
  }

  @Test
  void testCommandStringGeneration() {
    ParamMap map = new ParamMap();
    map.put(CompCmd.CRTRPGMOD, ParamCmd.MODULE, "MYLIB/HELLO");
    map.put(CompCmd.CRTRPGMOD, ParamCmd.TEXT, "Test");

    String cmd = map.getCommandString(CompCmd.CRTRPGMOD);
    assertEquals("CRTRPGMOD MODULE(MYLIB/HELLO) TEXT(''Test'')", cmd);
  }

  @Test
  void testResolveConflicts() {
    ParamMap map = new ParamMap();
    map.put(CompCmd.CRTSRVPGM, ParamCmd.SRCSTMF, "/ifs/source.bnd");
    map.put(CompCmd.CRTSRVPGM, ParamCmd.EXPORT, "ALL");

    String cmd = map.getCommandString(CompCmd.CRTSRVPGM);

    //map.ResolveConflicts(CompCmd.CRTSRVPGM);
    assertEquals(null, map.get(ParamCmd.EXPORT)); // Removed due to conflict with SRCSTMF
    assertEquals("CRTSRVPGM SRCSTMF(''/ifs/source.bnd'')", cmd);
  }

}
