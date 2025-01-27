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
package org.apache.lucene.analysis.core;


import java.io.IOException;
import java.io.Reader;
import java.nio.file.Path;

import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.LowerCaseFilter;
import org.apache.lucene.analysis.StopFilter;
import org.apache.lucene.analysis.StopwordAnalyzerBase;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.WordlistLoader;
import org.apache.lucene.analysis.en.EnglishAnalyzer;

/**
 * Filters {@link LetterTokenizer} with {@link LowerCaseFilter} and {@link StopFilter}.
 */
public final class StopAnalyzer extends StopwordAnalyzerBase {

	/**
	 * An unmodifiable set containing some common English words that are not usually useful
	 * for searching.
	 */
	@Deprecated
	public static final CharArraySet ENGLISH_STOP_WORDS_SET = EnglishAnalyzer.ENGLISH_STOP_WORDS_SET;

	/**
	 * Builds an analyzer which removes words in
	 * {@link #ENGLISH_STOP_WORDS_SET}.
	 *
	 * @deprecated Use a constructor with a specific stop word set
	 */
	@Deprecated
	public StopAnalyzer() {
		this(ENGLISH_STOP_WORDS_SET);
	}

	/**
	 * Builds an analyzer with the stop words from the given set.
	 *
	 * @param stopWords Set of stop words
	 */
	public StopAnalyzer(CharArraySet stopWords) {
		super(stopWords);
	}

	/**
	 * Builds an analyzer with the stop words from the given path.
	 *
	 * @param stopwordsFile File to load stop words from
	 * @see WordlistLoader#getWordSet(Reader)
	 */
	public StopAnalyzer(Path stopwordsFile) throws IOException {
		this(loadStopwordSet(stopwordsFile));
	}

	/**
	 * Builds an analyzer with the stop words from the given reader.
	 *
	 * @param stopwords Reader to load stop words from
	 * @see WordlistLoader#getWordSet(Reader)
	 */
	public StopAnalyzer(Reader stopwords) throws IOException {
		this(loadStopwordSet(stopwords));
	}

	/**
	 * Creates
	 * {@link org.apache.lucene.analysis.Analyzer.TokenStreamComponents}
	 * used to tokenize all the text in the provided {@link Reader}.
	 *
	 * @return {@link org.apache.lucene.analysis.Analyzer.TokenStreamComponents}
	 * built from a {@link LowerCaseTokenizer} filtered with
	 * {@link StopFilter}
	 */
	@Override
	protected TokenStreamComponents createComponents(String fieldName) {
		final Tokenizer source = new LowerCaseTokenizer();
		return new TokenStreamComponents(source, new StopFilter(source, stopwords));
	}

	@Override
	protected TokenStream normalize(String fieldName, TokenStream in) {
		return new LowerCaseFilter(in);
	}
}

