package datainsider.jobworker.exception

import com.twitter.finagle.http.Status
import datainsider.client.exception.DIException

class AlreadyCompletedException(message: String, cause: Throwable = null) extends DIException(message, cause) {
  override val reason: String = BaseException.AlreadyCompletedException

  override def getStatus: Status = Status.InternalServerError
}
