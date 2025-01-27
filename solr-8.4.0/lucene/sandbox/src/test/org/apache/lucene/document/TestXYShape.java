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
package org.apache.lucene.document;

import org.apache.lucene.document.ShapeField.QueryRelation;
import org.apache.lucene.geo.ShapeTestUtil;
import org.apache.lucene.geo.Tessellator;
import org.apache.lucene.geo.XYLine;
import org.apache.lucene.geo.XYPolygon;
import org.apache.lucene.geo.XYRectangle;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.RandomIndexWriter;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.store.Directory;
import org.apache.lucene.util.IOUtils;
import org.apache.lucene.util.LuceneTestCase;
import org.apache.lucene.util.TestUtil;

/**
 * Test case for indexing cartesian shapes and search by bounding box, lines, and polygons
 */
public class TestXYShape extends LuceneTestCase {

	protected static String FIELDNAME = "field";

	protected static void addPolygonsToDoc(String field, Document doc, XYPolygon polygon) {
		Field[] fields = XYShape.createIndexableFields(field, polygon);
		for (Field f : fields) {
			doc.add(f);
		}
	}

	protected static void addLineToDoc(String field, Document doc, XYLine line) {
		Field[] fields = XYShape.createIndexableFields(field, line);
		for (Field f : fields) {
			doc.add(f);
		}
	}

	protected Query newRectQuery(String field, double minX, double maxX, double minY, double maxY) {
		return XYShape.newBoxQuery(field, QueryRelation.INTERSECTS, (float) minX, (float) maxX, (float) minY, (float) maxY);
	}

	/**
	 * test we can search for a point with a standard number of vertices
	 */
	public void testBasicIntersects() throws Exception {
		int numVertices = TestUtil.nextInt(random(), 50, 100);
		Directory dir = newDirectory();
		RandomIndexWriter writer = new RandomIndexWriter(random(), dir);

		// add a random polygon document
		XYPolygon p = ShapeTestUtil.createRegularPolygon(0, 90, atLeast(1000000), numVertices);
		Document document = new Document();
		addPolygonsToDoc(FIELDNAME, document, p);
		writer.addDocument(document);

		// add a line document
		document = new Document();
		// add a line string
		float x[] = new float[p.numPoints() - 1];
		float y[] = new float[p.numPoints() - 1];
		for (int i = 0; i < x.length; ++i) {
			x[i] = (float) p.getPolyX(i);
			y[i] = (float) p.getPolyY(i);
		}
		XYLine l = new XYLine(x, y);
		addLineToDoc(FIELDNAME, document, l);
		writer.addDocument(document);

		////// search /////
		// search an intersecting bbox
		IndexReader reader = writer.getReader();
		writer.close();
		IndexSearcher searcher = newSearcher(reader);
		double minX = Math.min(x[0], x[1]);
		double minY = Math.min(y[0], y[1]);
		double maxX = Math.max(x[0], x[1]);
		double maxY = Math.max(y[0], y[1]);
		Query q = newRectQuery(FIELDNAME, minX, maxX, minY, maxY);
		assertEquals(2, searcher.count(q));

		// search a disjoint bbox
		q = newRectQuery(FIELDNAME, p.minX - 1d, p.minX + 1, p.minY - 1d, p.minY + 1d);
		assertEquals(0, searcher.count(q));

		// search w/ an intersecting polygon
		q = XYShape.newPolygonQuery(FIELDNAME, QueryRelation.INTERSECTS, new XYPolygon(
			new float[]{(float) minX, (float) minX, (float) maxX, (float) maxX, (float) minX},
			new float[]{(float) minY, (float) maxY, (float) maxY, (float) minY, (float) minY}
		));
		assertEquals(2, searcher.count(q));

		// search w/ an intersecting line
		q = XYShape.newLineQuery(FIELDNAME, QueryRelation.INTERSECTS, new XYLine(
			new float[]{(float) minX, (float) minX, (float) maxX, (float) maxX},
			new float[]{(float) minY, (float) maxY, (float) maxY, (float) minY}
		));
		assertEquals(2, searcher.count(q));

		IOUtils.close(reader, dir);
	}

	public void testBoundingBoxQueries() throws Exception {
		XYRectangle r1 = ShapeTestUtil.nextBox();
		XYRectangle r2 = ShapeTestUtil.nextBox();
		XYPolygon p;
		//find two boxes so that r1 contains r2
		while (true) {
			// TODO: Should XYRectangle hold values as float?
			if (areBoxDisjoint(r1, r2)) {
				p = toPolygon(r2);
				try {
					Tessellator.tessellate(p);
					break;
				} catch (Exception e) {
					// ignore, try other combination
				}
			}
			r1 = ShapeTestUtil.nextBox();
			r2 = ShapeTestUtil.nextBox();
		}

		Directory dir = newDirectory();
		RandomIndexWriter writer = new RandomIndexWriter(random(), dir);

		// add the polygon to the index
		Document document = new Document();
		addPolygonsToDoc(FIELDNAME, document, p);
		writer.addDocument(document);

		////// search /////
		IndexReader reader = writer.getReader();
		writer.close();
		IndexSearcher searcher = newSearcher(reader);
		// Query by itself should match
		Query q = newRectQuery(FIELDNAME, r2.minX, r2.maxX, r2.minY, r2.maxY);
		assertEquals(1, searcher.count(q));
		// r1 contains r2, intersects should match
		q = newRectQuery(FIELDNAME, r1.minX, r1.maxX, r1.minY, r1.maxY);
		assertEquals(1, searcher.count(q));
		// r1 contains r2, WITHIN should match
		q = XYShape.newBoxQuery(FIELDNAME, QueryRelation.WITHIN, (float) r1.minX, (float) r1.maxX, (float) r1.minY, (float) r1.maxY);
		assertEquals(1, searcher.count(q));

		IOUtils.close(reader, dir);
	}

	private static boolean areBoxDisjoint(XYRectangle r1, XYRectangle r2) {
		return ((float) r1.minX <= (float) r2.minX && (float) r1.minY <= (float) r2.minY && (float) r1.maxX >= (float) r2.maxX && (float) r1.maxY >= (float) r2.maxY);
	}

	private static XYPolygon toPolygon(XYRectangle r) {
		return new XYPolygon(new float[]{(float) r.minX, (float) r.maxX, (float) r.maxX, (float) r.minX, (float) r.minX},
			new float[]{(float) r.minY, (float) r.minY, (float) r.maxY, (float) r.maxY, (float) r.minY});
	}
}
