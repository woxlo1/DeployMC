package io.github.woxlo1.deploymc.transport;

import io.github.woxlo1.deploymc.core.config.ConfigException;
import io.github.woxlo1.deploymc.core.config.ConfigYamlParser;
import io.github.woxlo1.deploymc.core.config.YamlValue;
import io.github.woxlo1.deploymc.core.validation.JsonValidator;
import io.github.woxlo1.deploymc.core.validation.ValidationException;
import io.github.woxlo1.deploymc.core.validation.ValidationLimitException;
import io.github.woxlo1.deploymc.core.validation.ValidationLimits;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Bounded, strict JSON request-body reader with stable transport error codes. */
final class RequestJson {

  private RequestJson() {}

  static YamlValue parse(byte[] body, int maximumBytes) throws RequestProblem {
    if (body.length > maximumBytes) {
      throw RequestProblem.tooLarge();
    }
    try {
      new JsonValidator().validate("<request>", body, ValidationLimits.defaults());
    } catch (ValidationLimitException limit) {
      throw RequestProblem.tooLarge();
    } catch (ValidationException invalid) {
      throw RequestProblem.invalid();
    }
    String text = decode(body);
    try {
      YamlValue parsed = ConfigYamlParser.parseSingleDocument(text);
      if (!parsed.isObject()) {
        throw RequestProblem.invalid();
      }
      return parsed;
    } catch (ConfigException invalid) {
      throw RequestProblem.invalid();
    }
  }

  static String requiredString(YamlValue object, String field, int maximumLength)
      throws RequestProblem {
    YamlValue value = object.get(field);
    if (value == null || !value.isString()) {
      throw RequestProblem.invalid();
    }
    String text = (String) value.raw();
    if (text.length() > maximumLength) {
      throw RequestProblem.invalid();
    }
    return text;
  }

  static String optionalString(YamlValue object, String field, int maximumLength)
      throws RequestProblem {
    if (object.get(field) == null) {
      return null;
    }
    return requiredString(object, field, maximumLength);
  }

  static boolean optionalBoolean(YamlValue object, String field, boolean fallback)
      throws RequestProblem {
    YamlValue value = object.get(field);
    if (value == null) {
      return fallback;
    }
    if (!value.isBoolean()) {
      throw RequestProblem.invalid();
    }
    return ((Boolean) value.raw()).booleanValue();
  }

  static int optionalInteger(YamlValue object, String field, int minimum, int maximum, int fallback)
      throws RequestProblem {
    YamlValue value = object.get(field);
    if (value == null) {
      return fallback;
    }
    if (!value.isInteger()) {
      throw RequestProblem.invalid();
    }
    long number = ((Number) value.raw()).longValue();
    if (number < minimum || number > maximum) {
      throw RequestProblem.invalid();
    }
    return (int) number;
  }

  static void requireFields(YamlValue object, String... required) throws RequestProblem {
    for (String field : required) {
      if (object.get(field) == null) {
        throw RequestProblem.invalid();
      }
    }
  }

  static void rejectUnknownFields(YamlValue object, String... allowed) throws RequestProblem {
    List<String> allowedList = Arrays.asList(allowed);
    Set<String> known = new HashSet<String>(allowedList);
    for (String field : object.keys()) {
      if (!known.contains(field)) {
        throw RequestProblem.invalid();
      }
    }
  }

  private static String decode(byte[] body) throws RequestProblem {
    CharsetDecoder decoder =
        StandardCharsets.UTF_8
            .newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT);
    try {
      return decoder.decode(ByteBuffer.wrap(body)).toString();
    } catch (CharacterCodingException invalid) {
      throw RequestProblem.invalid();
    }
  }
}
