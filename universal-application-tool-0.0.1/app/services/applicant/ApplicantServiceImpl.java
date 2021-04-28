package services.applicant;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import java.time.Clock;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;
import javax.inject.Inject;
import models.Applicant;
import models.Application;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import play.libs.concurrent.HttpExecutionContext;
import repository.ApplicantRepository;
import services.ErrorAnd;
import services.Path;
import services.WellKnownPaths;
import services.applicant.question.Scalar;
import services.program.*;
import services.question.exceptions.UnsupportedScalarTypeException;
import services.question.types.ScalarType;

public class ApplicantServiceImpl implements ApplicantService {
  private final ApplicantRepository applicantRepository;
  private final ProgramService programService;
  private final Clock clock;
  private final HttpExecutionContext httpExecutionContext;
  private final Logger log = LoggerFactory.getLogger(ApplicantService.class);

  @Inject
  public ApplicantServiceImpl(
      ApplicantRepository applicantRepository,
      ProgramService programService,
      Clock clock,
      HttpExecutionContext httpExecutionContext) {
    this.applicantRepository = checkNotNull(applicantRepository);
    this.programService = checkNotNull(programService);
    this.clock = checkNotNull(clock);
    this.httpExecutionContext = checkNotNull(httpExecutionContext);
  }

  @Override
  public CompletionStage<Applicant> createApplicant(long userId) {
    Applicant applicant = new Applicant();
    return applicantRepository.insertApplicant(applicant).thenApply((unused) -> applicant);
  }

  @Override
  public CompletionStage<ReadOnlyApplicantProgramService> getReadOnlyApplicantProgramService(
      long applicantId, long programId) {
    CompletableFuture<Optional<Applicant>> applicantCompletableFuture =
        applicantRepository.lookupApplicant(applicantId).toCompletableFuture();
    CompletableFuture<ProgramDefinition> programDefinitionCompletableFuture =
        programService.getProgramDefinitionAsync(programId).toCompletableFuture();

    return CompletableFuture.allOf(applicantCompletableFuture, programDefinitionCompletableFuture)
        .thenApplyAsync(
            (v) -> {
              Applicant applicant = applicantCompletableFuture.join().get();
              ProgramDefinition programDefinition = programDefinitionCompletableFuture.join();

              return new ReadOnlyApplicantProgramServiceImpl(
                  applicant.getApplicantData(), programDefinition);
            },
            httpExecutionContext.current());
  }

  @Override
  public CompletionStage<ErrorAnd<ReadOnlyApplicantProgramService, Exception>>
      stageAndUpdateIfValid(
          long applicantId,
          long programId,
          String blockId,
          ImmutableMap<String, String> updateMap) {
    ImmutableSet<Update> updates =
        updateMap.entrySet().stream()
            .map(entry -> Update.create(Path.create(entry.getKey()), entry.getValue()))
            .collect(ImmutableSet.toImmutableSet());

    // Ensures updates do not collide with metadata scalars. "keyName[]" collides with "keyName".
    boolean updatePathsContainReservedKeys =
        !Sets.intersection(
                Scalar.getMetadataScalarKeys(),
                updates.stream()
                    .map(
                        update -> {
                          if (update.path().isArrayElement()) {
                            return update.path().withoutArrayReference().keyName();
                          }
                          return update.path().keyName();
                        })
                    .collect(Collectors.toSet()))
            .isEmpty();
    if (updatePathsContainReservedKeys) {
      return CompletableFuture.failedFuture(
          new IllegalArgumentException("Path contained reserved scalar key"));
    }

    return stageAndUpdateIfValid(applicantId, programId, blockId, updates);
  }

  protected CompletionStage<ErrorAnd<ReadOnlyApplicantProgramService, Exception>>
      stageAndUpdateIfValid(
          long applicantId, long programId, String blockId, ImmutableSet<Update> updates) {
    CompletableFuture<Optional<Applicant>> applicantCompletableFuture =
        applicantRepository.lookupApplicant(applicantId).toCompletableFuture();

    CompletableFuture<ProgramDefinition> programDefinitionCompletableFuture =
        programService.getProgramDefinitionAsync(programId).toCompletableFuture();

    return CompletableFuture.allOf(applicantCompletableFuture, programDefinitionCompletableFuture)
        .thenComposeAsync(
            (v) -> {
              Optional<Applicant> applicantMaybe = applicantCompletableFuture.join();
              if (applicantMaybe.isEmpty()) {
                return CompletableFuture.completedFuture(
                    ErrorAnd.error(ImmutableSet.of(new ApplicantNotFoundException(applicantId))));
              }
              Applicant applicant = applicantMaybe.get();

              // Create a ReadOnlyApplicantProgramService and get the current block.
              ProgramDefinition programDefinition = programDefinitionCompletableFuture.join();
              ReadOnlyApplicantProgramService readOnlyApplicantProgramServiceBeforeUpdate =
                  new ReadOnlyApplicantProgramServiceImpl(
                      applicant.getApplicantData(), programDefinition);
              Optional<Block> maybeBlockBeforeUpdate =
                  readOnlyApplicantProgramServiceBeforeUpdate.getBlock(blockId);
              if (maybeBlockBeforeUpdate.isEmpty()) {
                return CompletableFuture.completedFuture(
                    ErrorAnd.error(
                        ImmutableSet.of(new ProgramBlockNotFoundException(programId, blockId))));
              }
              Block blockBeforeUpdate = maybeBlockBeforeUpdate.get();

              UpdateMetadata updateMetadata = UpdateMetadata.create(programId, clock.millis());
              try {
                stageUpdates(
                    applicant.getApplicantData(), blockBeforeUpdate, updateMetadata, updates);
              } catch (UnsupportedScalarTypeException | PathNotInBlockException e) {
                return CompletableFuture.completedFuture(ErrorAnd.error(ImmutableSet.of(e)));
              }

              ReadOnlyApplicantProgramService roApplicantProgramService =
                  new ReadOnlyApplicantProgramServiceImpl(
                      applicant.getApplicantData(), programDefinition);

              Optional<Block> blockMaybe = roApplicantProgramService.getBlock(blockId);
              if (blockMaybe.isPresent() && !blockMaybe.get().hasErrors()) {
                return applicantRepository
                    .updateApplicant(applicant)
                    .thenApplyAsync(
                        (finishedSaving) -> ErrorAnd.of(roApplicantProgramService),
                        httpExecutionContext.current());
              }

              return CompletableFuture.completedFuture(ErrorAnd.of(roApplicantProgramService));
            },
            httpExecutionContext.current());
  }

  @Override
  public String applicantName(Application application) {
    try {
      String firstName =
          application.getApplicantData().readString(WellKnownPaths.APPLICANT_FIRST_NAME).get();
      String lastName =
          application.getApplicantData().readString(WellKnownPaths.APPLICANT_LAST_NAME).get();
      return String.format("%s, %s", lastName, firstName);
    } catch (NoSuchElementException e) {
      log.error("Application {} does not include an applicant name.");
      return "<Anonymous Applicant>";
    }
  }

  @Override
  public CompletionStage<ImmutableList<ProgramDefinition>> relevantPrograms(long applicantId) {
    return applicantRepository.programsForApplicant(applicantId);
  }

  /**
   * In-place update of {@link ApplicantData}. Adds program id and timestamp metadata with updates.
   *
   * @throws PathNotInBlockException if there are updates for questions that aren't in the block.
   */
  private void stageUpdates(
      ApplicantData applicantData,
      Block block,
      UpdateMetadata updateMetadata,
      ImmutableSet<Update> updates)
      throws UnsupportedScalarTypeException, PathNotInBlockException {
    ImmutableSet.Builder<Path> questionPaths = ImmutableSet.builder();
    for (Update update : updates) {
      ScalarType type =
          block
              .getScalarType(update.path())
              .orElseThrow(() -> new PathNotInBlockException(block.getId(), update.path()));
      questionPaths.add(update.path().parentPath());
      switch (type) {
        case STRING:
          applicantData.putString(update.path(), update.value());
          break;
        case LONG:
          applicantData.putLong(update.path(), update.value());
          break;
        default:
          throw new UnsupportedScalarTypeException(type);
      }
    }

    questionPaths
        .build()
        .forEach(path -> writeMetadataForPath(path, applicantData, updateMetadata));
  }

  private void writeMetadataForPath(Path path, ApplicantData data, UpdateMetadata updateMetadata) {
    data.putLong(path.join(Scalar.PROGRAM_UPDATED_IN), updateMetadata.programId());
    data.putLong(path.join(Scalar.UPDATED_AT), updateMetadata.updatedAt());
  }

  @AutoValue
  abstract static class UpdateMetadata {
    abstract long programId();

    abstract long updatedAt();

    static UpdateMetadata create(long programId, long updatedAt) {
      return new AutoValue_ApplicantServiceImpl_UpdateMetadata(programId, updatedAt);
    }
  }
}
