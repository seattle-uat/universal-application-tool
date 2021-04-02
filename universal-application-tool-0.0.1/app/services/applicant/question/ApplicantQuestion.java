package services.applicant.question;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Objects;
import java.util.Optional;
import javax.annotation.Nullable;
import services.Path;
import services.applicant.ApplicantData;
import services.question.QuestionDefinition;
import services.question.QuestionType;
import services.question.TranslationNotFoundException;

/**
 * Represents a question in the context of a specific applicant. Contains non-static inner classes
 * that represent the question as a specific question type (e.g. {@link NameQuestion}). These inner
 * classes provide access to the applicant's answer for the question. They can also implement
 * server-side validation logic.
 */
public class ApplicantQuestion {

  private final QuestionDefinition questionDefinition;
  private final ApplicantData applicantData;

  public ApplicantQuestion(QuestionDefinition questionDefinition, ApplicantData applicantData) {
    this.questionDefinition = checkNotNull(questionDefinition);
    this.applicantData = checkNotNull(applicantData);
  }

  public QuestionType getType() {
    return questionDefinition.getQuestionType();
  }

  public String getQuestionText() {
    try {
      return questionDefinition.getQuestionText(applicantData.preferredLocale());
    } catch (TranslationNotFoundException e) {
      throw new RuntimeException(e);
    }
  }

  public String getQuestionHelpText() {
    try {
      return questionDefinition.getQuestionHelpText(applicantData.preferredLocale());
    } catch (TranslationNotFoundException e) {
      throw new RuntimeException(e);
    }
  }

  public Path getPath() {
    return questionDefinition.getPath();
  }

  public boolean hasQuestionErrors() {
    return errorsPresenter().hasQuestionErrors();
  }

  public boolean hasErrors() {
    if (hasQuestionErrors()) {
      return true;
    }
    return errorsPresenter().hasTypeSpecificErrors();
  }

  public Optional<Long> getUpdatedInProgramMetadata() {
    return applicantData.readLong(questionDefinition.getProgramIdPath());
  }

  public Optional<Long> getLastUpdatedTimeMetadata() {
    return applicantData.readLong(questionDefinition.getLastUpdatedTimePath());
  }

  public AddressQuestion getAddressQuestion() {
    return new AddressQuestion(this);
  }

  public SingleSelectQuestion getSingleSelectQuestion() {
    return new SingleSelectQuestion(this);
  }

  public TextQuestion getTextQuestion() {
    return new TextQuestion(this);
  }

  public NameQuestion getNameQuestion() {
    return new NameQuestion(this);
  }

  public NumberQuestion getNumberQuestion() {
    return new NumberQuestion(this);
  }

  public PresentsErrors errorsPresenter() {
    switch (getType()) {
      case ADDRESS:
        return getAddressQuestion();
      case DROPDOWN:
        return getSingleSelectQuestion();
      case NAME:
        return getNameQuestion();
      case NUMBER:
        return getNumberQuestion();
      case TEXT:
        return getTextQuestion();
      default:
        throw new RuntimeException("Unrecognized question type: " + getType());
    }
  }

  @Override
  public boolean equals(@Nullable Object object) {
    if (object instanceof ApplicantQuestion) {
      ApplicantQuestion that = (ApplicantQuestion) object;
      return this.questionDefinition.equals(that.questionDefinition)
          && this.applicantData.equals(that.applicantData);
    }
    return false;
  }

  @Override
  public int hashCode() {
    return Objects.hash(questionDefinition, applicantData);
  }
}
