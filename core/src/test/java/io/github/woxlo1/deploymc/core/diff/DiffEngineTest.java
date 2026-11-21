package io.github.woxlo1.deploymc.core.diff;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.woxlo1.deploymc.core.diff.DiffResult.ContentInfo;
import io.github.woxlo1.deploymc.core.diff.DiffResult.JarDependencyChange;
import io.github.woxlo1.deploymc.core.diff.DiffResult.LineChangeKind;
import io.github.woxlo1.deploymc.core.diff.DiffResult.SemanticChange;
import io.github.woxlo1.deploymc.core.diff.DiffResult.SemanticChangeKind;
import io.github.woxlo1.deploymc.core.diff.DiffResult.WordSegmentKind;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.TreeSet;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;

class DiffEngineTest {

  @Test
  void identicalAndEmptyInputsHaveHashesAndNoDetails() {
    byte[] empty = new byte[0];
    DiffResult emptyResult = diff(empty, empty, DiffMode.AUTO);

    assertEquals(DiffStatus.IDENTICAL, emptyResult.status());
    assertEquals(DiffStrategy.NONE, emptyResult.strategy());
    assertContentInfo(emptyResult.before(), 0);
    assertContentInfo(emptyResult.after(), 0);
    assertTrue(emptyResult.notes().isEmpty());
    assertNoDetails(emptyResult);

    byte[] content = utf8("same\n");
    DiffResult result = diff(content, utf8("same\n"), DiffMode.TEXT);
    assertEquals(DiffStatus.IDENTICAL, result.status());
    assertEquals(DiffStrategy.NONE, result.strategy());
    assertNoDetails(result);
  }

  @Test
  void byteLimitIsInclusiveAndNextByteReturnsTooLarge() {
    int cap = DiffLimits.DEFAULTS.maxBytesPerSide();
    byte[] before = new byte[cap];
    Arrays.fill(before, (byte) 'a');
    byte[] after = new byte[cap];
    Arrays.fill(after, (byte) 'a');
    after[cap - 1] = (byte) 'b';

    DiffResult accepted = diff(before, after, DiffMode.TEXT);
    assertEquals(DiffStatus.CHANGED, accepted.status());
    assertFalse(accepted.lineHunks().isEmpty());

    byte[] oversized = Arrays.copyOf(after, cap + 1);
    oversized[cap] = (byte) 'c';
    DiffResult rejected = diff(before, oversized, DiffMode.TEXT);
    assertTooLarge(rejected, "bytes-per-side", cap + 1);
    assertContentInfo(rejected.before(), cap);
    assertContentInfo(rejected.after(), cap + 1);
  }

  @Test
  void loweredLimitsAreHonored() {
    DiffLimits limits = DiffLimits.DEFAULTS.withMaxBytesPerSide(4);
    DiffResult result =
        DiffEngine.diff(new DiffRequest(utf8("abcd"), utf8("abcde"), DiffMode.TEXT, limits));
    assertTooLarge(result, "bytes-per-side", 5);
  }

  @Test
  void crlfVersusLfPreservesEndingsAndAddsNote() {
    DiffResult result = diff(utf8("a\r\nb\n"), utf8("a\nb\n"), DiffMode.TEXT);

    assertEquals(DiffStatus.CHANGED, result.status());
    assertEquals(DiffStrategy.LINE, result.strategy());
    assertTrue(result.notes().contains("diff.line-ending"));
    assertFalse(result.lineHunks().isEmpty());
    boolean sawCrlfRemoval = false;
    boolean sawLfAddition = false;
    for (DiffResult.LineHunk hunk : result.lineHunks()) {
      for (DiffResult.LineChange line : hunk.lines()) {
        if (line.kind() == LineChangeKind.REMOVED
            && line.text().equals("a")
            && line.ending() == LineEnding.CRLF) {
          sawCrlfRemoval = true;
        }
        if (line.kind() == LineChangeKind.ADDED
            && line.text().equals("a")
            && line.ending() == LineEnding.LF) {
          sawLfAddition = true;
        }
      }
    }
    assertTrue(sawCrlfRemoval);
    assertTrue(sawLfAddition);
  }

  @Test
  void crAndFinalEofEndingsArePreserved() {
    DiffResult result = diff(utf8("a\rb"), utf8("a\nb"), DiffMode.TEXT);

    assertTrue(result.notes().contains("diff.line-ending"));
    boolean sawCr = false;
    boolean sawLf = false;
    boolean sawEof = false;
    for (DiffResult.LineHunk hunk : result.lineHunks()) {
      for (DiffResult.LineChange line : hunk.lines()) {
        sawCr |= line.kind() == LineChangeKind.REMOVED && line.ending() == LineEnding.CR;
        sawLf |= line.kind() == LineChangeKind.ADDED && line.ending() == LineEnding.LF;
        sawEof |= line.text().equals("b") && line.ending() == LineEnding.EOF;
      }
    }
    assertTrue(sawCr);
    assertTrue(sawLf);
    assertTrue(sawEof);
  }

  @Test
  void bomIsStrippedAndReported() {
    byte[] withBom = concat(new byte[] {(byte) 0xef, (byte) 0xbb, (byte) 0xbf}, utf8("a\n"));
    DiffResult result = diff(withBom, utf8("b\n"), DiffMode.TEXT);

    assertTrue(result.notes().contains("diff.bom"));
    assertEquals(DiffStatus.CHANGED, result.status());
    for (DiffResult.LineHunk hunk : result.lineHunks()) {
      for (DiffResult.LineChange line : hunk.lines()) {
        assertFalse(line.text().startsWith("\ufeff"));
      }
    }
  }

  @Test
  void invalidUtf8ReturnsInvalidInputInTextAndStructuredModes() {
    byte[] invalid = concat(new byte[] {(byte) 0xff, (byte) 0xfe}, utf8("a\n"));
    byte[] valid = utf8("b\n");

    DiffResult text = diff(invalid, valid, DiffMode.TEXT);
    DiffResult yaml = diff(invalid, valid, DiffMode.YAML);
    assertInvalid(text);
    assertInvalid(yaml);
    assertTrue(text.notes().isEmpty());
    assertTrue(yaml.notes().isEmpty());
  }

  @Test
  void movedBlockUsesStableRemovalThenAdditionGroups() {
    byte[] before = utf8("one\ntwo\nthree\n");
    byte[] after = utf8("two\nthree\none\n");
    DiffResult first = diff(before, after, DiffMode.TEXT);
    DiffResult second = diff(before, after, DiffMode.TEXT);

    assertEquals(DiffStatus.CHANGED, first.status());
    assertEquals(DiffJson.toJson(first), DiffJson.toJson(second));
    assertFalse(first.lineHunks().isEmpty());
    boolean sawRemoval = false;
    boolean sawAdditionAfterRemoval = false;
    for (DiffResult.LineHunk hunk : first.lineHunks()) {
      for (DiffResult.LineChange line : hunk.lines()) {
        if (line.kind() == LineChangeKind.REMOVED) {
          sawRemoval = true;
        } else if (line.kind() == LineChangeKind.ADDED) {
          sawAdditionAfterRemoval |= sawRemoval;
        }
      }
    }
    assertTrue(sawRemoval);
    assertTrue(sawAdditionAfterRemoval);
  }

  @Test
  void yamlRenameAndPureReorderAreSeparateChanges() {
    DiffResult rename =
        diff(utf8("name: old\nversion: 1\n"), utf8("version: 1\ntitle: old\n"), DiffMode.YAML);
    assertEquals(DiffStrategy.SEMANTIC, rename.strategy());
    SemanticChange renameChange = findChange(rename.semanticChanges(), SemanticChangeKind.RENAMED);
    assertNotNull(renameChange);
    assertEquals("/name", renameChange.from());
    assertEquals("/title", renameChange.to());
    assertEquals("old", renameChange.value());
    assertEquals("string", renameChange.valueType());

    DiffResult reorder =
        diff(utf8("name: alpha\nversion: 1\n"), utf8("version: 1\nname: alpha\n"), DiffMode.YAML);
    assertEquals(DiffStrategy.SEMANTIC, reorder.strategy());
    SemanticChange reorderChange =
        findChange(reorder.semanticChanges(), SemanticChangeKind.REORDERED);
    assertNotNull(reorderChange);
    assertEquals("", reorderChange.path());
    assertEquals(Arrays.asList("name", "version"), reorderChange.beforeOrder());
    assertEquals(Arrays.asList("version", "name"), reorderChange.afterOrder());
    assertEquals(1, reorder.semanticChanges().size());
  }

  @Test
  void reorderedIsNotEmittedWhenAValueAlsoChanged() {
    DiffResult result = diff(utf8("a: 1\nb: 2\n"), utf8("b: 3\na: 1\n"), DiffMode.YAML);

    assertEquals(DiffStrategy.SEMANTIC, result.strategy());
    assertNotNull(findChange(result.semanticChanges(), SemanticChangeKind.CHANGED));
    assertEquals(null, findChange(result.semanticChanges(), SemanticChangeKind.REORDERED));
  }

  @Test
  void semanticPathsEscapePointerSegmentsAndNestedObjects() {
    DiffResult result =
        diff(
            utf8("outer:\n  \"a/b\": 1\n  \"x~y\": 2\n"),
            utf8("outer:\n  \"a/b\": 3\n  \"x~y\": 4\n"),
            DiffMode.YAML);

    assertEquals(DiffStrategy.SEMANTIC, result.strategy());
    assertEquals(2, result.semanticChanges().size());
    assertEquals("/outer/a~1b", result.semanticChanges().get(0).path());
    assertEquals("1", result.semanticChanges().get(0).before());
    assertEquals("3", result.semanticChanges().get(0).after());
    assertEquals("/outer/x~0y", result.semanticChanges().get(1).path());
  }

  @Test
  void renamesAcrossDifferentParentsRemainSeparateChanges() {
    DiffResult result = diff(utf8("a:\n  k: 1\n"), utf8("c:\n  b:\n    k: 1\n"), DiffMode.YAML);

    assertEquals(DiffStrategy.SEMANTIC, result.strategy());
    assertEquals(null, findChange(result.semanticChanges(), SemanticChangeKind.RENAMED));
    SemanticChange removed = findChange(result.semanticChanges(), SemanticChangeKind.REMOVED);
    SemanticChange added = findChange(result.semanticChanges(), SemanticChangeKind.ADDED);
    assertNotNull(removed);
    assertNotNull(added);
    assertEquals("/a", removed.path());
    assertEquals("/c", added.path());
  }

  @Test
  void invalidYamlAndJsonFallBackToStableTextDiffs() {
    DiffResult yaml = diff(utf8("a: [1\n"), utf8("a: [2\nb: 3\n"), DiffMode.YAML);
    assertEquals(DiffStatus.CHANGED, yaml.status());
    assertEquals(DiffStrategy.TEXT_FALLBACK, yaml.strategy());
    assertTrue(yaml.notes().contains("diff.parse-fallback"));
    assertFalse(yaml.lineHunks().isEmpty());
    assertTrue(yaml.semanticChanges().isEmpty());

    DiffResult json = diff(utf8("{\"a\":1,\"a\":2}"), utf8("{\"a\":1,\"a\":3}"), DiffMode.JSON);
    assertEquals(DiffStrategy.TEXT_FALLBACK, json.strategy());
    assertTrue(json.notes().contains("diff.parse-fallback"));
  }

  @Test
  void autoAndYamlAcceptJson() {
    byte[] before = utf8("{\"a\":1,\"b\":2}");
    byte[] after = utf8("{\"a\":3,\"b\":2}");

    DiffResult auto = diff(before, after, DiffMode.AUTO);
    DiffResult yaml = diff(before, after, DiffMode.YAML);
    assertEquals(DiffStrategy.SEMANTIC, auto.strategy());
    assertEquals(DiffStrategy.SEMANTIC, yaml.strategy());
    assertEquals(DiffJson.toJson(auto), DiffJson.toJson(yaml));
    SemanticChange change = findChange(auto.semanticChanges(), SemanticChangeKind.CHANGED);
    assertNotNull(change);
    assertEquals("/a", change.path());
    assertEquals("1", change.before());
    assertEquals("3", change.after());
    assertEquals("integer", change.afterType());
  }

  @Test
  void multipleYamlDocumentsFallBackToText() {
    DiffResult result = diff(utf8("a: 1\n---\nb: 2\n"), utf8("a: 3\n---\nb: 2\n"), DiffMode.YAML);

    assertEquals(DiffStrategy.TEXT_FALLBACK, result.strategy());
    assertTrue(result.notes().contains("diff.parse-fallback"));
    assertFalse(result.lineHunks().isEmpty());
  }

  @Test
  void pairedChangedLineHasMergedWordSegments() {
    DiffResult result =
        diff(utf8("The quick brown fox\n"), utf8("The red brown fox\n"), DiffMode.TEXT);

    assertEquals(1, result.wordDiffs().size());
    assertEquals(
        WordSegmentKind.REMOVED, findSegment(result.wordDiffs().get(0).oldSegments(), "quick"));
    assertEquals(
        WordSegmentKind.ADDED, findSegment(result.wordDiffs().get(0).newSegments(), "red"));
    assertEquals(1, result.wordDiffs().get(0).oldLine());
    assertEquals(1, result.wordDiffs().get(0).newLine());
  }

  @Test
  void adjacentWordSegmentsAreMerged() {
    DiffResult result = diff(utf8("alpha beta\n"), utf8("alpha gamma delta\n"), DiffMode.TEXT);

    assertEquals(1, result.wordDiffs().size());
    List<DiffResult.WordSegment> added = new ArrayList<DiffResult.WordSegment>();
    for (DiffResult.WordSegment segment : result.wordDiffs().get(0).newSegments()) {
      if (segment.kind() == WordSegmentKind.ADDED) {
        added.add(segment);
      }
    }
    assertEquals(1, added.size());
    assertEquals("gamma delta", added.get(0).text());
  }

  @Test
  void textAndStructuredLimitsReturnNamedLimits() {
    assertTooLarge(
        DiffEngine.diff(
            new DiffRequest(
                utf8("a\nb\n"),
                utf8("a\nc\n"),
                DiffMode.TEXT,
                DiffLimits.DEFAULTS.withMaxLinesPerSide(1))),
        "lines-per-side",
        2);
    assertTooLarge(
        DiffEngine.diff(
            new DiffRequest(
                utf8("a\nb\nc\n"),
                utf8("a\nx\nc\n"),
                DiffMode.TEXT,
                DiffLimits.DEFAULTS.withMaxEditOperations(1))),
        "edit-operations",
        2);
    assertTooLarge(
        DiffEngine.diff(
            new DiffRequest(
                utf8("a b c\n"),
                utf8("a b d\n"),
                DiffMode.TEXT,
                DiffLimits.DEFAULTS.withMaxWordTokensPerLine(2))),
        "word-tokens-per-line",
        3);
    assertTooLarge(
        DiffEngine.diff(
            new DiffRequest(
                utf8("a: 1\n"),
                utf8("b: 2\n"),
                DiffMode.YAML,
                DiffLimits.DEFAULTS.withMaxSemanticNodes(3))),
        "semantic-nodes",
        4);
  }

  @Test
  void jarLimitsReturnNamedLimits() throws IOException {
    int descriptorBytes = utf8("name: demo\nversion: 1\n").length;
    assertTooLarge(
        DiffEngine.diff(
            new DiffRequest(
                jar("name: demo\nversion: 1\n"),
                jar("name: demo\nversion: 2\n"),
                DiffMode.JAR,
                DiffLimits.DEFAULTS.withMaxJarEntries(1))),
        "jar-entries",
        2);
    assertTooLarge(
        DiffEngine.diff(
            new DiffRequest(
                jar("name: demo\nversion: 1\n"),
                jar("name: demo\nversion: 2\n"),
                DiffMode.JAR,
                DiffLimits.DEFAULTS.withMaxJarDescriptorBytes(4))),
        "jar-descriptor-bytes",
        descriptorBytes);
    assertTooLarge(
        DiffEngine.diff(
            new DiffRequest(
                jar("name: demo\nversion: 1\n"),
                jar("name: demo\nversion: 2\n"),
                DiffMode.JAR,
                DiffLimits.DEFAULTS.withMaxJarDescriptorTotalBytes(4))),
        "jar-descriptor-total-bytes",
        descriptorBytes);

    DiffResult truncatedList =
        DiffEngine.diff(
            new DiffRequest(
                zip(entry("a.txt", "1"), entry("b.txt", "1"), entry("c.txt", "1")),
                zip(entry("a.txt", "1"), entry("x.txt", "1"), entry("y.txt", "1")),
                DiffMode.JAR,
                DiffLimits.DEFAULTS.withMaxJarEntryListOutput(1)));
    assertEquals(DiffStatus.CHANGED, truncatedList.status());
    assertNotNull(truncatedList.jarMetadata());
    assertEquals(1, truncatedList.jarMetadata().entriesAdded().size());
    assertEquals(1, truncatedList.jarMetadata().entriesRemoved().size());
    assertTrue(truncatedList.notes().contains("diff.entry-list-truncated"));
  }

  @Test
  void jarVersionBumpAndDependencyChangeAreReported() throws IOException {
    byte[] before =
        jar(
            "name: demo\n"
                + "version: \"1.0\"\n"
                + "main: example.Demo\n"
                + "api-version: \"1.20\"\n"
                + "depend:\n"
                + "  - Foo\n"
                + "softdepend:\n"
                + "  - Bar\n");
    byte[] after =
        jar(
            "name: demo\n"
                + "version: \"2.0\"\n"
                + "main: example.Demo\n"
                + "api-version: \"1.20\"\n"
                + "depend:\n"
                + "  - Baz\n"
                + "softdepend:\n"
                + "  - Bar\n");

    DiffResult result = diff(before, after, DiffMode.JAR);
    assertEquals(DiffStatus.CHANGED, result.status());
    assertEquals(DiffStrategy.JAR_METADATA, result.strategy());
    assertNotNull(result.jarMetadata());
    assertTrue(result.jarMetadata().hashChanged());
    assertEquals(after.length - before.length, result.jarMetadata().sizeDelta());
    assertTrue(result.jarMetadata().entriesAdded().isEmpty());
    assertTrue(result.jarMetadata().entriesRemoved().isEmpty());
    assertEquals(1, result.jarMetadata().descriptors().size());

    DiffResult.JarDescriptorDiff descriptor = result.jarMetadata().descriptors().get(0);
    assertEquals("plugin.yml", descriptor.name());
    assertEquals("changed", descriptor.status());
    SemanticChange version = findChange(descriptor.fieldChanges(), SemanticChangeKind.CHANGED);
    assertNotNull(version);
    assertEquals("/version", version.path());
    assertEquals("1.0", version.before());
    assertEquals("2.0", version.after());

    List<JarDependencyChange> dependencies = descriptor.dependencyChanges();
    assertEquals(2, dependencies.size());
    assertEquals("Baz", dependencies.get(0).name());
    assertEquals("added", dependencies.get(0).kind().value());
    assertEquals("Foo", dependencies.get(1).name());
    assertEquals("removed", dependencies.get(1).kind().value());
  }

  @Test
  void emptyAndEmptyZipJarsAreHandledConsistently() throws IOException {
    assertInvalid(diff(new byte[0], jar("name: demo\n"), DiffMode.JAR));

    DiffResult result = diff(zip(), jar("name: demo\n"), DiffMode.JAR);
    assertEquals(DiffStatus.CHANGED, result.status());
    assertEquals(DiffStrategy.JAR_METADATA, result.strategy());
    assertNotNull(result.jarMetadata());
    assertTrue(result.notes().contains("diff.descriptor-missing"));
    assertEquals(1, result.jarMetadata().descriptors().size());
    assertEquals("added", result.jarMetadata().descriptors().get(0).status());
  }

  @Test
  void falseEocdSignatureInCommentDoesNotRejectJar() throws IOException {
    byte[] before = jarWithComment("name: demo\nversion: 1\n", "PK\u0005\u0006tail");
    byte[] after = jarWithComment("name: demo\nversion: 2\n", "PK\u0005\u0006tail");

    DiffResult result = diff(before, after, DiffMode.JAR);
    assertEquals(DiffStatus.CHANGED, result.status());
    assertEquals(DiffStrategy.JAR_METADATA, result.strategy());
    assertFalse(result.notes().contains("diff.zip-truncated"));
    assertNotNull(result.jarMetadata());
  }

  @Test
  void truncatedCentralDirectoryReturnsZipTruncated() throws IOException {
    byte[] bytes = jar("name: demo\nversion: 1\n");
    int eocd = findEndRecord(bytes);
    putU16(bytes, eocd + 8, 1);
    putU16(bytes, eocd + 10, 1);
    putU32(bytes, eocd + 12, 4);
    putU32(bytes, eocd + 16, eocd - 4);
    putU32(bytes, eocd - 4, 0x02014b50L);

    DiffResult result = diff(bytes, jar("name: demo\nversion: 2\n"), DiffMode.JAR);
    assertInvalid(result);
    assertTrue(result.notes().contains("diff.zip-truncated"));
  }

  @Test
  void descriptorBomIsStrippedAndReported() {
    byte[] bom = {(byte) 0xef, (byte) 0xbb, (byte) 0xbf};
    byte[] descriptor = utf8("name: demo\nversion: \"1\"\n");
    DiffResult result =
        diff(
            zip(entryBytes(utf8("plugin.yml"), descriptor)),
            zip(entryBytes(utf8("plugin.yml"), concat(bom, descriptor))),
            DiffMode.JAR);

    assertEquals(DiffStatus.CHANGED, result.status());
    assertTrue(result.notes().contains("diff.bom"));
    assertFalse(result.notes().contains("diff.descriptor-invalid"));
    assertNotNull(result.jarMetadata());
    assertEquals("unchanged", result.jarMetadata().descriptors().get(0).status());
  }

  @Test
  void invalidAndMissingDescriptorsUseStableNotes() throws IOException {
    DiffResult invalid =
        diff(
            jar("name: demo\nversion: 1\n"),
            zip(entryBytes(utf8("plugin.yml"), utf8("name: [1\n"))),
            DiffMode.JAR);
    assertTrue(invalid.notes().contains("diff.descriptor-invalid"));

    DiffResult missing = diff(jar("name: demo\nversion: 1\n"), zip(), DiffMode.JAR);
    assertTrue(missing.notes().contains("diff.descriptor-missing"));
    assertEquals("removed", missing.jarMetadata().descriptors().get(0).status());

    List<String> sorted = new ArrayList<String>(missing.notes());
    assertEquals(new ArrayList<String>(new TreeSet<String>(sorted)), missing.notes());
  }

  @Test
  void duplicateDescriptorsReturnInvalidInput() throws IOException {
    byte[] duplicate =
        zip(
            entryBytes(utf8("plugin.yml"), utf8("name: demo\n")),
            entryBytes(utf8("plugin.yml"), utf8("name: demo\n")));

    DiffResult result = diff(duplicate, jar("name: demo\n"), DiffMode.JAR);
    assertInvalid(result);
    assertTrue(result.notes().contains("diff.descriptor-duplicate"));
  }

  @Test
  void controlCharactersInEntryNamesAreEscapedInStableJson() {
    byte[] before = zip(new byte[][] {utf8("a\u0001b.txt"), utf8("1")});
    byte[] after = zip(new byte[][] {utf8("c\u202e.txt"), utf8("1")});

    DiffResult result = diff(before, after, DiffMode.JAR);
    String json = DiffJson.toJson(result);
    assertTrue(json.contains("\\u0001"));
    assertTrue(json.contains("\\u202e"));
    assertFalse(json.contains("\u0001"));
    assertFalse(json.contains("\u202e"));
  }

  @Test
  void invalidUtf8EntryNameReturnsInvalidInput() {
    byte[] invalidName = zip(new byte[][] {new byte[] {(byte) 0xff}, utf8("x")});

    DiffResult result = diff(invalidName, zip(entry("a.txt", "x")), DiffMode.JAR);
    assertInvalid(result);
  }

  @Test
  void zip64LocatorIsRejectedBut65535CountAloneIsNot() {
    DiffResult zip64 = diff(zip64Locator(), zip(), DiffMode.JAR);
    assertInvalid(zip64);
    assertTrue(zip64.notes().contains("diff.zip64-unsupported"));

    byte[] countOnly = new byte[22];
    putU32(countOnly, 0, 0x06054b50L);
    putU16(countOnly, 8, 0xffff);
    putU16(countOnly, 10, 0xffff);
    DiffResult countResult =
        DiffEngine.diff(
            new DiffRequest(
                countOnly, zip(), DiffMode.JAR, DiffLimits.DEFAULTS.withMaxJarEntries(1000)));
    assertTooLarge(countResult, "jar-entries", 65535);
    assertFalse(countResult.notes().contains("diff.zip64-unsupported"));
  }

  @Test
  void invalidAndZip64ArchivesReturnInvalidInputWithStableNotes() {
    DiffResult invalid = diff(utf8("not a jar"), utf8("also not a jar"), DiffMode.JAR);
    assertInvalid(invalid);

    byte[] zip64 = new byte[22];
    putU32(zip64, 0, 0x06054b50L);
    putU16(zip64, 8, 0xffff);
    putU16(zip64, 10, 0xffff);
    putU32(zip64, 12, 0xffffffffL);
    putU32(zip64, 16, 0xffffffffL);
    byte[] modifiedZip64 = Arrays.copyOf(zip64, zip64.length);
    modifiedZip64[21] = 1;

    DiffResult explicit = diff(zip64, modifiedZip64, DiffMode.JAR);
    assertInvalid(explicit);
    assertTrue(explicit.notes().contains("diff.zip64-unsupported"));

    DiffResult autoResult = DiffEngine.diff(new DiffRequest(zip64, modifiedZip64, DiffMode.AUTO));
    assertInvalid(autoResult);
    assertTrue(autoResult.notes().contains("diff.zip64-unsupported"));
  }

  @Test
  void repeatedRunsProduceIdenticalStableJson() throws IOException {
    byte[] before = utf8("z: 1\na: 2\n");
    byte[] after = utf8("a: 3\nz: 1\n");
    String first = DiffJson.toJson(diff(before, after, DiffMode.YAML));

    for (int i = 0; i < 5; i++) {
      assertEquals(first, DiffJson.toJson(diff(before, after, DiffMode.YAML)));
    }

    byte[] jarBefore = jar("name: demo\nversion: \"1.0\"\n");
    byte[] jarAfter = jar("name: demo\nversion: \"1.1\"\n");
    String jarFirst = DiffJson.toJson(diff(jarBefore, jarAfter, DiffMode.JAR));
    for (int i = 0; i < 5; i++) {
      assertEquals(jarFirst, DiffJson.toJson(diff(jarBefore, jarAfter, DiffMode.JAR)));
    }
  }

  private static DiffResult diff(byte[] before, byte[] after, DiffMode mode) {
    return DiffEngine.diff(new DiffRequest(before, after, mode));
  }

  private static byte[] utf8(String value) {
    return value.getBytes(StandardCharsets.UTF_8);
  }

  private static byte[] concat(byte[] left, byte[] right) {
    byte[] result = Arrays.copyOf(left, left.length + right.length);
    System.arraycopy(right, 0, result, left.length, right.length);
    return result;
  }

  private static byte[][] entry(String name, String content) {
    return entryBytes(utf8(name), utf8(content));
  }

  private static byte[][] entryBytes(byte[] name, byte[] content) {
    return new byte[][] {name, content};
  }

  private static byte[] jar(String descriptor) throws IOException {
    return jarWithComment(descriptor, null);
  }

  private static byte[] jarWithComment(String descriptor, String comment) throws IOException {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    ZipOutputStream zip = new ZipOutputStream(bytes);
    if (comment != null) {
      zip.setComment(comment);
    }
    putEntry(zip, "plugin.yml", descriptor);
    putEntry(zip, "assets/readme.txt", "not a descriptor\n");
    zip.finish();
    zip.close();
    return bytes.toByteArray();
  }

  private static void putEntry(ZipOutputStream zip, String name, String content)
      throws IOException {
    zip.putNextEntry(new ZipEntry(name));
    zip.write(utf8(content));
    zip.closeEntry();
  }

  /** Builds a stored (uncompressed) ZIP from raw name/content byte pairs. */
  private static byte[] zip(byte[][]... entries) {
    ByteArrayOutputStream local = new ByteArrayOutputStream();
    ByteArrayOutputStream central = new ByteArrayOutputStream();
    int offset = 0;
    for (byte[][] entry : entries) {
      byte[] name = entry[0];
      byte[] content = entry[1];
      CRC32 crc = new CRC32();
      crc.update(content);

      byte[] localHeader = new byte[30 + name.length];
      putU32(localHeader, 0, 0x04034b50L);
      putU16(localHeader, 4, 20);
      putU32(localHeader, 14, crc.getValue());
      putU32(localHeader, 18, content.length);
      putU32(localHeader, 22, content.length);
      putU16(localHeader, 26, name.length);
      System.arraycopy(name, 0, localHeader, 30, name.length);
      local.write(localHeader, 0, localHeader.length);
      local.write(content, 0, content.length);

      byte[] centralHeader = new byte[46 + name.length];
      putU32(centralHeader, 0, 0x02014b50L);
      putU16(centralHeader, 4, 20);
      putU16(centralHeader, 6, 20);
      putU32(centralHeader, 16, crc.getValue());
      putU32(centralHeader, 20, content.length);
      putU32(centralHeader, 24, content.length);
      putU16(centralHeader, 28, name.length);
      putU32(centralHeader, 42, offset);
      System.arraycopy(name, 0, centralHeader, 46, name.length);
      central.write(centralHeader, 0, centralHeader.length);
      offset += localHeader.length + content.length;
    }

    byte[] directory = central.toByteArray();
    byte[] end = new byte[22];
    putU32(end, 0, 0x06054b50L);
    putU16(end, 8, entries.length);
    putU16(end, 10, entries.length);
    putU32(end, 12, directory.length);
    putU32(end, 16, offset);
    return concat(concat(local.toByteArray(), directory), end);
  }

  private static byte[] zip64Locator() {
    byte[] bytes = new byte[4 + 20 + 22];
    putU32(bytes, 0, 0x04034b50L);
    putU32(bytes, 4, 0x07064b50L);
    putU32(bytes, 24, 0x06054b50L);
    putU16(bytes, 28, 0xffff);
    putU16(bytes, 30, 0xffff);
    putU32(bytes, 32, 0xffffffffL);
    putU32(bytes, 36, 0xffffffffL);
    return bytes;
  }

  private static int findEndRecord(byte[] bytes) {
    for (int candidate = bytes.length - 22; candidate >= 0; candidate--) {
      if (u32(bytes, candidate) == 0x06054b50L) {
        return candidate;
      }
    }
    return -1;
  }

  private static void putU16(byte[] bytes, int offset, int value) {
    bytes[offset] = (byte) (value & 0xff);
    bytes[offset + 1] = (byte) ((value >>> 8) & 0xff);
  }

  private static void putU32(byte[] bytes, int offset, long value) {
    for (int i = 0; i < 4; i++) {
      bytes[offset + i] = (byte) ((value >>> (8 * i)) & 0xff);
    }
  }

  private static long u32(byte[] bytes, int offset) {
    return (bytes[offset] & 0xffL)
        | ((bytes[offset + 1] & 0xffL) << 8)
        | ((bytes[offset + 2] & 0xffL) << 16)
        | ((bytes[offset + 3] & 0xffL) << 24);
  }

  private static void assertContentInfo(ContentInfo info, int size) {
    assertEquals(size, info.size());
    assertTrue(info.sha256().matches("^sha256:[0-9a-f]{64}$"));
  }

  private static void assertTooLarge(DiffResult result, String limit, long observed) {
    assertEquals(DiffStatus.TOO_LARGE, result.status());
    assertEquals(DiffStrategy.NONE, result.strategy());
    assertEquals("diff.too-large", result.reason());
    assertEquals(limit, result.limit());
    assertEquals(observed, result.observed().longValue());
    assertNoDetails(result);
  }

  private static void assertInvalid(DiffResult result) {
    assertEquals(DiffStatus.INVALID_INPUT, result.status());
    assertEquals(DiffStrategy.NONE, result.strategy());
    assertEquals("diff.invalid-input", result.reason());
    assertNoDetails(result);
  }

  private static void assertNoDetails(DiffResult result) {
    assertTrue(result.lineHunks().isEmpty());
    assertTrue(result.wordDiffs().isEmpty());
    assertTrue(result.semanticChanges().isEmpty());
    assertEquals(null, result.jarMetadata());
  }

  private static SemanticChange findChange(List<SemanticChange> changes, SemanticChangeKind kind) {
    for (SemanticChange change : changes) {
      if (change.kind() == kind) {
        return change;
      }
    }
    return null;
  }

  private static WordSegmentKind findSegment(List<DiffResult.WordSegment> segments, String text) {
    for (DiffResult.WordSegment segment : segments) {
      if (segment.text().equals(text)) {
        return segment.kind();
      }
    }
    return null;
  }
}
