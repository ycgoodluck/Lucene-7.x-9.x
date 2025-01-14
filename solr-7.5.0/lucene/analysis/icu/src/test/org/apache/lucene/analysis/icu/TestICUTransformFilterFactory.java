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


import java.io.Reader;
import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;

import org.apache.lucene.analysis.BaseTokenStreamTestCase;
import org.apache.lucene.analysis.TokenStream;

/**
 * basic tests for {@link ICUTransformFilterFactory}
 */
public class TestICUTransformFilterFactory extends BaseTokenStreamTestCase {

	/**
	 * ensure the transform is working
	 */
	public void test() throws Exception {
		Reader reader = new StringReader("簡化字");
		Map<String, String> args = new HashMap<>();
		args.put("id", "Traditional-Simplified");
		ICUTransformFilterFactory factory = new ICUTransformFilterFactory(args);
		TokenStream stream = whitespaceMockTokenizer(reader);
		stream = factory.create(stream);
		assertTokenStreamContents(stream, new String[]{"简化字"});
	}

	/**
	 * test forward and reverse direction
	 */
	public void testForwardDirection() throws Exception {
		// forward
		Reader reader = new StringReader("Российская Федерация");
		Map<String, String> args = new HashMap<>();
		args.put("id", "Cyrillic-Latin");
		ICUTransformFilterFactory factory = new ICUTransformFilterFactory(args);
		TokenStream stream = whitespaceMockTokenizer(reader);
		stream = factory.create(stream);
		assertTokenStreamContents(stream, new String[]{"Rossijskaâ", "Federaciâ"});
	}

	public void testReverseDirection() throws Exception {
		// backward (invokes Latin-Cyrillic)
		Reader reader = new StringReader("Rossijskaâ Federaciâ");
		Map<String, String> args = new HashMap<>();
		args.put("id", "Cyrillic-Latin");
		args.put("direction", "reverse");
		ICUTransformFilterFactory factory = new ICUTransformFilterFactory(args);
		TokenStream stream = whitespaceMockTokenizer(reader);
		stream = factory.create(stream);
		assertTokenStreamContents(stream, new String[]{"Российская", "Федерация"});
	}

	/**
	 * Test that bogus arguments result in exception
	 */
	public void testBogusArguments() throws Exception {
		IllegalArgumentException expected = expectThrows(IllegalArgumentException.class, () -> {
			new ICUTransformFilterFactory(new HashMap<String, String>() {{
				put("id", "Null");
				put("bogusArg", "bogusValue");
			}});
		});
		assertTrue(expected.getMessage().contains("Unknown parameters"));
	}
}
