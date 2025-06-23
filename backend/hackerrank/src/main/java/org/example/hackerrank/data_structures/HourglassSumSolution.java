package org.example.hackerrank.data_structures;

import java.util.*;

class HourglassSumResult {

  /*
   * Complete the 'hourglassSum' function below.
   *
   * The function is expected to return an INTEGER.
   * The function accepts 2D_INTEGER_ARRAY arr as parameter.
   */

  public static int hourglassSum(List<List<Integer>> arr) {

    Integer max = null;

    for (int i = 0; i < arr.size() - 2; i++) {
      for (int j = 0; j < arr.size() - 2; j++) {
        var hourglassSum = 0;
        for (int m = i; m < i + 3; m++) {
          for (int n = j; n < j + 3; n++) {
            if (!((m == i + 1 && n == j) || (m == i + 1 && n == j + 2))) {
              hourglassSum += arr.get(m).get(n);
            }
          }
        }
        if (max == null) {
          max = hourglassSum;
        } else if (hourglassSum > max) {
          max = hourglassSum;
        }
      }
    }

    return max != null ? max : 0 ;
  }
}

public class HourglassSumSolution {

  public static void main(String[] args) {

    System.out.println(
        HourglassSumResult.hourglassSum(
            List.of(
                List.of(-1, -1, 0, -9, -2, -2),
                List.of(-2, -1, -6, -8, -2, -5),
                List.of(-1, -1, -1, -2, -3, -4),
                List.of(-1, -9, -2, -4, -4, -5),
                List.of(-7, -3, -3, -2, -9, -9),
                List.of(-1, -3, -1, -2, -4, -5))));
  }
}
