package org.example.hackerrank;

class KangarooResult {

  /*
   * Complete the 'kangaroo' function below.
   *
   * The function is expected to return a STRING.
   * The function accepts following parameters:
   *  1. INTEGER x1
   *  2. INTEGER v1
   *  3. INTEGER x2
   *  4. INTEGER v2
   */

  public static String kangaroo(int x1, int v1, int x2, int v2) {

    if (x1 == x2) {
      return "YES";
    }

    var kangarooAheadX = 0;
    var kangarooAheadV = 0;
    var kangarooBehindX = 0;
    var kangarooBehindV = 0;

    if (x2 > x1) {
      kangarooAheadX = x2;
      kangarooAheadV = v2;
      kangarooBehindX = x1;
      kangarooBehindV = v1;
    } else {
      kangarooAheadX = x1;
      kangarooAheadV = v1;
      kangarooBehindX = x2;
      kangarooBehindV = v2;
    }

    if (kangarooAheadV >= kangarooBehindV) {
      return "NO";
    }

    do {

      kangarooAheadX += kangarooAheadV;
      kangarooBehindX += kangarooBehindV;

      if (kangarooBehindX == kangarooAheadX) {
        return "YES";
      }

    } while (kangarooBehindX < kangarooAheadX);

    return "NO";
  }
}

public class KangarooSolution {

  public static void main(String[] args) {

    System.out.println(KangarooResult.kangaroo(0, 3, 4, 2));
  }
}
