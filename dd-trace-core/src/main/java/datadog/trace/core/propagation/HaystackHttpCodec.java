package datadog.trace.core.propagation;

import static datadog.trace.core.propagation.HttpCodec.firstHeaderValue;

import datadog.trace.api.Config;
import datadog.trace.api.DDSpanId;
import datadog.trace.api.DDTraceId;
import datadog.trace.api.sampling.PrioritySampling;
import datadog.trace.bootstrap.instrumentation.api.AgentPropagation;
import datadog.trace.core.DDSpanContext;
import de.thetaphi.forbiddenapis.SuppressForbidden;
import java.util.Map;
import java.util.TreeMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A codec designed for HTTP transport via headers using Haystack headers.
 *
 * @author Alex Antonov
 */
class HaystackHttpCodec {

  private static final Logger log = LoggerFactory.getLogger(HaystackHttpCodec.class);

  // https://github.com/ExpediaDotCom/haystack-client-java/blob/master/core/src/main/java/com/expedia/www/haystack/client/propagation/DefaultKeyConvention.java
  private static final String OT_BAGGAGE_PREFIX = "Baggage-";
  private static final String TRACE_ID_KEY = "Trace-ID";
  private static final String SPAN_ID_KEY = "Span-ID";
  private static final String PARENT_ID_KEY = "Parent-ID";

  private static final String DD_TRACE_ID_BAGGAGE_KEY = OT_BAGGAGE_PREFIX + "Datadog-Trace-Id";
  private static final String DD_SPAN_ID_BAGGAGE_KEY = OT_BAGGAGE_PREFIX + "Datadog-Span-Id";
  private static final String DD_PARENT_ID_BAGGAGE_KEY = OT_BAGGAGE_PREFIX + "Datadog-Parent-Id";

  private static final String HAYSTACK_TRACE_ID_BAGGAGE_KEY = "Haystack-Trace-ID";
  private static final String HAYSTACK_SPAN_ID_BAGGAGE_KEY = "Haystack-Span-ID";
  private static final String HAYSTACK_PARENT_ID_BAGGAGE_KEY = "Haystack-Parent-ID";

  // public static final long DATADOG = new BigInteger("Datadog!".getBytes()).longValue();
  public static final String DATADOG = "44617461-646f-6721";

  private HaystackHttpCodec() {
    // This class should not be created. This also makes code coverage checks happy.
  }

  public static HttpCodec.Injector newInjector(Map<String, String> invertedBaggageMapping) {
    return new Injector(invertedBaggageMapping);
  }

  private static class Injector implements HttpCodec.Injector {

    private final Map<String, String> invertedBaggageMapping;

    public Injector(Map<String, String> invertedBaggageMapping) {
      this.invertedBaggageMapping = invertedBaggageMapping;
    }

    @Override
    public <C> void inject(
        final DDSpanContext context, final C carrier, final AgentPropagation.Setter<C> setter) {
      try {
        // Given that Haystack uses a 128-bit UUID/GUID for all ID representations, need to convert
        // from 64-bit BigInteger
        //  also record the original DataDog IDs into Baggage payload
        //
        // If the original trace has originated within Haystack system and we have it saved in
        // Baggage, and it is equal
        //  to the converted value in BigInteger, use that instead.
        //  this will preserve the complete UUID/GUID without losing the most significant bit part
        String originalHaystackTraceId =
            getBaggageItemIgnoreCase(context.getBaggageItems(), HAYSTACK_TRACE_ID_BAGGAGE_KEY);
        String injectedTraceId;
        if (originalHaystackTraceId != null
            && DDTraceId.fromHex(convertUUIDToHexString(originalHaystackTraceId))
                .equals(context.getTraceId())) {
          injectedTraceId = originalHaystackTraceId;
        } else {
          injectedTraceId = convertLongToUUID(context.getTraceId().toLong());
        }
        setter.set(carrier, TRACE_ID_KEY, injectedTraceId);
        context.setTag(HAYSTACK_TRACE_ID_BAGGAGE_KEY, injectedTraceId);
        setter.set(
            carrier, DD_TRACE_ID_BAGGAGE_KEY, HttpCodec.encode(context.getTraceId().toString()));
        setter.set(carrier, SPAN_ID_KEY, convertLongToUUID(context.getSpanId()));
        setter.set(
            carrier,
            DD_SPAN_ID_BAGGAGE_KEY,
            HttpCodec.encode(DDSpanId.toString(context.getSpanId())));
        setter.set(carrier, PARENT_ID_KEY, convertLongToUUID(context.getParentId()));
        setter.set(
            carrier,
            DD_PARENT_ID_BAGGAGE_KEY,
            HttpCodec.encode(DDSpanId.toString(context.getParentId())));

        for (final Map.Entry<String, String> entry : context.baggageItems()) {
          String header = invertedBaggageMapping.get(entry.getKey());
          header = header != null ? header : OT_BAGGAGE_PREFIX + entry.getKey();
          setter.set(carrier, header, HttpCodec.encode(entry.getValue()));
        }
        log.debug(
            "{} - Haystack parent context injected - {}", context.getTraceId(), injectedTraceId);
      } catch (final NumberFormatException e) {
        log.debug(
            "Cannot parse context id(s): {} {}", context.getTraceId(), context.getSpanId(), e);
      }
    }

    private String getBaggageItemIgnoreCase(Map<String, String> baggage, String key) {
      for (final Map.Entry<String, String> mapping : baggage.entrySet()) {
        if (key.equalsIgnoreCase(mapping.getKey())) {
          return mapping.getValue();
        }
      }
      return null;
    }
  }

  public static HttpCodec.Extractor newExtractor(
      final Map<String, String> tagMapping, Map<String, String> baggageMapping) {
    return new TagContextExtractor(
        tagMapping,
        baggageMapping,
        new ContextInterpreter.Factory() {
          @Override
          protected ContextInterpreter construct(
              Map<String, String> mapping, Map<String, String> baggageMapping) {
            return new HaystackContextInterpreter(mapping, baggageMapping);
          }
        });
  }

  private static class HaystackContextInterpreter extends ContextInterpreter {

    private static final String BAGGAGE_PREFIX_LC = "baggage-";

    private static final int TRACE_ID = 0;
    private static final int SPAN_ID = 1;
    private static final int PARENT_ID = 2;
    private static final int TAGS = 3;
    private static final int BAGGAGE = 4;
    private static final int MAPPED_OT_BAGGAGE = 5;
    private static final int IGNORE = -1;

    private HaystackContextInterpreter(
        Map<String, String> taggedHeaders, Map<String, String> baggageMapping) {
      super(taggedHeaders, baggageMapping, Config.get());
    }

    @Override
    public boolean accept(String key, String value) {
      if (null == key || key.isEmpty()) {
        return true;
      }
      if (LOG_EXTRACT_HEADER_NAMES) {
        log.debug("Header: {}", key);
      }
      char first = Character.toLowerCase(key.charAt(0));
      String lowerCaseKey = null;
      int classification = IGNORE;
      switch (first) {
        case 't':
          if (TRACE_ID_KEY.equalsIgnoreCase(key)) {
            classification = TRACE_ID;
          }
          break;
        case 's':
          if (SPAN_ID_KEY.equalsIgnoreCase(key)) {
            classification = SPAN_ID;
          }
          break;
        case 'p':
          if (PARENT_ID_KEY.equalsIgnoreCase(key)) {
            classification = PARENT_ID;
          }
          break;
        case 'x':
          if (handledXForwarding(key, value)) {
            return true;
          }
          break;
        case 'f':
          if (handledForwarding(key, value)) {
            return true;
          }
          break;
        case 'b':
          lowerCaseKey = toLowerCase(key);
          if (lowerCaseKey.startsWith(BAGGAGE_PREFIX_LC)) {
            classification = BAGGAGE;
          }
          break;
        case 'u':
          if (handledUserAgent(key, value)) {
            return true;
          }
          break;
        default:
      }

      if (handledIpHeaders(key, value)) {
        return true;
      }

      if (!taggedHeaders.isEmpty() && classification == IGNORE) {
        lowerCaseKey = toLowerCase(key);
        if (taggedHeaders.containsKey(lowerCaseKey)) {
          classification = TAGS;
        }
      }

      if (!baggageMapping.isEmpty() && classification == IGNORE) {
        lowerCaseKey = toLowerCase(key);
        if (baggageMapping.containsKey(lowerCaseKey)) {
          classification = MAPPED_OT_BAGGAGE;
        }
      }

      if (IGNORE != classification) {
        try {
          String firstValue = firstHeaderValue(value);
          if (null != firstValue) {
            switch (classification) {
              case TRACE_ID:
                traceId = DDTraceId.fromHex(convertUUIDToHexString(value));
                addBaggageItem(HAYSTACK_TRACE_ID_BAGGAGE_KEY, value);
                break;
              case SPAN_ID:
                spanId = DDSpanId.fromHex(convertUUIDToHexString(value));
                addBaggageItem(HAYSTACK_SPAN_ID_BAGGAGE_KEY, value);
                break;
              case PARENT_ID:
                addBaggageItem(HAYSTACK_PARENT_ID_BAGGAGE_KEY, value);
                break;
              case TAGS:
                {
                  String mappedKey = taggedHeaders.get(lowerCaseKey);
                  if (null != mappedKey) {
                    if (tags.isEmpty()) {
                      tags = new TreeMap<>();
                    }
                    tags.put(mappedKey, HttpCodec.decode(value));
                  }
                  break;
                }
              case BAGGAGE:
                {
                  addBaggageItem(lowerCaseKey.substring(BAGGAGE_PREFIX_LC.length()), value);
                  break;
                }
              case MAPPED_OT_BAGGAGE:
                {
                  addBaggageItem(baggageMapping.get(lowerCaseKey), value);
                  break;
                }
              default:
            }
          }
        } catch (RuntimeException e) {
          invalidateContext();
          log.debug("Exception when extracting context", e);
          return false;
        }
      }
      return true;
    }

    private void addBaggageItem(String key, String value) {
      if (baggage.isEmpty()) {
        baggage = new TreeMap<>();
      }
      baggage.put(key, HttpCodec.decode(value));
    }

    @Override
    protected int defaultSamplingPriority() {
      return PrioritySampling.SAMPLER_KEEP;
    }
  }

  private static String convertLongToUUID(long id) {
    // This is not a true/real UUID, as we don't care about the version and variant markers
    //  the creation is just taking the least significant bits and doing static most significant
    // ones.
    //  this is done for the purpose of being able to maintain cardinality and idempotence of the
    // conversion
    String idHex = String.format("%016x", id);
    return DATADOG + "-" + idHex.substring(0, 4) + "-" + idHex.substring(4);
  }

  @SuppressForbidden
  private static String convertUUIDToHexString(String value) {
    try {
      if (value.contains("-")) {
        String[] strings = value.split("-");
        // We are only interested in the least significant bit component, dropping the most
        // significant one.
        if (strings.length == 5) {
          String idHex = strings[3] + strings[4];
          return idHex;
        }
        throw new NumberFormatException("Invalid UUID format: " + value);
      } else {
        // This could be a regular hex id without separators
        int length = value.length();
        if (length == 32) {
          return value.substring(16);
        } else {
          return value;
        }
      }
    } catch (final Exception e) {
      throw new IllegalArgumentException(
          "Exception when converting UUID to BigInteger: " + value, e);
    }
  }
}
