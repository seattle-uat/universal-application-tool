package services.applicant.question;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.util.Locale;
import java.util.Optional;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import models.Applicant;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import play.i18n.Lang;
import play.i18n.Messages;
import play.i18n.MessagesApi;
import repository.WithPostgresContainer;
import services.Path;
import services.applicant.ApplicantData;
import services.question.types.QuestionDefinition;
import services.question.types.RepeaterQuestionDefinition;
import support.QuestionAnswerer;
import support.TestQuestionBank;

@RunWith(JUnitParamsRunner.class)
public class EnumeratorQuestionTest extends WithPostgresContainer {
  private static final RepeaterQuestionDefinition enumeratorQuestionDefinition =
      new RepeaterQuestionDefinition(
          "household members",
          Path.create("applicant.household_members[]"),
          Optional.empty(),
          "description",
          ImmutableMap.of(Locale.US, "question?"),
          ImmutableMap.of(Locale.US, "help text"));

  private Applicant applicant;
  private ApplicantData applicantData;
  private Messages messages;

  private static final TestQuestionBank testQuestionBank = new TestQuestionBank(false);

  @Before
  public void setUp() {
    applicant = new Applicant();
    applicantData = applicant.getApplicantData();
    testQuestionBank.reset();
    messages = instanceOf(MessagesApi.class).preferred(ImmutableList.of(Lang.defaultLang()));
  }

  @Test
  public void withEmptyApplicantData() {
    ApplicantQuestion applicantQuestion =
        new ApplicantQuestion(
            enumeratorQuestionDefinition, applicantData, ApplicantData.APPLICANT_PATH);

    RepeaterQuestion enumeratorQuestion = new RepeaterQuestion(applicantQuestion);

    assertThat(enumeratorQuestion.isAnswered()).isFalse();
    assertThat(enumeratorQuestion.hasTypeSpecificErrors()).isFalse();
    assertThat(enumeratorQuestion.hasQuestionErrors()).isFalse();
  }

  @Test
  public void withApplicantData_passesValidation() {
    ApplicantQuestion applicantQuestion =
        new ApplicantQuestion(
            enumeratorQuestionDefinition, applicantData, ApplicantData.APPLICANT_PATH);
    QuestionAnswerer.answerEnumeratorQuestion(
        applicantData,
        applicantQuestion.getContextualizedPath(),
        ImmutableList.of("first", "second", "third"));

    RepeaterQuestion enumeratorQuestion = new RepeaterQuestion(applicantQuestion);

    assertThat(enumeratorQuestion.isAnswered()).isTrue();
    assertThat(enumeratorQuestion.getEntityNames()).contains("first", "second", "third");
    assertThat(enumeratorQuestion.hasTypeSpecificErrors()).isFalse();
    assertThat(enumeratorQuestion.hasQuestionErrors()).isFalse();
  }

  @Test
  @Parameters({"", " "})
  public void withBlankStrings_hasValidationErrors(String value) {
    ApplicantQuestion applicantQuestion =
        new ApplicantQuestion(
            enumeratorQuestionDefinition, applicantData, ApplicantData.APPLICANT_PATH);
    QuestionAnswerer.answerEnumeratorQuestion(
        applicantData, applicantQuestion.getContextualizedPath(), ImmutableList.of(value));

    RepeaterQuestion enumeratorQuestion = new RepeaterQuestion(applicantQuestion);

    assertThat(enumeratorQuestion.isAnswered()).isTrue();
    assertThat(enumeratorQuestion.getEntityNames()).contains(value);
    assertThat(enumeratorQuestion.hasTypeSpecificErrors()).isFalse();
    assertThat(enumeratorQuestion.hasQuestionErrors()).isTrue();
    assertThat(enumeratorQuestion.getQuestionErrors()).hasSize(1);
    assertThat(enumeratorQuestion.getQuestionErrors().asList().get(0).getMessage(messages))
        .isEqualTo("Please enter a value for each line.");
  }

  @Test
  public void getMetadata_forEnumeratorQuestion() {
    ApplicantData applicantData = new ApplicantData();
    QuestionDefinition enumeratorQuestionDefinition =
        testQuestionBank.applicantHouseholdMembers().getQuestionDefinition();
    Path enumeratorPath =
        ApplicantData.APPLICANT_PATH.join(enumeratorQuestionDefinition.getQuestionPathSegment());
    applicantData.putLong(enumeratorPath.atIndex(0).join(Scalar.UPDATED_AT), 123L);
    applicantData.putLong(enumeratorPath.atIndex(0).join(Scalar.PROGRAM_UPDATED_IN), 5L);

    ApplicantQuestion question =
        new ApplicantQuestion(
            enumeratorQuestionDefinition, applicantData, ApplicantData.APPLICANT_PATH);

    assertThat(question.getLastUpdatedTimeMetadata()).contains(123L);
    assertThat(question.getUpdatedInProgramMetadata()).contains(5L);
  }
}
