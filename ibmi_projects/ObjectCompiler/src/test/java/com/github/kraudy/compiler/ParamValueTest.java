package com.github.kraudy.compiler;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.github.kraudy.compiler.CompilationPattern.CompCmd;
import com.github.kraudy.compiler.CompilationPattern.ParamCmd;

import static org.junit.jupiter.api.Assertions.*;

public class ParamValueTest {
  @Test
  void testEmptyConstructor() {
    ParamValue pv = new ParamValue();

    assertNull(pv.get());
    assertEquals(0, pv.getCount()); // No history
    assertTrue(pv.getHistory().isEmpty());
    assertNull(pv.getLastChange());
    assertNull(pv.getPrevious()); // count < 1, but history empty → behavior as per code
  }

}
