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
package org.apache.lucene.analysis.id;


import java.io.IOException;
import java.io.Reader;

import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.LowerCaseFilter;
import org.apache.lucene.analysis.StopFilter;
import org.apache.lucene.analysis.StopwordAnalyzerBase;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.miscellaneous.SetKeywordMarkerFilter;
import org.apache.lucene.analysis.standard.StandardTokenizer;

/**
 * Analyzer for Indonesian (Bahasa)
 *
 * @since 3.1
 */
public final class IndonesianAnalyzer extends StopwordAnalyzerBase {
	/**
	 * File containing default Indonesian stopwords.
	 */
	public final static String DEFAULT_STOPWORD_FILE = "stopwords.txt";

	/**
	 * Returns an unmodifiable instance of the default stop-words set.
	 *
	 * @return an unmodifiable instance of the default stop-words set.
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
				DEFAULT_STOP_SET = loadStopwordSet(false, IndonesianAnalyzer.class, DEFAULT_STOPWORD_FILE, "#");
			} catch (IOException ex) {
				// default set should always be present as it is part of the
				// distribution (JAR)
				throw new RuntimeException("Unable to load default stopword set");
			}
		}
	}

	private final CharArraySet stemExclusionSet;

	/**
	 * Builds an analyzer with the default stop words: {@link #DEFAULT_STOPWORD_FILE}.
	 */
	public IndonesianAnalyzer() {
		this(DefaultSetHolder.DEFAULT_STOP_SET);
	}

	/**
	 * Builds an analyzer with the given stop words
	 *
	 * @param stopwords a stopword set
	 */
	public IndonesianAnalyzer(CharArraySet stopwords) {
		this(stopwords, CharArraySet.EMPTY_SET);
	}

	/**
	 * Builds an analyzer with the given stop word. If a none-empty stem exclusion set is
	 * provided this analyzer will add a {@link SetKeywordMarkerFilter} before
	 * {@link IndonesianStemFilter}.
	 *
	 * @param stopwords        a stopword set
	 * @param stemExclusionSet a set of terms not to be stemmed
	 */
	public IndonesianAnalyzer(CharArraySet stopwords, CharArraySet stemExclusionSet) {
		super(stopwords);
		this.stemExclusionSet = CharArraySet.unmodifiableSet(CharArraySet.copy(stemExclusionSet));
	}

	/**
	 * Creates
	 * {@link org.apache.lucene.analysis.Analyzer.TokenStreamComponents}
	 * used to tokenize all the text in the provided {@link Reader}.
	 *
	 * @return {@link org.apache.lucene.analysis.Analyzer.TokenStreamComponents}
	 * built from an {@link StandardTokenizer} filtered with
	 * {@link LowerCaseFilter},
	 * {@link StopFilter}, {@link SetKeywordMarkerFilter}
	 * if a stem exclusion set is provided and {@link IndonesianStemFilter}.
	 */
	@Override
	protected TokenStreamComponents createComponents(String fieldName) {
		final Tokenizer source = new StandardTokenizer();
		TokenStream result = new LowerCaseFilter(source);
		result = new StopFilter(result, stopwords);
		if (!stemExclusionSet.isEmpty()) {
			result = new SetKeywordMarkerFilter(result, stemExclusionSet);
		}
		return new TokenStreamComponents(source, new IndonesianStemFilter(result));
	}

	@Override
	protected TokenStream normalize(String fieldName, TokenStream in) {
		return new LowerCaseFilter(in);
	}
}
