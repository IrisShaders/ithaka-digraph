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
package de.odysseus.ithaka.digraph.io.graphml.yfiles;

import java.awt.Font;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import de.odysseus.ithaka.digraph.Digraph;
import de.odysseus.ithaka.digraph.DigraphProvider;
import de.odysseus.ithaka.digraph.io.graphml.GraphMLExporter;
import de.odysseus.ithaka.digraph.io.graphml.GraphMLProperty;
import de.odysseus.ithaka.digraph.io.graphml.GraphMLPropertyDomain;
import de.odysseus.ithaka.digraph.io.graphml.GraphMLProvider;
import de.odysseus.ithaka.digraph.io.graphml.SimpleGraphMLProvider;
import de.odysseus.ithaka.digraph.layout.DigraphLayoutArc;
import de.odysseus.ithaka.digraph.layout.DigrpahLayoutBuilder;
import de.odysseus.ithaka.digraph.layout.DigraphLayoutNode;

public class YFilesGraphML<V> {
	private static final String SCHEMA_LOCATION = "http://www.yworks.com/xml/schema/graphml/1.1/ygraphml.xsd";
	
	private final LayoutTree<V> layoutTree;
	private final SimpleGraphMLProvider<DigraphLayoutNode<V>, DigraphLayoutArc<V>, Digraph<? extends DigraphLayoutNode<V>,? extends DigraphLayoutArc<V>>> provider;
	
	static abstract class GraphMLPropertyDelegate<S, T> implements GraphMLProperty<T> {
		final GraphMLProperty<S> property;

		public GraphMLPropertyDelegate(GraphMLProperty<S> property) {
			this.property = property;
		}
		@Override
		public GraphMLPropertyDomain getDomain() {
			return property.getDomain();
		}
		@Override
		public String getNamespaceURI() {
			return property.getNamespaceURI();
		}
		@Override
		public String getPrefix() {
			return property.getPrefix();
		}
		@Override
		public void writeKey(XMLStreamWriter writer, String id) throws XMLStreamException {
			property.writeKey(writer, id);
		}
	}

	public YFilesGraphML(
			Digraph<V> digraph,
			DigrpahLayoutBuilder<V> builder,
			LabelResolver<? super V> labels,
			Font font) {
		this(digraph, builder, labels, null, font, font);
	}

	public YFilesGraphML(
			Digraph<V> digraph,
			DigrpahLayoutBuilder<V> builder,
			LabelResolver<? super V> nodeLabels,
			LabelResolver<? super E> edgeLabels,
			Font nodeFont,
			Font edgeFont) {
			this(digraph, null, builder, nodeLabels, edgeLabels, nodeFont, edgeFont, false);
	}

	public YFilesGraphML(
			Digraph<V> digraph,
			DigraphProvider<V, Digraph<V>> subgraphs,
			DigrpahLayoutBuilder<V> builder,
			LabelResolver<? super V> nodeLabels,
			Font nodeFont,
			boolean groupNodes) {
		this(digraph, subgraphs, builder, nodeLabels, null, nodeFont, null, groupNodes);
	}

	public YFilesGraphML(
			Digraph<V> digraph,
			DigraphProvider<V, Digraph<V>> subgraphs,
			DigrpahLayoutBuilder<V> builder,
			LabelResolver<? super V> nodeLabels,
			LabelResolver<? super E> edgeLabels,
			Font nodeFont,
			Font edgeFont,
			boolean groupNodes) {
		this(digraph, subgraphs, new SimpleGraphMLProvider<V, Digraph<V>>(), builder, nodeLabels, edgeLabels, nodeFont, edgeFont, groupNodes);
	}

	public YFilesGraphML(
			Digraph<V> digraph,
			DigraphProvider<V, Digraph<V>> subgraphs,
			GraphMLProvider<V, Digraph<V>> delegate,
			DigrpahLayoutBuilder<V> builder,
			LabelResolver<? super V> nodeLabels,
			LabelResolver<? super E> edgeLabels,
			Font nodeFont,
			Font edgeFont,
			boolean groupNodes) {
		this(
			delegate,
			new LayoutTree<V>(digraph, subgraphs, builder, nodeLabels, nodeFont),
			new NodeGraphicsProperty<V>(nodeLabels, nodeFont, subgraphs, groupNodes),
			new EdgeGraphicsProperty<V>(edgeLabels, edgeFont));
	}

	public YFilesGraphML(
			GraphMLProvider<V, Digraph<V>> delegate,
			LayoutTree<V> layoutTree,
			NodeGraphicsProperty<V> nodeGraphics,
			EdgeGraphicsProperty<V> edgeGraphics) {
		this.layoutTree = layoutTree;
		this.provider = new SimpleGraphMLProvider<DigraphLayoutNode<V>, DigraphLayoutArc<V>, Digraph<? extends DigraphLayoutNode<V>,? extends DigraphLayoutArc<V>>>(SCHEMA_LOCATION);

		for (final GraphMLProperty<Digraph<V>> graphProperty : delegate.getGraphProperties()) {
			provider.addGraphProperty(new GraphMLPropertyDelegate<Digraph<V>, Digraph<? extends DigraphLayoutNode<V>,? extends DigraphLayoutArc<V>>>(graphProperty) {
				@Override
				public void writeData(XMLStreamWriter writer, String id, Digraph<? extends DigraphLayoutNode<V>, ? extends DigraphLayoutArc<V>> value) throws XMLStreamException {
					property.writeData(writer, id, findDigraph(YFilesGraphML.this.layoutTree, value));
				}
				Digraph<V> findDigraph(LayoutTree<V> layoutTree, Digraph<? extends DigraphLayoutNode<V>, ? extends DigraphLayoutArc<V>> layoutGraph) {
					if (layoutTree.getLayout().getLayoutGraph() == layoutGraph) {
						return layoutTree.getDigraph();
					}
					for (V subtreeVertex : layoutTree.subtreeVertices()) {
						Digraph<V> result = findDigraph(layoutTree.getSubtree(subtreeVertex), layoutGraph);
						if (result != null) {
							return result;
						}
					}
					return null;
				}
			});
		}

		for (final GraphMLProperty<V> nodeProperty : delegate.getNodeProperties()) {
			provider.addNodeProperty(new GraphMLPropertyDelegate<V, DigraphLayoutNode<V>>(nodeProperty) {
				@Override
				public void writeData(XMLStreamWriter writer, String id, DigraphLayoutNode<V> value) throws XMLStreamException {
					property.writeData(writer, id, value.getVertex());
				}
			});
		}

		for (final GraphMLProperty<E> edgeProperty : delegate.getEdgeProperties()) {
			provider.addEdgeProperty(new GraphMLPropertyDelegate<E, DigraphLayoutArc<V>>(edgeProperty) {
				@Override
				public void writeData(XMLStreamWriter writer, String id, DigraphLayoutArc<V> value) throws XMLStreamException {
					property.writeData(writer, id, value.getEdge());
				}
			});
		}

		provider.addNodeProperty(nodeGraphics);
		provider.addEdgeProperty(edgeGraphics);
	}
	
	private DigraphProvider<? super DigraphLayoutNode<V>, Digraph<? extends DigraphLayoutNode<V>, ? extends DigraphLayoutArc<V>>> getSubgraphProvider() {
		return new DigraphProvider<DigraphLayoutNode<V>, Digraph<? extends DigraphLayoutNode<V>,? extends DigraphLayoutArc<V>>>() {
			@Override
			public Digraph<? extends DigraphLayoutNode<V>, ? extends DigraphLayoutArc<V>> get(DigraphLayoutNode<V> node) {
				LayoutTree<V> subtree = layoutTree.find(node.getVertex());
				return subtree == null ? null : subtree.getLayout().getLayoutGraph();
			}
		};
	}
	
	public void export(XMLStreamWriter writer) throws XMLStreamException {
		new GraphMLExporter().export(provider, layoutTree.getLayout().getLayoutGraph(), getSubgraphProvider(), writer);
	}
}
