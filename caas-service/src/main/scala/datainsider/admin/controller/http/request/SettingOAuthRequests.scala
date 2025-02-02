package datainsider.admin.controller.http.request

import com.twitter.finagle.http.Request
import com.twitter.finatra.validation.{MethodValidation, ValidationResult}
import datainsider.client.filter.LoggedInRequest
import datainsider.login_provider.domain.OAuthConfig
import datainsider.user_profile.util.JsonParser

import javax.inject.Inject

case class MultiUpdateOAuthRequest(@Inject request: Request) extends LoggedInRequest {

  lazy val oauthConfigAsMap: Map[String, OAuthConfig] = {
    Option(request.contentString) match {
      case Some(content) => JsonParser.fromJson[Map[String, OAuthConfig]](content)
      case _             => Map.empty
    }
  }

  @MethodValidation(fields = Array("oauthConfigAsMap"))
  def validateOAuthConfig(): ValidationResult = {
    ValidationResult.validate(oauthConfigAsMap.nonEmpty, "OauthConfigs must not empty")
  }

  @MethodValidation()
  def validateWhitelistEmail(): ValidationResult = {
    oauthConfigAsMap.forall {
      case (_, newConfig) => newConfig.isValid()
    } match {
      case true => ValidationResult.Valid()
      case _    => ValidationResult.Invalid("Whitelist email domain incorrect format")
    }
  }

}
