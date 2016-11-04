package tk.mygod.portal.helper.nju.util

import scala.util.Random

class RetryCounter {
  private var retryCount: Int = _
  def retry() {
    if (retryCount < 10) retryCount = retryCount + 1
    Thread.sleep(2000 + Random.nextInt(1000 << retryCount)) // prevent overwhelming failing notifications
  }
  def reset() = retryCount = 0
}
