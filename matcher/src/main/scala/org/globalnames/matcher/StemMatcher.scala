package org.globalnames
package matcher

import akka.http.impl.util.EnhancedString
import com.BoxOfC.LevenshteinAutomaton.LevenshteinAutomaton
import com.BoxOfC.MDAG.MDAG

import scala.collection.JavaConversions._
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

import scalaz.syntax.std.option._
import scalaz.syntax.std.boolean._

private[matcher]
class StemMatcher private(wordToDatasources: Map[String, Set[Int]],
                          wordStemToWords: mutable.Map[String, Set[String]],
                          mdag: MDAG) {

  private val maxEditDistance = 2

  def findMatches(word: String, dataSources: Set[Int]): Vector[Candidate] = {
    val wordStem = StemMatcher.transform(word)
    val stemMatches = LevenshteinAutomaton.tableFuzzySearch(maxEditDistance, wordStem, mdag)

    val result = for {
      stemMatch <- stemMatches.toVector
      fullWord <- wordStemToWords(stemMatch)
      fullWordDataSource <- wordToDatasources(fullWord)
      if dataSources.isEmpty || dataSources.contains(fullWordDataSource)
    } yield Candidate(stem = stemMatch, term = fullWord, dataSourceId = fullWordDataSource,
                      verbatimEditDistance =
                        LevenshteinAutomaton.computeEditDistance(word, fullWord).some,
                      stemEditDistance =
                        LevenshteinAutomaton.computeEditDistance(wordStem, stemMatch).some)
    result
  }
}

object StemMatcher {
  def apply(wordToDatasources: Map[String, Set[Int]]): StemMatcher = {
    val wordStemToWords = mutable.Map.empty[String, Set[String]]
    val wordStems = ArrayBuffer[String]()

    for (((word, _), idx) <- wordToDatasources.zipWithIndex) {
      if (idx > 0 && idx % 10000 == 0) {
        println(s"Stem matcher (progress): $idx")
      }

      val wordStem = transform(word)
      wordStems += wordStem
      wordStemToWords += wordStem -> (wordStemToWords.getOrElse(wordStem, Set()) + word)
    }

    val mdag = new MDAG(wordStems.sorted)
    val sm = new StemMatcher(wordToDatasources, wordStemToWords, mdag)
    sm
  }

  def transform(word: String): String = {
    val wordParts = word.toLowerCase.fastSplit(delimiter = ' ')
    (wordParts.length < 2) ?
      word | wordParts.map { w => LatinStemmer.stemmize(w).mappedStem }.mkString(" ")
  }
}
