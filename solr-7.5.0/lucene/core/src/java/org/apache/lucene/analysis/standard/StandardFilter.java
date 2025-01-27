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
package org.apache.lucene.analysis.standard;


import java.io.IOException;

import org.apache.lucene.analysis.TokenFilter;
import org.apache.lucene.analysis.TokenStream;

/**
 * Normalizes tokens extracted with {@link StandardTokenizer}.
 *
 * @deprecated StandardFilter is a no-op and can be removed from code
 */
@Deprecated
public class StandardFilter extends TokenFilter {

	/**
	 * Sole constructor
	 */
	public StandardFilter(TokenStream in) {
		super(in);
	}

	@Override
	public final boolean incrementToken() throws IOException {
		return input.incrementToken(); // TODO: add some niceties for the new grammar
	}
}
