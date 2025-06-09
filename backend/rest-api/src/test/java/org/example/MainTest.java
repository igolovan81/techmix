package org.example;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import org.junit.jupiter.api.Test;

class MainTest {

  // Main method executes without throwing exceptions
  @Test
  void test_main_method_executes_without_exceptions() {
    assertDoesNotThrow(
        () -> {
          Main.main(new String[] {});
        });
  }

  // Standard output stream is available and writable
  @Test
  void test_standard_output_stream_writable() {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    PrintStream originalOut = System.out;
    System.setOut(new PrintStream(outputStream));

    Main.main(new String[] {});

    System.setOut(originalOut);
    String output = outputStream.toString();
    assertTrue(output.contains("Hello and welcome!"));
    assertTrue(output.contains("i = 1"));
    assertTrue(output.contains("i = 5"));
  }
}
