import datadog.trace.agent.test.AgentTestRunner
import datadog.trace.agent.test.utils.PortUtils
import de.flapdoodle.embed.mongo.MongodExecutable
import de.flapdoodle.embed.mongo.MongodProcess
import de.flapdoodle.embed.mongo.MongodStarter
import de.flapdoodle.embed.mongo.config.IMongodConfig
import de.flapdoodle.embed.mongo.config.MongodConfigBuilder
import de.flapdoodle.embed.mongo.config.Net
import de.flapdoodle.embed.mongo.distribution.Version
import de.flapdoodle.embed.process.runtime.Network
import spock.lang.Shared

/**
 * Testing needs to be in a centralized project.
 * If tests in multiple different projects are using embedded mongo,
 * they downloader is at risk of a race condition.
 */
class MongoBaseTest extends AgentTestRunner {
  // https://github.com/flapdoodle-oss/de.flapdoodle.embed.mongo#executable-collision
  private static final MongodStarter STARTER = MongodStarter.getDefaultInstance()

  @Shared
  int port
  @Shared
  MongodExecutable mongodExe
  @Shared
  MongodProcess mongod

  def setupSpec() throws Exception {
    /*
     CI will provide us with a mongo container running alongside our build.
     When building locally we need to take matters into our own hands and
     start our own mongo server.
     */
    if ("true" != System.getenv("CI")) {
      port = PortUtils.randomOpenPort()

      final IMongodConfig mongodConfig =
        new MongodConfigBuilder()
        .version(Version.Main.PRODUCTION)
        .net(new Net("localhost", port, Network.localhostIsIPv6()))
        .build()

      mongodExe = STARTER.prepare(mongodConfig)
      mongod = mongodExe.start()
    } else {
      port = 27017 // default mongo port when running on CI
    }
  }

  def cleanupSpec() throws Exception {
    mongod?.stop()
    mongod = null
    mongodExe?.stop()
    mongodExe = null
  }

  def randomCollectionName() {
    return "testCollection-" + UUID.randomUUID()
  }

  def dbName() {
    return "?"
  }

  def matchesStatement(statement) {
    String dbName = dbName()
    return {
      assert it.replace(" ", "").replace(",\"\$db\":\"$dbName\"", "").replace(',"lsid":{"id":"?"}', '').replace(',"readPreference":{"node":"?"}', '') == statement
      return true
    }
  }

  def "test port open"() {
    when:
    new Socket("localhost", port)

    then:
    noExceptionThrown()
  }
}
