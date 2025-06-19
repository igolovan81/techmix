package org.example.hackerrank;

import java.util.*;

class BreakingRecordsResult {

  /*
   * Complete the 'breakingRecords' function below.
   *
   * The function is expected to return an INTEGER_ARRAY.
   * The function accepts INTEGER_ARRAY scores as parameter.
   */

  public static List<Integer> breakingRecords(List<Integer> scores) {

    var currentBestResult = scores.get(0);
    var currentWorseResult = scores.get(0);
    var moreThanRecordCounter = 0;
    var lessThanRecordCounter = 0;

    for (int i = 1; i < scores.size(); i++) {

      var score = scores.get(i);

      if (score > currentBestResult) {
        moreThanRecordCounter++;
        currentBestResult = score;
      } else if (score < currentWorseResult) {
        lessThanRecordCounter++;
        currentWorseResult = score;
      }
    }

    return Arrays.asList(moreThanRecordCounter, lessThanRecordCounter);
  }
}

public class BreakingRecordsSolution {

  public static void main(String[] args) {

    System.out.println(
        BreakingRecordsResult.breakingRecords(Arrays.asList(10, 5, 20, 20, 4, 5, 2, 25, 1)));
  }
}
