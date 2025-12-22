package com.github.kraudy.compiler;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.github.kraudy.compiler.CompilationPattern.CompCmd;
import com.github.kraudy.compiler.CompilationPattern.ParamCmd;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class UtilitiesTest {
  @Test
  void testDeserializeYaml() throws IOException {
    String yamlContent = 
      "targets:\n" +
      "  mylib.hello.pgm.rpgle:\n" +
      "    params:\n" +
      "      TEXT: \"Hello World\"\n";

    // Create temp YAML file
    Path tempYaml = Files.createTempFile("test", ".yaml");
    Files.write(tempYaml, yamlContent.getBytes());
      
    try {
      BuildSpec spec = Utilities.deserializeYaml(tempYaml.toString());

      assertFalse(spec.targets.isEmpty());
      // Use lowercase to match YAML key (equals/hashCode now handles field matching)
      TargetKey key = new TargetKey("mylib.hello.pgm.rpgle");
      assertEquals("Hello World", spec.targets.get(key).params.get(ParamCmd.TEXT));
    } finally {
      // Cleanup
      Files.deleteIfExists(tempYaml);
    }
  }
}
