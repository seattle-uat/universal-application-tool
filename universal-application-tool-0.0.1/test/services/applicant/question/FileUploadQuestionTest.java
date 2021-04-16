package services.applicant.question;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.ImmutableMap;
import java.util.Locale;
import java.util.Optional;
import junitparams.JUnitParamsRunner;
import models.Applicant;
import models.LifecycleStage;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import services.Path;
import services.applicant.ApplicantData;
import services.question.types.FileUploadQuestionDefinition;

@RunWith(JUnitParamsRunner.class)
public class FileUploadQuestionTest {
  private static final FileUploadQuestionDefinition fileUploadQuestionDefinition =
      new FileUploadQuestionDefinition(
          1L,
          "question name",
          Path.create("applicant.my.path.name"),
          Optional.empty(),
          "description",
          LifecycleStage.ACTIVE,
          ImmutableMap.of(Locale.US, "question?"),
          ImmutableMap.of(Locale.US, "help text"));

  private Applicant applicant;
  private ApplicantData applicantData;

  @Before
  public void setUp() {
    applicant = new Applicant();
    applicantData = applicant.getApplicantData();
  }

  @Test
  public void withEmptyApplicantData() {
    ApplicantQuestion applicantQuestion =
        new ApplicantQuestion(fileUploadQuestionDefinition, applicantData);

    TextQuestion textQuestion = new TextQuestion(applicantQuestion);

    assertThat(textQuestion.hasTypeSpecificErrors()).isFalse();
    assertThat(textQuestion.hasQuestionErrors()).isFalse();
  }

  @Test
  public void withApplicantData_passesValidation() {
    applicantData.putString(fileUploadQuestionDefinition.getFileKeyPath(), "file-key");
    ApplicantQuestion applicantQuestion =
        new ApplicantQuestion(fileUploadQuestionDefinition, applicantData);

    FileUploadQuestion fileUploadQuestion = new FileUploadQuestion(applicantQuestion);

    assertThat(fileUploadQuestion.getFileKeyValue().get()).isEqualTo("file-key");
    assertThat(fileUploadQuestion.hasTypeSpecificErrors()).isFalse();
    assertThat(fileUploadQuestion.hasQuestionErrors()).isFalse();
  }
}