package com.github.kraudy.compiler;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.github.kraudy.compiler.CompilationPattern.ParamCmd;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.Timestamp;

public class TargetKeyTest {
  @Test
  void testValidKeyParsing() {
    TargetKey key = new TargetKey("MYLIB.HELLO.PGM.RPGLE");

    assertEquals("MYLIB/HELLO", key.getQualifiedObject());
    assertEquals("HELLO", key.getObjectName());
    assertEquals("*PGM", key.getObjectType());
    assertEquals("QRPGLESRC", key.getSourceFile()); // Default from SourceType
    assertEquals("HELLO", key.getSourceName()); // Defaults to object name
    assertEquals("CRTBNDRPG", key.getCompilationCommand().name()); // From pattern
    assertTrue(key.needsRebuild()); // No timestamps yet
  }

  @Test
  void testInvalidKeyThrowsException() {
    assertThrows(IllegalArgumentException.class, () -> new TargetKey("INVALID")); // Wrong parts
    assertThrows(IllegalArgumentException.class, () -> new TargetKey("MYLIB..PGM.RPGLE")); // Empty object
    assertThrows(IllegalArgumentException.class, () -> new TargetKey("MYLIB.HELLO.INVALID.RPGLE")); // Bad object type
    assertThrows(IllegalArgumentException.class, () -> new TargetKey("MYLIB.HELLO.PGM.INVALID")); // Bad source type
  }

  @Test
  void testNeedsRebuildLogic() {
    TargetKey key = new TargetKey("MYLIB.HELLO.PGM.RPGLE");

    // No timestamps: rebuild
    assertTrue(key.needsRebuild());

    // Set timestamps: source newer -> rebuild
    Timestamp oldBuild = Timestamp.valueOf("2023-01-01 00:00:00");
    Timestamp newEdit = Timestamp.valueOf("2023-01-02 00:00:00");
    key.setLastBuild(oldBuild);
    key.setLastEdit(newEdit);
    assertTrue(key.needsRebuild());

    // Build newer: no rebuild
    key.setLastEdit(Timestamp.valueOf("2023-01-01 00:00:00"));
    assertFalse(key.needsRebuild());
  }

  @Test
  void testDefaultParamsApplied() {
    TargetKey key = new TargetKey("MYLIB.HELLO.PGM.RPGLE");

    assertEquals("MYLIB/QRPGLESRC", key.get(ParamCmd.SRCFILE));
    assertEquals("HELLO", key.get(ParamCmd.SRCMBR));
    assertEquals("*YES", key.get(ParamCmd.REPLACE)); // From Utilities
    assertEquals("CRTBNDRPG PGM(*CURLIB/HELLO) SRCFILE(MYLIB/QRPGLESRC) SRCMBR(HELLO) OPTION(*EVENTF) DBGVIEW(*ALL) REPLACE(*YES)", key.getCommandString()); // Partial match
  }

  @Test
  void testScapedParams() {
    TargetKey key = new TargetKey("MYLIB.HELLO.PGM.RPGLE");
    key.put(ParamCmd.TEXT, "Test Program");
    key.put(ParamCmd.SRCSTMF, "/source/route");

    assertEquals("''Test Program''", key.get(ParamCmd.TEXT));
    assertEquals("''/source/route''", key.get(ParamCmd.SRCSTMF));
  }

}
