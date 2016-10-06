import java.time.Instant

import com.typesafe.scalalogging.LazyLogging

package object utils {
  implicit class TimingOps(processName: String) extends LazyLogging {
    def timing[T](block: => T): T = {
      val started = Instant.now()
      logger.info(s"=== STARTED $processName")
      val result = block
      logger.info(s"=== END $processName: ${Instant.now().minusMillis(started.toEpochMilli).toEpochMilli}ms")
      result
    }
  }
}
