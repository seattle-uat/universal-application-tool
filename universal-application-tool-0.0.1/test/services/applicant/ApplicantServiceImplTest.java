package services.applicant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletionException;
import models.Applicant;
import org.junit.Before;
import org.junit.Test;
import repository.ApplicantRepository;
import repository.WithPostgresContainer;
import services.ErrorAnd;
import services.Path;
import services.applicant.question.Scalars;
import services.program.PathNotInBlockException;
import services.program.ProgramBlockNotFoundException;
import services.program.ProgramDefinition;
import services.program.ProgramNotFoundException;
import services.question.QuestionOption;
import services.question.QuestionService;
import services.question.types.CheckboxQuestionDefinition;
import services.question.types.NameQuestionDefinition;
import services.question.types.QuestionDefinition;
import support.ProgramBuilder;

public class ApplicantServiceImplTest extends WithPostgresContainer {

  private ApplicantServiceImpl subject;
  private QuestionService questionService;
  private QuestionDefinition questionDefinition;
  private ProgramDefinition programDefinition;
  private ApplicantRepository applicantRepository;

  @Before
  public void setUp() throws Exception {
    subject = instanceOf(ApplicantServiceImpl.class);
    questionService = instanceOf(QuestionService.class);
    applicantRepository = instanceOf(ApplicantRepository.class);
    createQuestions();
    createProgram();
  }

  @Test
  public void stageAndUpdateIfValid_emptySetOfUpdates_isNotAnErrorAndDoesNotChangeApplicant() {
    Applicant applicant = subject.createApplicant(1L).toCompletableFuture().join();
    ApplicantData applicantDataBefore = applicant.getApplicantData();

    ErrorAnd<ReadOnlyApplicantProgramService, Exception> errorAnd =
        subject
            .stageAndUpdateIfValid(
                applicant.id, programDefinition.id(), "1", ImmutableSet.<Update>builder().build())
            .toCompletableFuture()
            .join();

    ApplicantData applicantDataAfter =
        applicantRepository.lookupApplicantSync(applicant.id).get().getApplicantData();

    assertThat(applicantDataAfter).isEqualTo(applicantDataBefore);
    assertThat(errorAnd.getResult()).isInstanceOf(ReadOnlyApplicantProgramService.class);
    assertThat(errorAnd.isError()).isFalse();
  }

  @Test
  public void stageAndUpdateIfValid_withUpdates_isOk() {
    Applicant applicant = subject.createApplicant(1L).toCompletableFuture().join();

    ImmutableSet<Update> updates =
        ImmutableSet.of(
            Update.create(Path.create("applicant.applicant_name.first"), "Alice"),
            Update.create(Path.create("applicant.applicant_name.last"), "Doe"));

    ErrorAnd<ReadOnlyApplicantProgramService, Exception> errorAnd =
        subject
            .stageAndUpdateIfValid(applicant.id, programDefinition.id(), "1", updates)
            .toCompletableFuture()
            .join();

    assertThat(errorAnd.getResult()).isInstanceOf(ReadOnlyApplicantProgramService.class);
    assertThat(errorAnd.isError()).isFalse();

    ApplicantData applicantDataAfter =
        applicantRepository.lookupApplicantSync(applicant.id).get().getApplicantData();

    assertThat(applicantDataAfter.asJsonString()).contains("Alice", "Doe");
  }

  @Test
  public void stageAndUpdateIfValid_updatesMetadataForQuestionOnce() {
    Applicant applicant = subject.createApplicant(1L).toCompletableFuture().join();

    ImmutableSet<Update> updates =
        ImmutableSet.of(
            Update.create(Path.create("applicant.applicant_name.first"), "Alice"),
            Update.create(Path.create("applicant.applicant_name.last"), "Doe"));

    ErrorAnd<ReadOnlyApplicantProgramService, Exception> errorAnd =
        subject
            .stageAndUpdateIfValid(applicant.id, programDefinition.id(), "1", updates)
            .toCompletableFuture()
            .join();

    assertThat(errorAnd.isError()).isFalse();
    assertThat(errorAnd.getResult()).isInstanceOf(ReadOnlyApplicantProgramService.class);

    ApplicantData applicantDataAfter =
        applicantRepository.lookupApplicantSync(applicant.id).get().getApplicantData();

    Path programIdPath =
        Path.create("applicant.applicant_name." + Scalars.METADATA_UPDATED_PROGRAM_ID_KEY);
    Path timestampPath = Path.create("applicant.applicant_name." + Scalars.METADATA_UPDATED_AT_KEY);
    assertThat(applicantDataAfter.readLong(programIdPath)).hasValue(programDefinition.id());
    assertThat(applicantDataAfter.readLong(timestampPath)).isPresent();
  }

  @Test
  public void stageAndUpdateIfValid_rawUpdatesContainMultiSelectAnswers_isOk() throws Exception {
    QuestionDefinition multiSelectQuestion =
        questionService
            .create(
                new CheckboxQuestionDefinition(
                    "checkbox",
                    Path.create("applicant.checkbox"),
                    Optional.empty(),
                    "description",
                    ImmutableMap.of(Locale.US, "question?"),
                    ImmutableMap.of(Locale.US, "help text"),
                    ImmutableList.of(
                        QuestionOption.create(1L, ImmutableMap.of(Locale.US, "cat")),
                        QuestionOption.create(2L, ImmutableMap.of(Locale.US, "dog")))))
            .getResult();
    createProgram(multiSelectQuestion);

    Applicant applicant = subject.createApplicant(1L).toCompletableFuture().join();

    ImmutableMap<String, String> rawUpdates =
        ImmutableMap.of(
            "applicant.checkbox.selection[0]", "1",
            "applicant.checkbox.selection[1]", "2");

    ErrorAnd<ReadOnlyApplicantProgramService, Exception> errorAnd =
        subject
            .stageAndUpdateIfValid(applicant.id, programDefinition.id(), "1", rawUpdates)
            .toCompletableFuture()
            .join();

    assertThat(errorAnd.isError()).isFalse();
    assertThat(errorAnd.getResult()).isInstanceOf(ReadOnlyApplicantProgramService.class);

    ApplicantData applicantDataAfter =
        applicantRepository.lookupApplicantSync(applicant.id).get().getApplicantData();

    assertThat(applicantDataAfter.readList(Path.create("applicant.checkbox.selection")))
        .hasValue(ImmutableList.of(1L, 2L));
  }

  @Test
  public void stageAndUpdateIfValid_hasApplicantNotFoundException() {
    ImmutableSet<Update> updates = ImmutableSet.of();
    long badApplicantId = 1L;

    ErrorAnd<ReadOnlyApplicantProgramService, Exception> errorAnd =
        subject
            .stageAndUpdateIfValid(badApplicantId, programDefinition.id(), "1", updates)
            .toCompletableFuture()
            .join();

    assertThat(errorAnd.hasResult()).isFalse();
    assertThat(errorAnd.getErrors()).hasSize(1);
    assertThat(errorAnd.getErrors().asList().get(0)).isInstanceOf(ApplicantNotFoundException.class);
  }

  @Test
  public void stageAndUpdateIfValid_invalidProgram_throwsProgramNotFoundException() {
    Applicant applicant = subject.createApplicant(1L).toCompletableFuture().join();
    ImmutableSet<Update> updates = ImmutableSet.of();
    long badProgramId = programDefinition.id() + 1000L;

    Throwable thrown =
        catchThrowable(
            () ->
                subject
                    .stageAndUpdateIfValid(applicant.id, badProgramId, "1", updates)
                    .toCompletableFuture()
                    .join());

    assertThat(thrown).isInstanceOf(CompletionException.class);
    assertThat(thrown).hasCauseInstanceOf(ProgramNotFoundException.class);
  }

  @Test
  public void stageAndUpdateIfValid_invalidBlock_throwsRuntimeException() {
    Applicant applicant = subject.createApplicant(1L).toCompletableFuture().join();
    ImmutableSet<Update> updates = ImmutableSet.of();
    String badBlockId = "100";

    ErrorAnd<ReadOnlyApplicantProgramService, Exception> errorAnd =
        subject
            .stageAndUpdateIfValid(applicant.id, programDefinition.id(), badBlockId, updates)
            .toCompletableFuture()
            .join();

    assertThat(errorAnd.hasResult()).isFalse();
    assertThat(errorAnd.getErrors()).hasSize(1);
    assertThat(errorAnd.getErrors().asList().get(0))
        .isInstanceOf(ProgramBlockNotFoundException.class);
  }

  @Test
  public void stageAndUpdateIfValid_hasPathNotInBlockException() {
    Applicant applicant = subject.createApplicant(1L).toCompletableFuture().join();
    ImmutableSet<Update> updates =
        ImmutableSet.of(
            Update.create(Path.create("applicant.applicant_name.first"), "Alice"),
            Update.create(Path.create("this.is.not.in.block"), "Doe"));

    ErrorAnd<ReadOnlyApplicantProgramService, Exception> errorAnd =
        subject
            .stageAndUpdateIfValid(applicant.id, programDefinition.id(), "1", updates)
            .toCompletableFuture()
            .join();

    assertThat(errorAnd.hasResult()).isFalse();
    assertThat(errorAnd.getErrors()).hasSize(1);
    assertThat(errorAnd.getErrors().asList().get(0)).isInstanceOf(PathNotInBlockException.class);
  }

  @Test
  public void stageAndUpdateIfValid_hasIllegalArgumentExceptionForReservedScalarKeys() {
    Applicant applicant = subject.createApplicant(1L).toCompletableFuture().join();
    String reservedScalar = "applicant.name." + QuestionDefinition.METADATA_UPDATE_TIME_KEY;
    ImmutableMap<String, String> updates = ImmutableMap.of(reservedScalar, "12345");

    assertThatExceptionOfType(CompletionException.class)
        .isThrownBy(
            () ->
                subject
                    .stageAndUpdateIfValid(applicant.id, programDefinition.id(), "1", updates)
                    .toCompletableFuture()
                    .join())
        .withCauseInstanceOf(IllegalArgumentException.class)
        .withMessageContaining("Path contained reserved scalar key");
  }

  @Test
  public void createApplicant_createsANewApplicant() {
    Applicant applicant = subject.createApplicant(1l).toCompletableFuture().join();

    assertThat(applicant.id).isNotNull();
  }

  @Test
  public void createApplicant_ApplicantTime() {
    Applicant applicant = subject.createApplicant(1l).toCompletableFuture().join();

    Instant t = Instant.now();

    assertThat(applicant.getWhenCreated()).isNotNull();
    assertThat(applicant.getWhenCreated()).isBefore(t);
  }

  @Test
  public void getReadOnlyApplicantService_getsReadOnlyApplicantServiceForTheApplicantAndProgram() {
    Applicant applicant = subject.createApplicant(1l).toCompletableFuture().join();

    ReadOnlyApplicantProgramService roApplicantProgramService =
        subject
            .getReadOnlyApplicantProgramService(applicant.id, programDefinition.id())
            .toCompletableFuture()
            .join();

    assertThat(roApplicantProgramService).isInstanceOf(ReadOnlyApplicantProgramService.class);
  }

  private void createQuestions() {
    questionDefinition =
        questionService
            .create(
                new NameQuestionDefinition(
                    "applicant name",
                    Path.create("applicant.applicant_name"),
                    Optional.empty(),
                    "description",
                    ImmutableMap.of(Locale.US, "question?"),
                    ImmutableMap.of(Locale.US, "help text")))
            .getResult();
  }

  private void createProgram() {
    createProgram(questionDefinition);
  }

  private void createProgram(QuestionDefinition... questions) {
    programDefinition =
        ProgramBuilder.newDraftProgram("test program", "desc")
            .withBlock()
            .withQuestionDefinitions(ImmutableList.copyOf(questions))
            .buildDefinition();
  }
}
