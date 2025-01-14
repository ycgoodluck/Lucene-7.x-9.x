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

import java.util.Arrays;

import org.apache.lucene.document.LatLonShape.QueryRelation;
import org.apache.lucene.geo.GeoEncodingUtils;
import org.apache.lucene.geo.Polygon;
import org.apache.lucene.geo.Polygon2D;
import org.apache.lucene.index.PointValues.Relation;

/**
 * Finds all previously indexed shapes that intersect the specified arbitrary.
 *
 * <p>The field must be indexed using
 * {@link org.apache.lucene.document.LatLonShape#createIndexableFields(String, Polygon)} added per document.
 *
 * @lucene.experimental
 **/
final class LatLonShapePolygonQuery extends LatLonShapeQuery {
	final Polygon[] polygons;
	final private Polygon2D poly2D;

	/**
	 * Creates a query that matches all indexed shapes to the provided polygons
	 */
	public LatLonShapePolygonQuery(String field, QueryRelation queryRelation, Polygon... polygons) {
		super(field, queryRelation);
		if (polygons == null) {
			throw new IllegalArgumentException("polygons must not be null");
		}
		if (polygons.length == 0) {
			throw new IllegalArgumentException("polygons must not be empty");
		}
		for (int i = 0; i < polygons.length; i++) {
			if (polygons[i] == null) {
				throw new IllegalArgumentException("polygon[" + i + "] must not be null");
			} else if (polygons[i].minLon > polygons[i].maxLon) {
				throw new IllegalArgumentException("LatLonShapePolygonQuery does not currently support querying across dateline.");
			}
		}
		this.polygons = polygons.clone();
		this.poly2D = Polygon2D.create(polygons);
	}

	@Override
	protected Relation relateRangeBBoxToQuery(int minXOffset, int minYOffset, byte[] minTriangle,
																						int maxXOffset, int maxYOffset, byte[] maxTriangle) {
		double minLat = GeoEncodingUtils.decodeLatitude(minTriangle, minYOffset);
		double minLon = GeoEncodingUtils.decodeLongitude(minTriangle, minXOffset);
		double maxLat = GeoEncodingUtils.decodeLatitude(maxTriangle, maxYOffset);
		double maxLon = GeoEncodingUtils.decodeLongitude(maxTriangle, maxXOffset);

		// check internal node against query
		return poly2D.relate(minLat, maxLat, minLon, maxLon);
	}

	@Override
	protected boolean queryMatches(byte[] triangle) {
		double ay = GeoEncodingUtils.decodeLatitude(triangle, 0);
		double ax = GeoEncodingUtils.decodeLongitude(triangle, LatLonPoint.BYTES);
		double by = GeoEncodingUtils.decodeLatitude(triangle, 2 * LatLonPoint.BYTES);
		double bx = GeoEncodingUtils.decodeLongitude(triangle, 3 * LatLonPoint.BYTES);
		double cy = GeoEncodingUtils.decodeLatitude(triangle, 4 * LatLonPoint.BYTES);
		double cx = GeoEncodingUtils.decodeLongitude(triangle, 5 * LatLonPoint.BYTES);

		if (queryRelation == QueryRelation.WITHIN) {
			return poly2D.relateTriangle(ax, ay, bx, by, cx, cy) == Relation.CELL_INSIDE_QUERY;
		}
		// INTERSECTS
		return poly2D.relateTriangle(ax, ay, bx, by, cx, cy) != Relation.CELL_OUTSIDE_QUERY;
	}

	@Override
	public String toString(String field) {
		final StringBuilder sb = new StringBuilder();
		sb.append(getClass().getSimpleName());
		sb.append(':');
		if (this.field.equals(field) == false) {
			sb.append(" field=");
			sb.append(this.field);
			sb.append(':');
		}
		sb.append("Polygon(" + polygons[0].toGeoJSON() + ")");
		return sb.toString();
	}

	@Override
	protected boolean equalsTo(Object o) {
		return super.equalsTo(o) && Arrays.equals(polygons, ((LatLonShapePolygonQuery) o).polygons);
	}

	@Override
	public int hashCode() {
		int hash = super.hashCode();
		hash = 31 * hash + Arrays.hashCode(polygons);
		return hash;
	}
}
