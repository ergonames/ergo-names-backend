package local

import org.slf4j.{LoggerFactory, MDC}

object LogTest {
  val logger = LoggerFactory.getLogger(getClass)

  def main(Args: Array[String]): Unit = {
    logger.info("This is a JSON formatted log message with no properties")
//    MDC.put("userId", "123456")
    logger.info("This is a JSON formatted log message with properties {}{}", 123, 456)
  }
}
