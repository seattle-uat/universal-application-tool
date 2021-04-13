package services.question;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import models.LifecycleStage;
import models.Question;
import repository.QuestionRepository;
import services.CiviFormError;
import services.ErrorAnd;
import services.Path;
import services.question.exceptions.InvalidPathException;
import services.question.exceptions.InvalidUpdateException;
import services.question.types.QuestionDefinition;

public final class QuestionServiceImpl implements QuestionService {

  private QuestionRepository questionRepository;

  @Inject
  public QuestionServiceImpl(QuestionRepository questionRepository) {
    this.questionRepository = checkNotNull(questionRepository);
  }

  @Override
  public boolean addTranslation(
      Path path, Locale locale, String questionText, Optional<String> questionHelpText)
      throws InvalidPathException {
    throw new java.lang.UnsupportedOperationException("Not supported yet.");
  }

  @Override
  public ErrorAnd<QuestionDefinition, CiviFormError> create(QuestionDefinition questionDefinition) {
    ImmutableSet<CiviFormError> errors = validateQuestion(questionDefinition);
    if (!errors.isEmpty()) {
      return ErrorAnd.error(errors);
    }
    Question question = questionRepository.insertQuestionSync(new Question(questionDefinition));
    return ErrorAnd.of(question.getQuestionDefinition());
  }

  @Override
  public CompletionStage<ReadOnlyQuestionService> getReadOnlyQuestionService() {
    return listQuestionDefinitionsAsync()
        .thenApply(questionDefinitions -> new ReadOnlyQuestionServiceImpl(questionDefinitions));
  }

  @Override
  public ErrorAnd<QuestionDefinition, CiviFormError> update(QuestionDefinition questionDefinition)
      throws InvalidUpdateException {
    if (!questionDefinition.isPersisted()) {
      throw new InvalidUpdateException("question definition is not persisted");
    }

    ImmutableSet<CiviFormError> validateErrors = validateQuestion(questionDefinition);

    Optional<Question> maybeQuestion =
        questionRepository.lookupQuestion(questionDefinition.getId()).toCompletableFuture().join();
    if (!maybeQuestion.isPresent()) {
      throw new InvalidUpdateException(
          String.format("question with id %d does not exist", questionDefinition.getId()));
    }
    Question question = maybeQuestion.get();
    ImmutableSet<CiviFormError> invariantErrors =
        validateQuestionInvariants(question.getQuestionDefinition(), questionDefinition);

    ImmutableSet<CiviFormError> errors =
        ImmutableSet.<CiviFormError>builder()
            .addAll(validateErrors)
            .addAll(invariantErrors)
            .build();
    if (!errors.isEmpty()) {
      return ErrorAnd.error(errors);
    }

    if (question.getLifecycleStage() == LifecycleStage.DELETED) {
      return ErrorAnd.error(
          ImmutableSet.of(
              CiviFormError.of(
                  String.format("Question %d was DELETED.", questionDefinition.getId()))));
    }
    // DRAFT, ACTIVE, or OBSOLETE question here.
    question = questionRepository.updateOrCreateDraft(questionDefinition);
    return ErrorAnd.of(question.getQuestionDefinition());
  }

  private CompletionStage<ImmutableList<QuestionDefinition>> listQuestionDefinitionsAsync() {
    return questionRepository
        .listQuestions()
        .thenApply(
            questions ->
                questions.stream()
                    .map(question -> question.getQuestionDefinition())
                    .collect(ImmutableList.toImmutableList()));
  }

  /** Validates a question and checks for path conflicts. */
  private ImmutableSet<CiviFormError> validateQuestion(QuestionDefinition questionDefinition) {
    ImmutableSet<CiviFormError> errors = questionDefinition.validate();
    if (!errors.isEmpty()) {
      return errors;
    }
    Optional<Question> maybeConflict =
        questionRepository.findPathConflictingQuestion(questionDefinition);
    if (maybeConflict.isPresent()) {
      Question conflict = maybeConflict.get();
      return ImmutableSet.of(
          CiviFormError.of(
              String.format(
                  "path '%s' conflicts with question id: %s",
                  questionDefinition.getPath(), conflict.id)));
    }
    return ImmutableSet.of();
  }

  /**
   * Validates that a question's updates do not change its invariants.
   *
   * <p>Question invariants are: name, repeater id, path, and type.
   */
  private ImmutableSet<CiviFormError> validateQuestionInvariants(
      QuestionDefinition questionDefinition, QuestionDefinition toUpdate) {
    ImmutableSet.Builder<CiviFormError> errors = new ImmutableSet.Builder<>();

    if (!questionDefinition.getName().equals(toUpdate.getName())) {
      errors.add(
          CiviFormError.of(
              String.format(
                  "question names mismatch: %s does not match %s",
                  questionDefinition.getName(), toUpdate.getName())));
    }

    if (!questionDefinition.getRepeaterId().equals(toUpdate.getRepeaterId())) {
      errors.add(
          CiviFormError.of(
              String.format(
                  "question repeater ids mismatch: %s does not match %s",
                  questionDefinition.getRepeaterId().map(String::valueOf).orElse("[no repeater]"),
                  toUpdate.getRepeaterId().map(String::valueOf).orElse("[no repeater]"))));
    }

    if (!questionDefinition.getPath().equals(toUpdate.getPath())) {
      errors.add(
          CiviFormError.of(
              String.format(
                  "question paths mismatch: %s does not match %s",
                  questionDefinition.getPath(), toUpdate.getPath())));
    }

    if (!questionDefinition.getQuestionType().equals(toUpdate.getQuestionType())) {
      errors.add(
          CiviFormError.of(
              String.format(
                  "question types mismatch: %s does not match %s",
                  questionDefinition.getQuestionType(), toUpdate.getQuestionType())));
    }
    return errors.build();
  }
}
