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
package org.apache.lucene.analysis.fi;


import java.io.IOException;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.BaseTokenStreamTestCase;
import org.apache.lucene.analysis.CharArraySet;

public class TestFinnishAnalyzer extends BaseTokenStreamTestCase {
	/**
	 * This test fails with NPE when the
	 * stopwords file is missing in classpath
	 */
	public void testResourcesAvailable() {
		new FinnishAnalyzer().close();
	}

	/**
	 * test stopwords and stemming
	 */
	public void testBasics() throws IOException {
		Analyzer a = new FinnishAnalyzer();
		// stemming
		checkOneTerm(a, "edeltäjiinsä", "edeltäj");
		checkOneTerm(a, "edeltäjistään", "edeltäj");
		// stopword
		assertAnalyzesTo(a, "olla", new String[]{});
		a.close();
	}

	/**
	 * test use of exclusion set
	 */
	public void testExclude() throws IOException {
		CharArraySet exclusionSet = new CharArraySet(asSet("edeltäjistään"), false);
		Analyzer a = new FinnishAnalyzer(
			FinnishAnalyzer.getDefaultStopSet(), exclusionSet);
		checkOneTerm(a, "edeltäjiinsä", "edeltäj");
		checkOneTerm(a, "edeltäjistään", "edeltäjistään");
		a.close();
	}

	/**
	 * blast some random strings through the analyzer
	 */
	public void testRandomStrings() throws Exception {
		Analyzer a = new FinnishAnalyzer();
		checkRandomData(random(), a, 1000 * RANDOM_MULTIPLIER);
		a.close();
	}
}
