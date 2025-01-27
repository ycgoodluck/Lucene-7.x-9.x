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
package org.apache.lucene.analysis;

import java.io.IOException;
import java.util.Random;

import org.apache.lucene.analysis.tokenattributes.PayloadAttribute;
import org.apache.lucene.util.BytesRef;

/**
 * TokenFilter that adds random variable-length payloads.
 */
public final class MockVariableLengthPayloadFilter extends TokenFilter {
	private static final int MAXLENGTH = 129;

	private final PayloadAttribute payloadAtt = addAttribute(PayloadAttribute.class);
	private final Random random;
	private final byte[] bytes = new byte[MAXLENGTH];
	private final BytesRef payload;

	public MockVariableLengthPayloadFilter(Random random, TokenStream in) {
		super(in);
		this.random = random;
		this.payload = new BytesRef(bytes);
	}

	@Override
	public boolean incrementToken() throws IOException {
		if (input.incrementToken()) {
			random.nextBytes(bytes);
			payload.length = random.nextInt(MAXLENGTH);
			payloadAtt.setPayload(payload);
			return true;
		} else {
			return false;
		}
	}
}
