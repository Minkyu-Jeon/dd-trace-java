import datadog.trace.agent.test.log.injection.LogContextInjectionTestBase
import datadog.trace.api.Platform
import org.apache.log4j.MDC
import spock.lang.Requires

/**
 It looks like log4j1 is broken for any java version that doesn't have '.' in version number
 - it thinks it runs on ancient version. For example this happens for java13.
 See {@link org.apache.log4j.helpers.Loader}.
 */
@Requires({ !Platform.isJavaVersionAtLeast(9) })
class Log4j1MDCTest extends LogContextInjectionTestBase {

  @Override
  def put(String key, Object value) {
    return MDC.put(key, value)
  }

  @Override
  def get(String key) {
    return MDC.get(key)
  }

  @Override
  def remove(String key) {
    MDC.context
    return MDC.remove(key)
  }

  @Override
  def clear() {
    return MDC.clear()
  }

  @Override
  def getMap() {
    return MDC.getContext()
  }
}
