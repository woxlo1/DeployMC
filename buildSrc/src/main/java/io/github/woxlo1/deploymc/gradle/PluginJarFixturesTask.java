package io.github.woxlo1.deploymc.gradle;

import java.io.BufferedOutputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.CRC32;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;

/** Creates small positive and negative fixtures for {@link PluginJarVerifierTask}. */
public abstract class PluginJarFixturesTask extends DefaultTask {

  private static final String BASE = "io/github/woxlo1/deploymc/";
  private static final String MANIFEST = "Manifest-Version: 1.0\r\n";
  private static final String MR_MANIFEST = MANIFEST + "Multi-Release: true\r\n";
  private static final String SERVICE_PREFIX = "META-INF/services/";
  private static final String NAMESPACE_DOT = "io.github.woxlo1.deploymc.";

  @OutputDirectory
  public abstract DirectoryProperty getOutputDirectory();

  @TaskAction
  public void generate() throws IOException {
    Path directory = getOutputDirectory().get().getAsFile().toPath();
    Files.createDirectories(directory);

    write(
        directory.resolve("good.jar"),
        entry("META-INF/MANIFEST.MF", text(MANIFEST)),
        entry(BASE + "Good.class", clazz(52)));
    write(
        directory.resolve("valid-mr.jar"),
        entry("META-INF/MANIFEST.MF", text(MR_MANIFEST)),
        entry(BASE + "Foo.class", clazz(52)),
        entry("META-INF/versions/9/" + BASE + "Foo.class", clazz(53)));
    write(
        directory.resolve("good-service.jar"),
        entry("META-INF/MANIFEST.MF", text(MANIFEST)),
        entry(BASE + "Good.class", clazz(52)),
        entry(
            SERVICE_PREFIX + NAMESPACE_DOT + "Example",
            text(NAMESPACE_DOT + "Impl # comment\r\n")));
    write(
        directory.resolve("outside.jar"),
        entry("META-INF/MANIFEST.MF", text(MANIFEST)),
        entry("com/example/Bad.class", clazz(52)));
    write(
        directory.resolve("new-bytecode.jar"),
        entry("META-INF/MANIFEST.MF", text(MANIFEST)),
        entry(BASE + "TooNew.class", clazz(53)));
    write(
        directory.resolve("malformed-mr.jar"),
        entry("META-INF/MANIFEST.MF", text(MR_MANIFEST)),
        entry(BASE + "Foo.class", clazz(52)),
        entry("META-INF/versions/x/" + BASE + "Foo.class", clazz(52)));
    write(
        directory.resolve("old-mr.jar"),
        entry("META-INF/MANIFEST.MF", text(MR_MANIFEST)),
        entry(BASE + "Foo.class", clazz(52)),
        entry("META-INF/versions/8/" + BASE + "Foo.class", clazz(52)));
    write(
        directory.resolve("mr-without-manifest.jar"),
        entry("META-INF/MANIFEST.MF", text(MANIFEST)),
        entry(BASE + "Foo.class", clazz(52)),
        entry("META-INF/versions/9/" + BASE + "Foo.class", clazz(53)));
    write(
        directory.resolve("missing-base.jar"),
        entry("META-INF/MANIFEST.MF", text(MR_MANIFEST)),
        entry("META-INF/versions/9/" + BASE + "OnlyVersioned.class", clazz(53)));
    write(
        directory.resolve("duplicate.jar"),
        entry("META-INF/MANIFEST.MF", text(MANIFEST)),
        entry(BASE + "Duplicate.class", clazz(52)),
        entry(BASE + "Duplicate.class", clazz(52)));
    write(
        directory.resolve("bad-service.jar"),
        entry("META-INF/MANIFEST.MF", text(MANIFEST)),
        entry(BASE + "Good.class", clazz(52)),
        entry(SERVICE_PREFIX + "com.example.Service", text("com.example.Impl\r\n")));
    write(
        directory.resolve("bad-service-provider.jar"),
        entry("META-INF/MANIFEST.MF", text(MANIFEST)),
        entry(BASE + "Good.class", clazz(52)),
        entry(SERVICE_PREFIX + NAMESPACE_DOT + "Example", text("com.example.Impl\r\n")));
  }

  private static Entry entry(String name, byte[] data) {
    return new Entry(name, data);
  }

  private static byte[] clazz(int major) {
    return new byte[] {
      (byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE, 0, 0, 0, (byte) major
    };
  }

  private static byte[] text(String value) {
    return value.getBytes(StandardCharsets.UTF_8);
  }

  /**
   * Writes a stored-entry ZIP with a small hand-written directory so duplicate names can be
   * represented. {@code ZipOutputStream} rejects duplicate entry names.
   */
  private static void write(Path path, Entry... entries) throws IOException {
    List<CentralRecord> central = new ArrayList<CentralRecord>();
    long offset = 0;
    try (DataOutputStream out =
        new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(path)))) {
      for (Entry entry : entries) {
        byte[] name = entry.name.getBytes(StandardCharsets.UTF_8);
        CRC32 crc = new CRC32();
        crc.update(entry.data);
        long checksum = crc.getValue();

        writeInt(out, 0x04034B50L);
        writeShort(out, 20);
        writeShort(out, 0);
        writeShort(out, 0);
        writeShort(out, 0);
        writeShort(out, 0);
        writeInt(out, checksum);
        writeInt(out, entry.data.length);
        writeInt(out, entry.data.length);
        writeShort(out, name.length);
        writeShort(out, 0);
        out.write(name);
        out.write(entry.data);

        central.add(new CentralRecord(name, checksum, entry.data.length, offset));
        offset += 30L + name.length + entry.data.length;
      }

      long centralOffset = offset;
      for (CentralRecord record : central) {
        writeInt(out, 0x02014B50L);
        writeShort(out, 20);
        writeShort(out, 20);
        writeShort(out, 0);
        writeShort(out, 0);
        writeShort(out, 0);
        writeShort(out, 0);
        writeInt(out, record.checksum);
        writeInt(out, record.size);
        writeInt(out, record.size);
        writeShort(out, record.name.length);
        writeShort(out, 0);
        writeShort(out, 0);
        writeShort(out, 0);
        writeShort(out, 0);
        writeInt(out, 0);
        writeInt(out, record.offset);
        out.write(record.name);
        offset += 46L + record.name.length;
      }

      long centralSize = offset - centralOffset;
      writeInt(out, 0x06054B50L);
      writeShort(out, 0);
      writeShort(out, 0);
      writeShort(out, central.size());
      writeShort(out, central.size());
      writeInt(out, centralSize);
      writeInt(out, centralOffset);
      writeShort(out, 0);
    }
  }

  private static void writeShort(DataOutput out, int value) throws IOException {
    out.write(value & 0xFF);
    out.write((value >>> 8) & 0xFF);
  }

  private static void writeInt(DataOutput out, long value) throws IOException {
    for (int index = 0; index < 4; index++) {
      out.write((int) ((value >>> (8 * index)) & 0xFF));
    }
  }

  private static final class Entry {
    private final String name;
    private final byte[] data;

    private Entry(String name, byte[] data) {
      this.name = name;
      this.data = data;
    }
  }

  private static final class CentralRecord {
    private final byte[] name;
    private final long checksum;
    private final int size;
    private final long offset;

    private CentralRecord(byte[] name, long checksum, int size, long offset) {
      this.name = name;
      this.checksum = checksum;
      this.size = size;
      this.offset = offset;
    }
  }
}
