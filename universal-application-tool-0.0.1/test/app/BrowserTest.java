package app;

import static org.assertj.core.api.Assertions.assertThat;
import static play.test.Helpers.fakeApplication;

import auth.Roles;
import com.google.common.collect.ImmutableMap;
import controllers.routes;
import java.util.Optional;
import models.Program;
import org.junit.Test;
import play.Application;
import play.test.Helpers;
import play.test.TestBrowser;

public class BrowserTest extends WithBrowserBase {

  @Override
  protected Application provideApplication() {
    return fakeApplication(
        ImmutableMap.of(
            "db.default.driver",
            "org.testcontainers.jdbc.ContainerDatabaseDriver",
            "db.default.url",
            // See WithPostgresContainer.java for explanation of this string.
            "jdbc:tc:postgresql:12.5:///databasename",
            "play.evolutions.db.default.enabled",
            "true"));
  }

  @Override
  protected TestBrowser provideBrowser(int port) {
    return Helpers.testBrowser(port);
  }

  @Test
  public void homePage() {
    browser.goTo(BASE_URL);
    assertThat(browser.pageSource()).contains("Your new application is ready.");
  }

  @Test
  public void applicantProgramList_selectApply_redirectsToEdit() {
    Program program = insertProgram("My Program");
    goTo(controllers.applicant.routes.ApplicantProgramsController.index(1L));
    assertThat(browser.pageSource()).contains("My Program");

    // Redirect when "apply" link is clicked.
    String applyLinkId = "#apply" + program.id;
    browser.$(applyLinkId).click();
    assertUrlEquals(controllers.applicant.routes.ApplicantProgramsController.edit(1L, program.id));
  }

  @Test
  public void noCredLogin() {
    goTo(routes.HomeController.loginForm(Optional.empty()));
    browser.$("#guest").click();
    // should be redirected to root.
    assertThat(browser.url()).isEmpty();
    assertThat(browser.pageSource()).contains("Your new application is ready.");

    goTo(routes.HomeController.secureIndex());
    assertThat(browser.pageSource()).contains("You are logged in.");

    goTo(routes.ProfileController.myProfile());
    assertThat(browser.pageSource()).contains("GuestClient");
    assertThat(browser.pageSource()).contains("{\"created_time\":");
    assertThat(browser.pageSource()).contains(Roles.ROLE_APPLICANT.toString());
  }

  private static Program insertProgram(String name) {
    Program program = new Program(name, "description");
    program.save();
    return program;
  }
}
