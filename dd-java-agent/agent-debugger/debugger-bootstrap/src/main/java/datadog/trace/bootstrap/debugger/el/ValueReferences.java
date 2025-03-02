package datadog.trace.bootstrap.debugger.el;

/**
 * A helper class to create properly formatted reference paths.
 *
 * @see ValueReferenceResolver
 */
public final class ValueReferences {

  public static String SYNTHETIC_PREFIX = "@";
  public static String FIELD_PREFIX = "this.";

  public static String DURATION_EXTENSION_NAME = "duration";
  public static String RETURN_EXTENSION_NAME = "return";
  public static String ITERATOR_EXTENSION_NAME = "it";
  public static String DURATION_REF = SYNTHETIC_PREFIX + DURATION_EXTENSION_NAME;
  public static String RETURN_REF = SYNTHETIC_PREFIX + RETURN_EXTENSION_NAME;
  public static String ITERATOR_REF = SYNTHETIC_PREFIX + ITERATOR_EXTENSION_NAME;

  public static String synthetic(String name) {
    return SYNTHETIC_PREFIX + name;
  }

  public static String field(String name) {
    return FIELD_PREFIX + name;
  }

  public static boolean isRefPrefix(String prefix) {
    return SYNTHETIC_PREFIX.equals(prefix) || FIELD_PREFIX.equals(prefix);
  }
}
