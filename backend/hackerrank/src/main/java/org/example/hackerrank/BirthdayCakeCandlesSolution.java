package org.example.hackerrank;

import static java.util.stream.Collectors.toList;

import java.io.*;
import java.util.*;
import java.util.stream.*;

class BirthdayCakeCandlesResult {

  /*
   * Complete the 'birthdayCakeCandles' function below.
   *
   * The function is expected to return an INTEGER.
   * The function accepts INTEGER_ARRAY candles as parameter.
   */

  public static int birthdayCakeCandles(List<Integer> candles) {

    Collections.sort(candles);

    return Collections.frequency(candles, candles.get(candles.size() - 1));
  }
}

public class BirthdayCakeCandlesSolution {

  public static void main(String[] args) throws IOException {

    BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(System.in));

    List<Integer> arr =
        Stream.of(bufferedReader.readLine().replaceAll("\\s+$", "").split(" "))
            .map(Integer::parseInt)
            .collect(toList());

    System.out.println(BirthdayCakeCandlesResult.birthdayCakeCandles(arr));

    bufferedReader.close();
  }
}
