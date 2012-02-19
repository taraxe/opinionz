package services

import models.OpinionFormat
import play.api.libs.ws.WS

object Sentiment {
  /**
   * Get opinion
   */
  def getSentiment(text: String) = {
    WS.url(sentimentUrl).withQueryString(("api_key", key), ("text", text))
      .get()
      .map { response => play.api.libs.json.Json.fromJson(response.json)(OpinionFormat) }
      .value.get
  }
  /**
   * Add new sentence to repo
   */
  def train(text: String, mood: String): Boolean = {
    WS.url(trainUrl).withQueryString(("api_key", key), ("text", text), ("mood", mood))
      .get()
      .map { response => (response.json \ "status").as[String] }
      .value.get == "ok"
  }
  /**
   * Check quota
   */
  def checkQuota(): Long = {
    WS.url(quotaUrl).withQueryString(("api_key", key))
      .get()
      .map { response => (response.json \ "quota_remaining").as[Long] }
      .value.get;
  }

}