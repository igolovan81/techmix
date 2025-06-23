package org.example.hackerrank.algorithms;

import java.io.*;
import java.text.*;
import java.util.*;

class ResultSolution {

  /*
   * Complete the 'timeConversion' function below.
   *
   * The function is expected to return a STRING.
   * The function accepts STRING s as parameter.
   */

  public static String timeConversion(String s) {

    var parseFormat = new SimpleDateFormat("hh:mm:ssa");
    var displayFormat = new SimpleDateFormat("HH:mm:ss");

    Date date = new Date();
    try {
      date = parseFormat.parse(s);
    } catch (ParseException e) {
      return "";
    }

    return displayFormat.format(date);
  }
}

public class TimeConversionSolution {

  public static void main(String[] args) throws IOException {

    BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(System.in));

    String input = bufferedReader.readLine();

    System.out.println(ResultSolution.timeConversion(input));

    bufferedReader.close();
  }
}
