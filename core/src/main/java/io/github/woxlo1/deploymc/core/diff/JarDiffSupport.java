package io.github.woxlo1.deploymc.core.diff;

import io.github.woxlo1.deploymc.core.config.ConfigException;
import io.github.woxlo1.deploymc.core.config.ConfigYamlParser;
import io.github.woxlo1.deploymc.core.config.YamlValue;
import io.github.woxlo1.deploymc.core.diff.DiffResult.JarDependencyChange;
import io.github.woxlo1.deploymc.core.diff.DiffResult.JarDependencyChangeKind;
import io.github.woxlo1.deploymc.core.diff.DiffResult.JarDescriptorDiff;
import io.github.woxlo1.deploymc.core.diff.DiffResult.JarMetadataDiff;
import io.github.woxlo1.deploymc.core.diff.DiffResult.SemanticChange;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/** Metadata-only comparison of two ZIP archives and recognized plugin descriptors. */
final class JarDiffSupport {

  private static final List<String> SCALAR_FIELDS =
      Collections.unmodifiableList(
          Arrays.asList("name", "version", "main", "apiVersion", "description"));

  private JarDiffSupport() {}

  static Result diff(
      byte[] before, byte[] after, DiffLimits limits, boolean hashChanged, Set<String> notes) {
    ZipMetadataReader.ZipMetadata beforeZip = ZipMetadataReader.read(before, limits);
    ZipMetadataReader.ZipMetadata afterZip = ZipMetadataReader.read(after, limits);
    notes.addAll(beforeZip.notes());
    notes.addAll(afterZip.notes());
    if (beforeZip.descriptors().isEmpty() || afterZip.descriptors().isEmpty()) {
      notes.add(DiffNotes.DESCRIPTOR_MISSING);
    }

    List<String> entriesAdded = entryList(beforeZip.entries(), afterZip.entries(), limits, notes);
    List<String> entriesRemoved = entryList(afterZip.entries(), beforeZip.entries(), limits, notes);

    TreeMap<String, Descriptor> beforeDescriptors = normalize(beforeZip.descriptors(), notes);
    TreeMap<String, Descriptor> afterDescriptors = normalize(afterZip.descriptors(), notes);
    TreeSet<String> descriptorNames = new TreeSet<String>();
    descriptorNames.addAll(beforeDescriptors.keySet());
    descriptorNames.addAll(afterDescriptors.keySet());
    List<JarDescriptorDiff> descriptorDiffs = new ArrayList<JarDescriptorDiff>();
    for (String name : descriptorNames) {
      Descriptor beforeDescriptor = beforeDescriptors.get(name);
      Descriptor afterDescriptor = afterDescriptors.get(name);
      descriptorDiffs.add(compareDescriptor(name, beforeDescriptor, afterDescriptor));
    }

    boolean changed =
        hashChanged
            || !entriesAdded.isEmpty()
            || !entriesRemoved.isEmpty()
            || hasDescriptorChanges(descriptorDiffs);
    JarMetadataDiff metadata =
        new JarMetadataDiff(
            hashChanged,
            (long) after.length - (long) before.length,
            entriesAdded,
            entriesRemoved,
            descriptorDiffs);
    return new Result(metadata, changed);
  }

  private static List<String> entryList(
      Set<String> source, Set<String> target, DiffLimits limits, Set<String> notes) {
    TreeSet<String> sorted = new TreeSet<String>();
    for (String name : source) {
      if (!target.contains(name)) {
        sorted.add(name);
      }
    }
    List<String> result = new ArrayList<String>();
    for (String name : sorted) {
      if (result.size() >= limits.maxJarEntryListOutput()) {
        notes.add(DiffNotes.ENTRY_LIST_TRUNCATED);
        break;
      }
      result.add(name);
    }
    return result;
  }

  private static TreeMap<String, Descriptor> normalize(
      Map<String, String> descriptors, Set<String> notes) {
    TreeMap<String, Descriptor> result = new TreeMap<String, Descriptor>();
    for (Map.Entry<String, String> entry : descriptors.entrySet()) {
      String path = entry.getKey();
      String content = entry.getValue();
      if (content.isEmpty()) {
        notes.add(DiffNotes.DESCRIPTOR_INVALID);
        result.put(path, Descriptor.empty());
        continue;
      }
      YamlValue root;
      try {
        root = ConfigYamlParser.parseSingleDocument(content);
      } catch (ConfigException exception) {
        notes.add(DiffNotes.DESCRIPTOR_INVALID);
        result.put(path, Descriptor.empty());
        continue;
      }
      result.put(path, normalizeDescriptor(path, root));
    }
    return result;
  }

  private static Descriptor normalizeDescriptor(String path, YamlValue root) {
    boolean velocity = "velocity-plugin.json".equals(path);
    TreeMap<String, Scalar> fields = new TreeMap<String, Scalar>();
    putField(fields, "name", root, velocity ? "id" : "name");
    putField(fields, "version", root, "version");
    putField(fields, "main", root, "main");
    putField(fields, "apiVersion", root, "api-version");
    putField(fields, "description", root, "description");
    TreeMap<String, String> dependencies = dependencies(root, velocity);
    TreeMap<String, String> softDependencies = softDependencies(root, velocity);
    return new Descriptor(fields, dependencies, softDependencies);
  }

  private static void putField(
      TreeMap<String, Scalar> fields, String normalized, YamlValue root, String source) {
    YamlValue value = root.get(source);
    if (value == null) {
      fields.put(normalized, Scalar.nullValue());
      return;
    }
    fields.put(normalized, Scalar.of(value));
  }

  private static TreeMap<String, String> dependencies(YamlValue root, boolean velocity) {
    TreeMap<String, String> result = new TreeMap<String, String>();
    if (velocity) {
      YamlValue dependencies = root.get("dependencies");
      if (dependencies == null) {
        return result;
      }
      for (YamlValue item : dependencies.items()) {
        String name = text(item, "id");
        if (!name.isEmpty()) {
          result.put(name, bool(item, "optional") ? "optional" : "required");
        }
      }
      return result;
    }
    addNames(result, root, "depend", "required");
    addNames(result, root, "depends", "required");
    addNames(result, root, "loadbefore", "required");
    return result;
  }

  private static TreeMap<String, String> softDependencies(YamlValue root, boolean velocity) {
    TreeMap<String, String> result = new TreeMap<String, String>();
    if (!velocity) {
      addNames(result, root, "softdepend", "optional");
      addNames(result, root, "softdepends", "optional");
    }
    return result;
  }

  private static void addNames(
      TreeMap<String, String> target, YamlValue root, String key, String scope) {
    YamlValue value = root.get(key);
    if (value == null) {
      return;
    }
    if (value.isArray()) {
      for (YamlValue item : value.items()) {
        String name = canonicalScalar(item);
        if (!name.isEmpty()) {
          target.put(name, scope);
        }
      }
      return;
    }
    String name = canonicalScalar(value);
    if (!name.isEmpty()) {
      target.put(name, scope);
    }
  }

  private static String text(YamlValue object, String key) {
    YamlValue value = object.get(key);
    return value == null ? "" : canonicalScalar(value);
  }

  private static boolean bool(YamlValue object, String key) {
    YamlValue value = object.get(key);
    return value != null
        && value.raw() instanceof Boolean
        && ((Boolean) value.raw()).booleanValue();
  }

  private static JarDescriptorDiff compareDescriptor(
      String name, Descriptor before, Descriptor after) {
    if (before == null) {
      return new JarDescriptorDiff(
          name, "added", fieldChanges(null, after), dependencyChanges(null, after));
    }
    if (after == null) {
      return new JarDescriptorDiff(
          name, "removed", fieldChanges(before, null), dependencyChanges(before, null));
    }
    List<SemanticChange> changes = fieldChanges(before, after);
    List<JarDependencyChange> dependencies = dependencyChanges(before, after);
    String status = changes.isEmpty() && dependencies.isEmpty() ? "unchanged" : "changed";
    return new JarDescriptorDiff(name, status, changes, dependencies);
  }

  private static List<SemanticChange> fieldChanges(Descriptor before, Descriptor after) {
    List<SemanticChange> result = new ArrayList<SemanticChange>();
    for (String field : SCALAR_FIELDS) {
      Scalar beforeValue = before == null ? null : before.fields.get(field);
      Scalar afterValue = after == null ? null : after.fields.get(field);
      if (beforeValue == null && afterValue != null && !afterValue.isNull()) {
        result.add(SemanticChange.added("/" + field, afterValue.value, afterValue.type));
      } else if (afterValue == null && beforeValue != null && !beforeValue.isNull()) {
        result.add(SemanticChange.removed("/" + field, beforeValue.value, beforeValue.type));
      } else if (beforeValue != null && afterValue != null && !beforeValue.equals(afterValue)) {
        if (afterValue.isNull()) {
          result.add(SemanticChange.removed("/" + field, beforeValue.value, beforeValue.type));
        } else if (beforeValue.isNull()) {
          result.add(SemanticChange.added("/" + field, afterValue.value, afterValue.type));
        } else {
          result.add(
              SemanticChange.changed(
                  "/" + field,
                  beforeValue.value,
                  beforeValue.type,
                  afterValue.value,
                  afterValue.type));
        }
      }
    }
    return result;
  }

  private static List<JarDependencyChange> dependencyChanges(Descriptor before, Descriptor after) {
    TreeMap<String, String> beforeDependencies =
        before == null ? new TreeMap<String, String>() : allDependencies(before);
    TreeMap<String, String> afterDependencies =
        after == null ? new TreeMap<String, String>() : allDependencies(after);
    TreeSet<String> names = new TreeSet<String>();
    names.addAll(beforeDependencies.keySet());
    names.addAll(afterDependencies.keySet());
    List<JarDependencyChange> result = new ArrayList<JarDependencyChange>();
    for (String name : names) {
      String beforeScope = beforeDependencies.get(name);
      String afterScope = afterDependencies.get(name);
      if (beforeScope == null) {
        result.add(new JarDependencyChange(name, JarDependencyChangeKind.ADDED, afterScope));
      } else if (afterScope == null) {
        result.add(new JarDependencyChange(name, JarDependencyChangeKind.REMOVED, beforeScope));
      } else if (!beforeScope.equals(afterScope)) {
        result.add(
            new JarDependencyChange(name, JarDependencyChangeKind.SCOPE_CHANGED, afterScope));
      }
    }
    return result;
  }

  private static TreeMap<String, String> allDependencies(Descriptor descriptor) {
    TreeMap<String, String> result = new TreeMap<String, String>(descriptor.dependencies);
    for (Map.Entry<String, String> entry : descriptor.softDependencies.entrySet()) {
      if (!result.containsKey(entry.getKey())) {
        result.put(entry.getKey(), entry.getValue());
      }
    }
    return result;
  }

  private static boolean hasDescriptorChanges(List<JarDescriptorDiff> descriptors) {
    for (JarDescriptorDiff descriptor : descriptors) {
      if (!"unchanged".equals(descriptor.status())) {
        return true;
      }
    }
    return false;
  }

  private static String canonicalScalar(YamlValue value) {
    if (value.isNull()) {
      return "null";
    }
    Object raw = value.raw();
    if (raw instanceof Boolean) {
      return ((Boolean) raw).booleanValue() ? "true" : "false";
    }
    if (raw instanceof Double) {
      return Double.toString(((Double) raw).doubleValue());
    }
    if (raw instanceof Float) {
      return Double.toString(((Float) raw).doubleValue());
    }
    return String.valueOf(raw);
  }

  /** Result of one JAR comparison. */
  static final class Result {
    private final JarMetadataDiff metadata;
    private final boolean changed;

    Result(JarMetadataDiff metadata, boolean changed) {
      this.metadata = metadata;
      this.changed = changed;
    }

    JarMetadataDiff metadata() {
      return metadata;
    }

    boolean changed() {
      return changed;
    }
  }

  private static final class Descriptor {
    private final TreeMap<String, Scalar> fields;
    private final TreeMap<String, String> dependencies;
    private final TreeMap<String, String> softDependencies;

    Descriptor(
        TreeMap<String, Scalar> fields,
        TreeMap<String, String> dependencies,
        TreeMap<String, String> softDependencies) {
      this.fields = fields;
      this.dependencies = dependencies;
      this.softDependencies = softDependencies;
    }

    static Descriptor empty() {
      return new Descriptor(
          new TreeMap<String, Scalar>(),
          new TreeMap<String, String>(),
          new TreeMap<String, String>());
    }
  }

  private static final class Scalar {
    private final String value;
    private final String type;

    Scalar(String value, String type) {
      this.value = value;
      this.type = type;
    }

    static Scalar nullValue() {
      return new Scalar("null", "null");
    }

    static Scalar of(YamlValue value) {
      return new Scalar(canonicalScalar(value), value.jsonType());
    }

    boolean isNull() {
      return "null".equals(type);
    }

    @Override
    public boolean equals(Object other) {
      if (!(other instanceof Scalar)) {
        return false;
      }
      Scalar scalar = (Scalar) other;
      return value.equals(scalar.value) && type.equals(scalar.type);
    }

    @Override
    public int hashCode() {
      return 31 * value.hashCode() + type.hashCode();
    }
  }
}
