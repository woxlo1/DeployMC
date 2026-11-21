package io.github.woxlo1.deploymc.core.snapshot;

import io.github.woxlo1.deploymc.core.config.ConfigException;
import io.github.woxlo1.deploymc.core.config.ConfigYamlParser;
import io.github.woxlo1.deploymc.core.config.YamlValue;
import io.github.woxlo1.deploymc.core.files.FileRevision;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Strict reader and deterministic writer for format-1 snapshot JSON records. */
final class SnapshotJson {

  static final Pattern SNAPSHOT_ID = Pattern.compile("^snap_[0-9a-z]{26}$");
  private static final Pattern MODE = Pattern.compile("^[0-7]{4}$");
  private static final Set<String> PLUGIN_FIELDS =
      new LinkedHashSet<String>(Arrays.asList("name", "version", "descriptor-summary"));

  private SnapshotJson() {}

  static String writeRecord(SnapshotRecord record) {
    StringBuilder json = new StringBuilder(768);
    json.append('{');
    field(json, "id").append(quote(record.id())).append(',');
    field(json, "capture").append(quote(record.capture())).append(',');
    field(json, "path").append(quote(record.path())).append(',');
    field(json, "kind").append(quote(record.kind().value())).append(',');
    field(json, "hash").append(nullable(record.hash())).append(',');
    field(json, "size").append(record.size()).append(',');
    field(json, "mode").append(nullable(record.mode())).append(',');
    field(json, "created-at").append(quote(record.createdAt().toString())).append(',');
    field(json, "actor").append(quote(record.actor())).append(',');
    field(json, "reason").append(quote(record.reason().value())).append(',');
    field(json, "deployment-id").append(nullable(record.deploymentId())).append(',');
    field(json, "parent").append(nullable(record.parent())).append(',');
    field(json, "plugin-metadata").append(writePluginMetadata(record.pluginMetadata()));
    return json.append('}').toString();
  }

  static SnapshotRecord parseRecord(String source, String expectedId) {
    YamlValue root = parse(source);
    String id = requireString(root, "id");
    if (!SNAPSHOT_ID.matcher(id).matches() || !id.equals(expectedId)) {
      throw invalid("metadata id does not match file name");
    }
    String path = requireString(root, "path");
    if (path.isEmpty()) {
      throw invalid("metadata path is empty");
    }
    SnapshotKind kind = SnapshotKind.fromValue(requireString(root, "kind"));
    if (kind == null) {
      throw invalid("metadata kind is invalid");
    }
    String hash = optionalString(root, "hash");
    long size = requireLong(root, "size");
    if (size < 0L) {
      throw invalid("metadata size is negative");
    }
    String mode = optionalString(root, "mode");
    if (mode != null && !MODE.matcher(mode).matches()) {
      throw invalid("metadata mode is invalid");
    }
    Instant createdAt;
    try {
      createdAt = Instant.parse(requireString(root, "created-at"));
    } catch (DateTimeParseException exception) {
      throw invalid("metadata created-at is invalid");
    }
    String actor = requireString(root, "actor");
    if (actor.isEmpty()) {
      throw invalid("metadata actor is empty");
    }
    SnapshotReason reason = SnapshotReason.fromValue(requireString(root, "reason"));
    if (reason == null) {
      throw invalid("metadata reason is invalid");
    }
    String deploymentId = optionalString(root, "deployment-id");
    String parent = optionalString(root, "parent");
    if (parent != null && !SNAPSHOT_ID.matcher(parent).matches()) {
      throw invalid("metadata parent is invalid");
    }
    if (kind.hasContent()) {
      if (!FileRevision.isValid(hash)) {
        throw invalid("content record has an invalid hash");
      }
    } else if (hash != null || size != 0L) {
      throw invalid("non-content record has content fields");
    }
    Map<String, String> pluginMetadata = parsePluginMetadata(root.get("plugin-metadata"));
    return new SnapshotRecord(
        id,
        requireString(root, "capture"),
        path,
        kind,
        hash,
        size,
        mode,
        createdAt,
        actor,
        reason,
        deploymentId,
        parent,
        pluginMetadata);
  }

  static String writeIndex(String path, List<String> ids) {
    StringBuilder json = new StringBuilder(256);
    json.append('{');
    field(json, "path").append(quote(path)).append(',');
    field(json, "ids").append('[');
    for (int i = 0; i < ids.size(); i++) {
      if (i > 0) {
        json.append(',');
      }
      json.append(quote(ids.get(i)));
    }
    return json.append(']').append('}').toString();
  }

  static IndexData parseIndex(String source) {
    YamlValue root = parse(source);
    String path = requireString(root, "path");
    if (path.isEmpty()) {
      throw invalid("index path is empty");
    }
    YamlValue ids = root.get("ids");
    if (ids == null || !ids.isArray()) {
      throw invalid("index ids are not an array");
    }
    List<String> result = new ArrayList<String>();
    for (YamlValue item : ids.items()) {
      if (!item.isString() || !SNAPSHOT_ID.matcher(item.raw().toString()).matches()) {
        throw invalid("index contains an invalid snapshot id");
      }
      result.add(item.raw().toString());
    }
    return new IndexData(path, result);
  }

  private static Map<String, String> parsePluginMetadata(YamlValue value) {
    Map<String, String> result = new java.util.LinkedHashMap<String, String>();
    if (value == null || value.isNull()) {
      return result;
    }
    if (!value.isObject()) {
      throw invalid("plugin metadata is not an object");
    }
    for (String key : value.keys()) {
      if (!PLUGIN_FIELDS.contains(key)) {
        throw invalid("plugin metadata contains an unknown field");
      }
      YamlValue field = value.get(key);
      if (field == null || !field.isString()) {
        throw invalid("plugin metadata field is not a string");
      }
      result.put(key, field.raw().toString());
    }
    return result;
  }

  private static String writePluginMetadata(Map<String, String> metadata) {
    if (metadata == null || metadata.isEmpty()) {
      return "null";
    }
    StringBuilder json = new StringBuilder(192);
    json.append('{');
    boolean first = true;
    for (String key : PLUGIN_FIELDS) {
      String value = metadata.get(key);
      if (value == null) {
        continue;
      }
      if (!first) {
        json.append(',');
      }
      first = false;
      field(json, key).append(quote(value));
    }
    return json.append('}').toString();
  }

  private static YamlValue parse(String source) {
    try {
      return ConfigYamlParser.parse(source);
    } catch (ConfigException exception) {
      throw invalid("record is not valid JSON");
    }
  }

  private static String requireString(YamlValue parent, String key) {
    YamlValue value = parent.get(key);
    if (value == null || !value.isString()) {
      throw invalid("missing or invalid field " + key);
    }
    return value.raw().toString();
  }

  private static String optionalString(YamlValue parent, String key) {
    YamlValue value = parent.get(key);
    if (value == null || value.isNull()) {
      return null;
    }
    if (!value.isString()) {
      throw invalid("invalid field " + key);
    }
    return value.raw().toString();
  }

  private static long requireLong(YamlValue parent, String key) {
    YamlValue value = parent.get(key);
    if (value == null || !value.isInteger()) {
      throw invalid("missing or invalid field " + key);
    }
    return ((Long) value.raw()).longValue();
  }

  private static StringBuilder field(StringBuilder json, String name) {
    return json.append(quote(name)).append(':');
  }

  private static String nullable(String value) {
    return value == null ? "null" : quote(value);
  }

  private static String quote(String value) {
    StringBuilder result = new StringBuilder(value.length() + 2);
    result.append('"');
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      switch (c) {
        case '"':
          result.append("\\\"");
          break;
        case '\\':
          result.append("\\\\");
          break;
        case '\b':
          result.append("\\b");
          break;
        case '\f':
          result.append("\\f");
          break;
        case '\n':
          result.append("\\n");
          break;
        case '\r':
          result.append("\\r");
          break;
        case '\t':
          result.append("\\t");
          break;
        default:
          if (c < 0x20) {
            result.append(String.format("\\u%04x", Integer.valueOf(c)));
          } else {
            result.append(c);
          }
      }
    }
    return result.append('"').toString();
  }

  private static IllegalArgumentException invalid(String detail) {
    return new IllegalArgumentException(detail);
  }

  /** Parsed per-path lookup index. */
  static final class IndexData {
    private final String path;
    private final List<String> ids;

    IndexData(String path, List<String> ids) {
      this.path = path;
      this.ids = Collections.unmodifiableList(ids);
    }

    String path() {
      return path;
    }

    List<String> ids() {
      return ids;
    }
  }
}
