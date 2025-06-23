package org.example.hackerrank.algorithms;

import static java.util.stream.Collectors.toList;

import java.io.*;
import java.util.*;
import java.util.stream.*;

class MiniMaxSumResult {

  /*
   * Complete the 'miniMaxSum' function below.
   *
   * The function accepts INTEGER_ARRAY arr as parameter.
   */

  public static void miniMaxSum(List<Integer> arr) {

    if (arr == null || arr.size() != 5) {
      throw new IllegalArgumentException("Please provide list of 5 integers");
    }

    Collections.sort(arr);

    long minimum = 0;
    long maximum = 0;

    for (int i = 0; i < arr.size() - 1; i++) {

      if (arr.get(i) >= 1 && arr.get(i) <= 1_000_000_000) {
        minimum += arr.get(i);
      }
    }

    for (int i = 1; i < arr.size(); i++) {

      if (arr.get(i) >= 1 && arr.get(i) <= 1_000_000_000) {
        maximum += arr.get(i);
      }
    }

    System.out.println(minimum + " " + maximum);
  }
}

public class MiniMaxSumSolution {

  public static void main(String[] args) throws IOException {

    BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(System.in));

    List<Integer> arr =
        Stream.of(bufferedReader.readLine().replaceAll("\\s+$", "").split(" "))
            .map(Integer::parseInt)
            .collect(toList());

    MiniMaxSumResult.miniMaxSum(arr);

    bufferedReader.close();
  }
}
