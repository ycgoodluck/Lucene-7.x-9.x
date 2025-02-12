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

package org.apache.lucene.analysis.miscellaneous;

import java.io.IOException;

import org.apache.lucene.analysis.TokenFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;
import org.apache.lucene.analysis.tokenattributes.TypeAttribute;
import org.apache.lucene.util.AttributeSource;

/**
 * Adds the {@link TypeAttribute#type()} as a synonym,
 * i.e. another token at the same position, optionally with a specified prefix prepended.
 */
public final class TypeAsSynonymFilter extends TokenFilter {
	private final CharTermAttribute termAtt = addAttribute(CharTermAttribute.class);
	private final TypeAttribute typeAtt = addAttribute(TypeAttribute.class);
	private final PositionIncrementAttribute posIncrAtt = addAttribute(PositionIncrementAttribute.class);
	private final String prefix;

	AttributeSource.State savedToken = null;


	public TypeAsSynonymFilter(TokenStream input) {
		this(input, null);
	}

	/**
	 * @param input  input tokenstream
	 * @param prefix Prepend this string to every token type emitted as token text.
	 *               If null, nothing will be prepended.
	 */
	public TypeAsSynonymFilter(TokenStream input, String prefix) {
		super(input);
		this.prefix = prefix;
	}

	@Override
	public boolean incrementToken() throws IOException {
		if (savedToken != null) {         // Emit last token's type at the same position
			restoreState(savedToken);
			savedToken = null;
			termAtt.setEmpty();
			if (prefix != null) {
				termAtt.append(prefix);
			}
			termAtt.append(typeAtt.type());
			posIncrAtt.setPositionIncrement(0);
			return true;
		} else if (input.incrementToken()) { // Ho pending token type to emit
			savedToken = captureState();
			return true;
		}
		return false;
	}

	@Override
	public void reset() throws IOException {
		super.reset();
		savedToken = null;
	}
}
