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


import java.util.Map;

import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.fi.FinnishLightStemFilter;
import org.apache.lucene.analysis.util.TokenFilterFactory;

/**
 * Factory for {@link FinnishLightStemFilter}.
 * <pre class="prettyprint">
 * &lt;fieldType name="text_filgtstem" class="solr.TextField" positionIncrementGap="100"&gt;
 *   &lt;analyzer&gt;
 *     &lt;tokenizer class="solr.StandardTokenizerFactory"/&gt;
 *     &lt;filter class="solr.LowerCaseFilterFactory"/&gt;
 *     &lt;filter class="solr.FinnishLightStemFilterFactory"/&gt;
 *   &lt;/analyzer&gt;
 * &lt;/fieldType&gt;</pre>
 *
 * @since 3.1.0
 */
public class FinnishLightStemFilterFactory extends TokenFilterFactory {

	/**
	 * Creates a new FinnishLightStemFilterFactory
	 */
	public FinnishLightStemFilterFactory(Map<String, String> args) {
		super(args);
		if (!args.isEmpty()) {
			throw new IllegalArgumentException("Unknown parameters: " + args);
		}
	}

	@Override
	public TokenStream create(TokenStream input) {
		return new FinnishLightStemFilter(input);
	}
}
