/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.lucene.analysis.ga;


import java.io.IOException;
import java.io.Reader;
import java.util.Arrays;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.StopFilter;
import org.apache.lucene.analysis.StopwordAnalyzerBase;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.miscellaneous.SetKeywordMarkerFilter;
import org.apache.lucene.analysis.snowball.SnowballFilter;
import org.apache.lucene.analysis.standard.StandardTokenizer;
import org.apache.lucene.analysis.util.ElisionFilter;
import org.tartarus.snowball.ext.IrishStemmer;

/**
 * {@link Analyzer} for Irish.
 *
 * @since 3.6.0
 */
public final class IrishAnalyzer extends StopwordAnalyzerBase {
	private final CharArraySet stemExclusionSet;

	/**
	 * File containing default Irish stopwords.
	 */
	public final static String DEFAULT_STOPWORD_FILE = "stopwords.txt";

	private static final CharArraySet DEFAULT_ARTICLES = CharArraySet.unmodifiableSet(
		new CharArraySet(
			Arrays.asList(
				"d", "m", "b"
			), true));

	/**
	 * When StandardTokenizer splits t‑athair into {t, athair}, we don't
	 * want to cause a position increment, otherwise there will be problems
	 * with phrase queries versus tAthair (which would not have a gap).
	 */
	private static final CharArraySet HYPHENATIONS = CharArraySet.unmodifiableSet(
		new CharArraySet(
			Arrays.asList(
				"h", "n", "t"
			), true));

	/**
	 * Returns an unmodifiable instance of the default stop words set.
	 *
	 * @return default stop words set.
	 */
	public static CharArraySet getDefaultStopSet() {
		return DefaultSetHolder.DEFAULT_STOP_SET;
	}

	/**
	 * Atomically loads the DEFAULT_STOP_SET in a lazy fashion once the outer class
	 * accesses the static final set the first time.;
	 */
	private static class DefaultSetHolder {
		static final CharArraySet DEFAULT_STOP_SET;

		static {
			try {
				DEFAULT_STOP_SET = loadStopwordSet(false,
					IrishAnalyzer.class, DEFAULT_STOPWORD_FILE, "#");
			} catch (IOException ex) {
				// default set should always be present as it is part of the
				// distribution (JAR)
				throw new RuntimeException("Unable to load default stopword set");
			}
		}
	}

	/**
	 * Builds an analyzer with the default stop words: {@link #DEFAULT_STOPWORD_FILE}.
	 */
	public IrishAnalyzer() {
		this(DefaultSetHolder.DEFAULT_STOP_SET);
	}

	/**
	 * Builds an analyzer with the given stop words.
	 *
	 * @param stopwords a stopword set
	 */
	public IrishAnalyzer(CharArraySet stopwords) {
		this(stopwords, CharArraySet.EMPTY_SET);
	}

	/**
	 * Builds an analyzer with the given stop words. If a non-empty stem exclusion set is
	 * provided this analyzer will add a {@link SetKeywordMarkerFilter} before
	 * stemming.
	 *
	 * @param stopwords        a stopword set
	 * @param stemExclusionSet a set of terms not to be stemmed
	 */
	public IrishAnalyzer(CharArraySet stopwords, CharArraySet stemExclusionSet) {
		super(stopwords);
		this.stemExclusionSet = CharArraySet.unmodifiableSet(CharArraySet.copy(stemExclusionSet));
	}

	/**
	 * Creates a
	 * {@link org.apache.lucene.analysis.Analyzer.TokenStreamComponents}
	 * which tokenizes all the text in the provided {@link Reader}.
	 *
	 * @return A
	 * {@link org.apache.lucene.analysis.Analyzer.TokenStreamComponents}
	 * built from an {@link StandardTokenizer} filtered with
	 * {@link IrishLowerCaseFilter}, {@link StopFilter}
	 * , {@link SetKeywordMarkerFilter} if a stem exclusion set is
	 * provided and {@link SnowballFilter}.
	 */
	@Override
	protected TokenStreamComponents createComponents(String fieldName) {
		final Tokenizer source = new StandardTokenizer();
		TokenStream result = new StopFilter(source, HYPHENATIONS);
		result = new ElisionFilter(result, DEFAULT_ARTICLES);
		result = new IrishLowerCaseFilter(result);
		result = new StopFilter(result, stopwords);
		if (!stemExclusionSet.isEmpty())
			result = new SetKeywordMarkerFilter(result, stemExclusionSet);
		result = new SnowballFilter(result, new IrishStemmer());
		return new TokenStreamComponents(source, result);
	}

	@Override
	protected TokenStream normalize(String fieldName, TokenStream in) {
		TokenStream result = new ElisionFilter(in, DEFAULT_ARTICLES);
		result = new IrishLowerCaseFilter(result);
		return result;
	}
}
