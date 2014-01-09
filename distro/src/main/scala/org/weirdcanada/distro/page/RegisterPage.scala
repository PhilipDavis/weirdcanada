package org.weirdcanada.distro.page

import net.liftweb.http.{DispatchSnippet, SHtml}
import net.liftweb.util.Helpers._
import org.weirdcanada.distro.service.Service
import net.liftweb.http.js._
import net.liftweb.http.js.JsCmds.FocusOnLoad
import org.weirdcanada.distro.data.Account
import net.liftweb.util.Props
import org.weirdcanada.distro.data.UserRole
import net.liftweb.http.S
import scala.xml.Text
import net.liftweb.common.{Full, Failure}
import net.liftweb.http.provider.HTTPCookie
import net.liftweb.common.Empty

class RegisterPage(service: Service) extends DistroPage {
  var emailAddress = ""
  var password = ""
  var firstName = ""
  var lastName = ""
  var organization = ""
  var address1 = ""
  var address2 = ""
  var city = ""
  var province = ""
  var postalCode = ""
  var country = ""
  var phoneNumber = ""
  var paypalEmail = ""
    
  def updateEmailAddress(newValue: String, fnAssign: String => Unit) = {
    // TODO: perform basic validation (e.g. matches regex?, no domain name misspellings?, already registered,? etc)
    fnAssign(newValue)
    JsCmds.Noop
  }
  
  def render = {
    "@email-address" #> FocusOnLoad(SHtml.ajaxText(emailAddress, updateEmailAddress(_, emailAddress = _), "class" -> "form-control", "placeholder" -> "thomas@soft.org")) &
    "@password" #> SHtml.ajaxText(password, password = _, "type" -> "password") &
    "@first-name" #> SHtml.ajaxText(firstName, firstName = _) &
    "@last-name" #> SHtml.ajaxText(lastName, lastName = _) &
    "@organization" #> SHtml.ajaxText(organization, organization = _) &
    "@address1" #> SHtml.ajaxText(address1, address1 = _) &
    "@address2" #> SHtml.ajaxText(address2, address2 = _) &
    "@city" #> SHtml.ajaxText(city, city = _, "type" -> "text", "class" -> "form-control") &
    "@province" #> SHtml.ajaxText(province, province = _, "type" -> "text", "class" -> "form-control") & // TODO: drop down for Canada & US, text entry for all others?
    "@postal-code" #> SHtml.ajaxText(postalCode, postalCode = _, "type" -> "text", "class" -> "form-control") & // TODO: validate for US & Canada?
    "@country" #> SHtml.ajaxText(country, country = _, "type" -> "text", "class" -> "form-control") & // TODO: drop down
    "@phone-number" #> SHtml.ajaxText(phoneNumber, phoneNumber = _, "type" -> "text", "class" -> "form-control", "placeholder" -> "416-555-5555") &
    "@paypal-email" #> SHtml.ajaxText(paypalEmail, updateEmailAddress(_, paypalEmail = _), "type" -> "text", "class" -> "form-control") &
    "@register" #> SHtml.ajaxButton("Create my account", validateAndCreate _, "type" -> "submit", "class" -> "btn btn-default", "onmouseup" -> "$('.error').remove(); $('.has-error').removeClass('has-error');")
  }

  val NorthAmericanPhoneNumber = """^1?\(?(\d{3})\)?[ .-]?(\d{3})[ .-]?(\d{4})$""".r
  val CanadianPostalCode = """(?i)^(\w\d\w\s?\d\w\d$)""".r
  val USZip = """^(\d{5}(?:[ -]\d{4})?)$""".r
  
  case class Rule(field: String, message: String, validate: () => Boolean)
  def validations = List(
    Rule("password", "password must be at least 8 characters", () => password.length >= 8),
    Rule("email-address", "email address is invalid", () => service.AccountManager.isValidEmailAddress(emailAddress)),
    Rule("email-address", "email address already registered", () => Account.findByEmailAddress(emailAddress).isEmpty),
    Rule("first-name", "first name must not be empty", () => firstName.length > 0),
    Rule("last-name", "last name must not be empty", () => lastName.length > 0),
    Rule("address1", "Address line 1 must not be empty", () => address1.length > 0),
    Rule("city", "city must not be empty", () => city.length > 0),
    Rule("province", "province must not be empty", () => province.length > 0),
    Rule("postal-code", "postal code must not be empty", () => postalCode.length > 0),
    Rule("postal-code", "postal code is invalid", () => postalCode match { case CanadianPostalCode(_) => true case USZip(_) => true case _ => false }),
    Rule("country", "country must not be empty", () => country.length > 0),
    Rule("phone-number", "phone number must not be empty", () => phoneNumber.length >= 10),
    Rule("phone-number", "expecting phone number in ###-###-#### format", () => phoneNumber match { case NorthAmericanPhoneNumber(_, _, _) => true case "" => true case _ => false}),
    Rule("paypal-email", "Paypal email address is invalid", () => service.AccountManager.isValidEmailAddress(paypalEmail))
  )
  
  def setError(field: String, message: String) = {
    /*val runString = """
      (function() {
        var d = document.getElementById('%s');
        d.className = d.className + " has-error";
      })();""".format(field)*/
    val runString = """var yadda = document.getElementById("%s"); yadda.className = yadda.className + " has-error";""".format(field)
    //val runString = """$('#%s').addClass('has-error');""".format(field)
    JsCmds.Run(runString) & 
    JsCmds.SetHtml(field + "-error", <span class="help-block error" >{message}</span>)
  }

  def validateAndCreate = {
    validations
      .filterNot(_.validate())
      .map(rule => setError(rule.field, rule.message))
      match {
        case Nil => // No failed validation rules
          createAccount
        case list =>
          list.foldLeft(JsCmds.Noop)(_ & _)
      }
    }

  def createAccount: JsCmd = {
    service.AccountManager.createAccount(emailAddress, password, firstName, lastName, organization, address1, address2, city, province, postalCode, country, phoneNumber, paypalEmail) ?~ "createAccount is Empty"
      match {
        case Full(account) =>
          println("**** AM I HERE?")
          service.SessionManager.current.logIn(account)
          val yearFromNow = 60 * 60 * 24 * 365
          S.addCookie(HTTPCookie("wcdid", Full(account.wcdid.is), if (Props.devMode) Empty else Full(S.hostName), Full("/"), Full(yearFromNow), Empty, Empty, Full(true)))
          S.redirectTo("/check-your-inbox")

        case Failure(msg, eBox, _) =>
          // TODO: report to airbrake
          eBox.map(_.printStackTrace)
          setError("register", "Unexpected error during registration. Please try again tomorrow.")
          
        case _ => sys.error("Ce n'est pas possible") // Empty is converted to Failure via ?~
      }
  }
}
