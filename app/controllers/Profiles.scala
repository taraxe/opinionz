package controllers

import views._
import models._
import services._
import play.api._
import play.api.mvc._
import play.api.data._
import play.api.data.Forms._
import play.api.mvc._
import play.api.libs.ws._
import play.api.libs.oauth._
import play.api.libs.concurrent._
import play.api.libs.iteratee._
import play.api.libs._
import play.api.libs.ws._
import play.api.libs.oauth.OAuthCalculator
import play.Logger
import actors.StreamRecorder._
import akka.util.Timeout
import actors.{ ProfileWorker, StreamRecorder }
import akka.util.duration._
import akka.util.Timeout
import play.api.libs.json._
import play.api.libs.json.Json._
import akka.pattern.ask
import play.api.libs.iteratee._
import play.api.libs.concurrent._
import play.api.libs.Comet

object Profiles extends Controller {
  /** ====== Form definition ====== **/
  val profileFrom: Form[Profile] = Form(
    mapping("text" -> text)
    { term => Profile(expression = term) }
    { profile => Some(profile.expression) }
  )

  /** ====== Actions defintion ====== **/
  //Display form to create profile
  def index = Action { implicit request =>
    Ok(views.html.profiles.form("Ask me ! ", profileFrom))
  }

  //Create a profile and start streaming
  def search = Action { implicit request =>
    profileFrom.bindFromRequest.fold(
      //Form with validation errors case
      errors => {
        Ok(views.html.profiles.form("There is some errors ", errors))
      },
      profile => {
         Logger.debug("Find or create :"+profile.expression)
         Profile    //FIX-ME this should move inside StreamRecorder
               .byTerm(profile.expression)  // retrieve existing profile if any
               .orElse{Profile.insert(profile).map(i => profile.copy(id = i))}  //or create a new one in mongo
               .toRight(InternalServerError)  // if still no profile, fail
               .right.map { p =>
                     //Retrieve Twitter Oauth tokens
                     val tokens = Twitter.sessionTokenPair(request).get
                     //Launch Tweets recorder
                     Logger.debug("")
                     StreamRecorder.ref ! StartRecording(tokens, p.expression)
                     p
               }.fold(identity, p => Redirect(routes.Profiles.find(p.expression)))
      })
  }
  def find(term:String) = Action { implicit request =>
     Profile.byTerm(term)
           .toRight(NotFound)
           .fold(identity, p => Ok(views.html.profiles.stream("Now recording : " , p)))
  }

  /** ====== Define stream results  ====== **/
  val cometEnumeratee = Comet(callback = "window.parent.streamit")(Comet.CometMessage[Tweet](signer => {
    toJson(signer).toString
  }))

  def stream(term: String) = Action {
    import ProfileWorker._
    AsyncResult {
      implicit val timeout = Timeout(1 second)
      (ProfileWorker.ref ? Listen(term)).mapTo[Enumerator[Tweet]].asPromise.map {
        chunks =>
          {
            Ok.stream(chunks &> cometEnumeratee)
          }
      }
    }
  }
}