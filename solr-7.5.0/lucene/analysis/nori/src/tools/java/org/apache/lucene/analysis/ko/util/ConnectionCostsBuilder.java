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
package org.apache.lucene.analysis.ko.util;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

public class ConnectionCostsBuilder {

	private ConnectionCostsBuilder() {
	}

	public static ConnectionCostsWriter build(String filename) throws IOException {
		FileInputStream inputStream = new FileInputStream(filename);
		Charset cs = StandardCharsets.US_ASCII;
		CharsetDecoder decoder = cs.newDecoder()
			.onMalformedInput(CodingErrorAction.REPORT)
			.onUnmappableCharacter(CodingErrorAction.REPORT);
		InputStreamReader streamReader = new InputStreamReader(inputStream, decoder);
		LineNumberReader lineReader = new LineNumberReader(streamReader);

		String line = lineReader.readLine();
		String[] dimensions = line.split("\\s+");

		assert dimensions.length == 2;

		int forwardSize = Integer.parseInt(dimensions[0]);
		int backwardSize = Integer.parseInt(dimensions[1]);

		assert forwardSize > 0 && backwardSize > 0;

		ConnectionCostsWriter costs = new ConnectionCostsWriter(forwardSize, backwardSize);

		while ((line = lineReader.readLine()) != null) {
			String[] fields = line.split("\\s+");

			assert fields.length == 3;

			int forwardId = Integer.parseInt(fields[0]);
			int backwardId = Integer.parseInt(fields[1]);
			int cost = Integer.parseInt(fields[2]);

			costs.add(forwardId, backwardId, cost);
		}
		return costs;
	}
}
