package services.applicant.question;

import com.google.common.collect.ImmutableSet;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import services.Path;
import services.applicant.ValidationErrorMessage;
import services.question.types.AddressQuestionDefinition;
import services.question.types.QuestionType;

public class AddressQuestion implements PresentsErrors {
  private static final String PO_BOX_REGEX =
      "(?i)(.*(P(OST|.)?\\s*((O(FF(ICE)?)?)?.?\\s*(B(IN|OX|.?)))+)).*";

  private final ApplicantQuestion applicantQuestion;
  private Optional<String> streetValue;
  private Optional<String> cityValue;
  private Optional<String> stateValue;
  private Optional<String> zipValue;

  public AddressQuestion(ApplicantQuestion applicantQuestion) {
    this.applicantQuestion = applicantQuestion;
    assertQuestionType();
  }

  @Override
  public boolean hasQuestionErrors() {
    return !getQuestionErrors().isEmpty();
  }

  public ImmutableSet<ValidationErrorMessage> getQuestionErrors() {
    if (!isAnswered()) {
      return ImmutableSet.of();
    }

    AddressQuestionDefinition definition = getQuestionDefinition();
    ImmutableSet.Builder<ValidationErrorMessage> errors = ImmutableSet.builder();

    if (definition.getDisallowPoBox() && getStreetValue().isPresent()) {
      Pattern poBoxPattern = Pattern.compile(PO_BOX_REGEX);
      Matcher poBoxMatcher = poBoxPattern.matcher(getStreetValue().get());

      if (poBoxMatcher.matches()) {
        return ImmutableSet.of(ValidationErrorMessage.noPoBox());
      }
    }

    return errors.build();
  }

  @Override
  public boolean hasTypeSpecificErrors() {
    return !getAllTypeSpecificErrors().isEmpty();
  }

  public ImmutableSet<ValidationErrorMessage> getAllTypeSpecificErrors() {
    return ImmutableSet.<ValidationErrorMessage>builder()
        .addAll(getAddressErrors())
        .addAll(getStreetErrors())
        .addAll(getCityErrors())
        .addAll(getStateErrors())
        .addAll(getZipErrors())
        .build();
  }

  public ImmutableSet<ValidationErrorMessage> getAddressErrors() {
    // TODO: Implement address validation.
    return ImmutableSet.of();
  }

  public ImmutableSet<ValidationErrorMessage> getStreetErrors() {
    if (isStreetAnswered() && getStreetValue().isEmpty()) {
      return ImmutableSet.of(ValidationErrorMessage.streetRequired());
    }

    return ImmutableSet.of();
  }

  public ImmutableSet<ValidationErrorMessage> getCityErrors() {
    if (isCityAnswered() && getCityValue().isEmpty()) {
      return ImmutableSet.of(ValidationErrorMessage.cityRequired());
    }

    return ImmutableSet.of();
  }

  public ImmutableSet<ValidationErrorMessage> getStateErrors() {
    // TODO: Validate state further.
    if (isStateAnswered() && getStateValue().isEmpty()) {
      return ImmutableSet.of(ValidationErrorMessage.stateRequired());
    }

    return ImmutableSet.of();
  }

  public ImmutableSet<ValidationErrorMessage> getZipErrors() {
    if (isZipAnswered()) {
      Optional<String> zipValue = getZipValue();
      if (zipValue.isEmpty()) {
        return ImmutableSet.of(ValidationErrorMessage.zipRequired());
      }

      Pattern pattern = Pattern.compile("^[0-9]{5}(?:-[0-9]{4})?$");
      Matcher matcher = pattern.matcher(zipValue.get());
      if (!matcher.matches()) {
        return ImmutableSet.of(ValidationErrorMessage.invalidZip());
      }
    }

    return ImmutableSet.of();
  }

  public Optional<String> getStreetValue() {
    if (streetValue != null) {
      return streetValue;
    }

    streetValue = applicantQuestion.getApplicantData().readString(getStreetPath());
    return streetValue;
  }

  public Optional<String> getCityValue() {
    if (cityValue != null) {
      return cityValue;
    }

    cityValue = applicantQuestion.getApplicantData().readString(getCityPath());
    return cityValue;
  }

  public Optional<String> getStateValue() {
    if (stateValue != null) {
      return stateValue;
    }

    stateValue = applicantQuestion.getApplicantData().readString(getStatePath());
    return stateValue;
  }

  public Optional<String> getZipValue() {
    if (zipValue != null) {
      return zipValue;
    }

    zipValue = applicantQuestion.getApplicantData().readString(getZipPath());
    return zipValue;
  }

  public void assertQuestionType() {
    if (!applicantQuestion.getType().equals(QuestionType.ADDRESS)) {
      throw new RuntimeException(
          String.format(
              "Question is not an ADDRESS question: %s (type: %s)",
              applicantQuestion.getQuestionDefinition().getPath(),
              applicantQuestion.getQuestionDefinition().getQuestionType()));
    }
  }

  public AddressQuestionDefinition getQuestionDefinition() {
    assertQuestionType();
    return (AddressQuestionDefinition) applicantQuestion.getQuestionDefinition();
  }

  public Path getStreetPath() {
    return getQuestionDefinition().getStreetPath();
  }

  public Path getCityPath() {
    return getQuestionDefinition().getCityPath();
  }

  public Path getStatePath() {
    return getQuestionDefinition().getStatePath();
  }

  public Path getZipPath() {
    return getQuestionDefinition().getZipPath();
  }

  private boolean isStreetAnswered() {
    return applicantQuestion.getApplicantData().hasPath(getStreetPath());
  }

  private boolean isCityAnswered() {
    return applicantQuestion.getApplicantData().hasPath(getCityPath());
  }

  private boolean isStateAnswered() {
    return applicantQuestion.getApplicantData().hasPath(getStatePath());
  }

  private boolean isZipAnswered() {
    return applicantQuestion.getApplicantData().hasPath(getZipPath());
  }

  /**
   * Returns true if any one of the address fields is answered. Returns false if all are not
   * answered.
   */
  @Override
  public boolean isAnswered() {
    return isStreetAnswered() || isCityAnswered() || isStateAnswered() || isZipAnswered();
  }
}
