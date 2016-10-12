import java.time.Instant

import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.{ExecutionContext, Future}

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

  implicit class FutureTimingOps[T](f: Future[T]) extends LazyLogging {
    def timing(name: String)(implicit ec: ExecutionContext): Future[T] = {
      val started = Instant.now()

      logger.info(s"=== STARTED $name")

      f.onComplete(_ ⇒
        logger.info(s"=== END $name: ${Instant.now().minusMillis(started.toEpochMilli).toEpochMilli}ms")
      )

      f
    }
  }
}
