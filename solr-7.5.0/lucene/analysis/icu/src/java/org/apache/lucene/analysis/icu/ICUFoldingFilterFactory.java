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
package org.apache.lucene.analysis.icu;


import java.util.Map;

import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.icu.ICUFoldingFilter;
import org.apache.lucene.analysis.util.AbstractAnalysisFactory; // javadocs
import org.apache.lucene.analysis.util.MultiTermAwareComponent;
import org.apache.lucene.analysis.util.TokenFilterFactory;

import com.ibm.icu.text.FilteredNormalizer2;
import com.ibm.icu.text.Normalizer2;
import com.ibm.icu.text.UnicodeSet;

/**
 * Factory for {@link ICUFoldingFilter}.
 * <pre class="prettyprint">
 * &lt;fieldType name="text_folded" class="solr.TextField" positionIncrementGap="100"&gt;
 *   &lt;analyzer&gt;
 *     &lt;tokenizer class="solr.WhitespaceTokenizerFactory"/&gt;
 *     &lt;filter class="solr.ICUFoldingFilterFactory"/&gt;
 *   &lt;/analyzer&gt;
 * &lt;/fieldType&gt;</pre>
 *
 * @since 3.1.0
 */
public class ICUFoldingFilterFactory extends TokenFilterFactory implements MultiTermAwareComponent {
	private final Normalizer2 normalizer;

	/**
	 * Creates a new ICUFoldingFilterFactory
	 */
	public ICUFoldingFilterFactory(Map<String, String> args) {
		super(args);

		Normalizer2 normalizer = ICUFoldingFilter.NORMALIZER;
		String filter = get(args, "filter");
		if (filter != null) {
			UnicodeSet set = new UnicodeSet(filter);
			if (!set.isEmpty()) {
				set.freeze();
				normalizer = new FilteredNormalizer2(normalizer, set);
			}
		}
		if (!args.isEmpty()) {
			throw new IllegalArgumentException("Unknown parameters: " + args);
		}
		this.normalizer = normalizer;
	}

	@Override
	public TokenStream create(TokenStream input) {
		return new ICUFoldingFilter(input, normalizer);
	}

	@Override
	public AbstractAnalysisFactory getMultiTermComponent() {
		return this;
	}
}
