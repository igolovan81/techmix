package org.example.hackerrank.algorithms;

import java.util.*;

class NumberOfSegmentsResult {

  /*
   * Complete the 'birthday' function below.
   *
   * The function is expected to return an INTEGER.
   * The function accepts following parameters:
   *  1. INTEGER_ARRAY s
   *  2. INTEGER d
   *  3. INTEGER m
   */

  public static int birthday(List<Integer> s, int d, int m) {

    int numberOfMatchedSegments = 0;

    if (s.size() == 1 && m == 1 && s.get(0) == d) {
      return 1;
    }

    for (int i = 0; i < s.size() - m + 1; i++) {

      var segmentScore = 0;

      for (int j = 0; j < m; j++) {
        segmentScore += s.get(i + j);
      }

      if (segmentScore == d) {
        numberOfMatchedSegments++;
      }
    }

    return numberOfMatchedSegments;
  }
}

public class NumberOfSegmentsSolution {

  public static void main(String[] args) {

    System.out.println(
        NumberOfSegmentsResult.birthday(
            Arrays.asList(2, 5, 1, 3, 4, 4, 3, 5, 1, 1, 2, 1, 4, 1, 3, 3, 4, 2, 1), 18, 7));

    System.out.println(
        NumberOfSegmentsResult.birthday(
            Arrays.asList(
                3, 5, 4, 1, 2, 5, 3, 4, 3, 2, 1, 1, 2, 4, 2, 3, 4, 5, 3, 1, 2, 5, 4, 5, 4, 1, 1, 5,
                3, 1, 4, 5, 2, 3, 2, 5, 2, 5, 2, 2, 1, 5, 3, 2, 5, 1, 2, 4, 3, 1, 5, 1, 3, 3, 5),
            18,
            6));
  }
}
