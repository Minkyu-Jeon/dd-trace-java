package datadog.trace.instrumentation.java.lang;

import datadog.trace.agent.tooling.csi.CallSite;
import datadog.trace.api.iast.IastAdvice;
import datadog.trace.api.iast.InstrumentationBridge;
import datadog.trace.api.iast.sink.PathTraversalModule;
import javax.annotation.Nullable;

@CallSite(spi = IastAdvice.class)
public class PathCallSite {

  @CallSite.BeforeArray({
    @CallSite.Before("java.nio.file.Path java.nio.file.Path.resolve(java.lang.String)"),
    @CallSite.Before("java.nio.file.Path java.nio.file.Path.resolveSibling(java.lang.String)")
  })
  public static void beforeResolve(@CallSite.Argument @Nullable final String other) {
    if (other != null) {
      final PathTraversalModule module = InstrumentationBridge.PATH_TRAVERSAL;
      if (module != null) {
        try {
          module.onPathTraversal(other);
        } catch (final Throwable e) {
          module.onUnexpectedException("beforeResolve threw", e);
        }
      }
    }
  }
}
