package org.openlca.app.cloud.ui.compare.json;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Map.Entry;
import java.util.Set;

import org.openlca.app.cloud.ui.compare.json.JsonUtil.ElementFinder;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public abstract class JsonNodeBuilder implements Comparator<JsonNode> {

	private ElementFinder elementFinder;

	public JsonNodeBuilder(ElementFinder elementFinder) {
		this.elementFinder = elementFinder;
	}

	public JsonNode build(JsonElement localJson, JsonElement remoteJson) {
		JsonNode node = JsonNode.create(null, null, localJson, remoteJson,
				elementFinder, false);
		build(node, localJson, remoteJson);
		sort(node);
		return node;
	}

	private void build(JsonNode node, JsonElement local, JsonElement remote) {
		if (local != null)
			build(node, local, remote, true);
		else if (remote != null)
			build(node, local, remote, false);
	}

	private void build(JsonNode node, JsonElement local, JsonElement remote,
			boolean forLocal) {
		JsonElement toCheck = forLocal ? local : remote;
		if (toCheck.isJsonObject())
			build(node, JsonUtil.toJsonObject(local),
					JsonUtil.toJsonObject(remote));
		if (toCheck.isJsonArray())
			build(node, JsonUtil.toJsonArray(local),
					JsonUtil.toJsonArray(remote));
	}

	private void build(JsonNode node, JsonObject local, JsonObject remote) {
		Set<String> added = new HashSet<>();
		if (local != null)
			buildChildren(node, local, remote, added, true);
		if (remote != null)
			buildChildren(node, remote, local, added, false);
	}

	private void buildChildren(JsonNode node, JsonObject json,
			JsonObject other, Set<String> added, boolean forLocal) {
		for (Entry<String, JsonElement> child : json.entrySet()) {
			if (!forLocal && added.contains(child.getKey()))
				continue;
			JsonElement otherValue = null;
			if (other != null)
				otherValue = other.get(child.getKey());
			if (forLocal) {
				build(node, child.getKey(), child.getValue(), otherValue);
				added.add(child.getKey());
			} else
				build(node, child.getKey(), otherValue, child.getValue());
		}
	}

	private void build(JsonNode node, JsonArray local, JsonArray remote) {
		Set<Integer> added = new HashSet<>();
		if (local != null)
			buildChildren(node, local, remote, true, added);
		if (remote != null)
			buildChildren(node, remote, local, false, added);
	}

	private void buildChildren(JsonNode node, JsonArray array,
			JsonArray otherArray, boolean forLocal, Set<Integer> added) {
		int count = 0;
		int counter = node.children.size() + 1;
		for (JsonElement value : array) {
			if (!forLocal && added.contains(count++))
				continue;
			JsonElement otherValue = null;
			int index = elementFinder.find(node.property, value, otherArray);
			if (forLocal && index != -1) {
				otherValue = otherArray.get(index);
				added.add(index);
			}
			JsonElement local = forLocal ? value : otherValue;
			JsonElement remote = forLocal ? otherValue : value;
			String property = Integer.toString(counter++);
			JsonElement parent = node.parent.getElement(forLocal);
			JsonNode childNode = JsonNode.create(node, property, local, remote,
					elementFinder, isReadOnly(parent, property));
			if (!skipChildren(parent, value))
				build(childNode, local, remote);
			node.children.add(childNode);
		}
	}

	private void build(JsonNode parent, String property,
			JsonElement localValue, JsonElement remoteValue) {
		if (skip(parent.getElement(), property))
			return;
		JsonNode childNode = JsonNode.create(parent, property, localValue,
				remoteValue, elementFinder,
				isReadOnly(parent.getElement(), property));
		parent.children.add(childNode);
		if (localValue == null) {
			if (skipChildren(parent.getRemoteElement(), remoteValue))
				return;
		} else if (skipChildren(parent.getLocalElement(), localValue))
			return;
		build(childNode, localValue, remoteValue);
	}

	private void sort(JsonNode node) {
		Collections.sort(node.children, this);
		for (JsonNode child : node.children)
			sort(child);
	}

	protected abstract boolean skip(JsonElement parent, String property);

	protected abstract boolean skipChildren(JsonElement parent,
			JsonElement element);

	protected abstract boolean isReadOnly(JsonElement parent, String property);

}