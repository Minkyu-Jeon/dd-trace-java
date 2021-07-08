package datadog.communication.monitor

import org.junit.Test

class NoopCounterTest {
  Counter noopCounter = NoOpCounter.NO_OP

  @Test
  void 'cover the empty methods'() {
    noopCounter.increment(1)
    noopCounter.incrementErrorCount('', 0)
  }
}
