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
package org.apache.lucene.analysis.fa;


import java.util.Map;

import org.apache.lucene.analysis.fa.PersianNormalizationFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.util.AbstractAnalysisFactory;
import org.apache.lucene.analysis.util.MultiTermAwareComponent;
import org.apache.lucene.analysis.util.TokenFilterFactory;

/**
 * Factory for {@link PersianNormalizationFilter}.
 * <pre class="prettyprint">
 * &lt;fieldType name="text_fanormal" class="solr.TextField" positionIncrementGap="100"&gt;
 *   &lt;analyzer&gt;
 *     &lt;charFilter class="solr.PersianCharFilterFactory"/&gt;
 *     &lt;tokenizer class="solr.StandardTokenizerFactory"/&gt;
 *     &lt;filter class="solr.PersianNormalizationFilterFactory"/&gt;
 *   &lt;/analyzer&gt;
 * &lt;/fieldType&gt;</pre>
 */
public class PersianNormalizationFilterFactory extends TokenFilterFactory implements MultiTermAwareComponent {

	/**
	 * Creates a new PersianNormalizationFilterFactory
	 */
	public PersianNormalizationFilterFactory(Map<String, String> args) {
		super(args);
		if (!args.isEmpty()) {
			throw new IllegalArgumentException("Unknown parameters: " + args);
		}
	}

	@Override
	public PersianNormalizationFilter create(TokenStream input) {
		return new PersianNormalizationFilter(input);
	}

	@Override
	public AbstractAnalysisFactory getMultiTermComponent() {
		return this;
	}
}

