package repository;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.concurrent.CompletableFuture.supplyAsync;

import io.ebean.Ebean;
import io.ebean.EbeanServer;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import javax.inject.Inject;
import javax.inject.Provider;
import models.Question;
import models.Version;
import play.db.ebean.EbeanConfig;
import services.question.exceptions.UnsupportedQuestionTypeException;
import services.question.types.QuestionDefinition;
import services.question.types.QuestionDefinitionBuilder;

public class QuestionRepository {

  private final EbeanServer ebeanServer;
  private final DatabaseExecutionContext executionContext;
  private final Provider<VersionRepository> versionRepositoryProvider;

  @Inject
  public QuestionRepository(
      EbeanConfig ebeanConfig,
      DatabaseExecutionContext executionContext,
      ProgramRepository programRepository,
      Provider<VersionRepository> versionRepositoryProvider) {
    this.ebeanServer = Ebean.getServer(checkNotNull(ebeanConfig).defaultServer());
    this.executionContext = checkNotNull(executionContext);
    this.versionRepositoryProvider = checkNotNull(versionRepositoryProvider);
  }

  public CompletionStage<Set<Question>> listQuestions() {
    return supplyAsync(() -> ebeanServer.find(Question.class).findSet(), executionContext);
  }

  public CompletionStage<Optional<Question>> lookupQuestion(long id) {
    return supplyAsync(
        () -> ebeanServer.find(Question.class).setId(id).findOneOrEmpty(), executionContext);
  }

  /**
   * Find and update the draft of the question with this name, if one already exists. Create a new
   * draft if there isn't one.
   */
  public Question updateOrCreateDraft(QuestionDefinition definition) {
    Version draftVersion = versionRepositoryProvider.get().getDraftVersion();
    Optional<Question> existingDraft = draftVersion.getQuestionByName(definition.getName());
    try {
      if (existingDraft.isPresent()) {
        Question updatedDraft =
            new Question(
                new QuestionDefinitionBuilder(definition).setId(existingDraft.get().id).build());
        this.updateQuestionSync(updatedDraft);
        return updatedDraft;
      } else {
        Question newDraft =
            new Question(new QuestionDefinitionBuilder(definition).setId(null).build());
        insertQuestionSync(newDraft);
        newDraft.addVersion(draftVersion);
        newDraft.save();
        versionRepositoryProvider.get().updateProgramsForNewDraftQuestion(definition.getId());
        return newDraft;
      }
    } catch (UnsupportedQuestionTypeException e) {
      // This should not be able to happen since the provided question definition is inherently
      // valid.
      // Throw runtime exception so callers don't have to deal with it.
      throw new RuntimeException(e);
    }
  }

  /**
   * Maybe find a {@link Question} that conflicts with {@link QuestionDefinition}.
   *
   * <p>This is intended to be used for new question definitions, since updates will collide with
   * themselves and previous versions, and new versions of an old question will conflict with the
   * old question.
   *
   * <p>Questions collide if they share a {@link QuestionDefinition#getQuestionPathSegment()} and
   * {@link QuestionDefinition#getRepeaterId()}.
   */
  public Optional<Question> findConflictingQuestion(QuestionDefinition newQuestionDefinition) {
    ConflictDetector conflictDetector =
        new ConflictDetector(
            newQuestionDefinition.getRepeaterId(), newQuestionDefinition.getQuestionPathSegment());
    ebeanServer
        .find(Question.class)
        .findEachWhile(question -> !conflictDetector.hasConflict(question));
    return conflictDetector.getConflictedQuestion();
  }

  private static class ConflictDetector {
    private Optional<Question> conflictedQuestion = Optional.empty();
    private final Optional<Long> repeaterId;
    private final String questionPathSegment;

    private ConflictDetector(Optional<Long> repeaterId, String questionPathSegment) {
      this.repeaterId = checkNotNull(repeaterId);
      this.questionPathSegment = checkNotNull(questionPathSegment);
    }

    private Optional<Question> getConflictedQuestion() {
      return conflictedQuestion;
    }

    private boolean hasConflict(Question question) {
      if (question.getQuestionDefinition().getRepeaterId().equals(repeaterId)
          && question
              .getQuestionDefinition()
              .getQuestionPathSegment()
              .equals(questionPathSegment)) {
        conflictedQuestion = Optional.of(question);
        return true;
      }
      return false;
    }
  }

  public CompletionStage<Optional<Question>> lookupQuestionByPath(String path) {
    return supplyAsync(
        () -> ebeanServer.find(Question.class).where().eq("path", path).findOneOrEmpty(),
        executionContext);
  }

  public CompletionStage<Question> insertQuestion(Question question) {
    return supplyAsync(
        () -> {
          ebeanServer.insert(question);
          return question;
        },
        executionContext);
  }

  public Question insertQuestionSync(Question question) {
    ebeanServer.insert(question);
    return question;
  }

  public CompletionStage<Question> updateQuestion(Question question) {
    return supplyAsync(
        () -> {
          ebeanServer.update(question);
          return question;
        },
        executionContext);
  }

  public Question updateQuestionSync(Question question) {
    ebeanServer.update(question);
    return question;
  }
}
