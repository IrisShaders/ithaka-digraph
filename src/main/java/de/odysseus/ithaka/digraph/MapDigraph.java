/*
 * Copyright 2012 Odysseus Software GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.odysseus.ithaka.digraph;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.TreeMap;

/**
 * Map-based directed graph implementation.
 *
 * @param <V> vertex type
 */
public class MapDigraph<V> implements Digraph<V> {
	/**
	 * Factory creating default <code>MapDigraph</code>.
	 *
	 * @return map digraph factory
	 */
	public static <V> DigraphFactory<MapDigraph<V>> getDefaultDigraphFactory() {
		return getMapDigraphFactory(MapDigraph.getDefaultVertexMapFactory(null), MapDigraph.getDefaultEdgeMapFactory(null));
	}

	/**
	 * Factory creating <code>MapDigraph</code>.
	 *
	 * @param vertexMapFactory factory to create vertex --> edge-map maps
	 * @param edgeMapFactory   factory to create edge-target --> edge-value maps
	 * @return map digraph factory
	 */
	public static <V> DigraphFactory<MapDigraph<V>> getMapDigraphFactory(
			final VertexMapFactory<V> vertexMapFactory,
			final EdgeMapFactory<V> edgeMapFactory) {
		return () -> new MapDigraph<>(vertexMapFactory, edgeMapFactory);
	}

	/**
	 * Vertex map factory (vertex to edge map).
	 */
	public interface VertexMapFactory<V> {
		Map<V, Map<V, Integer>> create();
	}

	/**
	 * Edge map factory (edge target to edge value).
	 */
	public interface EdgeMapFactory<V> {
		Map<V, Integer> create(V source);
	}

	private static <V> VertexMapFactory<V> getDefaultVertexMapFactory(final Comparator<? super V> comparator) {
		return new VertexMapFactory<V>() {
			@Override
			public Map<V, Map<V, Integer>> create() {
				if (comparator == null) {
					return new LinkedHashMap<>(16);
				} else {
					return new TreeMap<>(comparator);
				}
			}
		};
	}

	private static <V> EdgeMapFactory<V> getDefaultEdgeMapFactory(final Comparator<? super V> comparator) {
		return new EdgeMapFactory<V>() {
			@Override
			public Map<V, Integer> create(V ignore) {
				if (comparator == null) {
					return new LinkedHashMap<>(16);
				} else {
					return new TreeMap<>(comparator);
				}
			}
		};
	}

	private final VertexMapFactory<V> vertexMapFactory;
	private final EdgeMapFactory<V> edgeMapFactory;
	private final Map<V, Map<V, Integer>> vertexMap;

	private int edgeCount;

	/**
	 * Create digraph.
	 * {@link LinkedHashMap}s will be used as vertex/edge maps.
	 * Vertices and edge targets will be iterated in no particular order.
	 */
	public MapDigraph() {
		this(null);
	}

	/**
	 * Create digraph.
	 * If a vertex comparator is given, {@link TreeMap}s will be used as vertex/edge maps.
	 * Vertices and edge targets will be iterated in the order given by the comparator.
	 *
	 * @param comparator vertex comparator (may be <code>null</code>)
	 */
	public MapDigraph(final Comparator<? super V> comparator) {
		this(comparator, comparator);
	}

	/**
	 * Create digraph.
	 * If a vertex comparator is given, {@link TreeMap}s will be used as vertex maps
	 * and vertices will be iterated in the order given by the vertex comparator.
	 * If an edge comparator is given, {@link TreeMap}s will be used as edge maps
	 * and edge targets will be iterated in the order given by the edge comparator.
	 */
	public MapDigraph(final Comparator<? super V> vertexComparator, final Comparator<? super V> edgeComparator) {
		this(MapDigraph.getDefaultVertexMapFactory(vertexComparator), MapDigraph.getDefaultEdgeMapFactory(edgeComparator));
	}

	/**
	 * Create digraph.
	 *
	 * @param vertexMapFactory factory to create vertex --> edge-map maps
	 * @param edgeMapFactory   factory to create edge-target --> edge-value maps
	 */
	public MapDigraph(VertexMapFactory<V> vertexMapFactory, EdgeMapFactory<V> edgeMapFactory) {
		this.vertexMapFactory = vertexMapFactory;
		this.edgeMapFactory = edgeMapFactory;

		vertexMap = vertexMapFactory.create();
	}

	@Override
	public boolean add(V vertex) {
		if (!vertexMap.containsKey(vertex)) {
			vertexMap.put(vertex, Collections.emptyMap());
			return true;
		}
		return false;
	}

	@Override
	public OptionalInt put(V source, V target, int weight) {
		Map<V, Integer> edgeMap = vertexMap.get(source);

		if (edgeMap == null || edgeMap.isEmpty()) {
			vertexMap.put(source, edgeMap = edgeMapFactory.create(source));
		}

		Integer result = edgeMap.put(target, weight);

		if (result == null) {
			add(target);
			edgeCount++;
		}

		return result == null ? OptionalInt.empty() : OptionalInt.of(result);
	}

	@Override
	public OptionalInt get(V source, V target) {
		Map<V, Integer> edgeMap = vertexMap.get(source);

		if (edgeMap == null) {
			return OptionalInt.empty();
		}

		Integer result = edgeMap.get(target);

		return result == null ? OptionalInt.empty() : OptionalInt.of(result);
	}

	@Override
	public OptionalInt remove(V source, V target) {
		Map<V, Integer> edgeMap = vertexMap.get(source);
		if (edgeMap == null || !edgeMap.containsKey(target)) {
			return OptionalInt.empty();
		}
		Integer result = edgeMap.remove(target);
		edgeCount--;
		if (edgeMap.isEmpty()) {
			vertexMap.put(source, Collections.emptyMap());
		}
		return result == null ? OptionalInt.empty() : OptionalInt.of(result);
	}

	@Override
	public boolean remove(V vertex) {
		Map<V, Integer> edgeMap = vertexMap.get(vertex);
		if (edgeMap == null) {
			return false;
		}
		edgeCount -= edgeMap.size();
		vertexMap.remove(vertex);
		for (V source : vertexMap.keySet()) {
			remove(source, vertex);
		}
		return true;
	}

	@Override
	public void removeAll(Collection<V> vertices) {
		for (V vertex : vertices) {
			Map<V, Integer> edgeMap = vertexMap.get(vertex);
			if (edgeMap != null) {
				edgeCount -= edgeMap.size();
				vertexMap.remove(vertex);
			}
		}
		for (V source : vertexMap.keySet()) {
			Map<V, Integer> edgeMap = vertexMap.get(source);
			Iterator<V> iterator = edgeMap.keySet().iterator();
			while (iterator.hasNext()) {
				if (vertices.contains(iterator.next())) {
					iterator.remove();
					edgeCount--;
				}
			}
			if (edgeMap.isEmpty()) {
				vertexMap.put(source, Collections.emptyMap());
			}
		}
	}

	@Override
	public boolean contains(V source, V target) {
		Map<V, Integer> edgeMap = vertexMap.get(source);

		if (edgeMap == null) {
			return false;
		}

		return edgeMap.containsKey(target);
	}

	@Override
	public boolean contains(V vertex) {
		return vertexMap.containsKey(vertex);
	}

	@Override
	public Iterable<V> vertices() {
		if (vertexMap.isEmpty()) {
			return Collections.emptySet();
		}
		return new Iterable<V>() {
			@Override
			public Iterator<V> iterator() {
				return new Iterator<V>() {
					private final Iterator<V> delegate = vertexMap.keySet().iterator();
					V vertex = null;

					@Override
					public boolean hasNext() {
						return delegate.hasNext();
					}

					@Override
					public V next() {
						return vertex = delegate.next();
					}

					@Override
					public void remove() {
						Map<V, Integer> edgeMap = vertexMap.get(vertex);
						delegate.remove();
						edgeCount -= edgeMap.size();
						for (V source : vertexMap.keySet()) {
							MapDigraph.this.remove(source, vertex);
						}
					}
				};
			}

			@Override
			public String toString() {
				return vertexMap.keySet().toString();
			}
		};
	}

	@Override
	public Iterable<V> targets(final V source) {
		final Map<V, Integer> edgeMap = vertexMap.get(source);
		if (edgeMap == null || edgeMap.isEmpty()) {
			return Collections.emptySet();
		}
		return new Iterable<V>() {
			@Override
			public Iterator<V> iterator() {
				return new Iterator<V>() {
					private final Iterator<V> delegate = edgeMap.keySet().iterator();

					@Override
					public boolean hasNext() {
						return delegate.hasNext();
					}

					@Override
					public V next() {
						return delegate.next();
					}

					@Override
					public void remove() {
						delegate.remove();
						edgeCount--;
						if (edgeMap.isEmpty()) {
							vertexMap.put(source, Collections.emptyMap());
						}
					}
				};
			}

			@Override
			public String toString() {
				return edgeMap.keySet().toString();
			}
		};
	}

	@Override
	public int getVertexCount() {
		return vertexMap.size();
	}

	@Override
	public int totalWeight() {
		int weight = 0;

		for (V source : vertices()) {
			for (V target : targets(source)) {
				weight += get(source, target).getAsInt();
			}
		}

		return weight;
	}

	@Override
	public int getOutDegree(V vertex) {
		Map<V, Integer> edgeMap = vertexMap.get(vertex);
		if (edgeMap == null) {
			return 0;
		}
		return edgeMap.size();
	}

	@Override
	public int getEdgeCount() {
		return edgeCount;
	}

	public DigraphFactory<? extends MapDigraph<V>> getDigraphFactory() {
		return () -> new MapDigraph<>(vertexMapFactory, edgeMapFactory);
	}

	@Override
	public MapDigraph<V> reverse() {
		return Digraphs.<V, MapDigraph<V>>reverse(this, getDigraphFactory());
	}

	@Override
	public MapDigraph<V> subgraph(Set<V> vertices) {
		return Digraphs.<V, MapDigraph<V>>subgraph(this, vertices, getDigraphFactory());
	}

	@Override
	public boolean isAcyclic() {
		return Digraphs.isAcyclic(this);
	}

	@Override
	public String toString() {
		StringBuilder b = new StringBuilder();
		b.append(getClass().getName().substring(getClass().getName().lastIndexOf('.') + 1));
		b.append("(");
		Iterator<V> vertices = vertices().iterator();
		while (vertices.hasNext()) {
			V v = vertices.next();
			b.append(v);
			b.append(targets(v));
			if (vertices.hasNext()) {
				b.append(", ");
				if (b.length() > 1000) {
					b.append("...");
					break;
				}
			}
		}
		b.append(")");
		return b.toString();
	}
}
