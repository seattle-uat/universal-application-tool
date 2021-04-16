package support;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.ebean.Ebean;
import io.ebean.EbeanServer;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import models.Account;
import models.Applicant;
import models.Application;
import models.LifecycleStage;
import models.Program;
import models.Question;
import play.db.ebean.EbeanConfig;
import play.inject.Injector;
import services.Path;
import services.program.DuplicateProgramQuestionException;
import services.program.ProgramBlockNotFoundException;
import services.program.ProgramNotFoundException;
import services.program.ProgramService;
import services.program.ProgramServiceImpl;
import services.question.QuestionService;
import services.question.exceptions.QuestionNotFoundException;
import services.question.types.QuestionDefinition;
import services.question.types.TextQuestionDefinition;

public class ResourceCreator {

  private final QuestionService questionService;
  private final ProgramService programService;
  private final EbeanServer ebeanServer;

  public ResourceCreator(Injector injector) {
    this.questionService = injector.instanceOf(QuestionService.class);
    this.programService = injector.instanceOf(ProgramServiceImpl.class);
    this.ebeanServer = Ebean.getServer(injector.instanceOf(EbeanConfig.class).defaultServer());
  }

  public void clearDatabase() {
    TestQuestionBank.reset();
    ebeanServer.truncate(
        Account.class, Applicant.class, Application.class, Program.class, Question.class);
  }

  public Question insertQuestion(String pathString) {
    return insertQuestion(pathString, 1L);
  }

  public Question insertQuestion(String pathString, long version) {
    String name = UUID.randomUUID().toString();
    return insertQuestion(pathString, version, name);
  }

  public Question insertQuestion(String pathString, long version, String name) {
    QuestionDefinition definition =
        new TextQuestionDefinition(
            version,
            name,
            Path.create(pathString),
            Optional.empty(),
            "",
            LifecycleStage.ACTIVE,
            ImmutableMap.of(),
            ImmutableMap.of());
    Question question = new Question(definition);
    question.save();
    return question;
  }

  public QuestionDefinition insertQuestionDefinition() {
    return questionService
        .create(
            new TextQuestionDefinition(
                1L,
                "question name",
                Path.create("applicant.my.path.name"),
                Optional.empty(),
                "description",
                LifecycleStage.ACTIVE,
                ImmutableMap.of(Locale.US, "question?"),
                ImmutableMap.of(Locale.US, "help text")))
        .getResult();
  }

  public Program insertProgram(String name, LifecycleStage lifecycleStage) {
    return ProgramBuilder.newProgram(name, "description")
        .withLifecycleStage(lifecycleStage)
        .build();
  }

  public Program insertProgram(String name) {
    return ProgramBuilder.newProgram(name, "description").build();
  }

  public Program insertProgram(Locale locale, String name, LifecycleStage lifecycleStage) {
    return ProgramBuilder.newProgram()
        .withLocalizedName(locale, name)
        .withLifecycleStage(lifecycleStage)
        .build();
  }

  public void addQuestionToProgram(Program program, Question question)
      throws DuplicateProgramQuestionException, QuestionNotFoundException,
          ProgramBlockNotFoundException, ProgramNotFoundException {
    programService.addQuestionsToBlock(program.id, 1L, ImmutableList.of(question.id));
  }

  public Applicant insertApplicant() {
    Applicant applicant = new Applicant();
    applicant.save();
    return applicant;
  }

  public Account insertAccount() {
    Account account = new Account();
    account.save();
    return account;
  }
}
